package kizzy.gateway.test

import kizzy.gateway.entities.op.OpCode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OpCodeSerializerTest {

    @Test
    fun `known opcodes decode to the matching enum`() {
        assertEquals(OpCode.DISPATCH, Json.decodeFromString<OpCode>("0"))
        assertEquals(OpCode.HELLO, Json.decodeFromString<OpCode>("10"))
        assertEquals(OpCode.HEARTBEAT_ACK, Json.decodeFromString<OpCode>("11"))
    }

    @Test
    fun `opcodes round-trip through encode and decode`() {
        for (op in OpCode.values()) {
            val encoded = Json.encodeToString(OpCode.serializer(), op)
            assertEquals(op, Json.decodeFromString<OpCode>(encoded))
        }
    }

    @Test
    fun `an unrecognised opcode falls back to UNKNOWN instead of throwing`() {
        // 42 is not a real gateway opcode. The old serializer threw here, which
        // aborted payload decoding and forced a reconnect.
        assertEquals(OpCode.UNKNOWN, Json.decodeFromString<OpCode>("42"))
    }
}
