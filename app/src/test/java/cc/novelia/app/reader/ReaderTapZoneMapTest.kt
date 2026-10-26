package cc.novelia.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点击分区映射的行为基线。
 *
 * 内置方案来自 FBReaderJ 的 assets/default/tapzones 目录，
 * 这些断言同时用于防止移植过程中坐标或动作被改错。
 */
class ReaderTapZoneMapTest {
    private val width = 900
    private val height = 1800

    private fun centerOf(map: ReaderTapZoneMap, column: Int, row: Int): Pair<Int, Int> {
        val cellWidth = width / map.columns
        val cellHeight = height / map.rows
        return (column * cellWidth + cellWidth / 2) to (row * cellHeight + cellHeight / 2)
    }

    private fun actionAt(map: ReaderTapZoneMap, column: Int, row: Int, kind: ReaderTapKind = ReaderTapKind.SingleTap) =
        map.getActionByCoordinates(centerOf(map, column, row).first, centerOf(map, column, row).second, width, height, kind)

    // ---- 四向翻页：FBReader 最核心的能力，横竖同时生效 ----

    @Test fun fourWayTurnsHorizontallyOnSideColumns() {
        val map = ReaderTapZoneMap.FourWay
        assertEquals(ReaderTapAction.PreviousPage, actionAt(map, 0, 1))
        assertEquals(ReaderTapAction.NextPage, actionAt(map, 2, 1))
    }

    @Test fun fourWayTurnsVerticallyOnMiddleColumn() {
        val map = ReaderTapZoneMap.FourWay
        assertEquals(ReaderTapAction.PreviousPage, actionAt(map, 1, 0))
        assertEquals(ReaderTapAction.NextPage, actionAt(map, 1, 2))
        // 正中留给工具栏
        assertEquals(ReaderTapAction.None, actionAt(map, 1, 1))
    }

    @Test fun fourWayCoversBothDirectionsInBothAxes() {
        val map = ReaderTapZoneMap.FourWay
        val actions = (0 until 3).flatMap { row -> (0 until 3).map { column -> actionAt(map, column, row) } }
        assertTrue("四向翻页必须同时包含上一页", actions.contains(ReaderTapAction.PreviousPage))
        assertTrue("四向翻页必须同时包含下一页", actions.contains(ReaderTapAction.NextPage))
        // 横向与纵向各自都能前后翻
        assertEquals(ReaderTapAction.PreviousPage, actionAt(map, 0, 0))
        assertEquals(ReaderTapAction.NextPage, actionAt(map, 2, 2))
    }

    // ---- 单向预设 ----

    @Test fun rightToLeftTurnsBackwardOnLeftAndForwardOnRight() {
        val map = ReaderTapZoneMap.RightToLeft
        assertEquals(ReaderTapAction.PreviousPage, actionAt(map, 0, 1))
        assertEquals(ReaderTapAction.NextPage, actionAt(map, 2, 1))
        assertEquals(ReaderTapAction.None, actionAt(map, 1, 1))
    }

    @Test fun leftToRightIsTheMirrorOfRightToLeft() {
        val map = ReaderTapZoneMap.LeftToRight
        assertEquals(ReaderTapAction.NextPage, actionAt(map, 0, 1))
        assertEquals(ReaderTapAction.PreviousPage, actionAt(map, 2, 1))
    }

    @Test fun bothSidesForwardTurnsForwardOnEitherSide() {
        val map = ReaderTapZoneMap.BothSidesForward
        assertEquals(ReaderTapAction.NextPage, actionAt(map, 0, 1))
        assertEquals(ReaderTapAction.NextPage, actionAt(map, 2, 1))
    }

    // ---- 逐格编辑 ----

    @Test fun withActionChangesOnlyTheTargetCell() {
        val map = ReaderTapZoneMap.RightToLeft
        val edited = map.withAction(1, 1, double = false, ReaderTapAction.NextPage)
        assertEquals(ReaderTapAction.NextPage, edited.singleAt(1, 1))
        // 其他格子不受影响
        assertEquals(ReaderTapAction.PreviousPage, edited.singleAt(0, 1))
        assertEquals(ReaderTapAction.NextPage, edited.singleAt(2, 1))
    }

    @Test fun editingABuiltInMapProducesACustomCopy() {
        val edited = ReaderTapZoneMap.RightToLeft.withAction(0, 0, double = false, ReaderTapAction.Menu)
        assertTrue("改过的内置方案应转为自定义", edited.isCustom)
        // 原内置方案保持不变，随时可以切回
        assertEquals(ReaderTapAction.PreviousPage, ReaderTapZoneMap.RightToLeft.singleAt(0, 0))
    }

    @Test fun writingNoneMarksTheCellAsExplicitlyDisabled() {
        val edited = ReaderTapZoneMap.RightToLeft.withAction(0, 1, double = false, ReaderTapAction.None)
        // 显式 None 保留在映射里，表示「这一格确实不响应」，与「未配置」区分开。
        assertEquals(ReaderTapAction.None, edited.singleAt(0, 1))
        assertEquals(ReaderTapAction.None, edited.singleActions[1 * 3 + 0])
    }

