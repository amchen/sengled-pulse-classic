package com.sengled.stspeaker.activity

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService
import com.sengled.stspeaker.widget.RetrySearchPopupWindow

/**
 * Retry screen shown when no master bulb is found, ported from the
 * original com.sengled.stspeaker.activity.RetrySearchActivity.
 *
 * Same layout (activity_retry_search): tapping the no-device image
 * searches again, the tips row opens the setup tips, and the menu
 * button shows the retry popup.
 */
class RetrySearchActivity : Activity(), View.OnClickListener {

    companion object {
        private const val SCAN_TIMEOUT_MS = 20000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var discoveryReceiver: BroadcastReceiver? = null
    private var connecting = false
    private var finished = false
    private var retrySearchPopupWindow: RetrySearchPopupWindow? = null

    private val timeoutRunnable = Runnable {
        if (!finished && !connecting) {
            stopScan()
            val bonded = try { PulseService.bondedMasters(this@RetrySearchActivity) } catch (_: Exception) { emptyList() }
            if (bonded.isNotEmpty()) {
                gotoDevicesList(bonded)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_retry_search)
        initView()
        if (isBtEnabled()) {
            startScan()
        }
    }

    private fun initView() {
        val gotoTips: LinearLayout = findViewById(R.id.goto_tips)
        gotoTips.setOnClickListener(this)
        val menuImg: Button = findViewById(R.id.menu_img)
        menuImg.setOnClickListener(this)
        val imgNoDevice: ImageView = findViewById(R.id.img_nodevice)
        imgNoDevice.setOnClickListener {
            val bonded = try { PulseService.bondedMasters(this) } catch (_: Exception) { emptyList() }
            if (bonded.isNotEmpty()) {
                gotoDevicesList(bonded)
            } else {
                startActivity(Intent(this, SearchAndConnectActivity::class.java))
                finish()
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.menu_img -> showMenuPopWindow()
            R.id.goto_tips -> {
                startActivity(Intent(this, SengledTipsActivity::class.java))
                finish()
            }
        }
    }

    private fun showMenuPopWindow() {
        retrySearchPopupWindow = RetrySearchPopupWindow(this)
        retrySearchPopupWindow?.showAsDropDown(findViewById(R.id.menu_img))
    }

    private fun isBtEnabled(): Boolean {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        return adapter != null && adapter.isEnabled
    }

    private fun startScan() {
        stopScan()
        // Bonded masters first.
        val bonded = try {
            PulseService.bondedMasters(this)
        } catch (_: SecurityException) {
            emptyList()
        }
        if (bonded.isNotEmpty()) {
            if (bonded.size == 1) {
                connectTo(bonded[0])
            } else {
                gotoDevicesList(bonded)
            }
            return
        }
        try {
            discoveryReceiver = PulseService.startDiscovery(
                this,
                onDevice = { device ->
                    runOnUiThread {
                        if (!finished && !connecting) connectTo(device)
                    }
                },
                onDone = {
                    runOnUiThread {
                        if (finished || connecting) return@runOnUiThread
                        val b = try { PulseService.bondedMasters(this@RetrySearchActivity) } catch (_: Exception) { emptyList() }
                        if (b.isNotEmpty()) {
                            gotoDevicesList(b)
                        }
                    }
                }
            )
        } catch (_: SecurityException) {
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

    private fun connectTo(device: BluetoothDevice) {
        if (connecting || finished) return
        connecting = true
        stopScan()
        PulseService.connectMaster(device, object : PulseService.ConnectListener {
            override fun onConnected() {
                runOnUiThread {
                    finished = true
                    startActivity(Intent(this@RetrySearchActivity, LightControlPannelActivity::class.java))
                    finish()
                }
            }

            override fun onFailed() {
                runOnUiThread {
                    connecting = false
                    val bonded = try { PulseService.bondedMasters() } catch (_: Exception) { emptyList() }
                    if (bonded.isNotEmpty() && !finished) {
                        gotoDevicesList(bonded)
                    }
                }
            }
        })
    }

    private fun gotoDevicesList(devices: List<BluetoothDevice>) {
        if (finished) return
        finished = true
        stopScan()
        val intent = Intent(this, DevicesListActivity::class.java)
        intent.putParcelableArrayListExtra(SearchAndConnectActivity.EXTRA_DEVICES, ArrayList(devices))
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        retrySearchPopupWindow?.dismiss()
        handler.removeCallbacksAndMessages(null)
        stopScan()
        super.onDestroy()
    }
}
