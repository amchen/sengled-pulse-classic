package com.sengled.stspeaker.activity

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.SeekBar
import com.sengled.stspeaker.NodeInfo
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService
import com.sengled.stspeaker.widget.SettingsMenuPopupWindow
import com.sengled.stspeaker.widget.ThTextView
import java.util.Timer
import java.util.TimerTask

/**
 * Lamp settings: per-node on/off + brightness rows.
 * Ported from the original com.sengled.stspeaker.activity.SettingLampActivity
 * (layout activity_setting_lamp_eui, rows adapter_led_list_item).
 */
class SettingLampActivity : Activity() {

    private lateinit var lvLamp: ListView
    private lateinit var btnMenu: Button
    private lateinit var lampAdapter: LampRowAdapter
    private lateinit var prefs: SharedPreferences
    private var menuWindow: SettingsMenuPopupWindow? = null

    private val stateListener: () -> Unit = {
        if (::lampAdapter.isInitialized) lampAdapter.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_setting_lamp_eui)
        prefs = getSharedPreferences("pulse_prefs", MODE_PRIVATE)
        initUi()
        PulseService.addStateListener(stateListener)
    }

    private fun initUi() {
        lvLamp = findViewById(R.id.lv_lamplist)
        lampAdapter = LampRowAdapter()
        lvLamp.adapter = lampAdapter
        btnMenu = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener {
            menuWindow = SettingsMenuPopupWindow(this, 0)
            menuWindow?.showAsDropDown(findViewById(R.id.control_actionbar))
            menuWindow?.setOnDismissListener(PopupWindow.OnDismissListener {
                btnMenu.setBackgroundResource(R.drawable.btn_menu_normal)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        lampAdapter.refresh()
    }

    override fun onDestroy() {
        PulseService.removeStateListener(stateListener)
        super.onDestroy()
    }

    private fun displayName(node: NodeInfo): String {
        val saved = prefs.getString("device_name", null)
        return if (!saved.isNullOrEmpty()) saved else node.name.ifEmpty { getString(R.string.app_name) }
    }

    private inner class LampRowAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@SettingLampActivity)
        private var rows: List<NodeInfo> = emptyList()
        private val handler = Handler(Looper.getMainLooper())
        private var lightnessTimer: Runnable? = null

        init {
            refresh()
        }

        fun refresh() {
            rows = PulseService.nodes.toList()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: inflater.inflate(R.layout.adapter_led_list_item, parent, false).also {
                    it.tag = RowHolder(
                        nameView = it.findViewById(R.id.lampname),
                        onOffBtn = it.findViewById(R.id.on_off),
                        seekBar = it.findViewById(R.id.sb_control),
                        lostView = it.findViewById(R.id.lost_connection)
                    )
                }
            val holder = view.tag as RowHolder
            val node = rows[position]
            holder.lostView.visibility = View.GONE
            holder.nameView.text = displayName(node)

            val on = node.lampOnOff == 0
            holder.onOffBtn.setImageResource(
                if (on) R.drawable.lightsetting_on else R.drawable.lightsetting_off
            )
            holder.seekBar.progress = node.brightness2
            holder.seekBar.isEnabled = on

            holder.onOffBtn.setOnClickListener {
                toggleLamp(node)
                refresh()
            }
            holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    startLightnessTimer(seekBar, node)
                    holder.nameView.setTextColor(resources.getColor(R.color.text_red))
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    holder.nameView.setTextColor(resources.getColor(R.color.text_plain_gray_deep))
                    lightnessTimer?.let { handler.removeCallbacks(it) }
                    lightnessTimer = null
                    sendBrightness(node, seekBar.progress)
                }
            })
            return view
        }

        private fun startLightnessTimer(seekBar: SeekBar, node: NodeInfo) {
            lightnessTimer?.let { handler.removeCallbacks(it) }
            val r = object : Runnable {
                override fun run() {
                    sendBrightness(node, seekBar.progress)
                    handler.postDelayed(this, 100)
                }
            }
            lightnessTimer = r
            handler.postDelayed(r, 100)
        }

        private fun sendBrightness(node: NodeInfo, progress: Int) {
            // Mirrors the original: addressed node -> (255, progress).
            PulseService.setLampBrightness(node.addr, 255, progress)
        }

        private fun toggleLamp(node: NodeInfo) {
            val newFlag = if (node.lampOnOff == 0) 1 else 0
            PulseService.setLampOnOff(node.addr, newFlag)
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    PulseService.setLampOnOff(node.addr, newFlag)
                }
            }, 200)
        }

        private inner class RowHolder(
            val nameView: ThTextView,
            val onOffBtn: ImageView,
            val seekBar: SeekBar,
            val lostView: ThTextView
        )
    }
}
