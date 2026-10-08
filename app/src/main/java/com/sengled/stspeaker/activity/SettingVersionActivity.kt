package com.sengled.stspeaker.activity

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import com.sengled.stspeaker.R
import com.sengled.stspeaker.widget.SettingsMenuPopupWindow
import com.sengled.stspeaker.widget.ThTextView

/**
 * About / version screen.
 * Ported from the original com.sengled.stspeaker.activity.SettingVersionActivity
 * (layout activity_setting_version).
 */
class SettingVersionActivity : Activity() {

    private lateinit var btnMenu: Button
    private lateinit var tvVersion: ThTextView
    private lateinit var rtWeb: RelativeLayout
    private lateinit var bottomView: LinearLayout
    private var menuWindow: SettingsMenuPopupWindow? = null
    private var isFromRetrySearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_setting_version)
        isFromRetrySearch = intent.getBooleanExtra("RetrySearch", false)
        initLayout()
    }

    private fun initLayout() {
        tvVersion = findViewById(R.id.tv_version_num)
        try {
            @Suppress("DEPRECATION")
            val pi = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = getString(R.string.version_num, pi.versionName)
        } catch (_: PackageManager.NameNotFoundException) {
        }

        btnMenu = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener {
            menuWindow = SettingsMenuPopupWindow(this, 5)
            menuWindow?.showAsDropDown(findViewById(R.id.control_actionbar))
            menuWindow?.setOnDismissListener(PopupWindow.OnDismissListener {
                btnMenu.setBackgroundResource(R.drawable.btn_menu_normal)
            })
        }
        if (isFromRetrySearch) {
            btnMenu.visibility = View.INVISIBLE
        }

        rtWeb = findViewById(R.id.rt_web)
        rtWeb.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.sengled.com"))
            startActivity(intent)
        }

        bottomView = findViewById(R.id.bottom_info)
        if (resources.displayMetrics.heightPixels <= 480) {
            bottomView.setPadding(10, 10, 10, 10)
        }
    }
}
