package com.sengled.stspeaker.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService
import com.sengled.stspeaker.widget.CircleView
import com.sengled.stspeaker.widget.SettingsMenuPopupWindow
import com.sengled.stspeaker.widget.ThTextView
import java.util.Timer
import java.util.TimerTask

/**
 * Sound control panel, ported from the original
 * com.sengled.stspeaker.activity.SoundControlPannelActivity.
 *
 * Same layout (activity_controlpannel_sound_eui), same dial, same
 * mute double-send behavior.
 */
class SoundControlPannelActivity : Activity() {

    companion object {
        const val MSG_SOUND_PANNEL_REFRESH = 100
        const val MSG_SOUND_PANNEL_SET_VOLUME = 110
    }

    private lateinit var btnMenu: Button
    private lateinit var btnSetting: Button
    private lateinit var cpTitle: ThTextView
    private lateinit var logoIcon: ImageView
    private lateinit var volumeDial: CircleView
    private lateinit var llLight: LinearLayout
    private lateinit var tvSound: ThTextView

    private val speakerSer = PulseService
    private var timer: Timer? = null

    private val uiHandler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MSG_SOUND_PANNEL_REFRESH -> refreshData()
            MSG_SOUND_PANNEL_SET_VOLUME -> setSpeakerVolume(msg.arg1)
        }
        true
    }

    private val stateListener: () -> Unit = {
        uiHandler.sendEmptyMessage(MSG_SOUND_PANNEL_REFRESH)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_controlpannel_sound_eui)
        initUi()
        volumeDial.setCircleType(CircleView.CircleType.SOUND)
        logoIcon.setImageResource(R.drawable.icon_pulse)
        speakerSer.addStateListener(stateListener)
        refreshData()
    }

    private fun initUi() {
        btnMenu = findViewById(R.id.btn_menu)
        btnSetting = findViewById(R.id.btn_setting)
        cpTitle = findViewById(R.id.cp_title)
        tvSound = findViewById(R.id.tv_sound)
        logoIcon = findViewById(R.id.logo_icon)
        volumeDial = findViewById(R.id.DialSeekBar)
        llLight = findViewById(R.id.ll_light)


        volumeDial.setOnCircleButtonClickListener(object : CircleView.CircleButtonClickListener {
            override fun onBottomButtonCLick(f: Float) {}
            override fun onSwitchButtonClick(onOrOff: Boolean) = turnVolumeOnOrOff()
            override fun onTopButtonClick(f: Float) {}
        })
        volumeDial.setOnCircleProgressDragListener(object : CircleView.CircleProgressDragListener {
            override fun onDragFinish(f: Float) {}
            override fun onProgressDrag(f: Float) {
                uiHandler.sendMessage(uiHandler.obtainMessage(MSG_SOUND_PANNEL_SET_VOLUME, f.toInt(), 0))
            }
        })

        btnMenu.setOnClickListener {
            SettingsMenuPopupWindow(this, 1).showAsDropDown(findViewById(R.id.btn_menu))
        }
        btnSetting.setOnClickListener {
            startActivity(Intent(this, SettingSpeakerActivity::class.java))
        }
        llLight.setOnClickListener {
            val intent = Intent(this, LightControlPannelActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun setSpeakerVolume(volume: Int) {
        speakerSer.setSoundVolume(PulseService.ADDR_BROADCAST, volume)
        // The original un-mutes the master when the volume dial is dragged.
        if (speakerSer.getSoundMute() == 1) {
            speakerSer.setSoundMute(PulseService.ADDR_BROADCAST, 0)
            volumeDial.turnOn()
        }
    }

    fun turnVolumeOnOrOff() {
        cancelTimer()
        when (speakerSer.getSoundMute()) {
            0 -> {
                // unmuted -> mute (sent twice, 200 ms apart, like the original)
                speakerSer.setSoundMute(PulseService.ADDR_BROADCAST, 1)
                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        speakerSer.setSoundMute(PulseService.ADDR_BROADCAST, 1)
                    }
                }, 200)
                volumeDial.turnOff()
            }
            else -> {
                speakerSer.setSoundMute(PulseService.ADDR_BROADCAST, 0)
                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        speakerSer.setSoundMute(PulseService.ADDR_BROADCAST, 0)
                    }
                }, 200)
                volumeDial.turnOn()
                val vol = speakerSer.getSoundVolume1()
                if (vol > 0) {
                    volumeDial.setProgress(vol.toFloat())
                }
            }
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    private fun refreshData() {
        if (speakerSer.isSingleLight()) {
            btnSetting.visibility = View.GONE
        }
        when (speakerSer.productId) {
            2 -> logoIcon.setImageResource(R.drawable.icon_pulse_link)
            else -> logoIcon.setImageResource(R.drawable.icon_pulse)
        }
        val currentVol = speakerSer.getSoundVolume1()
        if (currentVol > 0) {
            volumeDial.setProgress(currentVol.toFloat())
        }
        if (speakerSer.getSoundMute() == 1) {
            if (!volumeDial.isOff()) volumeDial.turnOff()
        } else {
            if (volumeDial.isOff()) volumeDial.turnOn()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val currentVol = speakerSer.getSoundVolume1()
        if (currentVol > 0) {
            volumeDial.setProgress(currentVol.toFloat())
        }
        refreshData()
    }

    override fun onResume() {
        super.onResume()
        val currentVol = speakerSer.getSoundVolume1()
        if (currentVol > 0) {
            volumeDial.setProgress(currentVol.toFloat())
        }
        refreshData()
    }

    override fun onDestroy() {
        speakerSer.removeStateListener(stateListener)
        cancelTimer()
        try {
            volumeDial.recycle()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        speakerSer.shutdown()
        finishAffinity()
    }
}
