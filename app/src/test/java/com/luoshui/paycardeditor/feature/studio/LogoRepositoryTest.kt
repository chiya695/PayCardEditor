package com.luoshui.paycardeditor.feature.studio

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun catalogContainsEveryLogoWithUniqueIdsAndExistingFiles() {
        val logos = LogoRepository(context).list().filter { it.assetPath != null }
        assertEquals(101, logos.size)
        assertEquals(101, logos.map { it.id }.distinct().size)
        assertEquals(70, logos.count { it.category == "金融" })
        assertEquals(5, logos.count { it.category == "街机卡" })
        logos.forEach { logo ->
            context.assets.open(requireNotNull(logo.assetPath)).use { assertTrue(it.read() >= 0) }
        }
    }

    @Test fun everyPackagedLogoDecodesAndHasVisiblePixels() {
        val repository = LogoRepository(context)
        repository.list().filter { it.assetPath != null }.forEach {
            val bitmap = repository.load(it)
            assertTrue(it.name, bitmap.width > 0 && bitmap.height > 0 && bitmap.width <= 1024 && bitmap.height <= 1024)
            bitmap.recycle()
        }
    }

    @Test fun transparentMarginsAreTrimmedWithoutRemovingWhiteArtwork() {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 5..14) for (x in 3..16) bitmap.setPixel(x, y, Color.WHITE)
        val trimmed = LogoRepository.trimTransparent(bitmap)
        assertEquals(14, trimmed.width)
        assertEquals(10, trimmed.height)
        assertEquals(Color.WHITE, trimmed.getPixel(0, 0))
    }

    @Test fun fullyTransparentImageIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            LogoRepository.trimTransparent(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        }
    }

    @Test fun importedLogoPersistsAfterSourceIsDeleted() {
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val source = File(context.cacheDir, "personal-test.png")
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val repo = LogoRepository(context)
        val logo = repo.import(Uri.fromFile(source))
        source.delete()
        assertEquals("我的 Logo", logo.category)
        assertTrue(logo.note.isNotEmpty())
        assertTrue(repo.list().any { it.id == logo.id })
        val decoded = repo.load(logo)
        assertEquals(Color.BLUE, decoded.getPixel(0, 0))
    }

    @Test fun invalidImageIsRejectedWithoutCreatingPersonalFiles() {
        val dir = File(context.filesDir, "personal_logos")
        val before = dir.list().orEmpty().toSet()
        val source = File(context.cacheDir, "invalid.png").apply { writeText("not an image") }
        assertThrows(Exception::class.java) { LogoRepository(context).import(Uri.fromFile(source)) }
        assertEquals(before, dir.list().orEmpty().toSet())
    }
}
