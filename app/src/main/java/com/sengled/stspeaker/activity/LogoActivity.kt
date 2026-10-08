package com.sengled.stspeaker.activity

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import com.sengled.stspeaker.R

/**
 * Splash screen, ported from the original
 * com.sengled.stspeaker.activity.LogoActivity.
 *
 * Shows the logo for 1 s, then routes to the tips (first run) or to
 * the search-and-connect screen.
 */
class LogoActivity : Activity() {

    companion object {
        const val PREFS_FIRST_START = "firstStart"
        const val KEY_IS_FIRST_START = "isFirstStart"
        private const val DELAY_TIME = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sp: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_ACTIVITY_BROUGHT_TO_FRONT: don't re-show the splash.
        if (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) {
            finish()
            return
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        sp = getSharedPreferences(PREFS_FIRST_START, MODE_PRIVATE)
        setContentView(R.layout.activity_logo)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed({
            if (sp.getBoolean(KEY_IS_FIRST_START, true)) {
                startActivity(Intent(this, SengledTipsActivity::class.java))
            } else {
                startActivity(Intent(this, SearchAndConnectActivity::class.java))
            }
            finish()
        }, DELAY_TIME)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }
}
