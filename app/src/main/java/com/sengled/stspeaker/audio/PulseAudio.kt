package com.sengled.stspeaker.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri

/**
 * Speaker side of the Pulse bulb. The bulb is a plain Bluetooth A2DP speaker:
 * pair it once in system settings and Android routes all media audio to it.
 * This helper covers: A2DP connection status, media volume, and a tiny
 * built-in player for local audio files.
 */
@SuppressLint("MissingPermission") // callers guarantee BLUETOOTH_CONNECT
class PulseAudio(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var player: MediaPlayer? = null
    var onPlaybackState: ((playing: Boolean) -> Unit)? = null

    // ---------- volume ----------

    fun maxMusicVolume(): Int =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun musicVolume(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun setMusicVolume(index: Int) {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            index.coerceIn(0, maxMusicVolume()),
            0
        )
    }

    /** Converts 0..100 percentage scale to/from stream music volume */
    fun musicVolumePercent(): Int {
        val max = maxMusicVolume()
        if (max == 0) return 0
        return Math.round(musicVolume() * 100.0f / max).coerceIn(0, 100)
    }

    fun setMusicVolumePercent(percent: Int) {
        val max = maxMusicVolume()
        if (max == 0) return
        val targetIndex = Math.round(percent.coerceIn(0, 100) * max / 100.0f)
        setMusicVolume(targetIndex)
    }

    // ---------- A2DP status ----------

    /** Calls back with the name of the connected A2DP device, or null if none. */
    fun queryA2dpDevice(callback: (String?) -> Unit) {
        val adapter: BluetoothAdapter? =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null) {
            callback(null)
            return
        }
        try {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val a2dp = proxy as BluetoothA2dp
                    val name = a2dp.connectedDevices.firstOrNull()?.name
                    adapter.closeProfileProxy(profile, proxy)
                    callback(name)
                }

                override fun onServiceDisconnected(profile: Int) {
                    callback(null)
                }
            }, BluetoothProfile.A2DP)
        } catch (_: Exception) {
            callback(null)
        }
    }

    // ---------- tiny built-in player ----------

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    fun playUri(uri: Uri, onError: (String) -> Unit) {
        stop()
        try {
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnPreparedListener { it.start(); onPlaybackState?.invoke(true) }
                setOnCompletionListener { onPlaybackState?.invoke(false) }
                setOnErrorListener { _, what, extra ->
                    onError("Player error $what/$extra")
                    onPlaybackState?.invoke(false)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            onError("Cannot play file: ${e.message}")
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            onPlaybackState?.invoke(false)
        } else {
            p.start()
            onPlaybackState?.invoke(true)
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) { }
        try {
            player?.release()
        } catch (_: Exception) { }
        player = null
        onPlaybackState?.invoke(false)
    }

    /** Plays a short 880 Hz test tone through the bulb speaker (no file needed). */
    fun playTestTone(onError: (String) -> Unit) {
        stop()
        try {
            val sampleRate = 22050
            val seconds = 2
            val n = sampleRate * seconds
            val pcm = ByteArray(n * 2)
            for (i in 0 until n) {
                val s = (Math.sin(2 * Math.PI * 880 * i / sampleRate) * 12000).toInt()
                pcm[i * 2] = (s and 0xFF).toByte()
                pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            val tmp = java.io.File.createTempFile("pulse_tone", ".wav", context.cacheDir)
            writeWav(tmp, pcm, sampleRate)
            player = MediaPlayer().apply {
                setDataSource(tmp.absolutePath)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setOnPreparedListener { it.start(); onPlaybackState?.invoke(true) }
                setOnCompletionListener { onPlaybackState?.invoke(false); tmp.delete() }
                setOnErrorListener { _, what, extra ->
                    onError("Player error $what/$extra")
                    onPlaybackState?.invoke(false)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            onError("Cannot play test tone: ${e.message}")
        }
    }

    private fun writeWav(file: java.io.File, pcm: ByteArray, sampleRate: Int) {
        val header = java.nio.ByteBuffer.allocate(44)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2) // byte rate
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        file.writeBytes(header.array() + pcm)
    }
}
