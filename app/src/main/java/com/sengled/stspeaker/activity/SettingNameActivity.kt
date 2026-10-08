package com.sengled.stspeaker.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupWindow
import com.sengled.stspeaker.NodeInfo
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService
import com.sengled.stspeaker.widget.SettingsMenuPopupWindow
import com.sengled.stspeaker.widget.ThTextView

/**
 * Device name list: rename the master bulb (persisted locally).
 * Ported from the original com.sengled.stspeaker.activity.SettingNameActivity
 * (layout activity_setting_name, rows adapter_name_list,
 * rename dialog dialog_input_name).
 *
 * There is no rename command in the Pulse protocol, so the name is kept in
 * SharedPreferences ("device_name") and shown across the app.
 */
class SettingNameActivity : Activity() {

    private lateinit var lvName: ListView
    private lateinit var btnMenu: Button
    private lateinit var nameAdapter: NameRowAdapter
    private lateinit var prefs: SharedPreferences
    private var menuWindow: SettingsMenuPopupWindow? = null

    private val stateListener: () -> Unit = {
        if (::nameAdapter.isInitialized) nameAdapter.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_setting_name)
        prefs = getSharedPreferences("pulse_prefs", MODE_PRIVATE)
        initUi()
        PulseService.addStateListener(stateListener)
    }

    private fun initUi() {
        lvName = findViewById(R.id.lv_namelist)
        nameAdapter = NameRowAdapter()
        lvName.adapter = nameAdapter
        btnMenu = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener {
            menuWindow = SettingsMenuPopupWindow(this, 2)
            menuWindow?.showAsDropDown(findViewById(R.id.control_actionbar))
            menuWindow?.setOnDismissListener(PopupWindow.OnDismissListener {
                btnMenu.setBackgroundResource(R.drawable.btn_menu_normal)
            })
        }
        findViewById<View>(R.id.add_new_device).setOnClickListener {
            val intent = Intent(this, SettingAddDeviceActivity::class.java)
            intent.flags = 131072 // FLAG_ACTIVITY_SINGLE_TOP, as in the original
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        nameAdapter.refresh()
    }

    override fun onDestroy() {
        PulseService.removeStateListener(stateListener)
        super.onDestroy()
    }

    fun displayName(node: NodeInfo): String {
        val saved = prefs.getString("device_name", null)
        return if (!saved.isNullOrEmpty()) saved else node.name.ifEmpty { getString(R.string.app_name) }
    }

    private fun showRenameDialog(node: NodeInfo) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_input_name, null)
        val edtName = view.findViewById<EditText>(R.id.edt_name)
        val tvTitle = view.findViewById<ThTextView>(R.id.tv_title)
        val tvCount = view.findViewById<ThTextView>(R.id.tv_count)
        tvTitle.setText(R.string.name_setting)
        edtName.setText(displayName(node))
        edtName.setSelection(edtName.text.length)
        edtName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tvCount.text = "${s?.length ?: 0}"
            }
        })
        tvCount.text = "${edtName.text.length}"
        val dialog = AlertDialog.Builder(this, R.style.MyDialog)
            .setView(view)
            .create()
        dialog.setCanceledOnTouchOutside(true)
        view.findViewById<View>(R.id.btn_ok).setOnClickListener {
            val name = edtName.text.toString().trim()
            if (name.isNotEmpty()) {
                prefs.edit().putString("device_name", name).apply()
                node.name = name
                nameAdapter.refresh()
            }
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_cancle).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.rl_cancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private inner class NameRowAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@SettingNameActivity)
        private var rows: List<NodeInfo> = emptyList()

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
                ?: inflater.inflate(R.layout.adapter_name_list, parent, false).also {
                    it.tag = RowHolder(
                        nodeName = it.findViewById(R.id.nodename),
                        editRow = it.findViewById(R.id.ll_edit),
                        deleteBtn = it.findViewById(R.id.tv_delete),
                        lostTips = it.findViewById(R.id.lost_connection_tips)
                    )
                }
            val holder = view.tag as RowHolder
            val node = rows[position]
            holder.nodeName.text = displayName(node)
            holder.lostTips.visibility = View.GONE
            // The master bulb cannot be removed in this build.
            holder.deleteBtn.visibility = if (node.addr == PulseService.ADDR_MASTER) View.GONE else View.VISIBLE
            holder.editRow.setOnClickListener { showRenameDialog(node) }
            view.findViewById<View>(R.id.tv_edit)?.setOnClickListener { showRenameDialog(node) }
            return view
        }

        private inner class RowHolder(
            val nodeName: ThTextView,
            val editRow: View,
            val deleteBtn: View,
            val lostTips: View
        )
    }
}
