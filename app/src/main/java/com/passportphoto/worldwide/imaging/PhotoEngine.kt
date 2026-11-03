package com.passportphoto.worldwide.imaging

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import com.passportphoto.worldwide.data.Project
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

object PhotoEngine {
    @androidx.annotation.RequiresApi(28)
    fun decode(file: File, maxSide: Int = 2400): Bitmap {
        val source = ImageDecoder.createSource(file)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val sample =
                max(1, ceil(max(info.size.width, info.size.height).toDouble() / maxSide).toInt())
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    fun originalFormat(file: File): Pair<String, String> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        val mime = options.outMimeType ?: "application/octet-stream"
        val extension =
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "photo"
        return mime to extension
    }

    // API 26/27 fallback. BitmapFactory bounds prevent full-resolution allocations.
    fun load(file: File, maxSide: Int = 2400): Bitmap {
        if (android.os.Build.VERSION.SDK_INT >= 28) return decode(file, maxSide)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val bitmap =
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: error("Cannot read photo")
        val exif = androidx.exifinterface.media.ExifInterface(file.path)
        val matrix = Matrix()
        when (exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            6 -> matrix.setRotate(90f)
            7 -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            8 -> matrix.setRotate(270f)
        }
        return if (matrix.isIdentity) bitmap
        else
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
    }

    fun render(project: Project, source: Bitmap): Bitmap {
        val crop =
            Geometry.crop(
                source.width,
                source.height,
                project.widthMm / project.heightMm,
                project.zoom,
                project.x,
                project.y,
            )
        val output =
            Bitmap.createBitmap(
                Geometry.pixels(project.widthMm),
                Geometry.pixels(project.heightMm),
                Bitmap.Config.ARGB_8888,
            )
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                source,
                RectF(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height).let {
                    Rect(
                        it.left.roundToInt(),
                        it.top.roundToInt(),
                        it.right.roundToInt(),
                        it.bottom.roundToInt(),
                    )
                },
                Rect(0, 0, output.width, output.height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
        return output
    }

    fun jpeg(context: Context, project: Project, quality: Int): File {
        val source = load(File(project.original))
        val output = render(project, source)
        val file =
            File(
                File(context.cacheDir, "exports").apply { mkdirs() },
                "${project.id}-${java.util.UUID.randomUUID()}.jpg",
            )
        try {
            file.outputStream().use {
                check(output.compress(Bitmap.CompressFormat.JPEG, quality, it))
            }
        } finally {
            source.recycle()
            output.recycle()
        }
        return file
    }

    fun pdf(context: Context, project: Project, paper: String): File {
        val (w, h) =
            when (paper) {
                "4 × 6 in" -> 101.6f to 152.4f
                "US Letter" -> 215.9f to 279.4f
                else -> 210f to 297f
            }
        val cells = Geometry.sheet(w, h, project.widthMm, project.heightMm)
        require(cells.isNotEmpty()) { "Photo is too large for this paper. Choose a larger sheet." }
        val source = load(File(project.original))
        val image = render(project, source)
        val file =
            File(
                File(context.cacheDir, "exports").apply { mkdirs() },
                "${project.id}-${java.util.UUID.randomUUID()}.pdf",
            )
        val pdf = PdfDocument()
        try {
            val page =
                pdf.startPage(
                    PdfDocument.PageInfo.Builder(
                            ceil(Geometry.points(w)).toInt(),
                            ceil(Geometry.points(h)).toInt(),
                            1,
                        )
                        .create()
                )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            cells.forEach { c ->
                page.canvas.drawBitmap(
                    image,
                    null,
                    RectF(
                        Geometry.points(c.left),
                        Geometry.points(c.top),
                        Geometry.points(c.left + c.width),
                        Geometry.points(c.top + c.height),
                    ),
                    paint,
                )
            }
            pdf.finishPage(page)
            file.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
            image.recycle()
            source.recycle()
        }
        return file
    }

    fun quality(bitmap: Bitmap, project: Project): List<String> {
        val crop =
            Geometry.crop(
                bitmap.width,
                bitmap.height,
                project.widthMm / project.heightMm,
                project.zoom,
                project.x,
                project.y,
            )
        val warnings = mutableListOf<String>()
        val req =
            com.passportphoto.worldwide.requirements.Requirements.get(
                project.country,
                project.document,
            )
        if (
            req.minWidth != null &&
                req.minHeight != null &&
                (bitmap.width < req.minWidth || bitmap.height < req.minHeight)
        )
            warnings +=
                "Image resolution is below the verified digital minimum of ${req.minWidth} × ${req.minHeight} pixels."
        if (abs(bitmap.width.toFloat() / bitmap.height - project.widthMm / project.heightMm) > .03f)
            warnings +=
                "Original aspect ratio differs from the selected print size. Review the crop; original export preserves the original ratio."
        if (
            crop.width < Geometry.pixels(project.widthMm) ||
                crop.height < Geometry.pixels(project.heightMm)
        )
            warnings +=
                "Image resolution is below 300 dpi at the selected print size; export will upscale."
        val small = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        val pixels = IntArray(128 * 128)
        small.getPixels(pixels, 0, 128, 0, 0, 128, 128)
        small.recycle()
        val lum =
            pixels.map { .2126 * Color.red(it) + .7152 * Color.green(it) + .0722 * Color.blue(it) }
        val mean = lum.average()
        val dark = lum.count { it < 15 }.toDouble() / lum.size
        val bright = lum.count { it > 245 }.toDouble() / lum.size
        if (mean < 65 || dark > .45) warnings += "Lighting may be too dark; check face detail."
        if (mean > 225 || bright > .65)
            warnings += "Photo may be overexposed; check highlights on the face."
        val energy =
            (1 until 127)
                .flatMap { y ->
                    (1 until 127).map { x ->
                        val i = y * 128 + x
                        abs(4 * lum[i] - lum[i - 1] - lum[i + 1] - lum[i - 128] - lum[i + 128])
                    }
                }
                .average()
        if (energy < 5) warnings += "Photo may be blurry or have too little detail."
        warnings +=
            "Manual review required: face position, head size, expression, shadows and background are not automatically verified."
        warnings += "Quality checks are advisory. Acceptance is decided by the issuing authority."
        return warnings
    }

    fun print(context: Context, file: File) {
        (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(
            "Passport photo sheet",
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    signal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: android.os.Bundle?,
                ) {
                    if (signal?.isCanceled == true) callback.onLayoutCancelled()
                    else
                        callback.onLayoutFinished(
                            PrintDocumentInfo.Builder(file.name)
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                .setPageCount(1)
                                .build(),
                            true,
                        )
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor,
                    signal: CancellationSignal?,
                    callback: WriteResultCallback,
                ) {
                    try {
                        if (signal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            return
                        }
                        file.inputStream().use { input ->
                            FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) }
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    }
                }
            },
            null,
        )
    }
}
