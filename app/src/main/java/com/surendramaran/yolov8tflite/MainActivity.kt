package com.surendramaran.yolov8tflite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener {
    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null

    private lateinit var cameraExecutor: ExecutorService

    private var isCapturing = false
    private var isProcessingFrame = false
    private val frameHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var targetType = "10-Ring"
    private var expectedArrows = 3
    private var sessionId: Long = -1L
    private var currentScores: List<ArrowScore> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val prefs = getSharedPreferences("ArcheryPrefs", MODE_PRIVATE)
        val savedConf = prefs.getFloat("CONF_THRESHOLD", 0.35f)
        Detector.CONFIDENCE_THRESHOLD = savedConf
        
        val tvConf = findViewById<android.widget.TextView>(R.id.tv_confidence)
        val seekConf = findViewById<android.widget.SeekBar>(R.id.seek_confidence)
        seekConf.progress = (savedConf * 100).toInt()
        tvConf.text = "Conf: ${seekConf.progress}%"
        
        seekConf.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val safeProgress = maxOf(5, progress) // Evitar 0 absoluto
                tvConf.text = "Conf: $safeProgress%"
                val floatConf = safeProgress / 100f
                Detector.CONFIDENCE_THRESHOLD = floatConf
                prefs.edit().putFloat("CONF_THRESHOLD", floatConf).apply()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        targetType = intent.getStringExtra("TARGET_TYPE") ?: "10-Ring"
        expectedArrows = intent.getIntExtra("EXPECTED_ARROWS", 3)
        sessionId = intent.getLongExtra("SESSION_ID", -1L)
        val isDuelCamera = intent.getBooleanExtra("IS_DUEL_CAMERA", false)

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        bindListeners()
    }

    private fun processNextFrame() {
        if (isCapturing || isProcessingFrame || binding.viewFinder.visibility != android.view.View.VISIBLE) return
        val bitmap = binding.viewFinder.bitmap
        if (bitmap == null) {
            frameHandler.postDelayed({ processNextFrame() }, 50)
            return
        }
        isProcessingFrame = true
        val squareBitmap = Detector.applyLetterbox(bitmap, 640, 640)
        cameraExecutor.execute {
            detector?.detect(squareBitmap)
        }
    }

    private fun bindListeners() {
        binding.apply {
            btnCapture.setOnClickListener {
                isCapturing = true

                // 1 -------- Extrae el frame exacto de CameraX como Bitmap
                val finalBitmap = viewFinder.bitmap ?: return@setOnClickListener
                // 2 --------  Aplica el Letterbox
                val squareBitmap = Detector.applyLetterbox(finalBitmap, 640, 640)

                // 3 -------- -- Congela la imagen en la interfaz para revisión
                ivCapturedPhoto.setImageBitmap(squareBitmap)
                ivCapturedPhoto.visibility = android.view.View.VISIBLE
                viewFinder.visibility = android.view.View.INVISIBLE


                btnCapture.visibility = android.view.View.GONE
                findViewById<android.widget.LinearLayout>(R.id.layout_confidence).visibility = android.view.View.GONE
                layoutReview.visibility = android.view.View.VISIBLE
                btnToggleLabels.visibility = android.view.View.VISIBLE

                inferenceTime.text = "Analyzing target and arrows..."
                // 4 --------  Envia el Bitmap al hilo secundario para inferencia ONNX definitiva y puntuación
                cameraExecutor.execute {
                    detector?.detect(squareBitmap)
                }
            }

            btnRetake.setOnClickListener {
                ivCapturedPhoto.visibility = android.view.View.GONE
                viewFinder.visibility = android.view.View.VISIBLE
                overlay.clear()

                layoutReview.visibility = android.view.View.GONE
                layoutScores.visibility = android.view.View.GONE
                btnToggleLabels.visibility = android.view.View.GONE
                btnCapture.visibility = android.view.View.VISIBLE
                findViewById<android.widget.LinearLayout>(R.id.layout_confidence).visibility = android.view.View.VISIBLE
                inferenceTime.text = "Aim at the target and tap Capture"

                isCapturing = false
                processNextFrame()
            }

            btnAccept.setOnClickListener {
                if (intent.getBooleanExtra("IS_DUEL_CAMERA", false)) {
                    val scoresArray = currentScores.map { it.score }.toIntArray()
                    val resultIntent = Intent().apply {
                        putExtra("USER_SCORES", scoresArray)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } else if (sessionId != -1L && currentScores.isNotEmpty()) {
                    val db = com.surendramaran.yolov8tflite.data.ArcheryDatabase.getDatabase(this@MainActivity)
                    java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                        val session = db.archeryDao().getSession(sessionId)
                        if (session != null) {
                            val rounds = db.archeryDao().getRoundsForSession(sessionId)
                            val nextRoundNum = rounds.size + 1
                            val scoresCsv = currentScores.joinToString(",") { it.score.toString() }
                            val coordsCsv = currentScores.joinToString(",") { "${it.nx}|${it.ny}" }
                            val totalRoundScore = currentScores.sumOf { it.score }
                            
                            val newRound = com.surendramaran.yolov8tflite.data.Round(
                                sessionId = sessionId,
                                roundNumber = nextRoundNum,
                                scoresCsv = scoresCsv,
                                coordsCsv = coordsCsv
                            )
                            db.archeryDao().insertRound(newRound)
                            
                            session.totalScore += totalRoundScore
                            db.archeryDao().updateSession(session)
                            
                            runOnUiThread {
                                val intent = Intent(this@MainActivity, ActiveSessionActivity::class.java).apply {
                                    putExtra("SESSION_ID", sessionId)
                                }
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
                }
            }

            btnToggleLabels.setOnClickListener {
                overlay.showLabels = !overlay.showLabels
                overlay.invalidate()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider  = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview =  Preview.Builder()
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

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            
            isCapturing = false
            processNextFrame()
        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) { startCamera() }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()){
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun loadBitmapFromAssets(fileName: String): Bitmap? {
        return try {
            val inputStream = assets.open(fileName)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.inferenceTime.text = "No target or arrows detected"
            binding.overlay.clear()

            isProcessingFrame = false
            if (!isCapturing) {
                frameHandler.postDelayed({ processNextFrame() }, 30)
            }
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            val boxesToDraw = if (!isCapturing) {
                boundingBoxes.filter { it.clsName.equals("arrow", ignoreCase = true) || it.clsName.equals("target_bounds", ignoreCase = true) || it.clsName.equals("bullseye", ignoreCase = true) }
            } else {
                boundingBoxes
            }
            binding.overlay.apply {
                setResults(boxesToDraw)
                invalidate()
            }

            if (isCapturing) {
                val bullseye = boundingBoxes.find { it.clsName.equals("bullseye", ignoreCase = true) }
                val targetBounds = boundingBoxes.find { it.clsName.equals("target_bounds", ignoreCase = true) }
                val arrows = boundingBoxes.filter { it.clsName.equals("arrow", ignoreCase = true) }

                val result = ScoringEngine.calculateScores(bullseye, targetBounds, arrows, targetType, expectedArrows)

                if (result.isSuccess) {
                    val scores = result.getOrNull() ?: emptyList()
                    currentScores = scores
                    displayScores(scores)
                    binding.inferenceTime.text = "Scoring complete! Total: ${scores.sumOf { it.score }}"
                } else {
                    binding.inferenceTime.text = result.exceptionOrNull()?.message
                    binding.layoutScores.removeAllViews()
                    binding.layoutScores.visibility = android.view.View.GONE
                }
            } else {
                val arrowCount = boundingBoxes.count { it.clsName.equals("arrow", ignoreCase = true) }
                val bullseyeCount = boundingBoxes.count { it.clsName.equals("bullseye", ignoreCase = true) }
                binding.inferenceTime.text = "Arrows: $arrowCount | Bullseye: $bullseyeCount (${inferenceTime}ms)"
            }

            isProcessingFrame = false
            if (!isCapturing) {
                frameHandler.postDelayed({ processNextFrame() }, 30)
            }
        }
    }

    private fun displayScores(scores: List<ArrowScore>) {
        binding.layoutScores.removeAllViews()
        binding.layoutScores.visibility = android.view.View.VISIBLE

        for (arrow in scores) {
            val tvScore = android.widget.TextView(this).apply {
                text = if (arrow.score == 0) "M" else arrow.score.toString()
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER

                val size = (48 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    setMargins(16, 0, 16, 0)
                }

                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(arrow.colorHex))
                    setStroke(2, android.graphics.Color.parseColor("#333333"))
                }
                background = bg

                setTextColor(if (arrow.colorHex == "#000000" || arrow.colorHex == "#FF0000" || arrow.colorHex == "#00B4FF") {
                    android.graphics.Color.WHITE
                } else {
                    android.graphics.Color.BLACK
                })
            }
            binding.layoutScores.addView(tvScore)
        }
    }
}
