package com.sengled.pulsea66.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import com.sengled.stspeaker.R

/**
 * Sliding on/off switch from the original app,
 * ported from com.sengled.pulsea66.view.SwitchView.
 */
class SwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface OnSwitchChangeListener {
        fun onSwitchChanged(open: Boolean)
    }

    private var isAninFinish = true
    private var isChangedByTouch = false
    private val maskImage = ImageView(context)
    private var open = false
    private var switchChangeListener: OnSwitchChangeListener? = null
    private var x = 0f

    init {
        setBackgroundResource(R.drawable.a66_setting_no)
        maskImage.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        maskImage.setImageResource(R.drawable.a66_setting)
        addView(maskImage)
    }

    fun getSwitchStatus(): Boolean = open

    fun setSwitchStatus(isOpen: Boolean) {
        open = isOpen
        if (isOpen) {
            setBackgroundResource(R.drawable.a66_setting_yes)
            gravity = Gravity.END
        } else {
            setBackgroundResource(R.drawable.a66_setting_no)
            gravity = Gravity.START
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> x = event.x
            MotionEvent.ACTION_UP -> {
                if (Math.abs(event.x - x) <= 5f) changeStatus()
                isChangedByTouch = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.x - x > 5f && !open) changeStatus()
                else if (event.x - x < -5f && open) changeStatus()
            }
            MotionEvent.ACTION_CANCEL -> isChangedByTouch = false
        }
        return true
    }

    private fun changeStatus() {
        if (isAninFinish && !isChangedByTouch) {
            isChangedByTouch = true
            open = !open
            isAninFinish = false
            switchChangeListener?.onSwitchChanged(open)
            changeOpenStatusWithAnim(open)
        }
    }

    private fun changeOpenStatusWithAnim(open: Boolean) {
        val anim = if (open) {
            TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f, Animation.ABSOLUTE, (width - maskImage.width).toFloat(),
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f
            ).apply {
                duration = 300
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(a: Animation) {}
                    override fun onAnimationRepeat(a: Animation) {}
                    override fun onAnimationEnd(a: Animation) {
                        maskImage.clearAnimation()
                        gravity = Gravity.END
                        isAninFinish = true
                        setBackgroundResource(R.drawable.a66_setting_yes)
                    }
                })
            }
        } else {
            TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f, Animation.ABSOLUTE, (maskImage.width - width).toFloat(),
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f
            ).apply {
                duration = 300
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(a: Animation) {}
                    override fun onAnimationRepeat(a: Animation) {}
                    override fun onAnimationEnd(a: Animation) {
                        maskImage.clearAnimation()
                        gravity = Gravity.START
                        setBackgroundResource(R.drawable.a66_setting_no)
                        isAninFinish = true
                    }
                })
            }
        }
        maskImage.startAnimation(anim)
    }

    fun setOnSwitchChangeListener(l: OnSwitchChangeListener?) {
        switchChangeListener = l
    }
}
