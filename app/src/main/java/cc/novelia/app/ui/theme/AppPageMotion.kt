package cc.novelia.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** 根导航与页内二级页面共用同一转场；travel 为 24 dp 对应的像素。 */
internal fun appPageEnterTransition(travel: Int, back: Boolean = false, direction: Int = 1): EnterTransition =
    fadeIn(tween(AppMotion.Standard)) + slideInHorizontally(tween(AppMotion.Standard, easing = FastOutSlowInEasing)) {
        if(back) -travel / 2 else direction * travel
    }

internal fun appPageExitTransition(travel: Int, back: Boolean = false, direction: Int = 1): ExitTransition =
    fadeOut(tween(AppMotion.Exit)) + slideOutHorizontally(tween(AppMotion.Standard, easing = FastOutSlowInEasing)) {
        if(back) travel else -direction * travel / 2
    }
