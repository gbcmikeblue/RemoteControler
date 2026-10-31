package com.zjianhao.aircon.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Android 15（API 35）起，targetSdk >= 35 的应用强制 edge-to-edge，
 * 内容会顶到状态栏下面，标题会与状态栏图标重叠。
 * 这里把 Toolbar 背景延伸到状态栏区域，同时把标题下移，保持视觉连续。
 */
object WindowInsetsHelper {

    fun fitToolbarToStatusBar(toolbar: View) {
        val baseHeight = toolbar.layoutParams?.height ?: return
        val basePaddingTop = toolbar.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.layoutParams = view.layoutParams.apply { height = baseHeight + top }
            view.setPadding(
                view.paddingLeft,
                basePaddingTop + top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)
    }

    /** 底部手势条/导航栏避让。 */
    fun fitBottomInset(view: View) {
        val basePaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePaddingBottom + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
