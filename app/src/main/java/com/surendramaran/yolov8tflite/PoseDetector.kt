package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.Collections

data class KeyPoint(val x: Float, val y: Float, val conf: Float)

data class PoseResult(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val cx: Float, val cy: Float, val w: Float, val h: Float,
    val cnf: Float,
    val keypoints: List<KeyPoint>
)

class PoseDetector(
    private val context: Context,
    private val modelPath: String,
    private val detectorListener: PoseDetectorListener,
) {

    private var env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var modelBytes: ByteArray? = null

    private var tensorWidth = 640
    private var tensorHeight = 640
    private var numChannel = 3
    private var numElements = 8400

    private var floatBuffer: FloatBuffer? = null
    private var pixels: IntArray? = null

    init {
        floatBuffer = FloatBuffer.allocate(1 * 3 * tensorWidth * tensorHeight)
        pixels = IntArray(tensorWidth * tensorHeight)

        try {
            modelBytes = context.assets.open(modelPath).readBytes()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
            }
            session = env.createSession(modelBytes, options)
            Log.d("PoseDetector", "ONNX Pose model loaded successfully: $modelPath")
        } catch (e: Exception) {
            Log.e("PoseDetector", "Error loading ONNX Pose model: ${e.message}", e)
        }
    }

    fun restart(isGpu: Boolean = false) {
        try {
            session?.close()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
            }
            session = env.createSession(modelBytes, options)
        } catch (e: Exception) {
            Log.e("PoseDetector", "Error restarting ONNX Pose model: ${e.message}", e)
        }
    }

    fun close() {
        try {
            session?.close()
            session = null
            env.close()
        } catch (e: Exception) {
            Log.e("PoseDetector", "Error closing ONNX Pose model: ${e.message}", e)
        }
    }

    fun detect(frame: Bitmap) {
        if (session == null) {
            detectorListener.onEmptyDetect()
            return
        }

        var inferenceTime = System.currentTimeMillis()

        try {
            val scale = minOf(tensorWidth.toFloat() / frame.width, tensorHeight.toFloat() / frame.height)
            val newWidth = (frame.width * scale).toInt()
            val newHeight = (frame.height * scale).toInt()
            val padLeft = (tensorWidth - newWidth) / 2f
            val padTop = (tensorHeight - newHeight) / 2f

            val scaledBitmap = Bitmap.createScaledBitmap(frame, newWidth, newHeight, true)
            
            val buffer = floatBuffer ?: return
            val pxs = pixels ?: return

            buffer.rewind()
            
            // Llenar el buffer con el color gris de padding (114)
            for (i in 0 until tensorWidth * tensorHeight) {
                buffer.put(i, 114f / 255f)
                buffer.put(i + tensorWidth * tensorHeight, 114f / 255f)
                buffer.put(i + 2 * tensorWidth * tensorHeight, 114f / 255f)
            }
            
            scaledBitmap.getPixels(pxs, 0, newWidth, 0, 0, newWidth, newHeight)

            // Sobrescribir el centro del buffer con los pixeles de la imagen escalada
            for (y in 0 until newHeight) {
                for (x in 0 until newWidth) {
                    val pixel = pxs[y * newWidth + x]
                    val bufferIdx = ((y + padTop.toInt()) * tensorWidth) + (x + padLeft.toInt())
                    
                    buffer.put(bufferIdx, ((pixel shr 16) and 0xFF) / 255.0f)
                    buffer.put(bufferIdx + tensorWidth * tensorHeight, ((pixel shr 8) and 0xFF) / 255.0f)
                    buffer.put(bufferIdx + 2 * tensorWidth * tensorHeight, (pixel and 0xFF) / 255.0f)
                }
            }

            val inputShape = longArrayOf(1, 3, tensorHeight.toLong(), tensorWidth.toLong())
            val inputTensor = OnnxTensor.createTensor(env, buffer, inputShape)

            val inputName = session?.inputNames?.iterator()?.next()
            val results = session?.run(Collections.singletonMap(inputName, inputTensor))

            val output = results?.get(0)?.value as Array<Array<FloatArray>>
            // output shape: [1][56][8400]
            val array = output[0]

            val poses = mutableListOf<PoseResult>()

            for (c in 0 until numElements) {
                val conf = array[4][c]
                if (conf > CONFIDENCE_THRESHOLD) {
                    var cx = array[0][c]
                    var cy = array[1][c]
                    var w = array[2][c]
                    var h = array[3][c]

                    if (cx <= 1.5f || cy <= 1.5f) {
                        cx *= tensorWidth.toFloat()
                        cy *= tensorHeight.toFloat()
                        w *= tensorWidth.toFloat()
                        h *= tensorHeight.toFloat()
                    }

                    // Deshacer el letterbox para obtener coordenadas relativas a la imagen original (en píxeles)
                    val unpaddedCx = (cx - padLeft) / scale
                    val unpaddedCy = (cy - padTop) / scale
                    val unpaddedW = w / scale
                    val unpaddedH = h / scale

                    // Normalizar respecto al frame original
                    val normCx = unpaddedCx / frame.width
                    val normCy = unpaddedCy / frame.height
                    val normW = unpaddedW / frame.width
                    val normH = unpaddedH / frame.height

                    val x1 = (normCx - (normW / 2F)).coerceIn(0F, 1F)
                    val y1 = (normCy - (normH / 2F)).coerceIn(0F, 1F)
                    val x2 = (normCx + (normW / 2F)).coerceIn(0F, 1F)
                    val y2 = (normCy + (normH / 2F)).coerceIn(0F, 1F)

                    val keypoints = mutableListOf<KeyPoint>()
                    for (k in 0 until 17) {
                        var kx = array[5 + k * 3][c]
                        var ky = array[5 + k * 3 + 1][c]
                        val kconf = array[5 + k * 3 + 2][c]

                        if (kx <= 1.5f && kx > 0f) {
                            kx *= tensorWidth.toFloat()
                            ky *= tensorHeight.toFloat()
                        }

                        // Deshacer letterbox de keypoints
                        val unpaddedKx = (kx - padLeft) / scale
                        val unpaddedKy = (ky - padTop) / scale

                        // Normalizar respecto al frame original
                        val normKx = unpaddedKx / frame.width
                        val normKy = unpaddedKy / frame.height

                        keypoints.add(KeyPoint(normKx, normKy, kconf))
                    }

                    poses.add(
                        PoseResult(
                            x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                            cx = normCx, cy = normCy, w = normW, h = normH,
                            cnf = conf, keypoints = keypoints
                        )
                    )
                }
            }

            val bestPoses = applyNMS(poses)
            inferenceTime = System.currentTimeMillis() - inferenceTime

            if (bestPoses.isEmpty()) {
                detectorListener.onEmptyDetect()
            } else {
                detectorListener.onDetect(bestPoses, inferenceTime)
            }
        } catch (e: Exception) {
            Log.e("PoseDetector", "Error during ONNX Pose inference: ${e.message}", e)
            detectorListener.onEmptyDetect()
        }
    }

    private fun applyNMS(poses: List<PoseResult>): List<PoseResult> {
        val sortedPoses = poses.sortedByDescending { it.cnf }.toMutableList()
        val selectedPoses = mutableListOf<PoseResult>()

        while (sortedPoses.isNotEmpty()) {
            val first = sortedPoses.first()
            selectedPoses.add(first)
            sortedPoses.remove(first)
            val iterator = sortedPoses.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                val iou = calculateIoU(first, next)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }
        return selectedPoses
    }

    private fun calculateIoU(box1: PoseResult, box2: PoseResult): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    interface PoseDetectorListener {
        fun onEmptyDetect()
        fun onDetect(poses: List<PoseResult>, inferenceTime: Long)
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.50F
        private const val IOU_THRESHOLD = 0.5F
    }
}
