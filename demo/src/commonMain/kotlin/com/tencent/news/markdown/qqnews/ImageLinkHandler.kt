package com.tencent.news.markdown.qqnews

import com.tencent.news.markdown.model.ImageData
import com.tencent.news.markdown.model.ImageLinkHandlerImpl

class QnImageLinkHandler : ImageLinkHandlerImpl() {
    override fun preview(image: ImageData) {
        println("preview images: $image")
    }
}