package cc.novelia.app.ui.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cc.novelia.app.ui.theme.AppMotion
import cc.novelia.app.ui.theme.appReducedMotion

/** 评论与原文共用从顶部伸缩的过渡；退出完成后才移除内容，快速反向点击可接续当前动画。 */
@Composable internal fun CommentDisclosure(expanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if(appReducedMotion()) {
        // 切换到电子纸或减少动态效果时，正在进行的动画也立即落到终态。
        if(expanded) Column(Modifier.fillMaxWidth(), content = content)
    } else AnimatedVisibility(expanded,
        enter = expandVertically(tween(AppMotion.Panel, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
            fadeIn(tween(AppMotion.Quick)),
        exit = shrinkVertically(tween(AppMotion.Panel, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
            fadeOut(tween(AppMotion.Quick))) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}
