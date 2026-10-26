/*
 * 改编自 FBReaderJ (https://github.com/geometer/FBReaderJ) v2.5.10
 * 原始文件：org/geometerplus/fbreader/fbreader/TapZoneMap.java
 * 原作者：FBReader.ORG Limited <contact@fbreader.org>
 * 原始许可：GPL-2.0-or-later（本项目按 GPL-3.0-only 使用）
 *
 * 修改内容：
 *  - 改写为 Kotlin，去掉 ZLStringOption / ZLFile / SAX 解析依赖
 *  - 只保留一套默认分区，不提供预设列表；用户逐格自由配置
 *  - 动作集合扩展为翻页、目录、工具栏、无操作，覆盖横向与纵向翻页
 *  - 坐标归一化保留 FBReader 原始算法，见 getActionByCoordinates
 */
package cc.novelia.app.reader

import kotlinx.serialization.Serializable

/** 单个格子或单次滑动的动作。密封枚举保证穷举，设置界面可列出全部可选值。 */
@Serializable
enum class ReaderTapAction {
    /** 上一页 */
    PreviousPage,

    /** 下一页 */
    NextPage,

    /** 打开目录 */
    Navigate,

    /** 显示或收起工具栏 */
    Menu,

    /** 不响应，事件继续向下传递 */
    None;

    val isPageTurn: Boolean get() = this == PreviousPage || this == NextPage

    companion object {
        /** 设置界面里给用户选的顺序。 */
        val editable: List<ReaderTapAction> = listOf(PreviousPage, NextPage, Navigate, Menu, None)

        fun fromName(name: String): ReaderTapAction =
            entries.firstOrNull { it.name == name } ?: None
    }
}

/**
 * 一次点击的判定类型，对应 FBReader 的 TapZoneMap.Tap。
 *  - SingleTap：不区分单双击时使用的合并查询（先查单击表，再查双击表）
 *  - SingleNotDoubleTap：确认不是双击的单击
 *  - DoubleTap：双击
 */
enum class ReaderTapKind { SingleTap, SingleNotDoubleTap, DoubleTap }

/**
 * 一套点击分区方案。
 *
 * 网格为 [columns] × [rows] 个格子（默认 3 × 3，共 9 格），每格可独立配置单击与双击动作。
 * [singleActions] / [doubleActions] 以 `row * columns + column` 为键；
 * 缺省的格子等同于 [ReaderTapAction.None]。
 *
 * 这是不可变快照：用户修改时用 [withAction] 产生新实例，
 * 便于 Compose 依赖比较与设置界面回退。
 */
