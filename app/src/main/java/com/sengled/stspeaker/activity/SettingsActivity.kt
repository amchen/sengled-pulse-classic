package com.sengled.stspeaker.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.sengled.pulsea66.view.SwitchView
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService
import com.sengled.stspeaker.widget.SettingsMenuPopupWindow

/**
 * Settings switch list: lamp "remember state", welcome screen, audio source.
 * Ported from the original com.sengled.stspeaker.activity.SettingsActivity
 * (layout a66_settings_activity).
 *
 * The memory switch sends the official management configuration command
 * (subsys 0 / cmd 11, item 0) via PulseService.setLampRemember().
 */
class SettingsActivity : Activity(), View.OnClickListener {

    companion object {
        private const val AUDIO_REQUEST_CODE = 16
    }

    private lateinit var btnMenu: ImageView
    private lateinit var welcomeSv: SwitchView
    private lateinit var autoSv: SwitchView
    private lateinit var memorySv: SwitchView
    private lateinit var updateNumView: TextView
    private lateinit var linkTypeTv: TextView
    private lateinit var prefs: SharedPreferences
    private var menuWindow: SettingsMenuPopupWindow? = null
    private var isFromRetrySearch = false

    private val stateListener: () -> Unit = { refreshMemoryRow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.a66_settings_activity)
        prefs = getSharedPreferences("pulse_prefs", MODE_PRIVATE)
        isFromRetrySearch = intent.getBooleanExtra("RetrySearch", false)
        initView()
        initData()
        PulseService.addStateListener(stateListener)
    }

    private fun initView() {
        welcomeSv = findViewById(R.id.welcome_sv)
        autoSv = findViewById(R.id.auto_sv)
        memorySv = findViewById(R.id.memory_sv)
        updateNumView = findViewById(R.id.tv_number)
        linkTypeTv = findViewById(R.id.tv_link_type)
        findViewById<View>(R.id.check_update_ln).setOnClickListener(this)
        btnMenu = findViewById(R.id.settings_back)
        btnMenu.setOnClickListener(this)
        btnMenu.setImageResource(R.drawable.btn_menu_normal)
        findViewById<View>(R.id.auto_ln).visibility = View.GONE
        findViewById<View>(R.id.memory_ln).visibility = View.GONE
        findViewById<View>(R.id.memory_iv).setOnClickListener { showRememberInfoDialog() }
    }

    private fun initData() {
        welcomeSv.setSwitchStatus(prefs.getBoolean("show_welcome", true))
        welcomeSv.setOnSwitchChangeListener(object : SwitchView.OnSwitchChangeListener {
            override fun onSwitchChanged(open: Boolean) {
                prefs.edit().putBoolean("show_welcome", open).apply()
            }
        })
        refreshMemoryRow()
        memorySv.setOnSwitchChangeListener(object : SwitchView.OnSwitchChangeListener {
            override fun onSwitchChanged(open: Boolean) {
                PulseService.setLampRemember(open)
                PulseService.getNodeInfo(0)?.rememberOn = open
            }
        })
        if (isFromRetrySearch) {
            findViewById<View>(R.id.welcome_ln).visibility = View.GONE
            findViewById<View>(R.id.memory_ln).visibility = View.GONE
            btnMenu.visibility = View.INVISIBLE
        }
        // Audio-box-only rows are hidden in this build.
        findViewById<View>(R.id.link_type_ln).visibility = View.GONE
        // No firmware-update backend in this build.
        updateNumView.visibility = View.GONE
    }

    private fun refreshMemoryRow() {
        val connected = PulseService.isConnected
        val memoryLn = findViewById<View>(R.id.memory_ln)
        val welcomeLn = findViewById<View>(R.id.welcome_ln)
        if (isFromRetrySearch) return
        if (connected) {
            memoryLn.visibility = View.VISIBLE
            welcomeLn.setBackgroundResource(R.drawable.a66_list_item_bottom)
            memorySv.setSwitchStatus(PulseService.getNodeInfo(0)?.rememberOn == true)
        } else {
            memoryLn.visibility = View.GONE
            welcomeLn.setBackgroundResource(R.drawable.a66_list_item_full)
        }
    }

    private fun showRememberInfoDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_remember, null)
        val ivCheckbox = view.findViewById<ImageView>(R.id.iv_checkbox)
        var showInfo = !prefs.getBoolean("remember_screen_label", false)
        ivCheckbox.setImageResource(
            if (showInfo) R.drawable.a66_checkbox_selector
            else R.drawable.a66_checkbox_normal_selector
        )
        val dialog = AlertDialog.Builder(this, R.style.MyDialog).setView(view).create()
        dialog.setCanceledOnTouchOutside(true)
        view.findViewById<View>(R.id.ll_checkbox).setOnClickListener {
            showInfo = !showInfo
            ivCheckbox.setImageResource(
                if (showInfo) R.drawable.a66_checkbox_selector
                else R.drawable.a66_checkbox_normal_selector
            )
            prefs.edit().putBoolean("remember_screen_label", !showInfo).apply()
        }
        view.findViewById<View>(R.id.btn_ok).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showSourceSwitchText(num: Int) {
        linkTypeTv.text = when (num) {
            0 -> getString(R.string.link_in)
            1 -> getString(R.string.bluetooth)
            else -> ""
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.check_update_ln -> {
                // No update backend in this build; row kept for UI parity.
            }
            R.id.link_type_ln -> {
                startActivityForResult(
                    Intent(this, AudioInputSwitchActivity::class.java), AUDIO_REQUEST_CODE
                )
            }
            else -> if (v == btnMenu) {
                menuWindow = SettingsMenuPopupWindow(this, 7)
                menuWindow?.showAsDropDown(findViewById(R.id.about_actionbar))
                menuWindow?.setOnDismissListener(PopupWindow.OnDismissListener {
                    btnMenu.setImageResource(R.drawable.btn_menu_normal)
                })
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AUDIO_REQUEST_CODE && resultCode == RESULT_OK &&
            data?.hasExtra(AudioInputSwitchActivity.EXTRA_AUDIO_SOURCE) == true
        ) {
            val num = data.getIntExtra(AudioInputSwitchActivity.EXTRA_AUDIO_SOURCE, -1)
            showSourceSwitchText(num)
            PulseService.audioSourceSwitchNum = num
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        PulseService.removeStateListener(stateListener)
        super.onDestroy()
    }
}
