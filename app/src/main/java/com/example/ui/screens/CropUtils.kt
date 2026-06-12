package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.FileOutputStream

fun cropAndSaveImage(
    context: Context,
    uri: Uri,
    scale: Float,
    offset: Offset,
    boxSizePx: Float, // the pixels of the 280.dp box 
    fileName: String
): String? {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        if (originalBitmap == null) return null

        val outputSize = 512f
        val outputBitmap = Bitmap.createBitmap(outputSize.toInt(), outputSize.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        val baseScale = maxOf(
            outputSize / originalBitmap.width,
            outputSize / originalBitmap.height
        )

        val scaledWidth = originalBitmap.width * baseScale
        val scaledHeight = originalBitmap.height * baseScale

        val userTranslateX = offset.x * (outputSize / boxSizePx)
        val userTranslateY = offset.y * (outputSize / boxSizePx)

        canvas.save()
        // Compose: start from center of the output box
        canvas.translate(outputSize / 2f, outputSize / 2f)
        
        // Compose: apply translation
        canvas.translate(userTranslateX, userTranslateY)
        
        // Compose: apply image scale
        canvas.scale(scale, scale)
        
        // Go from center to top-left of the base scaled image
        canvas.translate(-scaledWidth / 2f, -scaledHeight / 2f)
        
        // Scale the original bitmap by baseScale
        canvas.scale(baseScale, baseScale)
        
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)
        canvas.restore()

        val file = File(context.filesDir, fileName)
        val outStream = FileOutputStream(file)
        outputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
        outStream.flush()
        outStream.close()

        return file.toURI().toString()
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
