package com.tencent.kuiklybase.markdown.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuiklybase.markdown.compose.components.MarkdownComponents
import com.tencent.kuiklybase.markdown.compose.components.markdownComponents
import com.tencent.kuiklybase.markdown.model.ImageLinkHandler
import com.tencent.kuiklybase.markdown.model.ImageLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.ImageTransformer
import com.tencent.kuiklybase.markdown.model.MarkdownAnimations
import com.tencent.kuiklybase.markdown.model.MarkdownAnnotator
import com.tencent.kuiklybase.markdown.model.MarkdownColors
import com.tencent.kuiklybase.markdown.model.MarkdownDimens
import com.tencent.kuiklybase.markdown.model.MarkdownExtendedSpans
import com.tencent.kuiklybase.markdown.model.MarkdownEmojiShortcodes
import com.tencent.kuiklybase.markdown.model.MarkdownPadding
import com.tencent.kuiklybase.markdown.model.MarkdownState
import com.tencent.kuiklybase.markdown.model.MarkdownTypography
import com.tencent.kuiklybase.markdown.model.NoOpImageTransformerImpl
import com.tencent.kuiklybase.markdown.model.NoOpTextProvider
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandler
import com.tencent.kuiklybase.markdown.model.ReferenceLinkHandlerImpl
import com.tencent.kuiklybase.markdown.model.StreamingMarkdownState
import com.tencent.kuiklybase.markdown.model.TextProvider
import com.tencent.kuiklybase.markdown.model.markdownAnimations
import com.tencent.kuiklybase.markdown.model.markdownAnnotator
import com.tencent.kuiklybase.markdown.model.markdownDimens
import com.tencent.kuiklybase.markdown.model.markdownExtendedSpans
import com.tencent.kuiklybase.markdown.model.markdownPadding
import com.tencent.kuiklybase.markdown.model.rememberMarkdownState
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownColors
import com.tencent.kuiklybase.markdown.model.DefaultMarkdownTypography
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import com.tencent.kuiklybase.markdown.compat.createMarkdownParser


/**
 * Renders the markdown content.
 *
 * @param content The markdown content to be rendered.
 * @param colors The colors to be used for rendering.
 * @param typography The typography to be used for rendering.
 * @param modifier The modifier to be applied to the container.
 * @param padding The padding to be applied to the container.
 * @param dimens The dimensions to be used for rendering.
 * @param flavour The flavour descriptor for parsing the markdown. By default uses GFM flavour.
 * @param parser The parser to be used for parsing the markdown. By default uses the flavour supplied.
 * @param imageTransformer The image transformer to be used for rendering images.
 * @param imageLinkHandler The image link handler to be used for handling images.
 * @param textProvider The text to be used for rendering text.
 * @param annotator The annotator to be used for rendering annotations.
 * @param extendedSpans The extended spans to be used for rendering.
 * @param components The components to be used for rendering.
 * @param referenceLinkHandler The reference link handler to be used for handling links.
 * @param animations The animations to be used for rendering.
 * @param loading A composable function to be displayed while loading the content.
 * @param success A composable function to be displayed with the markdown content. It receives the modifier, state and components as parameters. By default this is a [Column].
 * @param error A composable function to be displayed in case of an error. Only really possible if assertions are enabled on the parser)
 */