@Serializable
data class ReaderTapZoneMap(
    val name: String = DEFAULT_NAME,
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    /** 每格的单击动作，键为 `row * columns + column`。 */
    val singleActions: Map<Int, ReaderTapAction> = emptyMap(),
    /** 每格的双击动作，键同上。 */
    val doubleActions: Map<Int, ReaderTapAction> = emptyMap(),
) {
    /** 格子总数。 */
    val cellCount: Int get() = columns * rows

    /**
     * 把屏幕坐标换算成动作。
     *
     * 归一化算法与 FBReader 一致：先把坐标夹到 [0, width) / [0, height)，
     * 再按 `columns * x / width` 映射到格子索引，因此最右/最下边界不会越界。
     *
     * @return 该位置没有配置动作时返回 [ReaderTapAction.None]
     */
    fun getActionByCoordinates(x: Int, y: Int, width: Int, height: Int, kind: ReaderTapKind): ReaderTapAction {
        if (width <= 0 || height <= 0) return ReaderTapAction.None
        val clampedX = x.coerceIn(0, width - 1)
        val clampedY = y.coerceIn(0, height - 1)
        val column = columns * clampedX / width
        val row = rows * clampedY / height
        return getActionByZone(column, row, kind)
    }

    /** 按格子索引查动作，越界或未配置时返回 [ReaderTapAction.None]。 */
    fun getActionByZone(column: Int, row: Int, kind: ReaderTapKind): ReaderTapAction {
        if (column !in 0 until columns || row !in 0 until rows) return ReaderTapAction.None
        val key = row * columns + column
        val single = singleActions[key] ?: ReaderTapAction.None
        val double = doubleActions[key] ?: ReaderTapAction.None
        return when (kind) {
            ReaderTapKind.SingleTap -> if (single != ReaderTapAction.None) single else double
            ReaderTapKind.SingleNotDoubleTap -> single
            ReaderTapKind.DoubleTap -> double
        }
    }

    /** 读取某格的单击动作，供设置界面显示。 */
    fun singleAt(column: Int, row: Int): ReaderTapAction =
        singleActions[row * columns + column] ?: ReaderTapAction.None

    /** 读取某格的双击动作，供设置界面显示。 */
    fun doubleAt(column: Int, row: Int): ReaderTapAction =
        doubleActions[row * columns + column] ?: ReaderTapAction.None

    /**
     * 产生修改了某格动作的新方案。
     *
     * 显式写入 [ReaderTapAction.None] 而不是删键：保留用户「这一格确实不响应」的意图，
     * 也让编码往返后方案完全相等。
     */
    fun withAction(column: Int, row: Int, double: Boolean, action: ReaderTapAction): ReaderTapZoneMap {
        if (column !in 0 until columns || row !in 0 until rows) return this
        val key = row * columns + column
        val nextSingle = if (double) singleActions else singleActions + (key to action)
        val nextDouble = if (double) doubleActions + (key to action) else doubleActions
        return copy(singleActions = nextSingle, doubleActions = nextDouble)
    }

    /** 设置界面用的一句话说明，描述整张分区表的翻页方向。 */
    fun describe(): String {
        val cells = (0 until rows).flatMap { row -> (0 until columns).map { column -> singleAt(column, row) } }
        val forward = cells.count { it == ReaderTapAction.NextPage }
        val backward = cells.count { it == ReaderTapAction.PreviousPage }
        val menu = cells.count { it == ReaderTapAction.None }
        val direction = when {
            forward > 0 && backward > 0 -> "同时包含向前与向后翻页"
            forward > 0 -> "只向后翻页，回退请用滑动"
            backward > 0 -> "只向前翻页"
            else -> "没有配置翻页区域"
        }
        return "$direction；$menu 个格子留给工具栏。点按任意格子可单独调整。"
    }

    companion object {
        const val DEFAULT_COLUMNS = 3
        const val DEFAULT_ROWS = 3
        const val DEFAULT_NAME = "default"

        /** FBReader 允许的最小/最大网格密度。 */
        const val MIN_GRID = 2
        const val MAX_GRID = 5

        private fun map(
            single: Map<Pair<Int, Int>, ReaderTapAction> = emptyMap(),
            double: Map<Pair<Int, Int>, ReaderTapAction> = emptyMap(),
            columns: Int = DEFAULT_COLUMNS,
            rows: Int = DEFAULT_ROWS,
        ): ReaderTapZoneMap {
            fun index(entries: Map<Pair<Int, Int>, ReaderTapAction>) =
                entries.entries.associate { (pos, action) -> (pos.second * columns + pos.first) to action }
            return ReaderTapZoneMap(DEFAULT_NAME, columns, rows, index(single), index(double))
        }

        /**
         * 默认方案：四向翻页，横向与纵向同时生效，这是 FBReader 分区方案的核心优势。
         *
         * 左列与上中格翻上一页，右列与下中格翻下一页，正中显示工具栏。
         * 四个角同时属于一条列和一条行，这里归给横向（与 FBReader 一致：
         * 角格只承载一个动作，避免同一格出现两个冲突的翻页方向）。
         *
         * 双击在顶部三格跳目录、底部三格收工具栏，给常用操作留了快捷键。
         */
        val Default: ReaderTapZoneMap = map(
            single = mapOf(
                // 左列：上一页
                (0 to 0) to ReaderTapAction.PreviousPage, (0 to 1) to ReaderTapAction.PreviousPage, (0 to 2) to ReaderTapAction.PreviousPage,
                // 右列：下一页
                (2 to 0) to ReaderTapAction.NextPage, (2 to 1) to ReaderTapAction.NextPage, (2 to 2) to ReaderTapAction.NextPage,
                // 中列上下：纵向翻页，正中：工具栏
                (1 to 0) to ReaderTapAction.PreviousPage, (1 to 1) to ReaderTapAction.None, (1 to 2) to ReaderTapAction.NextPage,
            ),
            double = mapOf(
                (0 to 0) to ReaderTapAction.Navigate, (1 to 0) to ReaderTapAction.Navigate, (2 to 0) to ReaderTapAction.Navigate,
                (0 to 2) to ReaderTapAction.Menu, (1 to 2) to ReaderTapAction.Menu, (2 to 2) to ReaderTapAction.Menu,
            ),
        )

        /**
         * 把方案编码成一行字符串，用于随阅读设置持久化。
         *
         * 格式：`名称|列数|行数|单击表|双击表`，表内以 `格子索引:动作名` 逗号分隔。
         * 解析失败时回退到 [Default]，保证旧版本数据或损坏值不会让阅读器不可用。
         */
        fun encode(map: ReaderTapZoneMap): String {
            fun table(values: Map<Int, ReaderTapAction>) =
                values.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.name}" }
            return listOf(map.name, map.columns.toString(), map.rows.toString(),
                table(map.singleActions), table(map.doubleActions)).joinToString("|")
        }

        /** 解析 [encode] 生成的字符串；任何异常都回退到 [Default]。 */
        fun decode(value: String): ReaderTapZoneMap {
            if (value.isBlank()) return Default
            return try {
                val parts = value.split('|')
                if (parts.size != 5) return Default
                val columns = parts[1].toIntOrNull()?.takeIf { it in MIN_GRID..MAX_GRID } ?: return Default
                val rows = parts[2].toIntOrNull()?.takeIf { it in MIN_GRID..MAX_GRID } ?: return Default
                fun table(raw: String): Map<Int, ReaderTapAction>? {
                    if (raw.isBlank()) return emptyMap()
                    val result = HashMap<Int, ReaderTapAction>()
                    for (entry in raw.split(',')) {
                        val pair = entry.split(':')
                        if (pair.size != 2) return null
                        val index = pair[0].toIntOrNull()?.takeIf { it in 0 until columns * rows } ?: return null
                        val action = ReaderTapAction.entries.firstOrNull { it.name == pair[1] } ?: return null
                        result[index] = action
                    }
                    return result
                }
                val single = table(parts[3]) ?: return Default
                val double = table(parts[4]) ?: return Default
                ReaderTapZoneMap(parts[0].ifBlank { DEFAULT_NAME }, columns, rows, single, double)
            } catch (_: RuntimeException) {
                Default
            }
        }

        /** 只有一套默认方案；未知名称回退到 [Default]。 */
        fun byName(name: String): ReaderTapZoneMap = Default
    }
}
