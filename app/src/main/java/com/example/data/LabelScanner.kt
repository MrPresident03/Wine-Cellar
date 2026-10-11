package com.example.data

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Reads the text on a wine label with Google ML Kit. The model is bundled in the app, so it works
 * offline and nothing is sent anywhere.
 */
object LabelScanner {

    suspend fun read(bitmap: Bitmap): LabelParser.Result {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val lines = text.textBlocks
                .flatMap { block -> block.lines }
                .map { line -> LabelParser.Line(line.text, line.boundingBox?.height() ?: 0) }
            return LabelParser.parse(lines, Calendar.getInstance().get(Calendar.YEAR))
        } finally {
            recognizer.close()
        }
    }
}
