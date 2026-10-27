package cc.novelia.app.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import cc.novelia.app.ui.theme.LocalEInkMode
import cc.novelia.app.ui.theme.appReducedMotion
import kotlinx.coroutines.delay

/** 绘制层反馈不参与测量、语义和命中测试，也不延迟实际翻页。 */
@Composable internal fun Modifier.readerTapFeedback(
    navigation: ReaderTapNavigation, foreground: Color, eInk: Boolean = LocalEInkMode.current
): Modifier {
    val reducedMotion = appReducedMotion() || eInk
    val feedback = navigation.feedback
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(feedback, reducedMotion) {
        if(feedback != null) {
            opacity.snapTo(1f)
            if(reducedMotion) delay(180) else opacity.animateTo(0f, tween(280))
            navigation.clearFeedback(feedback)
        }
        opacity.snapTo(0f)
    }
    return drawWithContent {
        drawContent()
        if(feedback != null) {
            val strength = if(reducedMotion) 1f else opacity.value
            // 轻微提示命中的区域，不绘制轮廓，避免深色页面出现突兀的白框。
            feedback.bounds.forEach { rect ->
                drawRect(foreground.copy(alpha = .06f * strength),
                    Offset(rect.left * size.width, rect.top * size.height),
                    Size((rect.right - rect.left) * size.width, (rect.bottom - rect.top) * size.height))
            }
        }
    }
}
