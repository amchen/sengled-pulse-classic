package com.sengled.stspeaker.activity

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService

/**
 * Add-device screen: scans for Pulse master bulbs and connects on tap.
 * Minimal functional port (the original pairs satellites via QR/camera flows
 * that need cloud backends); registered in the manifest and opened from
 * SettingNameActivity's "add new device" row.
 */
class SettingAddDeviceActivity : Activity() {

    private lateinit var lvDevice: ListView
    private lateinit var adapter: DeviceRowAdapter
    private var receiver: BroadcastReceiver? = null
    private val found = ArrayList<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_add_device)
        findViewById<View>(R.id.btn_menu).setOnClickListener { finish() }
        findViewById<View>(R.id.rl_back).setOnClickListener { finish() }
        lvDevice = findViewById(R.id.lv_device)
        adapter = DeviceRowAdapter()
        lvDevice.adapter = adapter
        lvDevice.setOnItemClickListener { _, _, position, _ ->
            connectTo(found[position])
        }
        startScan()
    }

    private fun startScan() {
        found.clear()
        adapter.notifyDataSetChanged()
        receiver = PulseService.startDiscovery(
            this,
            onDevice = { dev ->
                runOnUiThread {
                    if (found.none { it.address == dev.address }) {
                        found.add(dev)
                        adapter.notifyDataSetChanged()
                    }
                }
            },
            onDone = { }
        )
    }

    private fun connectTo(device: BluetoothDevice) {
        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
        PulseService.cancelDiscovery(this, receiver)
        receiver = null
        PulseService.connectMaster(device, object : PulseService.ConnectListener {
            override fun onConnected() {
                runOnUiThread {
                    val intent = Intent(
                        this@SettingAddDeviceActivity,
                        LightControlPannelActivity::class.java
                    )
                    intent.flags = 131072
                    startActivity(intent)
                    finish()
                }
            }

            override fun onFailed() {
                runOnUiThread {
                    Toast.makeText(
                        this@SettingAddDeviceActivity,
                        R.string.add_device_failed, Toast.LENGTH_SHORT
                    ).show()
                    startScan()
                }
            }
        })
    }

    override fun onDestroy() {
        PulseService.cancelDiscovery(this, receiver)
        receiver = null
        super.onDestroy()
    }

    private inner class DeviceRowAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@SettingAddDeviceActivity)

        override fun getCount(): Int = found.size
        override fun getItem(position: Int): Any = found[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(
                android.R.layout.simple_list_item_1, parent, false
            )
            val tv = view.findViewById<TextView>(android.R.id.text1)
            val dev = found[position]
            val name = try {
                dev.name
            } catch (_: Exception) {
                null
            }
            tv.text = name ?: dev.address
            return view
        }
    }
}
