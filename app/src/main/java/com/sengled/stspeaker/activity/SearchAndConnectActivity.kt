package com.sengled.stspeaker.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService
import com.sengled.stspeaker.update.InAppUpdateHelper

/**
 * Searching / auto-connect screen, ported from the original
 * com.sengled.stspeaker.activity.SearchAndConnectActivity.
 *
 * Same searching-animation layout; simplified connect path:
 * bonded master first, else the first discovered master. On success
 * goes to the light control panel, on failure/timeout to the retry
 * screen, and to the device list when several masters are found.
 */
class SearchAndConnectActivity : Activity() {

    companion object {
        private const val REQUEST_PERMISSIONS = 103
        private const val REQUEST_ENABLE_BT = 1
        private const val SCAN_TIMEOUT_MS = 25000L
        const val EXTRA_DEVICES = "currentDevices"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var discoveryReceiver: BroadcastReceiver? = null
    private var connecting = false
    private var finished = false
    private val foundDevices = ArrayList<BluetoothDevice>()

    private val timeoutRunnable = Runnable {
        if (!finished && !PulseService.isConnected) {
            stopScan()
            val bonded = try { PulseService.bondedMasters(this@SearchAndConnectActivity) } catch (_: Exception) { emptyList() }
            if (bonded.isNotEmpty()) {
                foundDevices.clear()
                foundDevices.addAll(bonded)
                gotoDevicesList()
            } else {
                gotoRetrySearch()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_search_and_connect)
        if (ensurePermissions()) {
            ensureBtEnabledAndScan()
        }
    }

    override fun onResume() {
        super.onResume()
        InAppUpdateHelper.resumeUpdateIfNeeded(this)
    }

    // ---- permissions ----

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.toTypedArray()
    }

    private fun ensurePermissions(): Boolean {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS &&
            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            ensureBtEnabledAndScan()
        } else {
            gotoRetrySearch()
        }
    }

    // ---- bluetooth enable / scan ----

    @SuppressLint("MissingPermission")
    private fun ensureBtEnabledAndScan() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            gotoRetrySearch()
            return
        }
        if (!adapter.isEnabled) {
            try {
                startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT
                )
            } catch (_: Exception) {
                gotoRetrySearch()
            }
            return
        }
        startScan()
    }

    @Deprecated("use registerForActivityResult in new code")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) startScan() else gotoRetrySearch()
        }
    }

    private fun startScan() {
        foundDevices.clear()
        // Bonded masters first, like the official app.
        val bonded = try {
            PulseService.bondedMasters(this)
        } catch (_: SecurityException) {
            emptyList()
        }
        if (bonded.isNotEmpty()) {
            if (bonded.size == 1) {
                connectTo(bonded[0])
            } else {
                foundDevices.addAll(bonded)
                gotoDevicesList()
            }
            return
        }
        try {
            discoveryReceiver = PulseService.startDiscovery(
                this,
                onDevice = { device ->
                    runOnUiThread {
                        if (finished || connecting) return@runOnUiThread
                        if (foundDevices.none { it.address == device.address }) {
                            foundDevices.add(device)
                        }
                        if (foundDevices.size == 1) {
                            // Auto-connect to the first master, like the original.
                            connectTo(device)
                        } else {
                            // Several masters: let the user pick.
                            stopScan()
                            gotoDevicesList()
                        }
                    }
                },
                onDone = {
                    runOnUiThread {
                        if (finished || connecting || PulseService.isConnected) return@runOnUiThread
                        when {
                            foundDevices.isEmpty() -> {
                                val b = try { PulseService.bondedMasters(this@SearchAndConnectActivity) } catch (_: Exception) { emptyList() }
                                if (b.isNotEmpty()) {
                                    foundDevices.addAll(b)
                                    gotoDevicesList()
                                } else {
                                    gotoRetrySearch()
                                }
                            }
                            foundDevices.size == 1 -> connectTo(foundDevices[0])
                            else -> gotoDevicesList()
                        }
                    }
                }
            )
        } catch (_: SecurityException) {
            gotoRetrySearch()
            return
        }
        handler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS)
    }

    private fun stopScan() {
        handler.removeCallbacks(timeoutRunnable)
        discoveryReceiver?.let {
            PulseService.cancelDiscovery(this, it)
            discoveryReceiver = null
        }
    }

    // ---- connect ----

    private fun connectTo(device: BluetoothDevice) {
        if (connecting || finished) return
        connecting = true
        stopScan()
        PulseService.connectMaster(device, object : PulseService.ConnectListener {
            override fun onConnected() {
                runOnUiThread { gotoControlPanel() }
            }

            override fun onFailed() {
                runOnUiThread {
                    connecting = false
                    val bonded = try { PulseService.bondedMasters(this@SearchAndConnectActivity) } catch (_: Exception) { emptyList() }
                    if (bonded.isNotEmpty() && !finished) {
                        foundDevices.clear()
                        foundDevices.addAll(bonded)
                        gotoDevicesList()
                    } else {
                        gotoRetrySearch()
                    }
                }
            }
        })
    }

    // ---- navigation ----

    private fun gotoControlPanel() {
        if (finished) return
        finished = true
        startActivity(Intent(this, LightControlPannelActivity::class.java))
        finish()
    }

    private fun gotoDevicesList() {
        if (finished) return
        finished = true
        val intent = Intent(this, DevicesListActivity::class.java)
        intent.putParcelableArrayListExtra(EXTRA_DEVICES, ArrayList(foundDevices))
        startActivity(intent)
        finish()
    }

    private fun gotoRetrySearch() {
        if (finished) return
        finished = true
        stopScan()
        startActivity(Intent(this, RetrySearchActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        discoveryReceiver?.let {
            PulseService.cancelDiscovery(this, it)
            discoveryReceiver = null
        }
        super.onDestroy()
    }
}
