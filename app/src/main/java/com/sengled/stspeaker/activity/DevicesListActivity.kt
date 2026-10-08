package com.sengled.stspeaker.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService

/**
 * Device picker, ported from the original
 * com.sengled.stspeaker.activity.DevicesListActivity.
 *
 * Lists discovered/bonded master bulbs using the original row layout;
 * tapping one connects and opens the light control panel.
 */
class DevicesListActivity : Activity() {

    private lateinit var devicesList: ListView
    private lateinit var currentDeviceName: TextView
    private lateinit var adapter: DeviceAdapter
    private val devices = ArrayList<BluetoothDevice>()
    private var discoveryReceiver: BroadcastReceiver? = null
    private var connecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_devices_list)

        findViewById<View>(R.id.deviceList_back).setOnClickListener { finish() }
        currentDeviceName = findViewById(R.id.currentdevice_name)
        devicesList = findViewById(R.id.list_devices)

        @Suppress("DEPRECATION")
        val initial: ArrayList<BluetoothDevice>? =
            intent.getParcelableArrayListExtra(SearchAndConnectActivity.EXTRA_DEVICES)
        initial?.forEach { addDevice(it) }
        try {
            PulseService.bondedMasters(this).forEach { addDevice(it) }
        } catch (_: SecurityException) {
        }

        adapter = DeviceAdapter(this, devices)
        devicesList.adapter = adapter
        devicesList.setOnItemClickListener { _, _, position, _ ->
            connectTo(devices[position])
        }

        refreshCurrentDevice()
        startDiscovery()
    }

    private fun addDevice(device: BluetoothDevice) {
        if (devices.none { it.address == device.address }) {
            devices.add(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshCurrentDevice() {
        val connected = PulseService.connectedDevice
        currentDeviceName.text = try {
            connected?.name ?: getString(R.string.app_name)
        } catch (_: SecurityException) {
            getString(R.string.app_name)
        } ?: getString(R.string.app_name)
    }

    private fun startDiscovery() {
        try {
            discoveryReceiver = PulseService.startDiscovery(
                this,
                onDevice = { device ->
                    runOnUiThread {
                        val before = devices.size
                        addDevice(device)
                        if (devices.size != before) adapter.notifyDataSetChanged()
                    }
                },
                onDone = {}
            )
        } catch (_: SecurityException) {
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        if (connecting) return
        connecting = true
        discoveryReceiver?.let {
            PulseService.cancelDiscovery(this, it)
            discoveryReceiver = null
        }
        PulseService.connectMaster(device, object : PulseService.ConnectListener {
            override fun onConnected() {
                runOnUiThread {
                    startActivity(Intent(this@DevicesListActivity, LightControlPannelActivity::class.java))
                    finish()
                }
            }

            override fun onFailed() {
                runOnUiThread { connecting = false }
            }
        })
    }

    override fun onDestroy() {
        discoveryReceiver?.let {
            PulseService.cancelDiscovery(this, it)
            discoveryReceiver = null
        }
        super.onDestroy()
    }

    private class DeviceAdapter(
        context: Context,
        private val devices: List<BluetoothDevice>
    ) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = devices.size
        override fun getItem(position: Int): BluetoothDevice = devices[position]
        override fun getItemId(position: Int): Long = position.toLong()

        @SuppressLint("MissingPermission")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.devicelist_item, parent, false)
            val icon: ImageView = view.findViewById(R.id.device_icon)
            val name: TextView = view.findViewById(R.id.device_name)
            val device = devices[position]
            icon.setImageResource(R.drawable.icon_pulse)
            name.text = try {
                device.name ?: device.address
            } catch (_: SecurityException) {
                device.address
            }
            return view
        }
    }
}
