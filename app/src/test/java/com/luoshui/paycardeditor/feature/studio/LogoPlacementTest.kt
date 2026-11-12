package com.luoshui.paycardeditor.feature.studio

import org.junit.Assert.*
import org.junit.Test

class LogoPlacementTest {
    @Test fun landscapeLogoFitsAndDoesNotDistort() {
        val p = LogoPlacement(-3f, 8f, 6f, 2f).bounded(1.6f, 4f)
        assertEquals(0.95f, p.width, 0.0001f)
        assertEquals(p.width / 2, p.x, 0.0001f)
        assertEquals(1 - p.width * 1.6f / 4f / 2, p.y, 0.0001f)
        assertEquals(1f, p.opacity, 0f)
    }
    @Test fun tallLogoFitsHeight() {
        val p = LogoPlacement(width = 1f).bounded(1.6f, 0.1f)
        assertEquals(0.95f, p.width * 1.6f / 0.1f, 0.0001f)
    }
    @Test fun nonFinitePositionsAndOpacityAreRepaired() {
        val p = LogoPlacement(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NaN).bounded(1.6f, 1f)
        assertEquals(0.5f, p.x, 0f)
        assertEquals(0.5f, p.y, 0f)
        assertEquals(1f, p.opacity, 0f)
    }
    @Test fun negativeSizeAndOpacityAreBounded() {
        val p = LogoPlacement(width = -3f, opacity = -1f).bounded(1.6f, 1f)
        assertEquals(0.03f, p.width, 0f)
        assertEquals(0f, p.opacity, 0f)
    }
}
