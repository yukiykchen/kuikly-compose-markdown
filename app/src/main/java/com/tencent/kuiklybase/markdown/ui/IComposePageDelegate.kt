package com.tencent.kuiklybase.markdown.ui

import android.app.Application
import android.util.Size
import android.view.ViewGroup
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule

interface IComposePageDelegate {

    fun onCreate(
        app: Application,
        rootView: ViewGroup,
        pageName: String,
        modules: Map<String, KuiklyRenderBaseModule> = emptyMap(),
        onFirstFrame: (() -> Unit)? = null,
        size: Size? = null,
        pageData: Map<String, Any> = emptyMap(),
    )

    fun onResume()

    fun onPause()

    fun onDestroy()

    fun onDispatchBackEvent(): Boolean
}
