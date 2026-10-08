package com.sengled.stspeaker.activity

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.animation.AccelerateInterpolator
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import com.sengled.stspeaker.NodeInfo
import com.sengled.stspeaker.R
import com.sengled.stspeaker.service.PulseService
import com.sengled.stspeaker.widget.SettingsMenuPopupWindow
import com.sengled.stspeaker.widget.ThTextView

/**
 * Speaker settings: EQ dropdown panel + per-node mute/channel rows.
 * Ported from the original com.sengled.stspeaker.activity.SettingSpeakerActivity
 * (layout activity_setting_speaker_eui, rows adapter_sound_list_eui,
 * EQ items item_eq_drop_down).
 */
class SettingSpeakerActivity : Activity() {

    companion object {
        const val EQ_NORMAL = 0
        const val EQ_POP = 1
        const val EQ_JAZZ = 2
        const val EQ_CLASSIC = 3
        const val EQ_ROCK = 4
        const val EQ_MOVIE = 5

        private val imageIDs_normal = intArrayOf(
            R.drawable.icn_normal_normal, R.drawable.icn_pop_normal,
            R.drawable.icn_jazz_normal, R.drawable.icn_classical_normal,
            R.drawable.icn_rock_normal, R.drawable.icn_movie_normal
        )
        private val imageIDss_normal = intArrayOf(
            R.drawable.icn_eq_normal_normal, R.drawable.icn_eq_pop_normal,
            R.drawable.icn_eq_jazz_normal, R.drawable.icn_eq_classical_normal,
            R.drawable.icn_eq_rock_normal, R.drawable.icn_eq_movie_normal
        )
        private val imageIDs_select = intArrayOf(
            R.drawable.icn_normal_selected, R.drawable.icn_pop_selected,
            R.drawable.icn_jazz_selected, R.drawable.icn_classical_selected,
            R.drawable.icn_rock_selected, R.drawable.icn_movie_selected
        )
        private val imageIDss_select = intArrayOf(
            R.drawable.icn_eq_normal_selected, R.drawable.icn_eq_pop_selected,
            R.drawable.icn_eq_jazz_selected, R.drawable.icn_eq_classical_selected,
            R.drawable.icn_eq_rock_selected, R.drawable.icn_eq_movie_selected
        )
        private const val mDuration = 350L
    }

    private lateinit var lvSpeaker: ListView
    private lateinit var btnMenu: Button
    private lateinit var btnSelect: Button
    private lateinit var tvInfo: ThTextView
    private lateinit var eqPanelScroll: ScrollView
    private lateinit var eqDropDownPanel: LinearLayout
    private lateinit var speakerAdapter: SpeakerRowAdapter
    private lateinit var prefs: SharedPreferences
    private var menuWindow: SettingsMenuPopupWindow? = null

    private var mHeightDropDownPanel = 0
    private var mIsNextClickDropDown = false
    private val mInterpolator = AccelerateInterpolator()
    private val workHandler = Handler(Looper.getMainLooper())
    private var mStartTime = 0L

    private val stateListener: () -> Unit = {
        if (::speakerAdapter.isInitialized) speakerAdapter.refresh()
    }

    private val mSlideRunnable = object : Runnable {
        override fun run() {
            var percent = (System.currentTimeMillis() - mStartTime).toFloat() / mDuration
            if (percent >= 1f) percent = 1f
            val nextHeight = if (mIsNextClickDropDown) {
                (mInterpolator.getInterpolation(percent) * mHeightDropDownPanel).toInt()
            } else {
                ((1f - mInterpolator.getInterpolation(percent)) * mHeightDropDownPanel).toInt()
            }
            val params = eqPanelScroll.layoutParams as LinearLayout.LayoutParams
            params.height = nextHeight
            eqPanelScroll.layoutParams = params
            if (percent < 1f) workHandler.postDelayed(this, 10)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_setting_speaker_eui)
        prefs = getSharedPreferences("pulse_prefs", MODE_PRIVATE)
        initUi()
        PulseService.addStateListener(stateListener)
        if (PulseService.isSingleLight()) {
            findViewById<View>(R.id.ll_move_panel).visibility = View.GONE
        }
    }

    private fun initUi() {
        tvInfo = findViewById(R.id.btn_info)
        btnSelect = findViewById(R.id.btn_select)
        updateEqMainItem(PulseService.getSoundEQ(), false)

        val toggleDropDown = View.OnClickListener {
            mIsNextClickDropDown = !mIsNextClickDropDown
            if (mIsNextClickDropDown) {
                btnSelect.setBackgroundResource(R.drawable.eq_up)
                updateDropDownPanel(-1, PulseService.getSoundEQ())
                updateEqMainItem(PulseService.getSoundEQ(), true)
            } else {
                btnSelect.setBackgroundResource(R.drawable.eq_down)
                updateEqMainItem(PulseService.getSoundEQ(), false)
            }
            startDownUpAnimation()
        }
        tvInfo.setOnClickListener(toggleDropDown)
        btnSelect.setOnClickListener(toggleDropDown)

        btnMenu = findViewById(R.id.btn_menu)
        btnMenu.setOnClickListener {
            menuWindow = SettingsMenuPopupWindow(this, 0)
            menuWindow?.showAsDropDown(findViewById(R.id.control_actionbar))
            menuWindow?.setOnDismissListener(PopupWindow.OnDismissListener {
                btnMenu.setBackgroundResource(R.drawable.btn_menu_normal)
            })
        }

        lvSpeaker = findViewById(R.id.listview_speaker)
        speakerAdapter = SpeakerRowAdapter()
        lvSpeaker.adapter = speakerAdapter

        eqPanelScroll = findViewById(R.id.eq_panel_scroll)
        initializeDropDownPanel()
    }

