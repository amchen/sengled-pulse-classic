package com.sengled.stspeaker.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sengled.stspeaker.NodeInfo
import com.sengled.stspeaker.protocol.PulseCommands
import com.sengled.stspeaker.protocol.PulseFrame
import com.sengled.stspeaker.spp.PulseSppClient

/**
 * Replacement for the official app's SLSpeakerService.
 *
 * Owns the SPP connection to the Pulse master bulb, the node list, and the
 * command API the activities call. All Bluetooth I/O runs off the UI thread;
 * state listeners are invoked on the main thread.
 *
 * Only the master bulb connects (satellites follow the master, exactly like
 * the official app).
 */
object PulseService {

    private const val TAG = "PulseService"

    const val ADDR_BROADCAST: Long = -1L
    const val ADDR_MASTER: Long = 0L
    const val BRIGHTNESS_EMPTY = 255

    private val spp = PulseSppClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Mirrors SLSpeakerService.mInitDevicelist — index 0 is the master. */
    val nodes = mutableListOf<NodeInfo>()

    private val stateListeners = mutableSetOf<() -> Unit>()

    var connectedDevice: BluetoothDevice? = null
        private set

    val isConnected: Boolean get() = spp.isConnected

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    /** Name filter supporting Sengled Pulse master bulbs and flexible naming. */
    fun isMasterDevice(device: BluetoothDevice): Boolean {
        @SuppressLint("MissingPermission")
        val name = try { device.name } catch (_: Exception) { null } ?: return true
        val lower = name.lowercase()
        return lower.contains("pulse") ||
            lower.contains("sengled") ||
            lower.contains("c01") ||
            lower.contains("br30") ||
            lower.contains("speaker") ||
            lower.contains("bulb")
    }

