package com.surendramaran.yolov8tflite

import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.surendramaran.yolov8tflite.data.ArcheryDatabase
import com.surendramaran.yolov8tflite.data.Session

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val tvAssist = findViewById<TextView>(R.id.tvAssist)
        tvAssist.post {
            val width = tvAssist.width.toFloat()
            if (width > 0) {
                val textShader = LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(Color.parseColor("#1A4FFF"), Color.parseColor("#8E1EFF")),
                    null, Shader.TileMode.CLAMP
                )
                tvAssist.paint.shader = textShader
                tvAssist.invalidate()
            }
        }

        val cardTarget = findViewById<LinearLayout>(R.id.cardTarget)
        val cardForm = findViewById<LinearLayout>(R.id.cardForm)
        val cardHistory = findViewById<LinearLayout>(R.id.cardHistory)
        val cardChallengeAi = findViewById<LinearLayout>(R.id.cardChallengeAi)

        cardChallengeAi.setOnClickListener {
            val intent = Intent(this, DuelGameModeActivity::class.java)
            startActivity(intent)
        }

        cardTarget.setOnClickListener {
            showTargetSetupDialog()
        }

        cardForm.setOnClickListener {
            val intent = Intent(this, PoseActivity::class.java)
            startActivity(intent)
        }

        cardHistory.setOnClickListener {
            val intent = Intent(this, TrainingHistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showTargetSetupDialog() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.layout_bottom_sheet_setup, null)
        bottomSheetDialog.setContentView(view)

        var selectedTarget = "6-Ring"
        var selectedArrows = 3

        val card6Ring = view.findViewById<LinearLayout>(R.id.card_6_ring)
        val card10Ring = view.findViewById<LinearLayout>(R.id.card_10_ring)
        val btn3Arrows = view.findViewById<TextView>(R.id.btn_3_arrows)
        val btn6Arrows = view.findViewById<TextView>(R.id.btn_6_arrows)
        val btnStart = view.findViewById<android.widget.Button>(R.id.btn_start_analysis)

        fun updateUI() {
            card6Ring.setBackgroundResource(if (selectedTarget == "6-Ring") R.drawable.bg_card_target else R.drawable.bg_card_unselected)
            card10Ring.setBackgroundResource(if (selectedTarget == "10-Ring") R.drawable.bg_card_target else R.drawable.bg_card_unselected)
            
            btn3Arrows.setBackgroundResource(if (selectedArrows == 3) R.drawable.bg_card_target else R.drawable.bg_card_unselected)
            btn6Arrows.setBackgroundResource(if (selectedArrows == 6) R.drawable.bg_card_target else R.drawable.bg_card_unselected)
        }

        card6Ring.setOnClickListener { selectedTarget = "6-Ring"; updateUI() }
        card10Ring.setOnClickListener { selectedTarget = "10-Ring"; updateUI() }
        btn3Arrows.setOnClickListener { selectedArrows = 3; updateUI() }
        btn6Arrows.setOnClickListener { selectedArrows = 6; updateUI() }

        btnStart.setOnClickListener {
            val db = ArcheryDatabase.getDatabase(this)
            val session = Session(
                targetType = selectedTarget,
                expectedArrows = selectedArrows
            )
            
            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                val sessionId = db.archeryDao().insertSession(session)
                
                runOnUiThread {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("TARGET_TYPE", selectedTarget)
                        putExtra("EXPECTED_ARROWS", selectedArrows)
                        putExtra("SESSION_ID", sessionId)
                    }
                    bottomSheetDialog.dismiss()
                    startActivity(intent)
                }
            }
        }

        bottomSheetDialog.show()
    }

    private fun showComingSoonDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle("🚀 $title")
            .setMessage(message)
            .setPositiveButton("Got it") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