    private fun startDownUpAnimation() {
        workHandler.removeCallbacks(mSlideRunnable)
        mStartTime = System.currentTimeMillis()
        workHandler.post(mSlideRunnable)
    }

    private fun initializeDropDownPanel() {
        eqDropDownPanel = findViewById(R.id.ll_drop_down_panel)
        val inflater = layoutInflater
        for (index in 0 until 6) {
            val itemLayout = inflater.inflate(R.layout.item_eq_drop_down, null) as LinearLayout
            val itemText = itemLayout.findViewById<ThTextView>(R.id.tv_item_eq)
            val left = drawable(imageIDs_normal[index])
            val right = drawable(imageIDss_normal[index])
            itemText.setCompoundDrawables(left, null, right, null)
            itemText.setText(eqStringId(index))
            itemLayout.setOnClickListener { v ->
                if (mIsNextClickDropDown) {
                    mIsNextClickDropDown = false
                    findViewById<View>(R.id.btn_select).setBackgroundResource(R.drawable.eq_down)
                    val currentEq = PulseService.getSoundEQ()
                    val clicked = (v as LinearLayout).getChildAt(0) as ThTextView
                    val clickedText = clicked.text.toString()
                    val currentText = resources.getText(eqStringId(currentEq)).toString()
                    if (clickedText != currentText) {
                        val newEq = eqIdFromName(clickedText)
                        updateDropDownPanel(currentEq, newEq)
                        updateEqMainItem(newEq, false)
                        PulseService.setSoundEQ(PulseService.ADDR_BROADCAST, newEq)
                    } else {
                        updateEqMainItem(currentEq, false)
                    }
                    startDownUpAnimation()
                }
            }
            eqDropDownPanel.addView(itemLayout)
        }
        eqPanelScroll.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (mHeightDropDownPanel == 0) {
                        mHeightDropDownPanel = eqPanelScroll.measuredHeight
                        val params = eqPanelScroll.layoutParams as LinearLayout.LayoutParams
                        params.width = eqPanelScroll.measuredWidth
                        params.height = 0
                        params.gravity = android.view.Gravity.CENTER_HORIZONTAL
                        eqPanelScroll.layoutParams = params
                    }
                }
            })
    }

    private fun drawable(id: Int): Drawable? {
        val d = ContextCompat.getDrawable(this, id) ?: return null
        d.setBounds(0, 0, d.minimumWidth, d.minimumHeight)
        return d
    }

    private fun eqIdFromName(eqName: String): Int {
        for (i in 0 until 6) {
            if (eqName == resources.getText(eqStringId(i)).toString()) return i
        }
        return 0
    }

    private fun eqStringId(eq: Int): Int = when (eq) {
        EQ_NORMAL -> R.string.EQ_normal
        EQ_POP -> R.string.EQ_Pop
        EQ_JAZZ -> R.string.EQ_Jazz
        EQ_CLASSIC -> R.string.EQ_Classic
        EQ_ROCK -> R.string.EQ_Rock
        EQ_MOVIE -> R.string.EQ_Movie
        else -> 0
    }

    private fun updateEqMainItem(eqIndex: Int, isDropDownState: Boolean) {
        val res: Resources = resources
        val left: Drawable?
        val right: Drawable?
        if (isDropDownState) {
            left = drawable(imageIDs_normal[eqIndex])
            right = drawable(imageIDss_normal[eqIndex])
            tvInfo.paint.shader = null
            tvInfo.setTextColor(res.getColor(R.color.text_deep))
        } else {
            left = drawable(imageIDs_select[eqIndex])
            right = drawable(imageIDss_select[eqIndex])
            shaderText(tvInfo, Color.rgb(213, 43, 30))
        }
        tvInfo.setCompoundDrawables(left, null, right, null)
        tvInfo.setText(eqStringId(eqIndex))
    }

    private fun updateDropDownPanel(currentEqIndex: Int, newEqIndex: Int) {
        val newName = resources.getString(eqStringId(newEqIndex))
        var tvNew: ThTextView? = null
        for (i in 0 until eqDropDownPanel.childCount) {
            val tv = (eqDropDownPanel.getChildAt(i) as ViewGroup).getChildAt(0) as ThTextView
            if (tv.text.toString() == newName) {
                tvNew = tv
                break
            }
        }
        tvNew?.let {
            it.setCompoundDrawables(drawable(imageIDs_select[newEqIndex]), null,
                drawable(imageIDss_select[newEqIndex]), null)
            shaderText(it, Color.rgb(213, 43, 30))
        }
        if (currentEqIndex != -1) {
            val curName = resources.getString(eqStringId(currentEqIndex))
            for (i in 0 until eqDropDownPanel.childCount) {
                val tv = (eqDropDownPanel.getChildAt(i) as ViewGroup).getChildAt(0) as ThTextView
                if (tv.text.toString() == curName) {
                    tv.setCompoundDrawables(drawable(imageIDs_normal[currentEqIndex]), null,
                        drawable(imageIDss_normal[currentEqIndex]), null)
                    tv.paint.shader = null
                    break
                }
            }
        }
    }

    private fun shaderText(tv: ThTextView, color: Int) {
        val fm = tv.paint.fontMetrics
        tv.paint.shader = LinearGradient(
            0f, 0f, 0f, fm.bottom - fm.top,
            color, color, Shader.TileMode.CLAMP
        )
    }

    override fun onResume() {
        super.onResume()
        speakerAdapter.refresh()
        updateEqMainItem(PulseService.getSoundEQ(), mIsNextClickDropDown)
    }

    override fun onDestroy() {
        PulseService.removeStateListener(stateListener)
        workHandler.removeCallbacks(mSlideRunnable)
        super.onDestroy()
    }

    private fun displayName(node: NodeInfo): String {
        val saved = prefs.getString("device_name", null)
        return if (!saved.isNullOrEmpty()) saved else node.name.ifEmpty { getString(R.string.app_name) }
    }

    private inner class SpeakerRowAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(this@SettingSpeakerActivity)
        private var rows: List<NodeInfo> = emptyList()

        init {
            refresh()
        }

        fun refresh() {
            rows = PulseService.nodes.toList()
            notifyDataSetChanged()
        }

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: inflater.inflate(R.layout.adapter_sound_list_eui, parent, false).also {
                    it.tag = RowHolder(
                        nameView = it.findViewById(R.id.speakername),
                        btnMute = it.findViewById(R.id.btn_mute),
                        btnL = it.findViewById(R.id.btn_L),
                        btnLR = it.findViewById(R.id.btn_LR),
                        btnR = it.findViewById(R.id.btn_R)
                    )
                }
            val holder = view.tag as RowHolder
            val node = rows[position]
            holder.nameView.text = displayName(node)

            holder.btnMute.setBackgroundResource(
                if (node.mute == 1) R.drawable.btn_mute_selector_l
                else R.drawable.btn_unmute_selector_l
            )
            holder.btnL.setBackgroundResource(R.drawable.btn_l_or_r_normal)
            holder.btnLR.setBackgroundResource(R.drawable.btn_l_and_r_normal)
            holder.btnR.setBackgroundResource(R.drawable.btn_l_or_r_normal)
            updateChannelIcon(holder, node.channel)

            // NOTE: the original app maps the L button to channel value 1 and the
            // R button to channel value 0 (verified in the decompiled sources);
            // this port keeps that exact behavior.
            holder.btnL.setOnClickListener {
                PulseService.setSoundChannel(node.addr, 1)
                updateChannelIcon(holder, 1)
            }
            holder.btnLR.setOnClickListener {
                PulseService.setSoundChannel(node.addr, 2)
                updateChannelIcon(holder, 2)
            }
            holder.btnR.setOnClickListener {
                PulseService.setSoundChannel(node.addr, 0)
                updateChannelIcon(holder, 0)
            }
            holder.btnMute.setOnClickListener {
                val newMute = if (node.mute == 1) 0 else 1
                PulseService.setSoundMute(node.addr, newMute)
                holder.btnMute.setBackgroundResource(
                    if (newMute == 1) R.drawable.btn_mute_selector_l
                    else R.drawable.btn_unmute_selector_l
                )
            }
            return view
        }

        private fun updateChannelIcon(holder: RowHolder, channel: Int) {
            val gray = resources.getColor(R.color.text_plain_gray_deep)
            val red = resources.getColor(R.color.text_red)
            when (channel) {
                0 -> {
                    holder.btnL.setTextColor(gray)
                    holder.btnLR.setTextColor(gray)
                    holder.btnR.setTextColor(red)
                }
                1 -> {
                    holder.btnL.setTextColor(red)
                    holder.btnLR.setTextColor(gray)
                    holder.btnR.setTextColor(gray)
                }
                else -> {
                    holder.btnL.setTextColor(gray)
                    holder.btnLR.setTextColor(red)
                    holder.btnR.setTextColor(gray)
                }
            }
        }

        private inner class RowHolder(
            val nameView: ThTextView,
            val btnMute: View,
            val btnL: ThTextView,
            val btnLR: ThTextView,
            val btnR: ThTextView
        )
    }
}