    @SuppressLint("MissingPermission")
    fun bondedMasters(context: Context? = null): List<BluetoothDevice> {
        return try {
            val adapter = if (context != null) {
                context.getSystemService(BluetoothManager::class.java)?.adapter
            } else {
                BluetoothAdapter.getDefaultAdapter()
            } ?: return emptyList()
            val bonded = adapter.bondedDevices?.toList() ?: emptyList()
            val filtered = bonded.filter { isMasterDevice(it) }
            if (filtered.isNotEmpty()) filtered else bonded
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Classic discovery for Pulse master bulbs. [onDevice] is called for each
     * matching device found; [onDone] when discovery finishes/cancelled.
     * Call [cancelDiscovery] to stop.
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(
        context: Context,
        onDevice: (BluetoothDevice) -> Unit,
        onDone: () -> Unit
    ): BroadcastReceiver {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        val seen = mutableSetOf<String>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val dev: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (dev != null && dev.address !in seen && isMasterDevice(dev)) {
                            seen.add(dev.address)
                            onDevice(dev)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onDone()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        })
        // Offer already-bonded masters immediately, like the official app.
        bondedMasters(context).forEach {
            if (it.address !in seen) {
                seen.add(it.address)
                onDevice(it)
            }
        }
        try {
            if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
            adapter?.startDiscovery()
        } catch (_: Exception) {
        }
        return receiver
    }

    @SuppressLint("MissingPermission")
    fun cancelDiscovery(context: Context, receiver: BroadcastReceiver?) {
        try {
            val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
            bluetoothManager?.adapter?.cancelDiscovery()
        } catch (_: Exception) {
        }
        try {
            if (receiver != null) context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    interface ConnectListener {
        fun onConnected()
        fun onFailed()
    }

    /**
     * Connect SPP to the master, run the official handshake (subsys 0/cmd 1
     * to address 0), then query lamp + speaker state.
     */
    fun connectMaster(device: BluetoothDevice, listener: ConnectListener) {
        disconnectQuiet()
        spp.listener = object : PulseSppClient.Listener {
            override fun onLog(msg: String) { Log.d(TAG, msg) }
            override fun onConnected(deviceName: String) {
                connectedDevice = device
                Thread({
                    try {
                        // Official handshake to the master node.
                        spp.sendFrame(PulseCommands.handshake(PulseFrame.DEST_MASTER))
                        Thread.sleep(300)
                        queryLampState()
                        Thread.sleep(300)
                        querySpeakerState()
                        mainHandler.post { listener.onConnected() }
                    } catch (_: Exception) {
                        mainHandler.post { listener.onFailed() }
                    }
                }, "PulseHandshake").apply { isDaemon = true }.start()
            }

            override fun onDisconnected() {
                connectedDevice = null
                mainHandler.post { listener.onFailed() }
            }

            override fun onFrame(frame: PulseFrame.Frame) {
                handleFrame(frame)
            }
        }
        nodes.clear()
        @SuppressLint("MissingPermission")
        val nodeName = try { device.name ?: "" } catch (_: Exception) { "" }
        nodes.add(NodeInfo(addr = ADDR_MASTER, name = nodeName, device = device))
        spp.connect(device)
    }

    private fun disconnectQuiet() {
        try {
            spp.listener = null
            spp.disconnect()
        } catch (_: Exception) {
        }
        connectedDevice = null
    }

    fun shutdown() {
        disconnectQuiet()
        nodes.clear()
    }

    // ------------------------------------------------------------------
    // Incoming frames -> node state
    // ------------------------------------------------------------------

    private fun handleFrame(frame: PulseFrame.Frame) {
        var changed = false
        val node = nodes.getOrNull(0)
        if (node != null) {
            when (frame.subsys to frame.cmd) {
                1 to 0, 1 to 1, 1 to 3 -> {
                    PulseCommands.parseLampState(frame)?.let {
                        node.lampOnOff = it.onOff
                        node.brightness1 = it.brightness1
                        node.brightness2 = it.brightness2
                        changed = true
                    }
                }
                2 to 0, 2 to 1, 2 to 2, 2 to 3, 2 to 4 -> {
                    PulseCommands.parseSpeakerState(frame)?.let {
                        node.channel = it.channel
                        node.eq = it.eq
                        node.mute = it.mute
                        node.volume1 = it.volume1
                        node.volume2 = it.volume2
                        changed = true
                    }
                }
            }
        }
        if (changed) notifyStateChanged()
    }

    fun addStateListener(l: () -> Unit) = stateListeners.add(l)
    fun removeStateListener(l: () -> Unit) = stateListeners.remove(l)

    private fun notifyStateChanged() {
        mainHandler.post { stateListeners.forEach { it() } }
    }

    // ------------------------------------------------------------------
    // Node helpers (mirror SLSpeakerService API used by the activities)
    // ------------------------------------------------------------------

    fun getNodeInfo(i: Int): NodeInfo? = nodes.getOrNull(i)

    /** True when only the master lamp node is present. */
    fun isSingleLight(): Boolean = false // speaker-bulb builds always show the settings gear

    /** The audio-box (Pulse Link) variant is not supported by this build. */
    fun isAudioBox(): Boolean = false

    fun getActivedNodes(): List<NodeInfo> = nodes.filter { it.isActived() }

    /** Product id reported by the master node (0 = unknown/regular master). */
    var productId: Int = 0

    /** Audio source switch count for the audio-box variant; -1 when not applicable. */
    var audioSourceSwitchNum: Int = -1

    /** 0 = any light on, 1 = all off (mirrors getLampOnOffFromAll). */
    fun getLampOnOffFromAll(): Int =
        if (nodes.any { it.isActived() && it.lampOnOff == 0 }) 0 else 1

    fun isAllLightOff(): Boolean = getLampOnOffFromAll() == 1

    fun getLampBrightness1(): Int = nodes.getOrNull(0)?.brightness1 ?: 100
    fun getLampBrightness2(): Int = nodes.getOrNull(0)?.brightness2 ?: 100
    fun getSoundVolume1(): Int = nodes.getOrNull(0)?.volume1 ?: 70
    fun getSoundVolume2(): Int = nodes.getOrNull(0)?.volume2 ?: 70
    fun getSoundMute(): Int = nodes.getOrNull(0)?.mute ?: 0
    fun getSoundChannel(): Int = nodes.getOrNull(0)?.channel ?: 2
    fun getSoundEQ(): Int = nodes.getOrNull(0)?.eq ?: 0

    // ------------------------------------------------------------------
    // Commands — all fire-and-forget on a worker thread, like the UI needs.
    // Each returns whether the frame was queued (i.e. we are connected).
    // ------------------------------------------------------------------

    private fun send(frame: ByteArray): Boolean {
        if (!spp.isConnected) {
            Log.w(TAG, "not connected; dropping frame")
            return false
        }
        spp.sendFrame(frame)
        return true
    }

    /** flag: 0 = ON, 1 = OFF (official semantics). */
    fun setLampOnOff(dest: Long, flag: Int): Boolean {
        nodes.forEach { node ->
            if (dest == ADDR_BROADCAST || node.addr == dest) {
                node.lampOnOff = flag
            }
        }
        return send(PulseCommands.lampOnOff(on = flag == 0, dest = dest))
    }

    fun setLampBrightness(addr: Long, b1: Int, b2: Int): Boolean {
        nodes.forEach { node ->
            if (addr == ADDR_BROADCAST || node.addr == addr) {
                if (b1 != BRIGHTNESS_EMPTY) {
                    node.brightness1 = b1
                    if (addr == ADDR_BROADCAST) node.brightness2 = b1
                }
                if (b2 != BRIGHTNESS_EMPTY) {
                    node.brightness2 = b2
                    if (addr == ADDR_BROADCAST) node.brightness1 = b2
                }
            }
        }
        return send(PulseCommands.lampBrightness(b1, b2, dest = addr))
    }

    /** Official "remember lamp state" toggle: subsys 0 / cmd 11, item 0. */
    fun setLampRemember(on: Boolean): Boolean =
        send(PulseCommands.setConfiguration(itemId = 0, value = if (on) 1 else 0))

    fun setSoundVolume(addr: Long, volume: Int): Boolean {
        nodes.forEach { node ->
            if (addr == ADDR_BROADCAST || node.addr == addr) {
                node.volume1 = volume
                node.volume2 = volume
            }
        }
        return send(PulseCommands.soundVolume(volume, dest = addr))
    }

    fun setSoundMute(addr: Long, mute: Int): Boolean {
        nodes.forEach { node ->
            if (addr == ADDR_BROADCAST || node.addr == addr) {
                node.mute = mute
            }
        }
        return send(PulseCommands.soundMute(muted = mute == 1, dest = addr))
    }

    fun setSoundEQ(addr: Long, eq: Int): Boolean {
        nodes.forEach { node ->
            if (addr == ADDR_BROADCAST || node.addr == addr) {
                node.eq = eq
            }
        }
        return send(PulseCommands.soundEQ(eq, dest = addr))
    }

    fun setSoundChannel(addr: Long, channel: Int): Boolean {
        nodes.forEach { node ->
            if (addr == ADDR_BROADCAST || node.addr == addr) {
                node.channel = channel
            }
        }
        return send(PulseCommands.soundChannel(channel, dest = addr))
    }

    /** source: 0 = AUX, 1 = Bluetooth (official semantics). */
    fun setAudioSource(addr: Long, source: Int): Boolean =
        send(PulseCommands.audioSource(source, dest = addr))

    fun queryLampState(): Boolean =
        send(PulseCommands.queryLampState(PulseFrame.DEST_MASTER))

    fun querySpeakerState(): Boolean =
        send(PulseCommands.querySpeakerState(PulseFrame.DEST_MASTER))
}
