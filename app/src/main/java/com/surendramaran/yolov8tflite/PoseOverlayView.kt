package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PoseOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<PoseResult>()
    
    private var  deviceRollAngle: Float = 0f
    var isGhostLineVisible: Boolean = true

    private val linePaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        strokeWidth = 6f // Adjusted for Android screen scale (thickness 3 in OpenCV is usually thicker on phones)
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint().apply {
        color = Color.parseColor("#FF0000")
        style = Paint.Style.FILL
    }

    private val axisPaint = Paint().apply {
        color = Color.parseColor("#00FFFF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val axisDotPaint = Paint().apply {
        color = Color.parseColor("#00FFFF")
        style = Paint.Style.FILL
    }

    fun setDeviceRoll(angle: Float) {
        deviceRollAngle = angle
        invalidate()
    }

    private val ghostLinePaint = Paint().apply {
        color = Color.parseColor("#80FFFF00") // Amarillo semi-transparente
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    fun clear() {
        results = listOf()
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        results.forEach { pose ->
            val kpts = pose.keypoints
            
            // Helper function to get valid point coordinates (mapped to screen width/height)
            fun getPt(index: Int): Pair<Float, Float>? {
                if (index >= kpts.size) return null
                val kp = kpts[index]
                if (kp.x <= 0f || kp.y <= 0f || kp.conf < 0.4f) return null // Valid point check
                return Pair(kp.x * width, kp.y * height)
            }

            val leftShoulder = getPt(5)
            val rightShoulder = getPt(6)
            val leftElbow = getPt(7)
            val rightElbow = getPt(8)
            val leftWrist = getPt(9)
            val rightWrist = getPt(10)
            val leftHip = getPt(11)
            val rightHip = getPt(12)

            // 1. ARMS LINE (9 -> 7 -> 5 -> 6 -> 8 -> 10)
            val armPoints = listOf(leftWrist, leftElbow, leftShoulder, rightShoulder, rightElbow, rightWrist)
            for (i in 0 until armPoints.size - 1) {
                val pt1 = armPoints[i]
                val pt2 = armPoints[i + 1]
                if (pt1 != null && pt2 != null) {
                    canvas.drawLine(pt1.first, pt1.second, pt2.first, pt2.second, linePaint)
                    canvas.drawCircle(pt1.first, pt1.second, 12f, dotPaint) // 12f is equivalent to rad 6
                    canvas.drawCircle(pt2.first, pt2.second, 12f, dotPaint)
                }
            }

            // 2. HIPS LINE (11 -> 12)
            if (leftHip != null && rightHip != null) {
                canvas.drawLine(leftHip.first, leftHip.second, rightHip.first, rightHip.second, linePaint)
                canvas.drawCircle(leftHip.first, leftHip.second, 12f, dotPaint)
                canvas.drawCircle(rightHip.first, rightHip.second, 12f, dotPaint)
            }

            // 3. BODY AXIS LINE (Mid Shoulders to Mid Hips, extrapolated)
            if (leftShoulder != null && rightShoulder != null && leftHip != null && rightHip != null) {
                val midShoulderX = (leftShoulder.first + rightShoulder.first) / 2
                val midShoulderY = (leftShoulder.second + rightShoulder.second) / 2

                val midHipX = (leftHip.first + rightHip.first) / 2
                val midHipY = (leftHip.second + rightHip.second) / 2

                canvas.drawCircle(midShoulderX, midShoulderY, 8f, axisDotPaint)
                canvas.drawCircle(midHipX, midHipY, 8f, axisDotPaint)

                val dx = midHipX - midShoulderX
                val dy = midHipY - midShoulderY

                val spineAngle = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dy.toDouble())).toFloat()
                val diff = kotlin.math.abs(spineAngle - deviceRollAngle) // Comparar inclinación con plomada

                axisPaint.color = when {
                    diff < 3f -> Color.parseColor("#00FF00") // Verde (Alineado)
                    diff < 8f -> Color.parseColor("#FFFF00") // Amarillo (Levemente inclinado)
                    else -> Color.parseColor("#FF0000") // Rojo (Muy inclinado)
                }

                val topX = midShoulderX - dx * 0.5f
                val topY = midShoulderY - dy * 0.5f

                val bottomX = midHipX + dx * 1.5f
                val bottomY = midHipY + dy * 1.5f

                canvas.drawLine(topX, topY, bottomX, bottomY, axisPaint)

                // 4. DIGITAL PLUMB LINE (Ghost Guide Line) anchored at midHip
                if (isGhostLineVisible) {
                    canvas.save()
                    canvas.rotate(deviceRollAngle, midHipX, midHipY)
                    canvas.drawLine(midHipX, 0f, midHipX, height.toFloat(), ghostLinePaint)
                    canvas.restore()
                }
            }
        }
    }

    fun setResults(poses: List<PoseResult>) {
        results = poses
        invalidate()
    }
}
