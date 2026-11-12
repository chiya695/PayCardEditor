package com.luoshui.paycardeditor.feature.studio

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoComposerTest {
    private val resource = LogoResource("test", "test", "test")
    private fun bitmap(color: Int, w: Int = 100, h: Int = 100) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun compositingPreservesSourceAndPlacesLogoInCorrectCoordinates() {
        val base = bitmap(Color.BLUE)
        val logo = bitmap(Color.RED, 10, 10)
        val output = LogoComposer.render(base, listOf(LogoLayer(resource, logo, LogoPlacement(width = 0.2f))))
        assertEquals(Color.RED, output.getPixel(50, 50))
        assertEquals(Color.BLUE, output.getPixel(10, 10))
        assertEquals(Color.BLUE, base.getPixel(50, 50))
    }
    @Test fun opacityAndLayerOrderMatchPreview() {
        val base = bitmap(Color.BLACK)
        val layers = listOf(LogoLayer(resource, bitmap(Color.RED), LogoPlacement(width = 0.8f)),
            LogoLayer(resource, bitmap(Color.BLUE), LogoPlacement(width = 0.4f, opacity = 0.5f)))
        val pixel = LogoComposer.render(base, layers).getPixel(50, 50)
        assertTrue(Color.red(pixel) in 126..129)
        assertTrue(Color.blue(pixel) in 126..129)
    }
    @Test fun transparentPixelsDoNotEraseCardAndRoundedAlphaRemainsIntact() {
        val base = bitmap(Color.BLUE).apply { setPixel(0, 0, Color.TRANSPARENT); setPixel(1, 0, 0x800000FF.toInt()) }
        val logo = bitmap(Color.TRANSPARENT)
        val output = LogoComposer.render(base, listOf(LogoLayer(resource, logo, LogoPlacement())))
        assertEquals(base.getPixel(50, 50), output.getPixel(50, 50))
        assertEquals(0, Color.alpha(output.getPixel(0, 0)))
        assertEquals(128, Color.alpha(output.getPixel(1, 0)))
    }
}
