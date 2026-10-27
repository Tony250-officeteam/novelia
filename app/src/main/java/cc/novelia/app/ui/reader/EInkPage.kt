@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package cc.novelia.app.ui.reader

import cc.novelia.app.ui.components.base.AppTextButton
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.CharacterStyle
import android.text.style.UnderlineSpan
import android.text.style.UpdateAppearance
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.model.Page
import cc.novelia.app.data.model.Position
import cc.novelia.app.data.model.ReaderSettings
import cc.novelia.app.reader.*
import cc.novelia.app.ui.components.media.IllustrationViewer
import cc.novelia.app.ui.theme.AppMotion
import cc.novelia.app.ui.theme.LocalEInkMode
import cc.novelia.app.ui.theme.LocalReducedMotion
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.mutableFloatStateOf

/**
 * 静态分页的位置状态，适用于普通屏幕和电子纸。页码是测量结果，持久定位使用段落及字符锚点。
 * install 在字体/视口变化后重新找页，move 才把锚点推进到用户实际翻到的页首，
 * 避免连续多次中间测量把位置逐步向前“吸附”。ready 为 false 时禁止按旧页表翻页。
 */
@Stable internal class EInkPageState(initial: Position?) {
    // 渲染器将此列表实例绑定到已测量文本快照；即使新测量的行值相等，
    // 也须替换旧列表实例，避免关联到旧快照。
    var pages by mutableStateOf<List<StaticPage>>(emptyList(), referentialEqualityPolicy())
        private set
    var pageIndex by mutableIntStateOf(0)
        private set
    var ready by mutableStateOf(false)
        private set
    private var anchorParagraph = ((initial?.index ?: 1) - 1).coerceAtLeast(0)
    private var anchorOffset = initial?.textOffset ?: 0
    private var sourceParagraph: Int? = null
    private var content = emptyList<ReadingParagraph>()
    val current get() = pages.getOrNull(pageIndex)
    val paragraph get() = current?.lines?.firstOrNull()?.paragraph ?: anchorParagraph
    val textOffset get() = current?.lines?.firstOrNull()?.start ?: anchorOffset
    val sourceIndex get() = content.getOrNull(paragraph)?.index ?: sourceParagraph
    val canGoBack get() = pageIndex > 0
    val canGoForward get() = pageIndex + 1 < pages.size

    fun install(next: List<StaticPage>, paragraphs: List<ReadingParagraph> = content) {
        val previousParagraph = content.getOrNull(anchorParagraph)
        sourceParagraph?.let { source ->
            anchorParagraph = paragraphs.indexOfFirst { it.index >= source }.takeIf { it >= 0 }
                ?: paragraphs.lastIndex.coerceAtLeast(0)
        }
        // 语言顺序变化后，拼接文本中的字符偏移含义也会变化；
        // 此时恢复整个原始段落，避免译文与原文分离。
        if(previousParagraph != null && previousParagraph.parts != paragraphs.getOrNull(anchorParagraph)?.parts) anchorOffset = 0
        content = paragraphs
        sourceParagraph = paragraphs.getOrNull(anchorParagraph)?.index
        pages = next
        // 视口连续测量期间始终保留请求的字符位置；
        // 若每次对齐到临时页首，会逐步丢失原来的阅读位置。
        pageIndex = pageForAnchor(next, anchorParagraph, anchorOffset)
        ready = true
    }
    fun invalidate() { ready = false }
    fun move(direction: Int) {
        if (ready && pages.isNotEmpty()) {
            pageIndex = (pageIndex + direction).coerceIn(0, pages.lastIndex)
            anchorParagraph = paragraph
            anchorOffset = textOffset
            sourceParagraph = content.getOrNull(anchorParagraph)?.index
        }
    }
    fun find(paragraph: Int, offset: Int = 0, source: Int? = null) {
        anchorParagraph = paragraph
        anchorOffset = offset.coerceAtLeast(0)
        sourceParagraph = source ?: content.getOrNull(paragraph)?.index
        pageIndex = pageForAnchor(pages, paragraph, anchorOffset)
    }
}

