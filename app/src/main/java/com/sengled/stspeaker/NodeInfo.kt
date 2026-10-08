package com.sengled.stspeaker

import android.bluetooth.BluetoothDevice

/**
 * State of one Pulse node (the master bulb; satellites follow it).
 *
 * Field semantics mirror the official app's NodeInfo:
 *  lampOnOff: 0 = ON, 1 = OFF
 *  brightness1/2: 0..100 (255 = unchanged slot)
 *  channel: 0 = left, 1 = right, 2 = stereo
 *  eq: 0 = Normal, 1 = Pop, 2 = Jazz, 3 = Classic, 4 = Rock, 5 = Movie
 *  mute: 0 = unmuted, 1 = muted
 *  volume1/2: 0..100
 */
data class NodeInfo(
    val addr: Long = 0L,
    var name: String = "",
    var device: BluetoothDevice? = null,
    var lampOnOff: Int = 0,
    var brightness1: Int = 100,
    var brightness2: Int = 100,
    var channel: Int = 2,
    var eq: Int = 0,
    var mute: Int = 0,
    var volume1: Int = 70,
    var volume2: Int = 70,
    var rememberOn: Boolean = false,
    var actived: Boolean = true
) {
    fun isActived(): Boolean = actived
}
