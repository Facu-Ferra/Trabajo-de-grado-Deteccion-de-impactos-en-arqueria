package com.surendramaran.yolov8tflite

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.surendramaran.yolov8tflite.data.ArcheryDatabase
import com.surendramaran.yolov8tflite.data.Session
import java.util.concurrent.Executors

class DuelResolutionActivity : AppCompatActivity() {

    private var opponentId: Int = 1
    private var opponentName: String = "Bot"
    private var botTotalScore: Int = 0
    private var botScores: ArrayList<Int> = arrayListOf()
    private var targetType: String = "10-Ring"

    private lateinit var layoutUserArrows: LinearLayout
    private lateinit var tvUserTotal: TextView
    private lateinit var tvScanHint: TextView
    private lateinit var ivCameraIcon: View
    private lateinit var layoutResultOverlay: View
    private lateinit var tvWinnerAnnouncement: TextView

    private val startCameraForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val userScores = result.data?.getIntArrayExtra("USER_SCORES")
            if (userScores != null) {
                processUserScores(userScores.toList())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_duel_resolution)

        opponentId = intent.getIntExtra("OPPONENT_ID", 1)
        opponentName = intent.getStringExtra("OPPONENT_NAME") ?: "Bot"
        botTotalScore = intent.getIntExtra("BOT_TOTAL", 0)
        botScores = intent.getIntegerArrayListExtra("BOT_SCORES") ?: arrayListOf()
        targetType = intent.getStringExtra("TARGET_TYPE") ?: "10-Ring"

        findViewById<TextView>(R.id.tv_bot_name).text = opponentName.uppercase()
        findViewById<TextView>(R.id.tv_bot_total).text = botTotalScore.toString()
        val layoutBotArrows = findViewById<LinearLayout>(R.id.layout_bot_arrows)
        drawArrowCircles(layoutBotArrows, botScores)

        layoutUserArrows = findViewById(R.id.layout_user_arrows)
        tvUserTotal = findViewById(R.id.tv_user_total)
        tvScanHint = findViewById(R.id.tv_scan_hint)
        ivCameraIcon = findViewById(R.id.iv_camera_icon)
        layoutResultOverlay = findViewById(R.id.layout_result_overlay)
        tvWinnerAnnouncement = findViewById(R.id.tv_winner_announcement)

        findViewById<View>(R.id.layout_user_side).setOnClickListener {
            // Only allow clicking if we haven't resolved yet
            if (tvUserTotal.text == "?") {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("IS_DUEL_CAMERA", true)
                    putExtra("EXPECTED_ARROWS", 3)
                    putExtra("TARGET_TYPE", targetType)
                }
                startCameraForResult.launch(intent)
            }
        }

        findViewById<Button>(R.id.btn_finish_duel).setOnClickListener {
            // Save duel result to DB and go to history or main menu
            saveDuelToDatabase()
        }
    }

    private fun processUserScores(userScores: List<Int>) {
        // Hide camera prompts
        tvScanHint.visibility = View.GONE
        ivCameraIcon.visibility = View.GONE

        // Show scores
        val userTotal = userScores.sum()
        tvUserTotal.text = userTotal.toString()
        drawArrowCircles(layoutUserArrows, userScores)

        // Determine winner
        if (userTotal > botTotalScore) {
            tvWinnerAnnouncement.text = "YOU WIN!"
            tvWinnerAnnouncement.setTextColor(Color.parseColor("#4CAF50"))
        } else if (userTotal < botTotalScore) {
            tvWinnerAnnouncement.text = "BOT WINS!"
            tvWinnerAnnouncement.setTextColor(Color.parseColor("#F44336"))
        } else {
            tvWinnerAnnouncement.text = "DRAW!"
            tvWinnerAnnouncement.setTextColor(Color.parseColor("#FFD700"))
        }

        // Show overlay after a short delay for dramatic effect
        tvUserTotal.postDelayed({
            layoutResultOverlay.visibility = View.VISIBLE
        }, 1500)
    }

    private fun saveDuelToDatabase() {
        val userScore = tvUserTotal.text.toString().toIntOrNull() ?: 0
        val session = Session(
            targetType = targetType,
            expectedArrows = 3,
            totalScore = userScore,
            isDuel = true,
            aiOpponentId = opponentId,
            aiTotalScore = botTotalScore
        )

        val db = ArcheryDatabase.getDatabase(this)
        Executors.newSingleThreadExecutor().execute {
            db.archeryDao().insertSession(session)
            
            runOnUiThread {
                val intent = Intent(this, MenuActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun drawArrowCircles(layout: LinearLayout, scores: List<Int>) {
        layout.removeAllViews()
        for (score in scores) {
            val colorHex = getRingColor(score)
            
            val tvScore = TextView(this).apply {
                text = if (score == 0) "M" else score.toString()
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER

                val size = (32 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(4, 0, 4, 0)
                }

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(colorHex))
                    setStroke(2, Color.parseColor("#333333"))
                }

                setTextColor(if (colorHex == "#000000" || colorHex == "#FF0000" || colorHex == "#00B4FF") {
                    Color.WHITE
                } else {
                    Color.BLACK
                })
            }
            layout.addView(tvScore)
        }
    }

    private fun getRingColor(score: Int): String {
        return when (score) {
            10, 9 -> "#FFD700"
            8, 7 -> "#FF0000"
            6, 5 -> "#00B4FF"
            4, 3 -> "#000000"
            2, 1 -> "#FFFFFF"
            else -> "#757575"
        }
    }
}
