package kizzy.gateway

/**
 * Coarse connection status of the Discord gateway socket, surfaced to the Android layer so a
 * service can tell the user when the presence is silently reconnecting instead of appearing
 * frozen. Kept intentionally small — the notification only distinguishes "reconnecting" from
 * a healthy connection.
 */
enum class ConnectionState {
    /** A connect attempt is in flight (initial connect). */
    CONNECTING,

    /** Gateway session is live (READY or RESUMED received). */
    CONNECTED,

    /** The socket dropped and is retrying with backoff. */
    RECONNECTING
}
