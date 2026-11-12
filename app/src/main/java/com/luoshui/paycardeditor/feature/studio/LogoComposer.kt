package com.luoshui.paycardeditor.feature.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import java.io.File
import kotlin.math.roundToInt

internal data class LogoLayer(val resource: LogoResource, val bitmap: Bitmap, val placement: LogoPlacement)

internal object LogoComposer {
    fun render(base: Bitmap, layers: List<LogoLayer>): Bitmap {
        val output = createBitmap(base.width, base.height)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(base, 0f, 0f, paint)
        layers.forEach { layer ->
            val position = layer.placement.bounded(base.width.toFloat() / base.height, layer.bitmap.width.toFloat() / layer.bitmap.height)
            val width = position.width * base.width
            val height = width * layer.bitmap.height / layer.bitmap.width
            paint.alpha = (position.opacity * 255).roundToInt()
            canvas.drawBitmap(layer.bitmap, null, RectF(position.x * base.width - width / 2,
                position.y * base.height - height / 2, position.x * base.width + width / 2,
                position.y * base.height + height / 2), paint)
        }
        // Keep exactly the original card alpha (rounded corners included).
        val baseRow = IntArray(base.width)
        val outputRow = IntArray(base.width)
        for (y in 0 until base.height) {
            base.getPixels(baseRow, 0, base.width, 0, y, base.width, 1)
            output.getPixels(outputRow, 0, base.width, 0, y, base.width, 1)
            for (x in baseRow.indices) outputRow[x] = (outputRow[x] and 0x00FFFFFF) or (baseRow[x] and -0x1000000)
            output.setPixels(outputRow, 0, base.width, 0, y, base.width, 1)
        }
        return output
    }

    fun write(base: Bitmap, layers: List<LogoLayer>, destination: File) {
        val output = render(base, layers)
        try { destination.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { output.recycle() }
    }
}
