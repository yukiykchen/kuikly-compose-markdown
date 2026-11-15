package com.tencent.kuiklybase.markdown

import android.util.Log
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuiklybase.markdown.ui.AndroidComposePageActivity

class MainActivity : AndroidComposePageActivity() {
    override fun onCreateComposePage() {
        val params = JSONObject()
            .put("aiChatApiUrl", BuildConfig.AI_CHAT_API_URL)
            .put("aiChatModel", BuildConfig.AI_CHAT_MODEL)
            .put("aiChatApiKey", BuildConfig.AI_CHAT_API_KEY)
        compose.onCreate(
            this.application,
            findViewById(android.R.id.content),
            "ai_chat",
            onFirstFrame = { Log.d("AiChatDemo", "onFirstFrame") },
            pageData = mapOf(
                "param" to params,
            ),
        )
    }
}
