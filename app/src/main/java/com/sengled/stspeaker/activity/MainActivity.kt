package com.sengled.stspeaker.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.sengled.stspeaker.service.PulseService

/**
 * Thin entry-point alias, as in the original app where MainActivity
 * forwards into the connect flow. Any stray intent for MainActivity
 * lands on the search-and-connect screen.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PulseService.isConnected) {
            startActivity(Intent(this, LightControlPannelActivity::class.java))
        } else {
            startActivity(Intent(this, SearchAndConnectActivity::class.java))
        }
        finish()
    }
}
