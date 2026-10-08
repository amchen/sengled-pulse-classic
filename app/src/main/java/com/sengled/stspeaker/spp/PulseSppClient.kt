package com.sengled.stspeaker.spp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.sengled.stspeaker.protocol.PulseFrame
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Classic Bluetooth SPP client for the Sengled Pulse master bulb.
 *
 * The official app (A2dpEngine) opens an RFCOMM socket with the standard
 * SPP UUID and writes raw HDLC frames to the socket's OutputStream; a
 * reader thread parses HDLC frames from the InputStream (TransportProcessor).
 * This class mirrors that behavior.
 *
 * Modern Android Bluetooth stacks often fail the SDP-based
 * createRfcommSocketToServiceRecord() call against embedded modules with
 * "read failed, socket might closed or timeout". The connect() below therefore
 * tries several methods in order: secure SDP-based, insecure SDP-based, then
 * direct RFCOMM channel access via reflection (the well-known workaround),
 * each guarded by a watchdog so a hung attempt cannot stall the chain.
 */
class PulseSppClient {

    interface Listener {
        fun onLog(msg: String)
        fun onConnected(deviceName: String)
        fun onDisconnected()
        fun onFrame(frame: PulseFrame.Frame)
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "PulseSpp"
    }

    var listener: Listener? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var reader: Thread? = null

    val isConnected: Boolean get() = socket?.isConnected == true

    fun connect(device: BluetoothDevice) {
        disconnect()
        Thread({
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            } catch (_: Exception) { }
            log("Connecting SPP to ${device.name} (${device.address}) ...")
            try {
                val cached = device.uuids
                log("Cached SDP UUIDs: ${cached?.joinToString { it.toString() } ?: "<none cached>"}")
            } catch (e: Exception) {
                log("SDP UUID read failed: ${e.message}")
            }

            val s = trySocket("secure-SDP") { device.createRfcommSocketToServiceRecord(SPP_UUID) }
                ?: trySocket("insecure-SDP") { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) }
                ?: tryRfcommChannels(device)

            if (s != null && s.isConnected) {
                socket = s
                log("SPP connected")
                startReader(s.inputStream)
                listener?.onConnected(device.name ?: device.address)
            } else {
                log("SPP connect failed on all methods")
                listener?.onDisconnected()
            }
        }, "PulseSppConnect").apply { isDaemon = true }.start()
    }

    /**
     * Run one socket-creation + connect() attempt guarded by a watchdog.
     * Returns the connected socket, or null when the attempt failed/timed out.
     */
    private fun trySocket(
        label: String,
        timeoutMs: Long = 12_000,
        open: () -> BluetoothSocket
    ): BluetoothSocket? {
        var sock: BluetoothSocket? = null
        var err: String? = null
        val t = Thread({
            try {
                val s = open()
                sock = s
                s.connect()
            } catch (e: Exception) {
                err = e.message
            }
        }, "PulseSppTry-$label")
        t.isDaemon = true
        t.start()
        t.join(timeoutMs)
        if (t.isAlive) {
            log("$label: timed out after ${timeoutMs}ms, trying next method")
            try { sock?.close() } catch (_: Exception) { }
            t.interrupt()
            return null
        }
        val s = sock
        return if (s != null && s.isConnected) {
            log("$label: connected")
            s
        } else {
            log("$label: failed${if (err != null) ": $err" else ""}")
            try { s?.close() } catch (_: Exception) { }
            null
        }
    }

    /**
     * Direct RFCOMM channel access via reflection, bypassing SDP entirely.
     * This is the standard workaround for embedded Bluetooth modules whose
     * SDP responses confuse modern Android stacks.
     */
    private fun tryRfcommChannels(device: BluetoothDevice): BluetoothSocket? {
        for (ch in 1..6) {
            val s = trySocket("rfcomm-ch$ch", 8_000) {
                val m = device.javaClass.getMethod(
                    "createRfcommSocket", Int::class.javaPrimitiveType
                )
                @Suppress("UNCHECKED_CAST")
                m.invoke(device, ch) as BluetoothSocket
            }
            if (s != null) return s
        }
        return null
    }

    fun disconnect() {
        reader?.interrupt()
        reader = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        listener?.onDisconnected()
    }

    /** Write one already-framed packet (from [PulseCommands]) to the bulb. */
    fun sendFrame(frame: ByteArray) {
        val s = socket
        if (s == null || !s.isConnected) {
            log("Not connected; frame not sent")
            return
        }
        Thread({
            try {
                s.outputStream.write(frame)
                s.outputStream.flush()
                log("TX ${PulseFrame.toHex(frame)}")
            } catch (e: IOException) {
                log("TX failed: ${e.message}")
            }
        }, "PulseSppWrite").apply { isDaemon = true }.start()
    }

    private fun startReader(input: InputStream) {
        val t = Thread({
            // Mirrors TransportProcessor.readHdlcPkt(): frames are 0x7E ... 0x7E
            // with 0x7D-escape handling.
            val buf = ByteArray(512)
            var pos = 0
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val r = input.read()
                    if (r < 0) break
                    val b = r.toByte()
                    if (b == PulseFrame.FLAG && pos == 0) {
                        buf[pos++] = b
                        continue
                    }
                    if (pos == 0) continue // skip bytes before opening flag
                    if (b == PulseFrame.FLAG) {
                        buf[pos++] = b
                        val raw = buf.copyOf(pos)
                        pos = 0
                        val unescaped = PulseFrame.hdlcUnescape(raw)
                        val frame = PulseFrame.parse(unescaped)
                        if (frame != null) {
                            log("RX subsys=${frame.subsys} cmd=${frame.cmd} " +
                                "payload=${PulseFrame.toHex(frame.payload)}")
                            listener?.onFrame(frame)
                        } else {
                            log("RX malformed frame (${raw.size}b): ${PulseFrame.toHex(raw)}")
                        }
                        continue
                    }
                    if (pos >= buf.size) {
                        log("RX frame too long, resyncing")
                        pos = 0
                        continue
                    }
                    buf[pos++] = b
                }
            } catch (e: IOException) {
                log("SPP reader ended: ${e.message}")
            }
            listener?.onDisconnected()
        }, "PulseSppReader")
        t.isDaemon = true
        reader = t
        t.start()
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        listener?.onLog(msg)
    }
}