@Composable
fun Markdown(
    content: String,
    colors: MarkdownColors = remember { DefaultMarkdownColors() },
    typography: MarkdownTypography = remember { DefaultMarkdownTypography() },
    modifier: Modifier = Modifier.fillMaxSize(),
    padding: MarkdownPadding = markdownPadding(),
    dimens: MarkdownDimens = markdownDimens(),
    flavour: MarkdownFlavourDescriptor = remember { GFMFlavourDescriptor() },
    parser: MarkdownParser = remember(flavour) {
        createMarkdownParser(flavour, CancellationToken.NonCancellable)
    },
    imageTransformer: ImageTransformer = NoOpImageTransformerImpl(),
    imageLinkHandler: ImageLinkHandler = ImageLinkHandlerImpl(),
    textProvider: TextProvider = NoOpTextProvider(),
    annotator: MarkdownAnnotator = markdownAnnotator(),
    extendedSpans: MarkdownExtendedSpans = markdownExtendedSpans(),
    components: MarkdownComponents = remember { markdownComponents() },
    animations: MarkdownAnimations = markdownAnimations(),
    referenceLinkHandler: ReferenceLinkHandler = remember { ReferenceLinkHandlerImpl() },
    emojiShortcodes: MarkdownEmojiShortcodes? = LocalMarkdownEmojiShortcodes.current,
    loading: @Composable (modifier: Modifier) -> Unit = { Box(modifier = modifier) },
    success: @Composable (state: MarkdownState, components: MarkdownComponents, modifier: Modifier) -> Unit = { state, components, modifier ->
        MarkdownSuccess(state = state, components = components, modifier = modifier)
    },
    error: @Composable (modifier: Modifier) -> Unit = { Box(modifier = modifier) },
) {
    val state = rememberMarkdownState(flavour = flavour, parser = parser, referenceLinkHandler = referenceLinkHandler)
    LaunchedEffect(content, state) {
        state.parse(content, false)
    }

    Markdown(
        state = state,
        colors = colors,
        typography = typography,
        modifier = modifier,
        padding = padding,
        dimens = dimens,
        imageTransformer = imageTransformer,
        imageLinkHandler = imageLinkHandler,
        textProvider = textProvider,
        annotator = annotator,
        extendedSpans = extendedSpans,
        components = components,
        animations = animations,
        referenceLinkHandler = referenceLinkHandler,
        emojiShortcodes = emojiShortcodes,
        loading = loading,
        success = success,
        error = error
    )
}

/**
 * Renders the markdown content.
 *
 * @param state The markdown state to be rendered.
 * @param colors The colors to be used for rendering.
 * @param typography The typography to be used for rendering.
 * @param modifier The modifier to be applied to the container.
 * @param padding The padding to be applied to the container.
 * @param dimens The dimensions to be used for rendering.
 * @param imageTransformer The image transformer to be used for rendering images.
 * @param imageLinkHandler The image link handler to be used for handling images.
 * @param textProvider The text to be used for rendering text.
 * @param annotator The annotator to be used for rendering annotations.
 * @param extendedSpans The extended spans to be used for rendering.
 * @param components The components to be used for rendering.
 * @param animations The animations to be used for rendering.
 * @param loading A composable function to be displayed while loading the content.
 * @param success A composable function to be displayed with the markdown content. It receives the modifier, state and components as parameters. By default this is a [Column].
 * @param error A composable function to be displayed in case of an error. Only really possible if assertions are enabled on the parser)
 */
@Composable
fun Markdown(
    state: MarkdownState,
    colors: MarkdownColors = remember { DefaultMarkdownColors() },
    typography: MarkdownTypography = remember { DefaultMarkdownTypography() },
    modifier: Modifier = Modifier.fillMaxSize(),
    padding: MarkdownPadding = markdownPadding(),
    dimens: MarkdownDimens = markdownDimens(),
    imageTransformer: ImageTransformer = NoOpImageTransformerImpl(),
    imageLinkHandler: ImageLinkHandler = ImageLinkHandlerImpl(),
    textProvider: TextProvider = NoOpTextProvider(),
    annotator: MarkdownAnnotator = markdownAnnotator(),
    extendedSpans: MarkdownExtendedSpans = markdownExtendedSpans(),
    components: MarkdownComponents = remember { markdownComponents() },
    animations: MarkdownAnimations = markdownAnimations(),
    referenceLinkHandler: ReferenceLinkHandler = state.referenceLinkHandler,
    emojiShortcodes: MarkdownEmojiShortcodes? = LocalMarkdownEmojiShortcodes.current,
    loading: @Composable (modifier: Modifier) -> Unit = { Box(modifier = modifier) },
    success: @Composable (state: MarkdownState, components: MarkdownComponents, modifier: Modifier) -> Unit = { state, components, modifier ->
        MarkdownSuccess(state = state, components = components, modifier = modifier)
    },
    error: @Composable (modifier: Modifier) -> Unit = { Box(modifier = modifier) },
) {
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides referenceLinkHandler,
        LocalMarkdownPadding provides padding,
        LocalMarkdownDimens provides dimens,
        LocalMarkdownColors provides colors,
        LocalMarkdownTypography provides typography,
        LocalImageTransformer provides imageTransformer,
        LocalImageLinkHandler provides imageLinkHandler,
        LocalTextProvider provides textProvider,
        LocalMarkdownAnnotator provides annotator,
        LocalMarkdownExtendedSpans provides extendedSpans,
        LocalMarkdownComponents provides components,
        LocalMarkdownAnimations provides animations,
        LocalMarkdownEmojiShortcodes provides emojiShortcodes,
    ) {
        success(state, components, modifier)
    }

}

