package com.sengled.stspeaker.protocol

/**
 * HDLC-like frame codec for the Sengled Pulse master bulb.
 *
 * Reverse-engineered from the official Sengled Pulse app
 * (com.sengled.stspeaker, class com.sengled.stspeaker.service.command.BaseCmdPacket
 * and com.sengled.stspeaker.manager.connect.engine.a2dp.A2dpEngine).
 *
 * Frame layout (before HDLC escaping), all multi-byte fields little-endian:
 *
 *   [0]      0x7E frame flag
 *   [1..4]   source address (phone app uses 0xFFFFFFFE = -2)
 *   [5..8]   destination address (0xFFFFFFFF = -1 broadcast, 0 = master node)
 *   [9]      subsystem: 0 = management, 1 = lamp, 2 = sound, 4 = audio source
 *   [10]     0x00 reserved
 *   [11]     command id
 *   [12]     0x00 reserved
 *   [13..]   body: [13] = device id (0), [14] = 0x00, [15..] command payload.
 *            (The 16-byte management frames — handshake etc. — have an empty body.)
 *   [n-3]    checksum: sum of bytes [1..n-4] mod 256
 *   [n-2]    0x00 reserved
 *   [n-1]    0x7E frame flag
 *
 * HDLC escaping is applied to bytes [1..len-2] (flags untouched):
 *   0x7D -> 0x7D 0x5D,   0x7E -> 0x7D 0x5E
 */
object PulseFrame {

    const val FLAG: Byte = 0x7E
    private const val ESC: Byte = 0x7D

    const val SRC_PHONE: Long = -2L          // 0xFFFFFFFE, what the official app uses
    const val DEST_BROADCAST: Long = -1L     // 0xFFFFFFFF
    const val DEST_MASTER: Long = 0L

    data class Frame(
        val subsys: Int,
        val cmd: Int,
        /** Full unescaped frame including flags; parse state fields by absolute offset. */
        val raw: ByteArray,
        val srcAddr: Long,
        val destAddr: Long
    ) {
        /** Body bytes starting at offset 13 (device id, reserved, payload). */
        val body: ByteArray
            get() = if (raw.size - 3 > 13) raw.copyOfRange(13, raw.size - 3) else ByteArray(0)

        /** Command payload starting at offset 15 (for frames that carry one). */
        val payload: ByteArray
            get() = if (raw.size - 3 > 15) raw.copyOfRange(15, raw.size - 3) else ByteArray(0)

        fun u8(index: Int): Int = raw[index].toInt() and 0xFF
    }

    /**
     * Build a complete escaped frame ready to write to the SPP socket.
     * @param body frame body starting at offset 13: normally
     *   [deviceId=0, 0x00, ...command payload]; empty for the 16-byte
     *   management frames (handshake). Total length = 16 + body.size.
     */
    fun build(subsys: Int, cmd: Int, body: ByteArray = ByteArray(0),
              destAddr: Long = DEST_BROADCAST, srcAddr: Long = SRC_PHONE): ByteArray {
        val raw = ByteArray(16 + body.size)
        raw[0] = FLAG
        putU32LE(raw, 1, srcAddr)
        putU32LE(raw, 5, destAddr)
        raw[9] = subsys.toByte()
        raw[10] = 0
        raw[11] = cmd.toByte()
        raw[12] = 0
        System.arraycopy(body, 0, raw, 13, body.size)
        var sum = 0
        for (i in 1 until raw.size - 3) sum += raw[i].toInt() and 0xFF
        raw[raw.size - 3] = sum.toByte()
        raw[raw.size - 2] = 0
        raw[raw.size - 1] = FLAG
        return hdlcEscape(raw)
    }

    /** Parse one unescaped frame (flags included). Returns null if malformed. */
    fun parse(unescaped: ByteArray): Frame? {
        if (unescaped.size < 16) return null
        if (unescaped[0] != FLAG || unescaped[unescaped.size - 1] != FLAG) return null
        var sum = 0
        for (i in 1 until unescaped.size - 3) sum += unescaped[i].toInt() and 0xFF
        if ((sum.toByte()) != unescaped[unescaped.size - 3]) return null
        return Frame(
            subsys = unescaped[9].toInt() and 0xFF,
            cmd = unescaped[11].toInt() and 0xFF,
            raw = unescaped,
            srcAddr = getU32LE(unescaped, 1),
            destAddr = getU32LE(unescaped, 5)
        )
    }

    private fun putU32LE(a: ByteArray, off: Int, v: Long) {
        var x = v
        for (i in 0..3) {
            a[off + i] = (x and 0xFF).toByte()
            x = x shr 8
        }
    }

    private fun getU32LE(a: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (a[off + i].toLong() and 0xFF)
        return v
    }

    private fun hdlcEscape(data: ByteArray): ByteArray {
        var extra = 0
        for (i in 1 until data.size - 1) {
            if (data[i] == ESC || data[i] == FLAG) extra++
        }
        if (extra == 0) return data
        val out = ByteArray(data.size + extra)
        out[0] = data[0]
        var j = 1
        for (i in 1 until data.size - 1) {
            when (data[i]) {
                ESC -> { out[j++] = ESC; out[j++] = 0x5D }
                FLAG -> { out[j++] = ESC; out[j++] = 0x5E }
                else -> out[j++] = data[i]
            }
        }
        out[out.size - 1] = data[data.size - 1]
        return out
    }

    /** Reverse of [hdlcEscape]; input must include both flags. */
    fun hdlcUnescape(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var j = 0
        var i = 0
        while (i < data.size) {
            val b = data[i]
            if (b == ESC && i + 1 < data.size) {
                val n = data[i + 1]
                out[j++] = when (n) {
                    0x5D.toByte() -> ESC
                    0x5E.toByte() -> FLAG
                    else -> n
                }
                i += 2
            } else {
                out[j++] = b
                i++
            }
        }
        return out.copyOf(j)
    }

    fun toHex(data: ByteArray): String =
        data.joinToString(" ") { "%02X".format(it) }
}
