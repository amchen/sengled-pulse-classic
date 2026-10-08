package com.sengled.cloud.ui.view

import android.content.Context
import android.util.AttributeSet

/**
 * Same bundled-font text view under the original cloud package name,
 * as referenced by a66_*.xml layouts.
 */
class ThTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : com.sengled.stspeaker.widget.ThTextView(context, attrs, defStyle)
