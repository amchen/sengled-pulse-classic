package com.sengled.stspeaker.widget

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * TextView using the original app's bundled font (assets/fonts/th.ttf),
 * ported from com.sengled.stspeaker.widget.ThTextView.
 */
open class ThTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    init {
        typeface = getTypeface(context)
    }

    companion object {
        @Volatile private var sTypeface: Typeface? = null

        fun getTypeface(context: Context): Typeface? {
            if (sTypeface == null) {
                sTypeface = try {
                    Typeface.createFromAsset(context.applicationContext.assets, "fonts/th.ttf")
                } catch (_: Exception) {
                    Typeface.DEFAULT
                }
            }
            return sTypeface
        }
    }
}
