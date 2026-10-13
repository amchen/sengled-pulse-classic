package com.sengled.stspeaker.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService

/**
 * Audio input source switch: line-in (AUX) vs Bluetooth.
 * Ported from the original com.sengled.stspeaker.activity.AudioInputSwitchActivity
 * (layout activity_audio_input_switch).
 *
 * Sends the official audio-source command (subsys 4 / cmd 6):
 * 0 = 3.5 mm AUX, 1 = Bluetooth A2DP.
 */
class AudioInputSwitchActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        const val EXTRA_AUDIO_SOURCE = "audioSourceSwitchNum"
        const val SOURCE_AUX = 0
        const val SOURCE_BLUETOOTH = 1
    }

    private val addr = PulseService.ADDR_BROADCAST
    private var audioSourceSwitchNum = -1
    private lateinit var lineinIcon: ImageView
    private lateinit var btIcon: ImageView
    private var progressDialog: AlertDialog? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val v = msg.obj as View
            when (v.id) {
                R.id.link_type_ln -> {
                    audioSourceSwitchNum = SOURCE_AUX
                    lineinIcon.visibility = View.VISIBLE
                    btIcon.visibility = View.GONE
                }
                R.id.bt_type_ln -> {
                    audioSourceSwitchNum = SOURCE_BLUETOOTH
                    lineinIcon.visibility = View.GONE
                    btIcon.visibility = View.VISIBLE
                }
            }
            PulseService.setAudioSource(addr, audioSourceSwitchNum)
            PulseService.audioSourceSwitchNum = audioSourceSwitchNum
            getSharedPreferences("pulse_prefs", MODE_PRIVATE).edit()
                .putInt("audio_source", audioSourceSwitchNum).apply()
            progressDialog?.dismiss()
            progressDialog = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_audio_input_switch)
        initView()
        initData()
    }

    private fun initView() {
        lineinIcon = findViewById(R.id.linein_icon)
        btIcon = findViewById(R.id.bt_icon)
        findViewById<View>(R.id.link_type_ln).setOnClickListener(this)
        findViewById<View>(R.id.bt_type_ln).setOnClickListener(this)
        findViewById<View>(R.id.btn_menu).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun initData() {
        audioSourceSwitchNum = PulseService.audioSourceSwitchNum
        if (audioSourceSwitchNum == -1) {
            audioSourceSwitchNum = getSharedPreferences("pulse_prefs", MODE_PRIVATE)
                .getInt("audio_source", -1)
        }
        when (audioSourceSwitchNum) {
            SOURCE_AUX -> {
                lineinIcon.visibility = View.VISIBLE
                btIcon.visibility = View.GONE
            }
            SOURCE_BLUETOOTH -> {
                lineinIcon.visibility = View.GONE
                btIcon.visibility = View.VISIBLE
            }
            else -> {
                lineinIcon.visibility = View.GONE
                btIcon.visibility = View.GONE
            }
        }
    }

    override fun onClick(v: View) {
        progressDialog = AlertDialog.Builder(this)
            .setView(ProgressBar(this).apply { isIndeterminate = true })
            .setCancelable(false)
            .show()
        val message = Message.obtain()
        message.obj = v
        handler.sendMessageDelayed(message, 1000)
    }

    private fun finishWithResult() {
        val intent = Intent()
        intent.putExtra(EXTRA_AUDIO_SOURCE, audioSourceSwitchNum)
        setResult(RESULT_OK, intent)
        finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finishWithResult()
        super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        progressDialog?.dismiss()
        super.onDestroy()
    }
}
