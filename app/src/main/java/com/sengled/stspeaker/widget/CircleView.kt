package com.sengled.stspeaker.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.sengled.stspeaker.R

/**
 * Circular brightness / volume dial from the original Pulse app,
 * ported 1:1 from com.sengled.stspeaker.widget.CircleView.
 *
 * Drag around the ring to change progress (0..100); tap the switch
 * button at the bottom of the ring to toggle on/off.
 */
@SuppressLint("NewApi")
class CircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface CircleButtonClickListener {
        fun onBottomButtonCLick(f: Float)
        fun onSwitchButtonClick(onOrOff: Boolean)
        fun onTopButtonClick(f: Float)
    }

    interface CircleProgressDragListener {
        fun onDragFinish(f: Float)
        fun onProgressDrag(f: Float)
    }

    interface CircleSwitchListener {
        fun onTurnOff(f: Float)
        fun onTurnOn(f: Float)
    }

    enum class CircleType { SOUND, LIGHT, AudioBox }

    private enum class IllegalType { NONE, FOURTOONE, ONETOFOUR }

    companion object {
        private const val sLOG_TAG = "CircleView"
    }

    private var isAudiobox = false
    private var mArrowEnable = true
    private var mAudioBoxTextSize = 0
    @Volatile private var mAudioboxIsOff = false
    private var mBlankAngle = 52.0f
    private var mBottomLongPressedThread: BottomLongPressedThread? = null
    private var mCanTapInCircle = false
    private var mCircleButtonClickListener: CircleButtonClickListener? = null
    private var mCircleProgressDragListener: CircleProgressDragListener? = null
    private val mCircleRect = RectF()
    private var mCircleSwitchListener: CircleSwitchListener? = null
    private var mCurrentAngle = 0.0f
    private var mCurrentType = CircleType.LIGHT
    private var mHundredPercent = 307.9999f
    private var mInit = false
    private var mIsBottomClick = false
    private var mIsDragging = false
    private var mIsLongPressed = false
    @Volatile private var mIsOff = false
    private var mIsSwitchBtnClick = false
    private var mIsTopClick = false
    private var mLightCircleBackground: Drawable? = null
    private var mLightCircleProgress: Drawable? = null
    private var mSoundCircleBackground: Drawable? = null
    private var mSoundCircleProgress: Drawable? = null
    private var mLightTypeTitle = "BRIGHTNESS"
    private var mLongPressTime = 400
    private val mMatrixArrow = Matrix()
    private val mMatrixSelect = Matrix()
    private var mOff = "Off"
    private var mOffCurrentAngle = 0.0f
    private var mOffProgress = 0.0f
    private var mOldDragEventX = 0.0f
    private val mPaint = Paint()
    private val mPaintText_P = Paint()
    private val mPath = Path()
    private var mPendingCheckForLongPress: CheckForLongPress? = null
    private var mProgress = 0.0f
    private var mProgressAngleStep = 3.08f
    private var mProgressStep = 1
    private var mProgressInterval = 80
    private var mProgressTextSize = 65
    private var mProgressTypeSize = 60
    private var mRadius = 0
    private var mRangAngle = 308.0f
    private var mSoundTypeTitle = "VOLUME"
    private var mStartAngle = 0.0f
    private var mSwitchBtnOff: Drawable? = null
    private var mSwitchBtnOn: Drawable? = null
    private var mTopLongPressedThread: TopLongPressedThread? = null
    private var mTouchInnerOffset = 32.0f
    private var mTouchOnMoveInner = 100.0f
    private var mTouchOuter = 80
    private var mTypeface: Typeface? = null
    private var mXCenterPosition = 0
    private var mYCenterPosition = 0

    init {
        if (Build.VERSION.SDK_INT >= 11) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
        mTypeface = try {
            Typeface.createFromAsset(context.assets, "fonts/th.ttf")
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        mLightCircleBackground = ContextCompat.getDrawable(context, R.drawable.lights_circle_bg)
        mLightCircleProgress = ContextCompat.getDrawable(context, R.drawable.lights_circle)
        mSoundCircleBackground = ContextCompat.getDrawable(context, R.drawable.sounds_circle_bg)
        mSoundCircleProgress = ContextCompat.getDrawable(context, R.drawable.sounds_circle)
        mSwitchBtnOn = ContextCompat.getDrawable(context, R.drawable.light_on)
        mSwitchBtnOff = ContextCompat.getDrawable(context, R.drawable.light_off)
        mProgressTextSize = resources.getDimensionPixelSize(R.dimen.a66_circie_progress_size)
        mAudioBoxTextSize = resources.getDimensionPixelSize(R.dimen.a66_circie_audio_size)
        mProgressTypeSize = resources.getDimensionPixelSize(R.dimen.a66_circie_type_size)
        mOff = resources.getString(R.string.off)
        mSoundTypeTitle = resources.getString(R.string.volume)
        mLightTypeTitle = resources.getString(R.string.brightness)
        mPaint.isAntiAlias = true
        // SupportMenu.CATEGORY_MASK (0xFFFF0000); only the alpha channel matters for drawBitmap.
        mPaint.color = -65536
        mPaint.isAntiAlias = true
        mPaint.style = Paint.Style.FILL
        mPaint.strokeCap = Paint.Cap.ROUND
        mPaintText_P.isAntiAlias = true
        mPaintText_P.strokeWidth = 0.0f
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        mInit = false
        mTouchOnMoveInner = 100.0f
        mTouchInnerOffset = resources.getDimension(R.dimen.circie_inner_margin) * resources.displayMetrics.density
        mTouchOuter = 80
        invalidate()
    }

    fun recycle() {
        recycleDrawable(mLightCircleBackground)
        recycleDrawable(mLightCircleProgress)
        recycleDrawable(mSoundCircleBackground)
        recycleDrawable(mSoundCircleProgress)
        recycleDrawable(mSwitchBtnOn)
        recycleDrawable(mSwitchBtnOff)
    }

    private fun recycleDrawable(drawable: Drawable?) {
        try {
            drawable?.callback = null
        } catch (_: Exception) {
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        if (!mInit) {
            mXCenterPosition = measuredWidth / 2
            mYCenterPosition = measuredHeight / 2
            val width = (mLightCircleBackground as BitmapDrawable).intrinsicWidth
            var mMin = Math.min(mXCenterPosition, mYCenterPosition)
            if (mMin > width / 2) mMin = width / 2
            mRadius = mMin
            mCircleRect.left = (mXCenterPosition - mRadius).toFloat()
            mCircleRect.top = (mYCenterPosition - mRadius).toFloat()
            mCircleRect.right = (mXCenterPosition + mRadius).toFloat()
            mCircleRect.bottom = (mYCenterPosition + mRadius).toFloat()
            mPaintText_P.typeface = mTypeface
            mInit = true
        }
        canvas.drawFilter = PaintFlagsDrawFilter(0, 3)
        mPaintText_P.textSize = mProgressTextSize.toFloat()
        val progress = (mProgress.toInt()).toString()
        val bounds = Rect()
        mPaintText_P.getTextBounds(progress, 0, progress.length, bounds)
        val fontMetrics = mPaintText_P.fontMetrics
        val textHeight = Math.abs(fontMetrics.top - fontMetrics.bottom)
        val textWidth = mPaintText_P.measureText(progress, 0, progress.length)
        mPaintText_P.color = resources.getColor(R.color.text_red)
        val mSwitchBtnWidth = (mSwitchBtnOn as BitmapDrawable).intrinsicWidth
        val mSwitchBtnHight = (mSwitchBtnOn as BitmapDrawable).intrinsicWidth
        if (isAudiobox) {
            mPaintText_P.textSize = mAudioBoxTextSize.toFloat()
            mPaintText_P.color = resources.getColor(R.color.a66_text_red)
            mOff = resources.getString(R.string.audio_box)
            val offWidth = mPaintText_P.measureText(mOff, 0, mOff.length)
            canvas.drawText(mOff, mXCenterPosition - offWidth / 2.0f, mYCenterPosition.toFloat(), mPaintText_P)
            mCurrentAngle = 0.0f
            canvas.drawBitmap(
                (mSwitchBtnOff as BitmapDrawable).bitmap,
                mXCenterPosition - mSwitchBtnWidth / 2f,
                (mYCenterPosition + (mRadius - mSwitchBtnHight / 2) - 20).toFloat(), mPaint
            )
        } else if (mIsOff) {
            mOff = resources.getString(R.string.off)
            val offWidth2 = mPaintText_P.measureText(mOff, 0, mOff.length)
            canvas.drawText(mOff, mXCenterPosition - offWidth2 / 2.0f, mYCenterPosition.toFloat(), mPaintText_P)
            mCurrentAngle = 0.0f
            canvas.drawBitmap(
                (mSwitchBtnOff as BitmapDrawable).bitmap,
                mXCenterPosition - mSwitchBtnWidth / 2f,
                (mYCenterPosition + (mRadius - mSwitchBtnHight / 2) - 20).toFloat(), mPaint
            )
        } else {
            canvas.drawText(progress, mXCenterPosition - textWidth / 2.0f, mYCenterPosition.toFloat(), mPaintText_P)
            mPaintText_P.textSize = (mProgressTextSize / 4).toFloat()
            val fontMetricsSub = mPaintText_P.fontMetrics
            val textHeightSub = Math.abs(fontMetricsSub.top - fontMetricsSub.bottom)
            canvas.drawText(
                "%", mXCenterPosition + textWidth / 2.0f,
                mYCenterPosition - (textHeight - textHeightSub) / 2.0f, mPaintText_P
            )
            if (mIsSwitchBtnClick) {
                canvas.drawBitmap(
                    (mSwitchBtnOff as BitmapDrawable).bitmap,
                    mXCenterPosition - mSwitchBtnWidth / 2f,
                    (mYCenterPosition + (mRadius - mSwitchBtnHight / 2) - 20).toFloat(), mPaint
                )
            } else {
                canvas.drawBitmap(
                    (mSwitchBtnOn as BitmapDrawable).bitmap,
                    mXCenterPosition - mSwitchBtnWidth / 2f,
                    (mYCenterPosition + (mRadius - mSwitchBtnHight / 2) - 20).toFloat(), mPaint
                )
            }
        }
        mPaintText_P.reset()
        mPaintText_P.typeface = mTypeface
        var type = mSoundTypeTitle
        if (mCurrentType == CircleType.LIGHT) type = mLightTypeTitle
        if (isAudiobox) type = ""
        mPaintText_P.color = resources.getColor(R.color.text_plain_gray_deep)
        mPaintText_P.textSize = mProgressTypeSize.toFloat()
        val textWidthType = mPaintText_P.measureText(type, 0, type.length)
        val fontMetricsType = mPaintText_P.fontMetrics
        val textHeightType = Math.abs(fontMetricsType.top - fontMetricsType.bottom)
        canvas.drawText(
            type, mXCenterPosition - textWidthType / 2.0f,
            mYCenterPosition + textHeightType + 20.0f, mPaintText_P
        )
        if (progress.toInt() > 99 && !mIsOff) {
            val bitmap = if (mCurrentType == CircleType.SOUND) {
                (mSoundCircleProgress as BitmapDrawable).bitmap
            } else {
                (mLightCircleProgress as BitmapDrawable).bitmap
            }
            canvas.drawBitmap(bitmap, null, mCircleRect, mPaint)
        } else {
            val bitmap = if (mCurrentType == CircleType.SOUND) {
                (mSoundCircleBackground as BitmapDrawable).bitmap
            } else {
                (mLightCircleBackground as BitmapDrawable).bitmap
            }
            canvas.drawBitmap(bitmap, null, mCircleRect, mPaint)
            mPath.reset()
            mPath.moveTo(mXCenterPosition.toFloat(), mYCenterPosition.toFloat())
            val a = (90.0f + mBlankAngle / 2.0f) * Math.PI / 180.0
            mPath.lineTo(
                (mXCenterPosition + mRadius * Math.cos(a)).toFloat(),
                (mYCenterPosition + mRadius * Math.sin(a)).toFloat()
            )
            mPath.lineTo(
                (mXCenterPosition + mRadius * Math.cos(a)).toFloat(),
                (mYCenterPosition + mRadius * Math.sin(a)).toFloat()
            )
            if (!mIsOff) {
                mPath.arcTo(mCircleRect, 90.0f + mBlankAngle / 2.0f, mCurrentAngle)
            } else {
                mPath.arcTo(mCircleRect, 90.0f + mBlankAngle / 2.0f, 0.0f)
            }
            mPath.close()
            canvas.clipPath(mPath)
            val bitmap2 = if (mCurrentType == CircleType.SOUND) {
                (mSoundCircleProgress as BitmapDrawable).bitmap
            } else {
                (mLightCircleProgress as BitmapDrawable).bitmap
            }
            canvas.drawBitmap(bitmap2, null, mCircleRect, mPaint)
        }
        super.onDraw(canvas)
    }

    private inner class TopLongPressedThread(
        private var currentAngle: Float,
        private var progress: Float
    ) : Thread() {
        @Volatile private var flag = true
        override fun run() {
            while (flag) {
                try {
                    if (currentAngle < 360.0f) {
                        progress += mProgressStep
                        if (progress >= 100.0f) progress = 100.0f
                        currentAngle = mRangAngle / 100.0f * progress.toInt()
                        currentAngle = if (currentAngle >= 360.0f) mHundredPercent else currentAngle
                    }
                    post {
                        mCurrentAngle = currentAngle
                        mProgress = progress
                        if (mProgress.toInt() == 100) {
                            mStartAngle = 0.0f
                            mOldDragEventX = mXCenterPosition.toFloat()
                        }
                        invalidate()
                        mCircleButtonClickListener?.onTopButtonClick(mProgress)
                    }
                    sleep(mProgressInterval.toLong())
                } catch (e: InterruptedException) {
                    flag = false
                }
            }
        }

        fun cancel() {
            flag = false
            interrupt()
        }
    }

    private inner class BottomLongPressedThread(
        private var currentAngle: Float,
        private var progress: Float
    ) : Thread() {
        @Volatile private var flag = true
        override fun run() {
            while (flag) {
                try {
                    if (currentAngle > 0.0f) {
                        progress -= mProgressStep
                        if (progress <= 0.0f) progress = 0.0f
                        currentAngle = mRangAngle / 100.0f * progress.toInt()
                        if (currentAngle <= 0.0f) currentAngle = 0.0f
                    }
                    post {
                        mCurrentAngle = currentAngle
                        mProgress = progress
                        if (mProgress.toInt() == 0) {
                            mStartAngle = 0.0f
                            mOldDragEventX = mXCenterPosition.toFloat()
                        }
                        invalidate()
                        mCircleButtonClickListener?.onBottomButtonCLick(mProgress)
                    }
                    sleep(mProgressInterval.toLong())
                } catch (e: InterruptedException) {
                    flag = false
                }
            }
        }

        fun cancel() {
            flag = false
            interrupt()
        }
    }

    private fun stopLongPressedThread(thread: Thread?) {
        if (thread != null && thread.isAlive) thread.interrupt()
    }

    private inner class CheckForLongPress : Runnable {
        override fun run() {
            mIsLongPressed = true
            if (!mIsTopClick || mProgress >= 100.0f) {
                if (mIsBottomClick && mProgress > 0.0f) {
                    if (mBottomLongPressedThread == null || mBottomLongPressedThread?.isAlive != true) {
                        mBottomLongPressedThread = BottomLongPressedThread(mCurrentAngle, mProgress)
                        mBottomLongPressedThread?.start()
                    }
                }
                return
            }
            if (mTopLongPressedThread == null || mTopLongPressedThread?.isAlive != true) {
                mTopLongPressedThread = TopLongPressedThread(mCurrentAngle, mProgress)
                mTopLongPressedThread?.start()
            }
        }
    }

    private fun removeLongPressCallback() {
        stopLongPressedThread(mTopLongPressedThread)
        stopLongPressedThread(mBottomLongPressedThread)
        if (mPendingCheckForLongPress != null) {
            removeCallbacks(mPendingCheckForLongPress)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mIsDragging = false
                mIsSwitchBtnClick = touchInSwitchBtn(event)
            }
            MotionEvent.ACTION_UP -> {
                removeLongPressCallback()
                mOldDragEventX = -1.0f
                mStartAngle = 0.0f
                if (!mIsLongPressed && mIsSwitchBtnClick && touchInSwitchBtn(event)) {
                    mCircleButtonClickListener?.onSwitchButtonClick(mIsOff)
                    mIsSwitchBtnClick = false
                }
                if (mIsSwitchBtnClick && touchInSwitchBtn(event)) {
                    mCircleButtonClickListener?.onSwitchButtonClick(mIsOff)
                    mIsSwitchBtnClick = false
                }
                if (mIsDragging) {
                    mCircleProgressDragListener?.onDragFinish(mProgress)
                }
                mIsDragging = false
                mIsLongPressed = false
                mIsTopClick = false
                mIsBottomClick = false
                mIsSwitchBtnClick = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (mIsOff || isAudiobox) return true
                val inCircle = touchInCircle(
                    event.x.toDouble(), event.y.toDouble(),
                    mXCenterPosition.toFloat(), mYCenterPosition.toFloat(),
                    mRadius - mTouchOnMoveInner, mTouchOuter + mRadius
                )
                if (inCircle && !mIsTopClick && !mIsBottomClick && !mIsSwitchBtnClick) {
                    mIsDragging = true
                    dragging(event)
                    mCircleProgressDragListener?.onProgressDrag(mProgress)
                }
            }
        }
        invalidate()
        return true
    }

    private fun dragging(event: MotionEvent) {
        val radian = getCircleAngle(
            event.x.toDouble(), event.y.toDouble(),
            mXCenterPosition.toFloat(), mYCenterPosition.toFloat()
        )
        val angle2 = (180.0 * (radian / Math.PI)).toFloat()
        val angle: Float = if (angle2 < 90.0f) angle2 + 270.0f else angle2 - 90.0f
        var a = angle
        if (a > 360.0f) a = mHundredPercent
        if (mStartAngle == 0.0f) {
            mStartAngle = a
            return
        }
        if (mProgress.toInt() == 0) {
            if (mStartAngle >= a) {
                mStartAngle = 0.0f
                return
            }
        } else if (mProgress.toInt() == 100 && mStartAngle <= a) {
            mStartAngle = 0.0f
            return
        }
        if (mCanTapInCircle) {
            val progress = mCurrentAngle / mProgressAngleStep * mProgressStep
            when (isIllegal(progress.toDouble(), mOldDragEventX, event.x)) {
                IllegalType.NONE -> {
                    mProgress = a / mProgressAngleStep * mProgressStep
                    mCurrentAngle = a
                    mStartAngle = a
                    mOldDragEventX = event.x
                }
                IllegalType.FOURTOONE -> {
                    mCurrentAngle = mHundredPercent
                    mProgress = 100.0f
                    mStartAngle = 0.0f
                    mOldDragEventX = mXCenterPosition.toFloat()
                }
                IllegalType.ONETOFOUR -> {
                    mCurrentAngle = 0.0f
                    mProgress = 0.0f
                    mStartAngle = 0.0f
                    mOldDragEventX = mXCenterPosition.toFloat()
                }
            }
            return
        }
        if (mStartAngle > 270.0f && a < 90.0f) {
            mProgress += (360.0f + a - mStartAngle) / mProgressAngleStep * mProgressStep
        } else if (mStartAngle < 90.0f && a > 270.0f) {
            mProgress -= (mStartAngle + 360.0f - a) / mProgressAngleStep * mProgressStep
        } else {
            mProgress += (a - mStartAngle) / mProgressAngleStep * mProgressStep
        }
        if (mProgress <= 0.0f) mProgress = 0.0f
        if (mProgress >= 100.0f) mProgress = 100.0f
        mCurrentAngle = mRangAngle / 100.0f * mProgress.toInt()
        if (mCurrentAngle <= 0.0f) mCurrentAngle = 0.0f
        if (mCurrentAngle >= 360.0f) mCurrentAngle = mHundredPercent
        mStartAngle = a
    }

    private fun touchInSwitchBtn(event: MotionEvent): Boolean {
        val mSwitchBtnWidth = (mSwitchBtnOn as BitmapDrawable).intrinsicWidth
        val mSwitchBtnHight = (mSwitchBtnOn as BitmapDrawable).intrinsicWidth
        val xPos = event.x
        val yPos = event.y
        return xPos > (mXCenterPosition - mSwitchBtnWidth / 2).toFloat() &&
            xPos < (mXCenterPosition + mSwitchBtnWidth / 2).toFloat() &&
            yPos > (mYCenterPosition - ((mRadius - mSwitchBtnHight / 2) + -20)).toFloat() &&
            yPos > (mYCenterPosition + ((mRadius - mSwitchBtnHight / 2) + -20)).toFloat()
    }

    private fun touchInCircle(
        xPoint: Double, yPoint: Double, xCenter: Float, yCenter: Float,
        innerRadius: Float, outerRadius: Int
    ): Boolean {
        val diffX = xPoint - xCenter
        val diffY = yPoint - yCenter
        val dist = diffX * diffX + diffY * diffY
        return dist < (outerRadius * outerRadius).toDouble() && dist > (innerRadius * innerRadius).toDouble()
    }

    private fun isIllegal(progress: Double, oldEventX: Float, nowEventX: Float): IllegalType {
        if (oldEventX == -1.0f) return IllegalType.NONE
        if (oldEventX >= mXCenterPosition && nowEventX <= mXCenterPosition && progress > 90.0) {
            return IllegalType.FOURTOONE
        }
        if (oldEventX <= mXCenterPosition && nowEventX >= mXCenterPosition && progress < 10.0) {
            return IllegalType.ONETOFOUR
        }
        return IllegalType.NONE
    }

    private fun getCircleAngle(px: Double, py: Double, xRadiusCenter: Float, yRadiusCenter: Float): Double {
        val diffX = px - xRadiusCenter
        val diffY = py - yRadiusCenter
        val angle = Math.atan2(diffY, diffX)
        return if (angle < 0.0) angle + 6.283185307179586 else angle
    }

    fun setOnCircleButtonClickListener(l: CircleButtonClickListener?) {
        mCircleButtonClickListener = l
    }

    fun setOnCircleProgressDragListener(l: CircleProgressDragListener?) {
        mCircleProgressDragListener = l
    }

    fun setOnCircleSwitchListener(l: CircleSwitchListener?) {
        mCircleSwitchListener = l
    }

    fun turnOff() {
        if (!mIsOff) {
            mIsOff = true
            isAudiobox = false
            removeLongPressCallback()
            mIsLongPressed = false
            mIsTopClick = false
            mIsBottomClick = false
            mArrowEnable = false
            mOffProgress = mProgress
            mOffCurrentAngle = mCurrentAngle
            mProgress = 0.0f
            mCurrentAngle = 0.0f
            invalidate()
            mCircleSwitchListener?.onTurnOff(mOffProgress)
        }
    }

    fun turnOn() {
        if (mIsOff) {
            isAudiobox = false
            mArrowEnable = true
            mIsOff = false
            mProgress = mOffProgress
            mCurrentAngle = mOffCurrentAngle
            mOffProgress = 0.0f
            mOffCurrentAngle = 0.0f
            invalidate()
            mCircleSwitchListener?.onTurnOn(mProgress)
        }
    }

    fun turnAudioBox() {
        isAudiobox = true
        removeLongPressCallback()
        mIsLongPressed = false
        mIsTopClick = false
        mIsBottomClick = false
        mArrowEnable = false
        mOffProgress = mProgress
        mCurrentAngle = 0.0f
        mProgress = 0.0f
        invalidate()
        mCircleSwitchListener?.onTurnOff(mOffProgress)
    }

    fun initIsAudiobox() {
        isAudiobox = false
    }

    fun isOff(): Boolean = mIsOff

    fun setProgress(progress: Float) {
        mProgress = progress
        mCurrentAngle = if (mProgress == 100.0f) mHundredPercent else progress / 100.0f * mRangAngle
        invalidate()
    }

    fun getProgress(): Double = mProgress.toDouble()

    fun setCircleType(type: CircleType) {
        if (mCurrentType != type) {
            mCurrentType = type
            invalidate()
        }
    }

    fun getCurrentType(): CircleType = mCurrentType
}
