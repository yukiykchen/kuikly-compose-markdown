package com.tencent.news.markdown.ui

import android.app.Application
import android.view.ViewGroup
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewDelegator
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewDelegatorDelegate
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule

class AndroidComposePageDelegate : KuiklyRenderViewDelegatorDelegate, IComposePageDelegate {

    private val kuiklyRenderViewDelegator = KuiklyRenderViewDelegator(this)

    private var onFirstFrame: (() -> Unit)? = null

    private val exportModules = HashMap<String, KuiklyRenderBaseModule>()

    override fun onCreate(
        app: Application,
        rootView: ViewGroup,
        pageName: String,
        modules: Map<String, KuiklyRenderBaseModule>,
        onFirstFrame: (() -> Unit)?,
        size: android.util.Size?
    ) {

        exportModules.putAll(modules)
        this.onFirstFrame = onFirstFrame

        kuiklyRenderViewDelegator.onAttach(
            container = rootView,
            contextCode = "",
            pageName = pageName,
            pageData = emptyMap(),
            size = size
        )

    }

    override fun onResume() {
        kuiklyRenderViewDelegator.onResume()
    }

    override fun onPause() {
        kuiklyRenderViewDelegator.onPause()
    }

    override fun onDestroy() {
        kuiklyRenderViewDelegator.onDetach()
    }

    override fun onDispatchBackEvent(): Boolean {
        return kuiklyRenderViewDelegator.onBackPressed()
    }
}