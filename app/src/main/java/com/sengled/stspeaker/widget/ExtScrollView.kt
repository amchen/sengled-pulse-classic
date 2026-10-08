package com.sengled.stspeaker.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * Plain ScrollView subclass used by the original layouts,
 * ported from com.sengled.stspeaker.widget.ExtScrollView.
 */
class ExtScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    interface ScrollStateListener {
        fun scrollToBottom()
        fun scrollToTop()
    }

    var scrollStateListener: ScrollStateListener? = null
}
