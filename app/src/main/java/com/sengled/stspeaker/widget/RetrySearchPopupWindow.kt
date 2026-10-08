package com.sengled.stspeaker.widget

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.sengled.stspeaker.R
import com.sengled.stspeaker.activity.SettingVersionActivity
import com.sengled.stspeaker.activity.SettingsActivity

/**
 * Menu shown from the retry screen, ported from the original
 * com.sengled.stspeaker.widget.RetrySearchPopupWindow.
 */
class RetrySearchPopupWindow(
    private val mContext: Activity
) : PopupWindow(mContext), View.OnClickListener {

    private var anchor: View? = null
    private val mMenuView: View =
        (mContext.getSystemService("layout_inflater") as LayoutInflater)
            .inflate(R.layout.popupwindow_retrysearch, null)

    init {
        contentView = mMenuView
        width = -1
        height = -1
        isFocusable = true
        setBackgroundDrawable(ColorDrawable(-1342177280))
        val popSettingItem: LinearLayout = mMenuView.findViewById(R.id.pop_setting_item)
        val popAboutItem: LinearLayout = mMenuView.findViewById(R.id.pop_about_item)
        val btCancel: Button = mMenuView.findViewById(R.id.bt_cancle)
        popSettingItem.setOnClickListener(this)
        popAboutItem.setOnClickListener(this)
        btCancel.setOnClickListener(this)
        mMenuView.findViewById<View>(R.id.title_rt).setOnTouchListener { _, _ -> true }
        mMenuView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) dismiss()
            true
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.bt_cancle -> dismiss()
            R.id.pop_setting_item -> {
                dismiss()
                val intent = Intent(mContext, SettingsActivity::class.java)
                intent.putExtra("RetrySearch", true)
                mContext.startActivity(intent)
            }
            R.id.pop_about_item -> {
                dismiss()
                val intent = Intent(mContext, SettingVersionActivity::class.java)
                intent.putExtra("RetrySearch", true)
                mContext.startActivity(intent)
            }
        }
    }

    override fun showAsDropDown(anchor: View) {
        if (this.anchor == null) this.anchor = ImageView(mContext)
        super.showAsDropDown(this.anchor, 0, statusBarHeight)
    }

    private val statusBarHeight: Int
        get() {
            val frame = Rect()
            mContext.window.decorView.getWindowVisibleDisplayFrame(frame)
            return frame.top
        }
}
