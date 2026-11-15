package com.tencent.kuiklybase.markdown.ui

import android.app.Application
import android.view.ViewGroup
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule

class AndroidComposePageDelegate : KuiklyRenderViewBaseDelegatorDelegate, IComposePageDelegate {

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)

    private var onFirstFrame: (() -> Unit)? = null

    private val exportModules = HashMap<String, KuiklyRenderBaseModule>()

    override fun onCreate(
        app: Application,
        rootView: ViewGroup,
        pageName: String,
        modules: Map<String, KuiklyRenderBaseModule>,
        onFirstFrame: (() -> Unit)?,
        size: android.util.Size?,
        pageData: Map<String, Any>,
    ) {

        exportModules.putAll(modules)
        this.onFirstFrame = onFirstFrame

        kuiklyRenderViewDelegator.onAttach(
            container = rootView,
            contextCode = "",
            pageName = pageName,
            pageData = pageData,
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

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        exportModules.forEach { (name, module) ->
            kuiklyRenderExport.moduleExport(name) {
                module
            }
        }
    }

    override fun onKuiklyRenderContentViewCreated() {
        onFirstFrame?.invoke()
    }
}
