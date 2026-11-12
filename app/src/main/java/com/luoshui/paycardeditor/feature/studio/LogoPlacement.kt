package com.luoshui.paycardeditor.feature.studio

/** Coordinates are fractions of the card, independent of preview size. */
internal data class LogoPlacement(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val width: Float = 0.28f,
    val opacity: Float = 1f,
) {
    fun bounded(cardAspect: Float, logoAspect: Float): LogoPlacement {
        require(cardAspect.isFinite() && cardAspect > 0 && logoAspect.isFinite() && logoAspect > 0)
        val maxWidth = minOf(0.95f, 0.95f * logoAspect / cardAspect)
        val w = width.takeIf { it.isFinite() }?.coerceIn(minOf(0.03f, maxWidth), maxWidth) ?: minOf(0.28f, maxWidth)
        val h = w * cardAspect / logoAspect
        return copy(
            x = (x.takeIf { it.isFinite() } ?: 0.5f).coerceIn(w / 2, 1 - w / 2),
            y = (y.takeIf { it.isFinite() } ?: 0.5f).coerceIn(h / 2, 1 - h / 2),
            width = w,
            opacity = (opacity.takeIf { it.isFinite() } ?: 1f).coerceIn(0f, 1f),
        )
    }
}
