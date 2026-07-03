package com.tencent.news.markdown.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

abstract class AndroidComposePageActivity : FragmentActivity() {

    protected val compose: IComposePageDelegate = AndroidComposePageDelegate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
        // 一定要干掉这个属性，否则下面setStatusBarColor一定失效
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        // 这个属性也需要开启，否则setStatusBarColor也会有问题
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.decorView.applySystemUiFlag(true, View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        // 拉满屏幕（在小米手机上下面代码有效）
        window.decorView.applySystemUiFlag(true, View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        // 状态栏是否使用深色字体（在小米手机上下面代码失效）
        window.decorView.applySystemUiFlag(true, 0x00002000)
        window.statusBarColor = Color.TRANSPARENT

        onCreateComposePage()
    }

    abstract fun onCreateComposePage()

    override fun onResume() {
        super.onResume()
        compose.onResume()
    }

    override fun onPause() {
        super.onPause()
        compose.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        compose.onDestroy()
    }

    private fun View?.applySystemUiFlag(append: Boolean, flag: Int) {
        this ?: return
        systemUiVisibility = if (append) {
            systemUiVisibility.or(flag)
        } else {
            systemUiVisibility and flag.inv()
        }
    }

}

