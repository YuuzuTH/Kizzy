package kizzy.gateway

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.interfaces.NoOpLogger
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kizzy.gateway.entities.Heartbeat
import kizzy.gateway.entities.Identify.Companion.toIdentifyPayload
import kizzy.gateway.entities.OutgoingPayload
import kizzy.gateway.entities.Payload
import kizzy.gateway.entities.PayloadData
import kizzy.gateway.entities.Ready
import kizzy.gateway.entities.Resume
import kizzy.gateway.entities.op.OpCode
import kizzy.gateway.entities.op.OpCode.*
import kizzy.gateway.entities.presence.Presence
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

open class DiscordWebSocketImpl(
    private val token: String,
    private val logger: Logger = NoOpLogger,
    // Invoked when Discord rejects the token (gateway close 4004). Lets the host
    // clear the stored token so the app can prompt a re-login instead of appearing
    // to work while nothing ever shows up on Discord.
    private val onAuthenticationFailed: () -> Unit = {},
    // Invoked on every coarse connection-state change so the host can surface a
    // "reconnecting…" status instead of the presence appearing frozen.
    private val onConnectionStateChanged: (ConnectionState) -> Unit = {},
    // Invoked at real gateway-session transitions (connect requested, session ready/resumed,
    // deliberate close, reconnect scheduled, fatal close) so the host can forward a compact,
    // structured trail to remote diagnostics — never per-heartbeat, only on state changes.
    // This module can't depend on the `data` module's LogWebhookReporter directly (that would
    // be a circular Gradle dependency: data already depends on gateway), so the host wires this
    // callback instead — same pattern as [onConnectionStateChanged] above.
    private val onGatewayEvent: (String) -> Unit = {}
) : DiscordWebSocket {
    private val gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json"
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    private var resumeGatewayUrl: String? = null
    private var heartbeatJob: Job? = null
    private var connected = false
    // True from the moment a connect attempt starts until it terminally resolves (deliberate
    // close or a fatal/unretryable close) — covers the whole retry backoff chain, not just the
    // initial handshake. Guards connect() against a caller (KizzyRPC.connectToWebSocket, called
    // on every fresh build()) invoking it again while a session is already live or a reconnect
    // is already pending: without this, overlapping connect() calls each bump connectEpoch and
    // open their own websocket, racing each other and producing the reconnect storm seen in
    // 2026-07-13 field logs (epoch jumping 3→4→5 within ~3s, presence flickering/going stale).
    private var connecting = false
    private var deliberateClose = false
    private var reconnectAttempts = 0
    private var connectEpoch = 0
    private var lastPresence: Presence? = null
    // Deliberately never closed/recreated (tried in v6.13.2.000, reverted in v6.13.2.002): calling
    // client.close() in close() tears down the whole underlying engine, which aborts whatever
    // connect() attempt happens to be using it at that exact moment — including a legitimate one
    // racing a spurious close() from Media RPC's blank-track detection. Field evidence showed
    // that as gateway close code 1006 ("closed without close frame") on nearly every reconnect,
    // and the connection never reaching READY again. The one long-lived client survives every
    // close()/connect() cycle on this object instead, same as the original 2023 code.
    private val client: HttpClient = HttpClient(CIO) {
        install(WebSockets)
        // CIO's default requestTimeout is 15s, applied to the whole session including a
        // WebSocket — fine for a normal HTTP request, fatal here: this account's READY
        // payload (guild list etc., a large account active since 2018) can take longer than
        // that to fully arrive on a mobile connection, so the engine kills the connection with
        // an abrupt 1006 right as it's waiting on READY, on essentially every fresh IDENTIFY.
        // A long-lived gateway session has no natural request boundary to time out against.
        engine { requestTimeout = 0 }
    }
    private val json = Json{
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Stored (not recomputed) so every launch{} in this class shares one real parent — a `get()`
    // that built `SupervisorJob() + Dispatchers.Default` fresh on every access (the original
    // upstream code) meant `this.cancel()` in close() cancelled a brand-new, childless job every
    // time instead of the job any coroutine was actually launched under, and `isActive` always
    // read a freshly-minted (hence always-active) job regardless of whether close() had run.
    // Net effect: close() never actually stopped the gateway receive loop or any reconnect
    // backoff in flight — it just kept running in the background, still mutating the shared
    // `websocket`/`connected`/`sessionId` fields, racing whatever connect() started next. Both
    // Media RPC and App Detection close()+rebuild on every pause/app-switch, so this is the
    // gateway instability (never showing up / dropping silently while still "detecting") that
    // the 2026-07-13 connecting-flag guard reduced the frequency of but didn't fix at the root.
    // A cancelled Job can't launch new children, so it's replaced with a fresh one in connect()
    // rather than here — that keeps isActive meaningfully false in the gap between close() and
    // the next connect(), instead of flipping back to "active" immediately.
    private var supervisorJob: CompletableJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = supervisorJob + Dispatchers.Default

    // Guards every read-then-write of connected/connecting/deliberateClose/connectEpoch/
    // supervisorJob together. close() and connect() are called from unrelated callers
    // (App Detection's app-switch thread vs. Media RPC's playback-callback thread) with no
    // other coordination — without a shared lock, close() could run its teardown between
    // connect()'s "not already connecting" check and connectInternal() actually setting
    // connecting=true, cancelling a job that hadn't been launched under yet and leaving
    // connecting permanently stuck true (nothing else ever resets it back to false).
    private val stateLock = Any()

    override suspend fun connect() {
        val epoch = synchronized(stateLock) {
            // Already connected, or a connect/retry chain is already in flight — a redundant
            // call here (e.g. KizzyRPC.connectToWebSocket() firing again from a second build()
            // before isRpcRunning() catches up) must no-op instead of racing the existing
            // session/backoff.
            if (connected || connecting) return@synchronized null
            // A prior close() cancelled the old job for good — a cancelled Job can never launch
            // new children, so a fresh one is needed before this connect can start anything.
            if (!supervisorJob.isActive) supervisorJob = SupervisorJob()
            connecting = true
            // A deliberate close is only sticky until the next explicit connect;
            // the epoch invalidates any reconnect attempt still sleeping in backoff
            // so two connect loops can never run at once.
            deliberateClose = false
            ++connectEpoch
        } ?: return
        launchConnectAttempt(epoch)
    }

    private suspend fun connectInternal() {
        val epoch = synchronized(stateLock) {
            connecting = true
            deliberateClose = false
            ++connectEpoch
        }
        launchConnectAttempt(epoch)
    }

    private fun launchConnectAttempt(epoch: Int) {
        onGatewayEvent("gateway_connect epoch=$epoch resuming=${resumeGatewayUrl != null}")
        launch {
            try {
                logger.i("Gateway","Connect called")
                val url = resumeGatewayUrl ?: gatewayUrl
                websocket = client.webSocketSession(url)

                // start receiving messages
                websocket!!.incoming.receiveAsFlow()
                    .collect {
                        when (it) {
                            is Frame.Text -> {
                                val jsonString = it.readText()
                                onMessage(jsonString)
                            }
                            else -> {}
                        }
                    }
                handleClose(epoch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Gateway",e.message?:"")
                scheduleReconnect(epoch)
            }
        }
    }

    private suspend fun handleClose(epoch: Int){
        heartbeatJob?.cancel()
        connected = false
        val close = websocket?.closeReason?.await()
        val code = close?.code?.toInt()
        logger.w("Gateway","Closed with code: $code, reason: ${close?.message}")
        when {
            deliberateClose || epoch != connectEpoch -> {}
            code == 4000 -> {
                onGatewayEvent("gateway_resume_close code=4000")
                delay(200.milliseconds)
                connectInternal()
            }
            // 4004 = auth failed, 4010..4014 = fatal (invalid intents etc.) — retrying is pointless
            code == 4004 || (code != null && code in 4010..4014) -> {
                logger.e("Gateway","Fatal close code $code — not reconnecting")
                onGatewayEvent("gateway_fatal_close code=$code")
                // 4004 = the account token is invalid/expired. Signal the host so it can
                // clear the token and surface a re-login prompt (otherwise the service keeps
                // "running" while Discord silently ignores every presence update).
                if (code == 4004) onAuthenticationFailed()
                close()
            }
            else -> scheduleReconnect(epoch)
        }
    }

    private suspend fun scheduleReconnect(epoch: Int) {
        if (deliberateClose || epoch != connectEpoch) return
        onConnectionStateChanged(ConnectionState.RECONNECTING)
        heartbeatJob?.cancel()
        connected = false
        runCatching { websocket?.close() }
        val delayMs = (2000L shl minOf(reconnectAttempts, 5)).coerceAtMost(60_000L)
        reconnectAttempts++
        // After a few failed resume attempts, fall back to a fresh identify
        if (reconnectAttempts >= 3) {
            resumeGatewayUrl = null
            sessionId = null
        }
        logger.w("Gateway","Connection lost — reconnecting in ${delayMs}ms (attempt $reconnectAttempts)")
        onGatewayEvent("gateway_reconnect_scheduled attempt=$reconnectAttempts delayMs=$delayMs freshIdentify=${reconnectAttempts >= 3}")
        delay(delayMs)
        if (!deliberateClose && epoch == connectEpoch) connectInternal()
    }

    private suspend fun onMessage(jsonString: String) {
        val payload = json.decodeFromString<Payload>(jsonString)
        logger.d("Gateway","Received op:${payload.op}, seq:${payload.s}, event :${payload.t}")

        payload.s?.let {
            sequence = it
        }
        when (payload.op) {
            DISPATCH -> payload.handleDispatch(jsonString)
            HEARTBEAT -> sendHeartBeat()
            RECONNECT -> reconnectWebSocket()
            INVALID_SESSION -> handleInvalidSession()
            HELLO -> handleHello(jsonString)
            else -> {}
        }
    }

    open fun Payload.handleDispatch(jsonString: String) {
        when (this.t.toString()) {
            "READY" -> {
                val ready = decodePayloadData<Ready>(jsonString) ?: return
                sessionId = ready.sessionId
                resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=10&encoding=json"
                logger.i("Gateway","resume_gateway_url updated to $resumeGatewayUrl")
                logger.i("Gateway","session_id updated to $sessionId")
                connected = true
                // Only a confirmed session counts as a successful reconnect
                reconnectAttempts = 0
                onConnectionStateChanged(ConnectionState.CONNECTED)
                onGatewayEvent("gateway_ready session_id=$sessionId")
                resendLastPresence()
                return
            }
            "RESUMED" -> {
                logger.i("Gateway","Session Resumed")
                connected = true
                reconnectAttempts = 0
                onConnectionStateChanged(ConnectionState.CONNECTED)
                onGatewayEvent("gateway_resumed session_id=$sessionId")
                resendLastPresence()
            }
            else -> {}
        }
    }

    private suspend inline fun handleInvalidSession() {
        logger.i("Gateway","Handling Invalid Session")
        logger.d("Gateway","Sending Identify after 150ms")
        delay(150)
        sendIdentify()
    }

    private suspend inline fun handleHello(jsonString: String) {
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        heartbeatInterval = decodePayloadData<Heartbeat>(jsonString)?.heartbeatInterval ?: return
        logger.i("Gateway","Setting heartbeatInterval= $heartbeatInterval")
        startHeartbeatJob(heartbeatInterval)
    }

    protected fun decodeReady(jsonString: String): Ready? {
        return decodePayloadData(jsonString)
    }

    private inline fun <reified T> decodePayloadData(jsonString: String): T? {
        return json.decodeFromString<PayloadData<T>>(jsonString).d
    }

    private suspend fun sendHeartBeat() {
        logger.i("Gateway","Sending $HEARTBEAT with seq: $sequence")
        send(
            op = HEARTBEAT,
            d = if (sequence == 0) "null" else sequence.toString(),
        )
    }

    private suspend inline fun reconnectWebSocket() {
        websocket?.close(
            CloseReason(
                code = 4000,
                message = "Attempting to reconnect"
            )
        )
    }

    private suspend fun sendIdentify() {
        logger.i("Gateway","Sending $IDENTIFY")
        send(
            op = IDENTIFY,
            d = token.toIdentifyPayload()
        )
    }

    private suspend fun sendResume() {
        logger.i("Gateway","Sending $RESUME")
        send(
            op = RESUME,
            d = Resume(
                seq = sequence,
                sessionId = sessionId,
                token = token
            )
        )
    }

    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = launch {
            while (isActive) {
                sendHeartBeat()
                delay(interval)
            }
        }
    }

    private fun isSocketConnectedToAccount(): Boolean {
        return connected && websocket?.isActive == true
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun isWebSocketConnected(): Boolean {
        return websocket?.incoming != null && websocket?.outgoing?.isClosedForSend == false
    }

    private suspend inline fun <reified T> send(op: OpCode, d: T?) {
        if (websocket?.isActive == true) {
            val payload = json.encodeToString(
                OutgoingPayload(
                    op = op,
                    d = d,
                )
            )
            websocket?.send(Frame.Text(payload))
        }
    }

    override fun close() {
        onGatewayEvent("gateway_close deliberate=true wasConnected=$connected")
        synchronized(stateLock) {
            deliberateClose = true
            connecting = false
            connectEpoch++
            // Cancels the actual job every launch{} in this class runs under (see the field's
            // kdoc) — the gateway receive loop and any reconnect backoff still in flight both
            // die here instead of lingering in the background to race the next connect(). Paired
            // with connect()'s guard check in the same lock so close() can never land between
            // that check and connecting being set true, which used to leave connecting stuck.
            supervisorJob.cancel()
        }
        reconnectAttempts = 0
        lastPresence = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        resumeGatewayUrl = null
        sessionId = null
        connected = false
        runBlocking {
            // runCatching (same as scheduleReconnect() above) so a throw from an already-broken
            // session can't skip the websocket = null below — isWebSocketConnected() reads that
            // reference directly, and a caller checking it right after close() (e.g. App Detection
            // deciding whether to update-in-place vs. rebuild after turning the elapsed-timer off)
            // must see "not connected" even when the underlying close errors out.
            runCatching { websocket?.close() }
            logger.e("Gateway","Connection to gateway closed")
        }
        websocket = null
    }

    private fun resendLastPresence() {
        val presence = lastPresence ?: return
        launch {
            logger.i("Gateway","Re-sending last presence after (re)connect")
            send(op = PRESENCE_UPDATE, d = presence)
        }
    }

    override suspend fun sendActivity(presence: Presence) {
        // Remember the latest presence so a (re)connect can replay it — a drop
        // here is recoverable because READY/RESUMED re-sends it.
        lastPresence = presence
        var waitedMs = 0L
        while (!isSocketConnectedToAccount()){
            if (deliberateClose) {
                logger.w("Gateway","Socket deliberately closed — presence will be replayed on next connect")
                return
            }
            delay(250.milliseconds)
            waitedMs += 250
            if (waitedMs >= 60_000) {
                logger.w("Gateway","Socket not connected after 60s — presence will be replayed on connect")
                return
            }
        }
        logger.i("Gateway","Sending $PRESENCE_UPDATE")
        send(
            op = PRESENCE_UPDATE,
            d = presence
        )
    }

}
