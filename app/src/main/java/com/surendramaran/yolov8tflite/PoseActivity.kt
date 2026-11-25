package com.surendramaran.yolov8tflite

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import kotlin.math.atan2
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PoseActivity : AppCompatActivity(), PoseDetector.PoseDetectorListener {
    // Note: Due to missing ViewBinding for activity_pose.xml in this snippet, we will use findViewById
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: PoseDetector? = null

    private lateinit var cameraExecutor: ExecutorService

    private var isCapturing = false
    private var isProcessingFrame = false
    private val frameHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private lateinit var sensorManager: SensorManager
    private var gravitySensor: Sensor? = null
    
    private var isPhoneStraight = false
    private var lastStraightToastTime = 0L
    private var currentRollAngle = 0f

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_GRAVITY || event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                // Calculamos el ángulo en grados (0 es vertical).
                val rollAngle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                currentRollAngle = rollAngle

                val overlay = findViewById<PoseOverlayView>(R.id.overlay)
                overlay.setDeviceRoll(-rollAngle) // Negativo para contrarrestar la inclinación

                val tvTilt = findViewById<android.widget.TextView>(R.id.tv_tilt_angle)
                tvTilt.text = String.format("Tilt: %.1f°", rollAngle)
                if (kotlin.math.abs(rollAngle) < 2.0f) {
                    tvTilt.setTextColor(android.graphics.Color.GREEN)
                } else {
                    tvTilt.setTextColor(android.graphics.Color.WHITE)
                }

                if (isCapturing) return

                val isCurrentlyStraight = kotlin.math.abs(rollAngle) < 2.0f
                if (isCurrentlyStraight && !isPhoneStraight) {
                    isPhoneStraight = true
                    val now = System.currentTimeMillis()
                    if (now - lastStraightToastTime > 5000) {
                        android.widget.Toast.makeText(this@PoseActivity, "Celular alineado", android.widget.Toast.LENGTH_SHORT).show()
                        lastStraightToastTime = now
                    }
                } else if (!isCurrentlyStraight && isPhoneStraight) {
                    if (kotlin.math.abs(rollAngle) > 5.0f) {
                        isPhoneStraight = false
                    }
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pose)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (gravitySensor == null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            // Cargar el modelo que acaba de proveer el usuario en la carpeta assets
            detector = PoseDetector(baseContext, "yolov8s-pose.onnx", this)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        bindListeners()
    }

    override fun onResume() {
        super.onResume()
        gravitySensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun processNextFrame() {
        val viewFinder = findViewById<androidx.camera.view.PreviewView>(R.id.view_finder)
        if (isCapturing || isProcessingFrame || viewFinder.visibility != android.view.View.VISIBLE) return
        
        val bitmap = viewFinder.bitmap
        if (bitmap == null) {
            frameHandler.postDelayed({ processNextFrame() }, 50)
            return
        }
        
        isProcessingFrame = true
        cameraExecutor.execute {
            detector?.detect(bitmap)
        }
    }

    private fun bindListeners() {
        val btnCapture = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_capture)
        val btnRetake = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_retake)
        val btnAccept = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_accept)
        
        val viewFinder = findViewById<androidx.camera.view.PreviewView>(R.id.view_finder)
        val ivCapturedPhoto = findViewById<android.widget.ImageView>(R.id.iv_captured_photo)
        val layoutReview = findViewById<android.widget.LinearLayout>(R.id.layout_review)
        val inferenceTime = findViewById<android.widget.TextView>(R.id.inferenceTime)
        val overlay = findViewById<PoseOverlayView>(R.id.overlay)

        btnCapture.setOnClickListener {
            isCapturing = true
            val finalBitmap = viewFinder.bitmap ?: return@setOnClickListener

            overlay.isGhostLineVisible = false
            sensorManager.unregisterListener(sensorEventListener)

            ivCapturedPhoto.setImageBitmap(finalBitmap)
            ivCapturedPhoto.visibility = android.view.View.VISIBLE
            viewFinder.visibility = android.view.View.INVISIBLE

            btnCapture.visibility = android.view.View.GONE
            layoutReview.visibility = android.view.View.VISIBLE

            inferenceTime.text = "Analyzing posture..."
            cameraExecutor.execute {
                detector?.detect(finalBitmap)
            }
        }

        btnRetake.setOnClickListener {
            ivCapturedPhoto.visibility = android.view.View.GONE
            viewFinder.visibility = android.view.View.VISIBLE
            overlay.clear()

            layoutReview.visibility = android.view.View.GONE
            val layoutReport = findViewById<android.widget.LinearLayout>(R.id.layout_report)
            layoutReport.visibility = android.view.View.GONE
            btnCapture.visibility = android.view.View.VISIBLE
            inferenceTime.text = "Stand in frame to analyze posture"

            overlay.isGhostLineVisible = true
            gravitySensor?.let {
                sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
            }

            isCapturing = false
            processNextFrame()
        }

        btnAccept.setOnClickListener {
            android.widget.Toast.makeText(this, "Posture analysis saved successfully", android.widget.Toast.LENGTH_SHORT).show()
            btnRetake.performClick()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val viewFinder = findViewById<androidx.camera.view.PreviewView>(R.id.view_finder)
        val rotation = viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview
            )
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            
            isCapturing = false
            processNextFrame()
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            val inferenceTime = findViewById<android.widget.TextView>(R.id.inferenceTime)
            val overlay = findViewById<PoseOverlayView>(R.id.overlay)
            inferenceTime.text = "No archer detected"
            overlay.clear()

            isProcessingFrame = false
            if (!isCapturing) {
                frameHandler.postDelayed({ processNextFrame() }, 30)
            }
        }
    }

    override fun onDetect(poses: List<PoseResult>, inferenceTime: Long) {
        runOnUiThread {
            val inferenceTimeView = findViewById<android.widget.TextView>(R.id.inferenceTime)
            val overlay = findViewById<PoseOverlayView>(R.id.overlay)
            
            inferenceTimeView.text = "Posture analyzed (${inferenceTime}ms)"
            overlay.apply {
                setResults(poses)
            }

            if (isCapturing && poses.isNotEmpty()) {
                generatePostureReport(poses[0])
            }

            isProcessingFrame = false
            if (!isCapturing) {
                frameHandler.postDelayed({ processNextFrame() }, 30)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()
    }

    private fun generatePostureReport(pose: PoseResult) {
        val layoutReport = findViewById<android.widget.LinearLayout>(R.id.layout_report)
        layoutReport.visibility = android.view.View.VISIBLE

        val tvSpine = findViewById<android.widget.TextView>(R.id.tv_report_spine)
        val tvShoulders = findViewById<android.widget.TextView>(R.id.tv_report_shoulders)
        val tvElbow = findViewById<android.widget.TextView>(R.id.tv_report_elbow)

        val kpts = pose.keypoints

        fun getPt(index: Int): KeyPoint? {
            if (index >= kpts.size) return null
            val kp = kpts[index]
            if (kp.x <= 0f || kp.y <= 0f || kp.conf < 0.4f) return null
            return kp
        }

        val leftShoulder = getPt(5)
        val rightShoulder = getPt(6)
        val leftElbow = getPt(7)
        val rightElbow = getPt(8)
        val leftWrist = getPt(9)
        val rightWrist = getPt(10)
        val leftHip = getPt(11)
        val rightHip = getPt(12)

        // 1. Spine Alignment
        if (leftShoulder != null && rightShoulder != null && leftHip != null && rightHip != null) {
            val midShoulderX = (leftShoulder.x + rightShoulder.x) / 2
            val midShoulderY = (leftShoulder.y + rightShoulder.y) / 2
            val midHipX = (leftHip.x + rightHip.x) / 2
            val midHipY = (leftHip.y + rightHip.y) / 2

            val dx = midHipX - midShoulderX
            val dy = midHipY - midShoulderY
            val spineAngleScreen = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
            val absSpineTilt = kotlin.math.abs(spineAngleScreen - (-currentRollAngle))

            when {
                absSpineTilt < 3f -> {
                    tvSpine.text = "🟢 Spine: Perfect posture"
                    tvSpine.setTextColor(android.graphics.Color.parseColor("#00FF00"))
                }
                absSpineTilt < 8f -> {
                    tvSpine.text = "🟡 Spine: Leaning slightly"
                    tvSpine.setTextColor(android.graphics.Color.parseColor("#FFFF00"))
                }
                else -> {
                    tvSpine.text = "🔴 Spine: Leaning critically"
                    tvSpine.setTextColor(android.graphics.Color.parseColor("#FF0000"))
                }
            }
        } else {
            tvSpine.text = "⚪ Spine: Cannot detect"
            tvSpine.setTextColor(android.graphics.Color.WHITE)
        }

        // 2. Shoulder Alignment
        if (leftShoulder != null && rightShoulder != null) {
            var dxS = rightShoulder.x - leftShoulder.x
            var dyS = rightShoulder.y - leftShoulder.y
            if (dxS < 0) {
                dxS = -dxS
                dyS = -dyS
            }
            val shoulderAngleScreen = Math.toDegrees(atan2(dyS.toDouble(), dxS.toDouble())).toFloat()
            val absShoulderTilt = kotlin.math.abs(shoulderAngleScreen - currentRollAngle)

            if (absShoulderTilt < 6f) {
                tvShoulders.text = "🟢 Shoulders: Leveled"
                tvShoulders.setTextColor(android.graphics.Color.parseColor("#00FF00"))
            } else {
                tvShoulders.text = "🔴 Shoulders: Dropped or raised"
                tvShoulders.setTextColor(android.graphics.Color.parseColor("#FF0000"))
            }
        } else {
            tvShoulders.text = "⚪ Shoulders: Cannot detect"
            tvShoulders.setTextColor(android.graphics.Color.WHITE)
        }

        // 3. Draw Elbow Height
        if (leftShoulder != null && rightShoulder != null && leftWrist != null && rightWrist != null && leftElbow != null && rightElbow != null) {
            val distLeft = kotlin.math.hypot((leftWrist.x - leftShoulder.x).toDouble(), (leftWrist.y - leftShoulder.y).toDouble())
            val distRight = kotlin.math.hypot((rightWrist.x - rightShoulder.x).toDouble(), (rightWrist.y - rightShoulder.y).toDouble())

            val isLeftDrawArm = distLeft < distRight
            val drawElbow = if (isLeftDrawArm) leftElbow else rightElbow
            val drawShoulder = if (isLeftDrawArm) leftShoulder else rightShoulder

            val dyElbow = drawElbow.y - drawShoulder.y

            if (dyElbow > 0.08f) { // Dropped significantly
                tvElbow.text = "🔴 Draw Elbow: Dropped"
                tvElbow.setTextColor(android.graphics.Color.parseColor("#FF0000"))
            } else {
                tvElbow.text = "🟢 Draw Elbow: Good height/tension"
                tvElbow.setTextColor(android.graphics.Color.parseColor("#00FF00"))
            }
        } else {
            tvElbow.text = "⚪ Draw Elbow: Cannot detect arms"
            tvElbow.setTextColor(android.graphics.Color.WHITE)
        }
    }

    companion object {
        private const val TAG = "PoseCamera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).toTypedArray()
    }
}
