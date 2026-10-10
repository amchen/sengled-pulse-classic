package com.sengled.stspeaker.widget

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.sengled.stspeaker.R
import com.sengled.stspeaker.activity.LightControlPannelActivity
import com.sengled.stspeaker.activity.SettingLampActivity
import com.sengled.stspeaker.activity.SettingNameActivity
import com.sengled.stspeaker.activity.SettingPlayerActivity
import com.sengled.stspeaker.activity.SettingVersionActivity
import com.sengled.stspeaker.activity.SettingsActivity
import com.sengled.stspeaker.activity.SoundControlPannelActivity
import com.sengled.stspeaker.config.MenuConfig
import com.sengled.stspeaker.service.PulseService

/**
 * Full-screen settings menu, ported from the original
 * com.sengled.stspeaker.widget.SettingsMenuPopupWindow.
 */
class SettingsMenuPopupWindow(
    private val mContext: Activity,
    index: Int
) : PopupWindow(mContext), View.OnClickListener {

    private var anchor: View? = null
    private val ivDots = arrayOfNulls<View>(7)
    private val mMenuView: View =
        (mContext.getSystemService("layout_inflater") as LayoutInflater)
            .inflate(R.layout.popupwindow_settings, null)
    private val mUpdateNumView: TextView = mMenuView.findViewById(R.id.tv_number)

    init {
        mMenuView.findViewById<View>(R.id.pop_main_item).setOnClickListener(this)
        mMenuView.findViewById<View>(R.id.pop_about_item).setOnClickListener(this)
        mMenuView.findViewById<View>(R.id.pop_add_item).setOnClickListener(this)
        mMenuView.findViewById<View>(R.id.pop_editname_item).setOnClickListener(this)
        mMenuView.findViewById<View>(R.id.pop_refresh_item).setOnClickListener(this)
        mMenuView.findViewById<View>(R.id.pop_play_item).setOnClickListener(this)
        mMenuView.findViewById<View>(R.id.pop_setttings_item).setOnClickListener(this)
        mMenuView.findViewById<View>(R.id.pop_choose_advice_item).setOnClickListener(this)
        val ucenter = mMenuView.findViewById<View>(R.id.pop_ucenter_item)
        ucenter.visibility = View.GONE // user center is a cloud feature, not in this build
        val editNameItem = mMenuView.findViewById<View>(R.id.pop_editname_item)
        val playItem = mMenuView.findViewById<View>(R.id.pop_play_item)
        if (!MenuConfig.isDevicesManagementEnabled(mContext) || PulseService.isSingleLight()) {
            editNameItem.visibility = View.GONE
        }
        if (!MenuConfig.isLaunchMediaAppsEnabled(mContext) || PulseService.isSingleLight()) {
            playItem.visibility = View.GONE
        }
        mMenuView.findViewById<View>(R.id.bt_cancle).setOnClickListener { dismiss() }
        for (i in 1..7) {
            val dotId = mContext.resources.getIdentifier("iv_dot$i", "id", mContext.packageName)
            if (dotId != 0) ivDots[i - 1] = mMenuView.findViewById(dotId)
        }
        setCurrentPos(index)
        contentView = mMenuView
        width = -1
        height = -1
        isFocusable = true
        setBackgroundDrawable(ColorDrawable(-1342177280))
        mMenuView.findViewById<View>(R.id.title_rt).setOnTouchListener { _, _ -> true }
        mMenuView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) dismiss()
            true
        }
    }

    private fun setCurrentPos(index: Int) {
        if (index in 1..7) ivDots[index - 1]?.visibility = View.VISIBLE
    }

    private fun isCurrent(cls: Class<*>): Boolean =
        mContext.javaClass.name == cls.name

    override fun onClick(v: View) {
        val id = v.id
        dismiss()
        val intent = Intent()
        when (id) {
            R.id.pop_main_item -> {
                if (!isCurrent(SoundControlPannelActivity::class.java) &&
                    !isCurrent(LightControlPannelActivity::class.java)
                ) {
                    intent.setClass(mContext, LightControlPannelActivity::class.java)
                } else return
            }
            R.id.pop_editname_item -> {
                if (!isCurrent(SettingNameActivity::class.java)) {
                    intent.setClass(mContext, SettingNameActivity::class.java)
                } else return
            }
            R.id.pop_play_item -> {
                if (!isCurrent(SettingPlayerActivity::class.java)) {
                    intent.setClass(mContext, SettingPlayerActivity::class.java)
                } else return
            }
            R.id.pop_about_item -> {
                if (!isCurrent(SettingVersionActivity::class.java)) {
                    intent.setClass(mContext, SettingVersionActivity::class.java)
                } else return
            }
            R.id.pop_setttings_item -> {
                if (!isCurrent(SettingsActivity::class.java)) {
                    intent.setClass(mContext, SettingsActivity::class.java)
                } else return
            }
            R.id.pop_add_item -> {
                // Add-device flow is not part of this build; refresh instead.
                PulseService.queryLampState()
                PulseService.querySpeakerState()
                return
            }
            R.id.pop_refresh_item -> {
                PulseService.queryLampState()
                PulseService.querySpeakerState()
                return
            }
            R.id.pop_choose_advice_item -> {
                // Re-scan for the master bulb.
                mContext.startActivity(Intent(mContext, com.sengled.stspeaker.activity.MainActivity::class.java))
                return
            }
            else -> return
        }
        intent.flags = 131072
        mContext.startActivity(intent)
        if (!isCurrent(SoundControlPannelActivity::class.java) &&
            !isCurrent(LightControlPannelActivity::class.java)
        ) {
            mContext.finish()
        }
    }

    override fun showAsDropDown(anchor: View) {
        if (this.anchor == null) this.anchor = ImageView(mContext)
        mUpdateNumView.visibility = View.GONE // no firmware-update backend in this build
        super.showAsDropDown(this.anchor, 0, statusBarHeight)
    }

    private val statusBarHeight: Int
        get() {
            val frame = Rect()
            mContext.window.decorView.getWindowVisibleDisplayFrame(frame)
            return frame.top
        }
}