    @Test fun doubleTapActionsAreSeparateFromSingleTap() {
        val map = ReaderTapZoneMap.RightToLeft
        assertEquals(ReaderTapAction.PreviousPage, actionAt(map, 0, 0, ReaderTapKind.SingleNotDoubleTap))
        assertEquals(ReaderTapAction.Navigate, actionAt(map, 0, 0, ReaderTapKind.DoubleTap))
    }

    @Test fun singleTapFallsBackToDoubleTapWhenOnlyDoubleIsConfigured() {
        val map = ReaderTapZoneMap.RightToLeft
        assertEquals(ReaderTapAction.None, actionAt(map, 1, 0, ReaderTapKind.SingleNotDoubleTap))
        assertEquals(ReaderTapAction.Navigate, actionAt(map, 1, 0, ReaderTapKind.DoubleTap))
        assertEquals(ReaderTapAction.Navigate, actionAt(map, 1, 0, ReaderTapKind.SingleTap))
    }

    // ---- 坐标边界 ----

    @Test fun coordinatesAreClampedToTheLastCell() {
        val map = ReaderTapZoneMap.RightToLeft
        assertEquals(ReaderTapAction.NextPage, map.getActionByCoordinates(width - 1, height - 1, width, height, ReaderTapKind.SingleTap))
        assertEquals(ReaderTapAction.PreviousPage, map.getActionByCoordinates(0, 0, width, height, ReaderTapKind.SingleTap))
    }

    @Test fun negativeOrOutOfRangeCoordinatesAreClampedNotDropped() {
        val map = ReaderTapZoneMap.RightToLeft
        assertEquals(ReaderTapAction.PreviousPage, map.getActionByCoordinates(-50, -50, width, height, ReaderTapKind.SingleTap))
        assertEquals(ReaderTapAction.NextPage, map.getActionByCoordinates(width + 500, height + 500, width, height, ReaderTapKind.SingleTap))
    }

    @Test fun zeroSizedViewportYieldsNoAction() {
        val map = ReaderTapZoneMap.RightToLeft
        assertEquals(ReaderTapAction.None, map.getActionByCoordinates(0, 0, 0, 0, ReaderTapKind.SingleTap))
        assertEquals(ReaderTapAction.None, map.getActionByCoordinates(0, 0, 0, height, ReaderTapKind.SingleTap))
        assertEquals(ReaderTapAction.None, map.getActionByCoordinates(0, 0, width, 0, ReaderTapKind.SingleTap))
    }

    @Test fun everyBuiltInMapIsThreeByThree() {
        assertTrue(ReaderTapZoneMap.BUILT_IN.size >= 6)
        ReaderTapZoneMap.BUILT_IN.forEach { map ->
            assertEquals("${map.name} 应为 3 列", 3, map.columns)
            assertEquals("${map.name} 应为 3 行", 3, map.rows)
            assertTrue("${map.name} 应为内置方案", map.isCustom.not())
        }
    }

    // ---- 编码往返（持久化与 WebDAV 同步依赖它）----

    @Test fun encodeDecodeRoundTripsBuiltInMaps() {
        ReaderTapZoneMap.BUILT_IN.forEach { map ->
            assertEquals("${map.name} 编码后应能还原", map, ReaderTapZoneMap.decode(ReaderTapZoneMap.encode(map)))
        }
    }

    @Test fun encodeDecodeRoundTripsCustomMaps() {
        val custom = ReaderTapZoneMap.FourWay
            .withAction(0, 0, double = false, ReaderTapAction.Menu)
            .withAction(2, 2, double = true, ReaderTapAction.Navigate)
        assertEquals(custom, ReaderTapZoneMap.decode(ReaderTapZoneMap.encode(custom)))
    }

    @Test fun malformedEncodedValueFallsBackToDefault() {
        assertEquals(ReaderTapZoneMap.RightToLeft, ReaderTapZoneMap.decode(""))
        assertEquals(ReaderTapZoneMap.RightToLeft, ReaderTapZoneMap.decode("垃圾数据"))
        assertEquals(ReaderTapZoneMap.RightToLeft, ReaderTapZoneMap.decode("a|b|c|d|e"))
    }

    @Test fun unknownNameFallsBackToRightToLeft() {
        assertEquals(ReaderTapZoneMap.RightToLeft, ReaderTapZoneMap.byName("不存在的方案"))
        assertEquals(ReaderTapZoneMap.FourWay, ReaderTapZoneMap.byName("four_way"))
    }

    @Test fun pageTurningOptionsResolveUnknownMapNameAndClampSpeed() {
        val options = ReaderPageTurningOptions(animationSpeed = 99)
        assertEquals(ReaderTapZoneMap.RightToLeft, options.resolvedTapZoneMap)
        assertEquals(1f, options.animationFraction, 0f)
        assertEquals(0f, ReaderPageTurningOptions(animationSpeed = 1).animationFraction, 0f)
    }

    @Test fun fingerScrollingCapabilitiesMatchTheirNames() {
        assertTrue(ReaderFingerScrolling.TapAndFlick.supportsTap)
        assertTrue(ReaderFingerScrolling.TapAndFlick.supportsFlick)
        assertTrue(ReaderFingerScrolling.Tap.supportsTap)
        assertTrue(ReaderFingerScrolling.Tap.supportsFlick.not())
        assertTrue(ReaderFingerScrolling.Flick.supportsFlick)
        assertTrue(ReaderFingerScrolling.Flick.supportsTap.not())
    }
}
