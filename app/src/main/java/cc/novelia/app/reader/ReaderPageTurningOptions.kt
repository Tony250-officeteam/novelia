/*
 * 改编自 FBReaderJ (https://github.com/geometer/FBReaderJ) v2.5.10
 * 原始文件：org/geometerplus/fbreader/fbreader/TapZoneMap.java
 * 原始文件：org/geometerplus/fbreader/fbreader/options/PageTurningOptions.java
 * 原作者：FBReader.ORG Limited <contact@fbreader.org>
 * 原始许可：GPL-2.0-or-later（本项目按 GPL-3.0-only 使用）
 *
 * 修改内容：
 *  - 改写为 Kotlin，去掉 ZLEnumOption / ZLStringOption 依赖，改为 @Serializable 数据类
 *  - 翻页手势枚举与 Compose 交互模型对齐
 */
package cc.novelia.app.reader

import kotlinx.serialization.Serializable

/**
 * 手指翻页方式，对应 FBReader 的 FingerScrollingType。
 *  - Tap：只能点击翻页
 *  - Flick：只能滑动翻页
 *  - TapAndFlick：两者都启用
 */
@Serializable
enum class ReaderFingerScrolling {
    Tap,
    Flick,
    TapAndFlick;

    val supportsTap: Boolean get() = this != Flick
    val supportsFlick: Boolean get() = this != Tap

    companion object {
        fun fromName(name: String): ReaderFingerScrolling =
            entries.firstOrNull { it.name == name } ?: TapAndFlick
    }
}

/**
 * 翻页动画类型，对应 FBReader 的 ZLView.Animation。
 * Compose 版本只实现前三种；Curl（卷曲）需要 Canvas 级像素变换，暂未移植。
 */
@Serializable
enum class ReaderPageAnimation {
    None,
    Shift,
    Slide;

    companion object {
        fun fromName(name: String): ReaderPageAnimation =
            entries.firstOrNull { it.name == name } ?: Slide
    }
}

/**
 * 翻页相关偏好，对应 FBReader 的 PageTurningOptions。
 *
 * 这些值随 [cc.novelia.app.data.model.ReaderSettings] 一起持久化；
 * 点击分区方案用 [tapZoneMap] 存名称，未知名称回退到右开本。
 */
@Serializable
data class ReaderPageTurningOptions(
    /** 手指翻页方式 */
    val fingerScrolling: ReaderFingerScrolling = ReaderFingerScrolling.TapAndFlick,
    /** 翻页动画 */
    val animation: ReaderPageAnimation = ReaderPageAnimation.Slide,
    /** 动画时长倍率，1..10，越大越慢。对应 FBReader 的 AnimationSpeed */
    val animationSpeed: Int = 7,
    /** 是否横向翻页（false 为纵向） */
    val horizontal: Boolean = true,
    /** 点击分区方案名称，见 [ReaderTapZoneMap.zoneMapNames] */
    val tapZoneMap: String = ReaderTapZoneMap.Default.name,
) {
    /** 动画速度归一化为 0f..1f，供 Compose 动画时长使用；值越大越慢。 */
    val animationFraction: Float
        get() = ((animationSpeed.coerceIn(MIN_SPEED, MAX_SPEED) - MIN_SPEED).toFloat() /
            (MAX_SPEED - MIN_SPEED).toFloat())

    /** 解析出实际的点击分区方案，保证永远有可用值。 */
    val resolvedTapZoneMap: ReaderTapZoneMap
        get() = ReaderTapZoneMap.byName(tapZoneMap)

    companion object {
        const val MIN_SPEED = 1
        const val MAX_SPEED = 10
    }
}