internal data class MeasuredEInkChapter(val paragraphs: List<ReadingParagraph>, val layouts: Map<Int, StaticLayout>, val pages: List<StaticPage>,
    /** 整章行序列（含段距），行级滚动按它推进。 */
    val allLines: List<PageLine> = emptyList())
private data class EInkMeasureInput(val paragraphs: List<ReadingParagraph>, val typography: List<Any>, val width: Int, val height: Int, val density: Float, val fontScale: Float)
private data class EInkMeasurement(val input: EInkMeasureInput, val chapter: MeasuredEInkChapter)

/** 在当前主题画笔上应用辅文本透明度，不保存固定颜色。 */
private class SecondaryOpacity(private val alpha: Float) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(paint: TextPaint) { paint.alpha = (paint.alpha * alpha).toInt().coerceIn(0, 255) }
}

/**
 * 用 Android StaticLayout 一次测量整章，再把不可分的行交给纯分页算法。
 * width/height 为像素，字号需乘 density 与 fontScale，段距只乘 density。
 * 拼接标签、缩进和语言分隔符的顺序必须与 paragraphPartStarts 一致，才能复用搜索字符锚点。
 * 返回的段落、布局与页表属于同一测量快照，显示时不能与新一轮内容混用。
 */
internal fun measureEInkChapter(paragraphs: List<ReadingParagraph>, settings: ReaderSettings, width: Int, height: Int, density: Float, fontScale: Float,
    readerTypeface: Typeface = Typeface.SANS_SERIF, checkCancelled: () -> Unit = {}): MeasuredEInkChapter {
    val layouts = mutableMapOf<Int, StaticLayout>()
    val lines = mutableListOf<PageLine>()
    paragraphs.forEachIndexed { index, paragraph ->
        checkCancelled()
        if (paragraph.imageUrl != null || paragraph.localImageId != null) {
            lines += PageLine(index, 0, 0, height, image = true)
        } else {
            val text = SpannableStringBuilder()
            paragraph.parts.forEachIndexed { partIndex, part ->
                checkCancelled()
                if (partIndex > 0) text.append(READING_PART_SEPARATOR)
                if (settings.parallel && part.source in listOf("sakura", "gpt", "youdao")) text.append(part.source.uppercase()).append("\n")
                val start = text.length
                if (settings.indent) text.append("　　")
                text.append(part.text)
                if (part.secondary) {
                    text.setSpan(SecondaryOpacity(settings.secondaryAlpha), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.setSpan(AbsoluteSizeSpan((readerPartFontSize(settings, true) * density * fontScale).toInt()), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (settings.underline) text.setSpan(UnderlineSpan(), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (text.isEmpty()) text.append(" ")
            val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = settings.fontSize * density * fontScale
                color = android.graphics.Color.BLACK
                typeface = Typeface.create(readerTypeface, if (settings.weight) Typeface.BOLD else Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setLineSpacing(0f, settings.lineHeight).build()
            layouts[index] = layout
            for (line in 0 until layout.lineCount) {
                if (line % 32 == 0) checkCancelled()
                lines += PageLine(index, layout.getLineStart(line), layout.getLineEnd(line), layout.getLineBottom(line) - layout.getLineTop(line))
            }
        }
    }
    val pairedParagraphs = paragraphs.indices.filterTo(mutableSetOf()) { paragraphs[it].parts.size > 1 }
    return MeasuredEInkChapter(paragraphs, layouts,
        paginateLines(lines, height, (settings.resolvedParagraphSpacing * density).roundToInt(), pairedParagraphs, checkCancelled),
        allLines = lines.toList())
}

/**
 * 绘制预先测量的完整行，翻页复用布局，手势在释放时最多提交一次翻页。
 * 字体、内容或视口变化才重新测量；电子纸与减少动效模式直接切页，普通模式可使用短过渡。
 * Canvas 只裁切当前页片段，同时提供可访问性文本、搜索高亮与段落长按入口。
 */
@Composable internal fun EInkPage(
    paragraphs: List<ReadingParagraph>, settings: ReaderSettings, state: EInkPageState,
    modifier: Modifier = Modifier, imageModel: (ReadingParagraph) -> Any?,
    onToggleMenu: () -> Unit, onSelect: (ReadingParagraph) -> Unit, onPage: (Int) -> Unit,
    background: Color = Color.White, foreground: Color = Color.Black,
    activeMatch: ReadingTextMatch? = null,
    interactionEnabled: Boolean = true, contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val font = rememberReaderFont(settings)
    var expandedImage by remember { mutableStateOf<Any?>(null) }
    val animate = !settings.eInkMode && !LocalEInkMode.current && !LocalReducedMotion.current && ValueAnimator.areAnimatorsEnabled()
    // 单一源左右翻页动画状态：消除双重动画和时序打架带来的画面闪烁
    val animOffset = remember { Animatable(0f) }
    var dragAxis by remember { mutableIntStateOf(0) } // 0: 横向, 1: 纵向
    val dragScope = rememberCoroutineScope()
    val densityValue = density.density
    val latestPage by rememberUpdatedState(onPage)

    // 纵向行级位移偏移（像素）
    val verticalLineOffset = remember { Animatable(0f) }
    // 纵向当前行起点偏移（以当前基准页第一行为 0 基准，增加为往后移 N 行）
    var lineIndexOffset by remember(state.pageIndex, state.pages) { mutableIntStateOf(0) }

    // 左右翻页执行函数：动画彻底完成后才调用 latestPage(dir) 并归零，绝不闪现旧页
    val executePageTurn: (Int, Float, Int) -> Unit = { dir, startOffset, durationMs ->
        if (dir != 0 && !animOffset.isRunning) {
            dragScope.launch {
                animOffset.snapTo(startOffset)
                val targetNorm = if (dir > 0) -1f else 1f
                animOffset.animateTo(
                    targetValue = targetNorm,
                    animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
                )
                latestPage(dir)
                animOffset.snapTo(0f)
                lineIndexOffset = 0
            }
        }
    }

    val tapNavigation = rememberReaderTapNavigation(
        settings.tapPageTurn,
        interactionEnabled && expandedImage == null,
        onToggleMenu,
        { dir ->
            if (animate) {
                // 点击左右翻页：走平滑横向滑入，与滑动完全一致；且支持跨章！
                dragAxis = 0
                executePageTurn(dir, 0f, AppMotion.Standard)
            } else {
                latestPage(dir)
            }
        },
        settings.resolvedTapZoneMap
    )

    BoxWithConstraints(
        modifier.background(background).clipToBounds()
            .readerTapFeedback(tapNavigation, foreground, settings.eInkMode || LocalEInkMode.current)
            .readerTapNavigation(tapNavigation)
            .pointerInput(settings.scrollPageTurn, settings.horizontalPageTurn, animate) {
                var totalX = 0f
                var totalY = 0f
                var axisLocked = false
                var lastTime = 0L
                var flingVelocity = 0f

                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        axisLocked = false
                        lastTime = 0L
                        flingVelocity = 0f
                        if (animOffset.isRunning) {
                            dragScope.launch { animOffset.stop() }
                        }
                    },
                    onDragCancel = {
                        totalX = 0f
                        totalY = 0f
                        dragScope.launch {
                            if (dragAxis == 0) {
                                animOffset.animateTo(0f, tween(AppMotion.Quick, easing = FastOutSlowInEasing))
                            } else {
                                verticalLineOffset.animateTo(0f, tween(AppMotion.Quick, easing = FastOutSlowInEasing))
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragAxis == 0) {
                            // ====== 横向翻页（支持滑动跨章！） ======
                            val enabled = settings.horizontalPageTurn
                            val extent = size.width.toFloat()
                            val minSwipeDistance = 40f * densityValue
                            val isFling = Math.abs(flingVelocity) >= 0.25f
                            val isPassed = Math.abs(totalX) >= minSwipeDistance || isFling

                            val direction = if (isFling && flingVelocity != 0f) {
                                if (flingVelocity < 0) 1 else -1
                            } else {
                                if (totalX < 0) 1 else -1
                            }

                            // 不再用 canGoForward / canGoBack 拦截！在第一页向右滑或者最后一页向左滑直接触发跨章！
                            if (enabled && isPassed && direction != 0) {
                                val currentNorm = if (extent > 0) totalX / extent else 0f
                                val remainingDistance = 1f - Math.abs(currentNorm)
                                val duration = (AppMotion.Standard * remainingDistance).toInt().coerceIn(120, AppMotion.Standard)
                                executePageTurn(direction, currentNorm, duration)
                            } else {
                                dragScope.launch {
                                    animOffset.animateTo(0f, tween(AppMotion.Quick, easing = FastOutSlowInEasing))
                                }
                            }
                        } else {
                            // ====== 纵向：跟手滑动！拖动时下/上一页实时紧贴浮现，松手平滑滚动完成 ======
                            val enabled = settings.scrollPageTurn
                            val extent = size.height.toFloat()
                            val minSwipeDistance = 40f * densityValue
                            val isFling = Math.abs(flingVelocity) >= 0.25f
                            val isPassed = Math.abs(totalY) >= minSwipeDistance || isFling

                            val direction = if (isFling && flingVelocity != 0f) {
                                if (flingVelocity < 0) 1 else -1
                            } else {
                                if (totalY < 0) 1 else -1
                            }

                            if (enabled && isPassed && direction != 0) {
                                val targetGoal = if (direction > 0) -extent else extent
                                dragScope.launch {
                                    verticalLineOffset.animateTo(
                                        targetGoal,
                                        tween(AppMotion.Standard, easing = FastOutSlowInEasing)
                                    )
                                    latestPage(direction)
                                    verticalLineOffset.snapTo(0f)
                                }
                            } else {
                                dragScope.launch {
                                    verticalLineOffset.animateTo(0f, tween(AppMotion.Quick, easing = FastOutSlowInEasing))
                                }
                            }
                        }
                        totalX = 0f
                        totalY = 0f
                    }
                ) { change, amount ->
                    change.consume()
                    if (!axisLocked) {
                        dragAxis = if (Math.abs(amount.x) >= Math.abs(amount.y)) 0 else 1
                        axisLocked = true
                    }

                    val now = change.uptimeMillis
                    val delta = if (dragAxis == 0) amount.x else amount.y

                    if (lastTime != 0L && now > lastTime) {
                        flingVelocity = flingVelocity * 0.5f + (delta / (now - lastTime).toFloat()) * 0.5f
                    }
                    lastTime = now

                    totalX += amount.x
                    totalY += amount.y

                    if (animate) {
                        if (dragAxis == 0) {
                            val extent = size.width.toFloat()
                            if (extent > 0f) {
                                val normOffset = totalX / extent
                                dragScope.launch { animOffset.snapTo(normOffset) }
                            }
                        } else {
                            // 纵向拖动中跟随手指平移动效（amount.y 负为向上）
                            dragScope.launch {
                                verticalLineOffset.snapTo(verticalLineOffset.value + amount.y)
                            }
                        }
                    }
                }
            }.padding(contentPadding).clipToBounds()
    ) {
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val typography = listOf(settings.fontSize, settings.lineHeight, settings.paragraphSpacing, settings.weight, settings.indent, settings.parallel, settings.underline, settings.secondaryAlpha, font.typeface, font.loading)
        val input = EInkMeasureInput(paragraphs, typography, width, height, density.density, density.fontScale)
        val measured by produceState<EInkMeasurement?>(null, input) {
            if(value != null) delay(120)
            if (!font.loading && width != Constraints.Infinity && height != Constraints.Infinity) {
                val next = withContext(Dispatchers.Default) {
                    val jobContext = currentCoroutineContext()
                    measureEInkChapter(paragraphs, settings, width, height, density.density, density.fontScale, font.typeface) { jobContext.ensureActive() }
                }
                state.install(next.pages, next.paragraphs)
                value = EInkMeasurement(input, next)
            }
        }
        val chapter = measured?.takeIf { it.input == input && state.pages === it.chapter.pages }?.chapter
        SideEffect { if(chapter == null) state.invalidate() }
        if (chapter == null) Text("正在分页…", Modifier.align(Alignment.Center), color = foreground)
        else {
            val page = state.current
            val currentOffsetNorm = animOffset.value
            val isHorizontal = dragAxis == 0

            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                val viewportW = constraints.maxWidth.toFloat()
                val viewportH = constraints.maxHeight.toFloat()

                if (isHorizontal) {
                    // ====== 横向翻页双页咬合（整页平滑滑入） ======
                    val targetDir = when {
                        currentOffsetNorm < 0f -> 1
                        currentOffsetNorm > 0f -> -1
                        else -> 0
                    }

                    val neighborPage = if (targetDir != 0) {
                        chapter.pages.getOrNull(state.pageIndex + targetDir)
                    } else null

                    if (neighborPage != null && targetDir != 0 && Math.abs(currentOffsetNorm) > 0.001f) {
                        val neighborNorm = if (targetDir > 0) 1f + currentOffsetNorm else -1f + currentOffsetNorm
                        val neighborShift = neighborNorm * viewportW

                        PageBody(
                            neighborPage, chapter, settings, foreground, activeMatch, density, context,
                            tapNavigation, onSelect, imageModel, { expandedImage = it },
                            Modifier.fillMaxSize().background(background).graphicsLayer {
                                translationX = neighborShift
                                translationY = 0f
                            }
                        )
                    }

                    val currentShiftX = currentOffsetNorm * viewportW
                    PageBody(
                        page, chapter, settings, foreground, activeMatch, density, context,
                        tapNavigation, onSelect, imageModel, { expandedImage = it },
                        Modifier.fillMaxSize().background(background).graphicsLayer {
                            translationX = currentShiftX
                            translationY = 0f
                        }
                    )
                } else {
                    // ====== 纵向连续卷带：手指推拉，上下页面的真实文字实时无缝浮现，原样排版不缩放不变形！ ======
                    val vOffset = verticalLineOffset.value

                    // 如果手指向上推 (vOffset < 0)，下一页紧紧咬合在屏幕底部 (viewportH + vOffset)，实时向上显露！
                    if (vOffset < -0.5f) {
                        val nextPage = chapter.pages.getOrNull(state.pageIndex + 1)
                        if (nextPage != null) {
                            PageBody(
                                nextPage, chapter, settings, foreground, activeMatch, density, context,
                                tapNavigation, onSelect, imageModel, { expandedImage = it },
                                Modifier.fillMaxSize().background(background).graphicsLayer {
                                    translationX = 0f
                                    translationY = viewportH + vOffset
                                }
                            )
                        }
                    }

                    // 如果手指向下拉 (vOffset > 0)，上一页紧紧咬合在屏幕顶部 (-viewportH + vOffset)，实时拉下来！
                    if (vOffset > 0.5f) {
                        val prevPage = chapter.pages.getOrNull(state.pageIndex - 1)
                        if (prevPage != null) {
                            PageBody(
                                prevPage, chapter, settings, foreground, activeMatch, density, context,
                                tapNavigation, onSelect, imageModel, { expandedImage = it },
                                Modifier.fillMaxSize().background(background).graphicsLayer {
                                    translationX = 0f
                                    translationY = -viewportH + vOffset
                                }
                            )
                        }
                    }

                    // 当前页跟随手指物理位移，100% 原始尺寸与字体，绝不变形
                    PageBody(
                        page, chapter, settings, foreground, activeMatch, density, context,
                        tapNavigation, onSelect, imageModel, { expandedImage = it },
                        Modifier.fillMaxSize().background(background).graphicsLayer {
                            translationX = 0f
                            translationY = vOffset
                        }
                    )
                }
            }
        }
    }
    expandedImage?.let { model -> IllustrationViewer(model) { expandedImage = null } }
}

/**
 * 单页正文渲染，供跟手翻页同时绘制当前页与相邻页。
 *
 * 从 [EInkPage] 抽出：拖动时两页共存，必须把渲染逻辑做成可复用组件，
 * 各自持有独立的图片重试等局部状态。
 */
@Composable
private fun PageBody(
    page: StaticPage?,
    chapter: MeasuredEInkChapter,
    settings: ReaderSettings,
    foreground: Color,
    activeMatch: ReadingTextMatch?,
    density: Density,
    context: android.content.Context,
    tapNavigation: ReaderTapNavigation,
    onSelect: (ReadingParagraph) -> Unit,
    imageModel: (ReadingParagraph) -> Any?,
    onExpandImage: (Any?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp }
    Column(modifier.combinedClickable(onClickLabel = "显示或收起阅读工具栏", onClick = tapNavigation::click)) {
        page?.lines?.groupBy { it.paragraph }?.entries?.forEachIndexed { groupIndex, (index, lines) ->
            if (groupIndex > 0) Spacer(Modifier.height(settings.resolvedParagraphSpacing.dp))
            val paragraph = chapter.paragraphs[index]
            if (lines.first().image) {
                val model = imageModel(paragraph)
                var retry by remember(model) { mutableIntStateOf(0) }
                val request = remember(context, model, retry) {
                    ImageRequest.Builder(context).data(model).setParameter("readerRetry", retry).crossfade(false).build()
                }
                var failed by remember(model) { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().combinedClickable(onClick = tapNavigation::click,
                    onLongClickLabel = "放大查看插图", onLongClick = { onExpandImage(model) }), contentAlignment = Alignment.Center) {
                    AsyncImage(request, "小说插图", Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        onLoading = { failed = false }, onSuccess = { failed = false }, onError = { failed = true })
                    if(failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("插图暂时无法加载", color = foreground)
                        AppTextButton(onClick = { retry++ }) { Text("重新加载插图", color = foreground) }
                    }
                }
            } else {
                val layout = chapter.layouts.getValue(index)
                val top = layout.getLineTop(layout.getLineForOffset(lines.first().start))
                val sliceHeight = lines.sumOf { it.height }
                // 超大系统字号可能使一行文字高于整个横屏视口；
                // 此时缩放适配该行，避免裁切字形。
                val drawScale = (maxHeight.value * density.density / sliceHeight).coerceAtMost(1f)
                val visibleText = layout.text.subSequence(lines.first().start, lines.last().end).toString()
                Canvas(Modifier.fillMaxWidth().height(with(density) { (sliceHeight * drawScale).toDp() }).clipToBounds()
                    .semantics { text = AnnotatedString(visibleText) }
                    .combinedClickable(onClickLabel = "显示或收起阅读工具栏", onClick = tapNavigation::click,
                        onLongClickLabel = "选择段落、分享或添加笔记", onLongClick = { onSelect(paragraph) })) {
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        native.scale(drawScale, drawScale)
                        native.translate(0f, -top.toFloat())
                        activeMatch?.takeIf { it.paragraph == index && it.end > it.start }?.let { match ->
                            val start = match.textOffset(paragraph, settings).coerceIn(0, layout.text.length)
                            val end = (start + match.end - match.start).coerceIn(start, layout.text.length)
                            val path = android.graphics.Path()
                            layout.getSelectionPath(start, end, path)
                            native.drawPath(path, android.graphics.Paint().apply { color = foreground.copy(alpha = .2f).toArgb() })
                        }
                        layout.paint.color = foreground.toArgb()
                        layout.draw(native)
                        native.restore()
                    }
                }
            }
        }
        if (page?.lines.isNullOrEmpty()) Text("本章暂无正文", color = foreground)
    }
}
