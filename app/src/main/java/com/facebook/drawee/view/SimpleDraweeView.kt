package com.facebook.drawee.view

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * Minimal stand-in for Fresco's SimpleDraweeView, which the original
 * activity_sengled_tips.xml layout references. Fresco is not bundled in
 * this rebuild, so this ImageView subclass keeps the layout inflatable
 * and plays GIF drawables on API 28+ (first frame below that).
 */
class SimpleDraweeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    /** Load a GIF resource, animating it when the platform supports it. */
    fun setGifResource(resId: Int) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val source = ImageDecoder.createSource(resources, resId)
                val drawable = ImageDecoder.decodeDrawable(source)
                setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) drawable.start()
                return
            } catch (_: Exception) {
            }
        }
        setImageResource(resId)
    }
}
