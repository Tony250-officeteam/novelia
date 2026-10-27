package cc.novelia.app.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import cc.novelia.app.reader.ReaderTapAction
import cc.novelia.app.reader.ReaderTapRect

/** 只画合并区域的外边，不在相邻格之间重复描边。 */
internal fun DrawScope.drawTapRegion(bounds: List<ReaderTapRect>, color: Color, fillAlpha: Float, borderAlpha: Float, stroke: Float) {
    bounds.forEach { rect ->
        val x = rect.left * size.width
        val y = rect.top * size.height
        val right = rect.right * size.width
        val bottom = rect.bottom * size.height
        drawRect(color.copy(alpha = fillAlpha), Offset(x, y), Size(right - x, bottom - y))
        val edge = color.copy(alpha = borderAlpha)
        val inset = stroke / 2
        if(bounds.none { it.right == rect.left && it.top == rect.top && it.bottom == rect.bottom })
            drawLine(edge, Offset(x + inset, y), Offset(x + inset, bottom), stroke)
        if(bounds.none { it.left == rect.right && it.top == rect.top && it.bottom == rect.bottom })
            drawLine(edge, Offset(right - inset, y), Offset(right - inset, bottom), stroke)
        if(bounds.none { it.bottom == rect.top && it.left == rect.left && it.right == rect.right })
            drawLine(edge, Offset(x, y + inset), Offset(right, y + inset), stroke)
        if(bounds.none { it.top == rect.bottom && it.left == rect.left && it.right == rect.right })
            drawLine(edge, Offset(x, bottom - inset), Offset(right, bottom - inset), stroke)
    }
}

internal fun ReaderTapAction.label(staticPagination: Boolean = true): String = when(this) {
    ReaderTapAction.PreviousPage -> if(staticPagination) "上一页" else "上一屏"
    ReaderTapAction.NextPage -> if(staticPagination) "下一页" else "下一屏"
    ReaderTapAction.Navigate -> "目录"
    ReaderTapAction.Menu -> "工具栏"
    ReaderTapAction.None -> "无响应"
}

internal fun ReaderTapAction.zoneColor(): Color = when(this) {
    ReaderTapAction.PreviousPage -> Color(0xFF315EA8)
    ReaderTapAction.NextPage -> Color(0xFF14775E)
    ReaderTapAction.Navigate -> Color(0xFF8453AD)
    ReaderTapAction.Menu -> Color(0xFFA45B16)
    ReaderTapAction.None -> Color(0xFF626975)
}
