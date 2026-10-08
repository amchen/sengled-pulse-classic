package com.sengled.stspeaker.protocol

/**
 * Sengled Pulse command set, reverse-engineered from the official app
 * (com.sengled.stspeaker.service.command.*Cmd classes).
 *
 * Subsystem / command ids and payload semantics below match the official
 * app byte-for-byte (verified against LampOnOffCmd, LampBrightnessCmd,
 * SoundVolumeCmd, SoundMuteCmd, SoundEQCmd, SoundChannelCmd,
 * SetAudioSourceSwitchCmd, HandShakeCmd, QueryLampStateCmd,
 * QuerySpeakerStateCmd).
 */
object PulseCommands {

    // ---- subsystems ----
    const val SUBSYS_MGMT = 0
    const val SUBSYS_LAMP = 1
    const val SUBSYS_SOUND = 2
    const val SUBSYS_SOURCE = 4

    // ---- lamp ----
    const val LAMP_ONOFF = 0
    const val LAMP_BRIGHTNESS = 1
    const val LAMP_QUERY_STATE = 3

    const val LAMP_ON = 0      // note: inverted vs intuition, per LampOnOffCmd
    const val LAMP_OFF = 1

    const val SLOT_UNCHANGED = 255

    // ---- sound ----
    const val SOUND_CHANNEL = 0
    const val SOUND_EQ = 1
    const val SOUND_MUTE = 2
    const val SOUND_VOLUME = 3
    const val SOUND_QUERY_STATE = 4

    const val CHANNEL_LEFT = 0
    const val CHANNEL_RIGHT = 1
    const val CHANNEL_STEREO = 2

    const val EQ_NORMAL = 0
    const val EQ_POP = 1
    const val EQ_JAZZ = 2
    const val EQ_CLASSIC = 3
    const val EQ_ROCK = 4
    const val EQ_MOVIE = 5
    val EQ_NAMES = listOf("Normal", "Pop", "Jazz", "Classic", "Rock", "Movie")

    // ---- audio source ----
    const val SOURCE_SET = 6
    const val SOURCE_AUX = 0        // 3.5 mm input
    const val SOURCE_BLUETOOTH = 1  // Bluetooth A2DP

    // ---- management ----
    const val MGMT_HANDSHAKE = 1
    const val MGMT_SET_CONFIGURATION = 11

    /**
     * Frame body builder: [deviceId=0, 0x00 reserved, ...payload],
     * matching the official app's command templates byte-for-byte.
     */
    private fun body(vararg payload: Byte): ByteArray =
        byteArrayOf(0, 0, *payload)

    /** Sent once after the SPP socket connects (official app sends it to addr 0). */
    fun handshake(dest: Long = PulseFrame.DEST_MASTER): ByteArray =
        PulseFrame.build(SUBSYS_MGMT, MGMT_HANDSHAKE, destAddr = dest)

    /**
     * Management configuration item (official SetConfigurationCmd template):
     * subsys 0 / cmd 11, 20-byte frame, body [itemId, 0, value, 0].
     * Item 0 = "remember lamp state" (0 = off, 1 = on).
     */
    fun setConfiguration(itemId: Int, value: Int, dest: Long = PulseFrame.DEST_BROADCAST): ByteArray =
        PulseFrame.build(SUBSYS_MGMT, MGMT_SET_CONFIGURATION,
            byteArrayOf(itemId.toByte(), 0, value.toByte(), 0), dest)

    /** on=true -> light on. dest=-1 broadcast mirrors the app's main light panel. */
    fun lampOnOff(on: Boolean, dest: Long = PulseFrame.DEST_BROADCAST): ByteArray =
        // Official LampOnOffCmd template is 20 bytes: body is [devId, 0, flag, 0].
        PulseFrame.build(SUBSYS_LAMP, LAMP_ONOFF,
            body((if (on) LAMP_ON else LAMP_OFF).toByte(), 0), dest)

    /**
     * Brightness 0..100 per slot; 255 = leave unchanged.
     * Mirrors the official app: broadcast (-1) uses (value, 255);
     * a directly addressed node (0) uses (255, value).
     */
    fun lampBrightness(b1: Int, b2: Int, dest: Long = PulseFrame.DEST_BROADCAST): ByteArray =
        PulseFrame.build(SUBSYS_LAMP, LAMP_BRIGHTNESS,
            body(b1.toByte(), b2.toByte()), dest)

    /** 0 = left, 1 = right, 2 = stereo. */
    fun soundChannel(channel: Int, dest: Long = PulseFrame.DEST_BROADCAST): ByteArray =
        PulseFrame.build(SUBSYS_SOUND, SOUND_CHANNEL, body(channel.toByte()), dest)

    /** 0..5, see [EQ_NAMES]. */
    fun soundEQ(eq: Int, dest: Long = PulseFrame.DEST_BROADCAST): ByteArray =
        PulseFrame.build(SUBSYS_SOUND, SOUND_EQ, body(eq.toByte()), dest)

    fun soundMute(muted: Boolean, dest: Long = PulseFrame.DEST_BROADCAST): ByteArray =
        PulseFrame.build(SUBSYS_SOUND, SOUND_MUTE,
            body((if (muted) 1 else 0).toByte()), dest)

    /** Volume 0..100. Second payload byte is 0xFF in the official app. */
    fun soundVolume(volume: Int, dest: Long = PulseFrame.DEST_BROADCAST): ByteArray =
        PulseFrame.build(SUBSYS_SOUND, SOUND_VOLUME,
            body(volume.toByte(), 0xFF.toByte()), dest)

    /** 0 = 3.5mm AUX, 1 = Bluetooth. */
    fun audioSource(source: Int, dest: Long = PulseFrame.DEST_BROADCAST): ByteArray =
        PulseFrame.build(SUBSYS_SOURCE, SOURCE_SET, body(source.toByte()), dest)

    fun queryLampState(dest: Long = PulseFrame.DEST_MASTER): ByteArray =
        // Official template is 18 bytes: body is just [devId, 0x00], no payload.
        PulseFrame.build(SUBSYS_LAMP, LAMP_QUERY_STATE, byteArrayOf(0, 0), dest)

    fun querySpeakerState(dest: Long = PulseFrame.DEST_MASTER): ByteArray =
        PulseFrame.build(SUBSYS_SOUND, SOUND_QUERY_STATE, byteArrayOf(0, 0), dest)

    // ---- response parsing (absolute frame offsets, unescaped, flags included) ----
    // Verified against the official app's response filters: its mData[] drops the
    // opening 0x7E flag, so official offset N == raw offset N+1 here.
    data class LampState(val onOff: Int, val brightness1: Int, val brightness2: Int)
    data class SpeakerState(val channel: Int, val eq: Int, val mute: Int,
                            val volume1: Int, val volume2: Int)

    /** Response: [15]=on/off (0=on), [16]=brightness1, [17]=brightness2. */
    fun parseLampState(f: PulseFrame.Frame): LampState? {
        if (f.subsys != SUBSYS_LAMP || f.cmd != LAMP_QUERY_STATE || f.raw.size < 18) return null
        return LampState(f.u8(15), f.u8(16), f.u8(17))
    }

    /** Response: [15]=channel, [16]=eq, [17]=mute, [18]=vol1, [19]=vol2. */
    fun parseSpeakerState(f: PulseFrame.Frame): SpeakerState? {
        if (f.subsys != SUBSYS_SOUND || f.cmd != SOUND_QUERY_STATE || f.raw.size < 20) return null
        return SpeakerState(f.u8(15), f.u8(16), f.u8(17), f.u8(18), f.u8(19))
    }
}
