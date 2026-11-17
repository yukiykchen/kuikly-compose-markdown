package com.tencent.kuiklybase.markdown.compat

internal actual fun createDefaultParserCompatFactory(): ParserCompatFactory =
    OhosParserCompatFactory()
