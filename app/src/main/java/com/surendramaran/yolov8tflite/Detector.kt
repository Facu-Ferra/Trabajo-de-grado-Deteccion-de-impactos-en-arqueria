package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.util.Collections

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener,
) {

    private var env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var labels = mutableListOf<String>()
    private var modelBytes: ByteArray? = null

    private var tensorWidth = 640
    private var tensorHeight = 640
    private var numChannel = 0
    private var numElements = 0

    init {
        try {
            modelBytes = context.assets.open(modelPath).readBytes()
            session = env.createSession(modelBytes, OrtSession.SessionOptions()) // creamos la sesion de ONNX
            Log.d("Detector", "ONNX Runtime sesión creada exitosamente con modelo $modelPath")
        } catch (e: Exception) {
            Log.e("Detector", "Error cargando modelo ONNX: ${e.message}", e)
        }

        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = reader.readLine()
            while (line != null && line != "") {
                labels.add(line)
                line = reader.readLine()
            }

            reader.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun restart(isGpu: Boolean) {
        try {
            session?.close()
            session = env.createSession(modelBytes, OrtSession.SessionOptions())
        } catch (e: Exception) {
            Log.e("Detector", "Error en restart de ONNX: ${e.message}", e)
        }
    }

    fun close() {
        try {
            session?.close()
            env.close()
        } catch (e: Exception) {
            Log.e("Detector", "Error cerrando ONNX: ${e.message}", e)
        }
    }

    private fun saveBitmap(bitmap: Bitmap, name: String) {
        try {
            val file = java.io.File(context.getExternalFilesDir(null), name)
            val out = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Log.d("Detector", "Guardada imagen en: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("Detector", "Error guardando bitmap $name: ${e.message}", e)
        }
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val width = bitmap.width
        val height = bitmap.height
        val buffer = FloatBuffer.allocate(3 * width * height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val area = width * height
        for (i in 0 until area) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            buffer.put(i, r) // Canal Rojo (red)
            buffer.put(i + area, g) // Canal Verde
            buffer.put(i + 2 * area, b) // Azul
        }
        buffer.rewind()
        return buffer
    }

    fun detect(frame: Bitmap) {
        val currentSession = session ?: return
        var inferenceTime = SystemClock.uptimeMillis()

        saveBitmap(frame, "1_original_input.png")
        val resizedBitmap = if (frame.width == tensorWidth && frame.height == tensorHeight) frame else applyLetterbox(frame, tensorWidth, tensorHeight)
        saveBitmap(resizedBitmap, "2_tensor_input_640x640.png")
        saveBitmap(Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false), "3_old_squashed_comparison.png")

        try {
            val floatBuffer = bitmapToFloatBuffer(resizedBitmap)
            val inputName = currentSession.inputNames.iterator().next()
            val inputTensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 3, tensorHeight.toLong(), tensorWidth.toLong()))
            val output = currentSession.run(Collections.singletonMap(inputName, inputTensor))

            val outputTensor = output.iterator().next().value as OnnxTensor
            val shape = outputTensor.info.shape
            if (shape.size >= 3) {
                numChannel = shape[1].toInt()
                numElements = shape[2].toInt()
            }

            val outBuffer = outputTensor.floatBuffer
            val floatArray = FloatArray(outBuffer.remaining())
            outBuffer.get(floatArray)

            inputTensor.close()
            output.close()

            val bestBoxes = bestBox(floatArray)
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            if (bestBoxes == null) {
                detectorListener.onEmptyDetect()
                return
            }

            detectorListener.onDetect(bestBoxes, inferenceTime)
        } catch (e: Exception) {
            Log.e("Detector", "Error durante inferencia ONNX: ${e.message}", e)
            detectorListener.onEmptyDetect()
        }
    }

    private fun bestBox(array: FloatArray) : List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()
        var overallMaxConf = 0f
        var overallMaxCls = ""

        for (c in 0 until numElements) {
            var maxConf = CONFIDENCE_THRESHOLD
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                if (array[arrayIdx] > overallMaxConf) {
                    overallMaxConf = array[arrayIdx]
                    overallMaxCls = labels.getOrElse(j - 4) { "cls_${j-4}" }
                }
                j++
                arrayIdx += numElements
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels.getOrElse(maxIdx) { "cls_$maxIdx" }
                var cx = array[c] // 0
                var cy = array[c + numElements] // 1
                var w = array[c + numElements * 2]
                var h = array[c + numElements * 3]

                // Si por alguna razón las coordenadas vienen en píxeles (0 a 640) en vez de normalizadas (0 a 1), normalizarlas:
                if (cx > 1.5f || cy > 1.5f || w > 1.5f || h > 1.5f) {
                    cx /= tensorWidth.toFloat()
                    cy /= tensorHeight.toFloat()
                    w /= tensorWidth.toFloat()
                    h /= tensorHeight.toFloat()
                }

                val x1 = (cx - (w/2F)).coerceIn(0F, 1F)
                val y1 = (cy - (h/2F)).coerceIn(0F, 1F)
                val x2 = (cx + (w/2F)).coerceIn(0F, 1F)
                val y2 = (cy + (h/2F)).coerceIn(0F, 1F)

                Log.d("YOLO_BOX", "📦 Detectado: $clsName (${(maxConf*100).toInt()}%) en [x1=$x1, y1=$y1, x2=$x2, y2=$y2] crudo:(cx=$cx, cy=$cy, w=$w, h=$h)")

                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h,
                        cnf = maxConf, cls = maxIdx, clsName = clsName
                    )
                )
            }
        }

        Log.d("YOLO_DEBUG", "🔥 Máxima confianza encontrada en toda la imagen: '$overallMaxCls' con ${(overallMaxConf*100).toInt()}% ($overallMaxConf)")

        if (boundingBoxes.isEmpty()) return null

        return applyNMS(boundingBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>) : MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        // Filtro Final: Solo puede existir 1 bullseye y 1 target_bounds físico por imagen.
        // Como 'selectedBoxes' ya viene ordenado de mayor a menor confianza, nos quedamos solo con la primera aparición.
        val finalBoxes = mutableListOf<BoundingBox>()
        var hasBullseye = false
        var hasTargetBounds = false
        
        for (box in selectedBoxes) {
            when (box.clsName) {
                "bullseye" -> {
                    if (!hasBullseye) {
                        finalBoxes.add(box)
                        hasBullseye = true
                    }
                }
                "target_bounds" -> {
                    if (!hasTargetBounds) {
                        finalBoxes.add(box)
                        hasTargetBounds = true
                    }
                }
                else -> {
                    // "arrow" o cualquier otra clase
                    finalBoxes.add(box)
                }
            }
        }

        return finalBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        var CONFIDENCE_THRESHOLD = 0.35F // Editable desde la UI
        private const val IOU_THRESHOLD = 0.7F

        fun applyLetterbox(source: Bitmap, targetWidth: Int = 640, targetHeight: Int = 640): Bitmap {
            val scale = minOf(targetWidth.toFloat() / source.width, targetHeight.toFloat() / source.height)
            val newWidth = (source.width * scale).toInt()
            val newHeight = (source.height * scale).toInt()

            val scaledBitmap = Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
            val letterboxed = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

            val canvas = android.graphics.Canvas(letterboxed)
            canvas.drawColor(android.graphics.Color.rgb(114, 114, 114)) // uso este color pq es el color gris oficial de Ultralytics

            val left = (targetWidth - newWidth) / 2f
            val top = (targetHeight - newHeight) / 2f
            canvas.drawBitmap(scaledBitmap, left, top, null)

            return letterboxed
        }
    }
}