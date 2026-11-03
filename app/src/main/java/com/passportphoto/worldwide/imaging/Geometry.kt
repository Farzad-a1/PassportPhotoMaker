package com.passportphoto.worldwide.imaging

import kotlin.math.*

data class Crop(val left: Float, val top: Float, val width: Float, val height: Float)

object Geometry {
    fun crop(iw: Int, ih: Int, ratio: Float, zoom: Float, x: Float, y: Float): Crop {
        require(iw > 0 && ih > 0 && ratio > 0 && zoom >= 1)
        val w = min(iw.toFloat(), ih * ratio) / zoom
        val h = w / ratio
        return Crop(
            ((iw - w) / 2 - x * w).coerceIn(0f, iw - w),
            ((ih - h) / 2 - y * h).coerceIn(0f, ih - h),
            w,
            h,
        )
    }

    fun pixels(mm: Float, dpi: Int = 300) = (mm / 25.4f * dpi).roundToInt()

    fun points(mm: Float) = mm * 72f / 25.4f

    fun sheet(
        paperW: Float,
        paperH: Float,
        photoW: Float,
        photoH: Float,
        margin: Float = 6f,
        gap: Float = 3f,
    ): List<Crop> {
        require(photoW > 0 && photoH > 0 && margin >= 0 && gap >= 0)
        val cols = floor((paperW - 2 * margin + gap) / (photoW + gap)).toInt().coerceAtLeast(0)
        val rows = floor((paperH - 2 * margin + gap) / (photoH + gap)).toInt().coerceAtLeast(0)
        return (0 until rows).flatMap { row ->
            (0 until cols).map { col ->
                Crop(margin + col * (photoW + gap), margin + row * (photoH + gap), photoW, photoH)
            }
        }
    }
}
