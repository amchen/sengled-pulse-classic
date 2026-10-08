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
import android.widget.TextView
import com.facebook.drawee.view.SimpleDraweeView
import com.sengled.pulsea66.view.ThTextView
import com.sengled.stspeaker.R
import java.util.Locale

/**
 * First-run illustrated tips, ported from the original
 * com.sengled.stspeaker.activity.SengledTipsActivity.
 *
 * Two pages of animated setup GIFs; finishing (or skipping) goes to
 * the search-and-connect screen.
 */
class SengledTipsActivity : Activity(), View.OnClickListener {

    private lateinit var sp: SharedPreferences
    private var dialog: AlertDialog? = null

    private lateinit var tipsPage1: LinearLayout
    private lateinit var tipsPage2: LinearLayout
    private lateinit var tipsGif1: SimpleDraweeView
    private lateinit var tipsGif2: SimpleDraweeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_sengled_tips)
        sp = getSharedPreferences(LogoActivity.PREFS_FIRST_START, MODE_PRIVATE)
        initUi()
    }

    override fun onResume() {
        super.onResume()
        sp.edit().putBoolean(LogoActivity.KEY_IS_FIRST_START, false).apply()
    }

    private fun initUi() {
        tipsPage1 = findViewById(R.id.tips_page1)
        tipsGif1 = findViewById(R.id.tips_gif1)
        val tipsFinish1: ImageView = findViewById(R.id.tips_finish_1)
        val nextTip1: ThTextView = findViewById(R.id.next_tip_1)
        tipsFinish1.setOnClickListener(this)
        nextTip1.setOnClickListener(this)

        tipsPage2 = findViewById(R.id.tips_page2)
        tipsGif2 = findViewById(R.id.tips_gif2)
        val tipsFinish2: ImageView = findViewById(R.id.tips_finish_2)
        val nextTip2: ThTextView = findViewById(R.id.next_tip_2)
        tipsFinish2.setOnClickListener(this)
        nextTip2.setOnClickListener(this)

        val dialogTip: LinearLayout = findViewById(R.id.dialog_tip)
        dialogTip.setOnClickListener(this)

        initGifs()
    }

    private fun initGifs() {
        val zh = isZh()
        tipsGif1.setGifResource(if (zh) R.drawable.tips_gif1 else R.drawable.tips_gif1_english)
        tipsGif2.setGifResource(if (zh) R.drawable.tips_gif2 else R.drawable.tips_gif2_english)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.tips_finish_1, R.id.tips_finish_2, R.id.next_tip_2 -> {
                startActivity(Intent(this, SearchAndConnectActivity::class.java))
                finish()
            }
            R.id.next_tip_1 -> {
                tipsPage1.visibility = View.GONE
                tipsPage2.visibility = View.VISIBLE
            }
            R.id.dialog_tip -> showHelpDialog()
            R.id.confirm -> dialog?.dismiss()
        }
    }

    private fun showHelpDialog() {
        dialog = AlertDialog.Builder(this).create()
        dialog?.show()
        val layout = LayoutInflater.from(this).inflate(R.layout.dialog_help, null) as LinearLayout
        dialog?.setContentView(layout)
        val confirm: TextView = layout.findViewById(R.id.confirm)
        confirm.setOnClickListener(this)
    }

    private fun isZh(): Boolean {
        val language = Locale.getDefault().language
        return language.endsWith("zh")
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }
}
