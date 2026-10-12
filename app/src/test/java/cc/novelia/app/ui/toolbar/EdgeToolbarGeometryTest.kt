package cc.novelia.app.ui.toolbar

import org.junit.Assert.*
import org.junit.Test

class EdgeToolbarGeometryTest {
    @Test fun panelsStayInsideContentAtBothEdgesAndAfterResize() {
        for(height in listOf(180, 420, 900)) {
            val bounds = EdgeToolbarBounds(80, 100, 600, 100 + height)
            for(percent in 0..100) {
                val center = bounds.centerY(percent / 100f, 48)
                assertTrue(center - 24 >= bounds.top)
                assertTrue(center + 24 <= bounds.bottom)
                for(panelHeight in listOf(140, 220, 400).filter { it <= height }) {
                    val top = bounds.panelTop(center, panelHeight)
                    assertTrue(top >= bounds.top)
                    assertTrue(top + panelHeight <= bounds.bottom)
                }
            }
        }
    }

    @Test fun topDotOpensDownAndBottomDotOpensUp() {
        val bounds = EdgeToolbarBounds(0, 80, 360, 600)
        assertEquals(80, bounds.panelTop(bounds.centerY(0f, 48), 220))
        assertEquals(380, bounds.panelTop(bounds.centerY(1f, 48), 220))
    }

    @Test fun dragPositionRoundTripsAndClampsOutsideGestures() {
        val bounds = EdgeToolbarBounds(0, 100, 360, 700)
        assertEquals(.37f, bounds.fractionAt(bounds.centerY(.37f, 48), 48), .0001f)
        assertEquals(0f, bounds.fractionAt(-1000f, 48), 0f)
        assertEquals(1f, bounds.fractionAt(2000f, 48), 0f)
        val landscape = bounds.copy(bottom = 340)
        assertEquals(.37f, landscape.fractionAt(landscape.centerY(.37f, 48), 48), .0001f)
    }

    @Test fun loginEditorsAndConfigurationNeverShowTheDot() {
        listOf(null, "login", "forum-login", "compose?article={article}&draft={draft}", "edit/{provider}/{id}",
            "edge-toolbar", "edge-toolbar-edit", "settings?section={section}").forEach { assertFalse(edgeToolbarRouteAllowed(it)) }
        listOf("history", "downloads", "reader/{provider}/{id}/{chapter}", "discover?query={query}", "shelf").forEach {
            assertTrue(edgeToolbarRouteAllowed(it))
        }
    }
}
