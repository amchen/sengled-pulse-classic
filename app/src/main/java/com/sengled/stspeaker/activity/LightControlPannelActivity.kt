package com.sengled.stspeaker.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * Light control panel, ported from the original
 * com.sengled.stspeaker.activity.LightControlPannelActivity.
 *
 * Same layout (activity_controlpannel_light_eui), same dial, same
 * brightness throttle, same on/off double-send behavior.
 */
class LightControlPannelActivity : Activity() {

    companion object {
        const val MSG_LIGHT_PANNEL_REFRESH = 100
        const val MSG_LIGHT_PANNEL_SET_BRIGHTNESS = 110
        const val MSG_LIGHT_PANNEL_TIMER = 200
    }

    private lateinit var btnMenu: Button
    private lateinit var btnSetting: Button
    private lateinit var cpTitle: ThTextView
    private lateinit var logoIcon: ImageView
    private lateinit var lampDial: CircleView
    private lateinit var llSound: LinearLayout
    private lateinit var tvLight: ThTextView

    private val speakerSer = PulseService
    private var lampBrightness = 100
    private var timer: Timer? = null

    private var lastSentBrightness = -1
    private var lastSendTime = 0L
    private var pendingBrightness = -1

    private val uiHandler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MSG_LIGHT_PANNEL_REFRESH -> refreshData()
        }
        true
    }

    private val sendBrightnessRunnable = Runnable {
        if (pendingBrightness >= 0) {
            val valToSend = pendingBrightness
            pendingBrightness = -1
            sendBrightness(valToSend)
        }
    }

    private fun setBrightnessThrottled(brightness: Int, force: Boolean) {
        lampBrightness = brightness
        if (force) {
            uiHandler.removeCallbacks(sendBrightnessRunnable)
            pendingBrightness = -1
            sendBrightness(brightness)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSendTime > 120) {
            uiHandler.removeCallbacks(sendBrightnessRunnable)
            pendingBrightness = -1
            sendBrightness(brightness)
        } else {
            pendingBrightness = brightness
            uiHandler.removeCallbacks(sendBrightnessRunnable)
            uiHandler.postDelayed(sendBrightnessRunnable, 120)
        }
    }

    private fun sendBrightness(brightness: Int) {
        lastSentBrightness = brightness
        lastSendTime = System.currentTimeMillis()
        speakerSer.setLampBrightness(PulseService.ADDR_BROADCAST, brightness, PulseService.BRIGHTNESS_EMPTY)
    }

    private val stateListener: () -> Unit = {
        uiHandler.sendEmptyMessage(MSG_LIGHT_PANNEL_REFRESH)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_controlpannel_light_eui)
        initUi()
        lampDial.setCircleType(CircleView.CircleType.LIGHT)
        logoIcon.setImageResource(R.drawable.icon_pulse)
        speakerSer.addStateListener(stateListener)
        refreshData()
    }

    private fun initUi() {
        btnMenu = findViewById(R.id.btn_menu)
        btnSetting = findViewById(R.id.btn_setting)
        cpTitle = findViewById(R.id.cp_title)
        tvLight = findViewById(R.id.tv_light)
        logoIcon = findViewById(R.id.logo_icon)
        lampDial = findViewById(R.id.DialSeekBar)
        llSound = findViewById(R.id.ll_sound)

        lampDial.setOnCircleButtonClickListener(object : CircleView.CircleButtonClickListener {
            override fun onBottomButtonCLick(f: Float) {}
            override fun onSwitchButtonClick(onOrOff: Boolean) = turnLightOnOff()
            override fun onTopButtonClick(f: Float) {}
        })
        lampDial.setOnCircleProgressDragListener(object : CircleView.CircleProgressDragListener {
            override fun onDragFinish(f: Float) {
                if (!speakerSer.isAllLightOff()) {
                    setBrightnessThrottled(f.toInt(), force = true)
                }
            }

            override fun onProgressDrag(f: Float) {
                if (!speakerSer.isAllLightOff()) {
                    setBrightnessThrottled(f.toInt(), force = false)
                }
            }
        })

        btnMenu.setOnClickListener {
            SettingsMenuPopupWindow(this, 1).showAsDropDown(findViewById(R.id.btn_menu))
        }
        btnSetting.setOnClickListener {
            startActivity(Intent(this, SettingLampActivity::class.java))
        }
        llSound.setOnClickListener {
            val intent = Intent(this, SoundControlPannelActivity::class.java)
            intent.flags = 131072 // FLAG_ACTIVITY_SINGLE_TOP, as in the original
            startActivity(intent)
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    private fun turnLightOnOff() {
        when (speakerSer.getLampOnOffFromAll()) {
            0 -> {
                // lights on -> turn off (sent twice, 200 ms apart, like the original)
                cancelTimer()
                speakerSer.setLampOnOff(PulseService.ADDR_BROADCAST, 1)
                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        speakerSer.setLampOnOff(PulseService.ADDR_BROADCAST, 1)
                    }
                }, 200)
                lampDial.turnOff()
            }
            1 -> {
                cancelTimer()
                speakerSer.setLampOnOff(PulseService.ADDR_BROADCAST, 0)
                timer = Timer()
                timer?.schedule(object : TimerTask() {
                    override fun run() {
                        speakerSer.setLampOnOff(PulseService.ADDR_BROADCAST, 0)
                    }
                }, 200)
                lampDial.turnOn()
            }
        }
    }

    private fun refreshData() {
        if (speakerSer.isSingleLight()) {
            btnSetting.visibility = View.GONE
        }
        when (speakerSer.productId) {
            2 -> logoIcon.setImageResource(R.drawable.icon_pulse_link)
            else -> logoIcon.setImageResource(R.drawable.icon_pulse)
        }
        if (speakerSer.isAudioBox()) {
            lampDial.turnAudioBox()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lampDial.setProgress(speakerSer.getLampBrightness1().toFloat())
        refreshData()
    }

    override fun onResume() {
        super.onResume()
        lampDial.setProgress(speakerSer.getLampBrightness1().toFloat())
        if (speakerSer.isAllLightOff()) {
            lampDial.turnOff()
        }
        refreshData()
    }

    override fun onDestroy() {
        speakerSer.removeStateListener(stateListener)
        cancelTimer()
        try {
            lampDial.recycle()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        speakerSer.shutdown()
        finishAffinity()
    }
}