/**
 * Renders append-only streaming Markdown using stable block slots and a
 * changing active tail. The default success slot is non-scrolling so a host
 * chat list can own scrolling and stick-to-bottom behavior.
 */
@Composable
fun Markdown(
    streamingMarkdownState: StreamingMarkdownState,
    colors: MarkdownColors = remember { DefaultMarkdownColors() },
    typography: MarkdownTypography = remember { DefaultMarkdownTypography() },
    modifier: Modifier = Modifier.fillMaxSize(),
    padding: MarkdownPadding = markdownPadding(),
    dimens: MarkdownDimens = markdownDimens(),
    imageTransformer: ImageTransformer = NoOpImageTransformerImpl(),
    imageLinkHandler: ImageLinkHandler = ImageLinkHandlerImpl(),
    textProvider: TextProvider = NoOpTextProvider(),
    annotator: MarkdownAnnotator = markdownAnnotator(),
    extendedSpans: MarkdownExtendedSpans = markdownExtendedSpans(),
    components: MarkdownComponents = remember { markdownComponents() },
    animations: MarkdownAnimations = markdownAnimations(),
    referenceLinkHandler: ReferenceLinkHandler = streamingMarkdownState.referenceLinkHandler,
    emojiShortcodes: MarkdownEmojiShortcodes? = LocalMarkdownEmojiShortcodes.current,
    success: @Composable (state: StreamingMarkdownState, components: MarkdownComponents, modifier: Modifier) -> Unit =
        { state, markdownComponents, successModifier ->
            StreamingMarkdownContent(
                streamingMarkdownState = state,
                components = markdownComponents,
            )
        },
) {
    CompositionLocalProvider(
        LocalReferenceLinkHandler provides referenceLinkHandler,
        LocalMarkdownRenderCache provides streamingMarkdownState.renderCache,
        LocalMarkdownPadding provides padding,
        LocalMarkdownDimens provides dimens,
        LocalMarkdownColors provides colors,
        LocalMarkdownTypography provides typography,
        LocalImageTransformer provides imageTransformer,
        LocalImageLinkHandler provides imageLinkHandler,
        LocalTextProvider provides textProvider,
        LocalMarkdownAnnotator provides annotator,
        LocalMarkdownExtendedSpans provides extendedSpans,
        LocalMarkdownComponents provides components,
        LocalMarkdownAnimations provides animations,
        LocalMarkdownEmojiShortcodes provides emojiShortcodes,
    ) {
        success(streamingMarkdownState, components, modifier)
    }
}

/**
 * Renders the parsed markdown content.
 *
 * @param state The success markdown state.
 * @param components The MarkdownComponents instance containing the components to use.
 * @param modifier The modifier to be applied to the container.
 */
@Composable
fun MarkdownSuccess(
    state: MarkdownState,
    components: MarkdownComponents,
    modifier: Modifier = Modifier,
) {
    val content = { state.getContent() }
    val skipLinkDefinition = state.skipLinkDefinition()
    val rememberedComponents = components
    Column(modifier = modifier) {
        state.nodeListState.forEachIndexed { index, result ->
            handleElement(
                result,
                rememberedComponents,
                content,
                includeSpacer = false,
                skipLinkDefinition = skipLinkDefinition
            )
        }
    }
}
