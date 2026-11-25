package com.surendramaran.yolov8tflite

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MatchPlayActivity : AppCompatActivity() {

    private var opponentId: Int = 1
    private var opponentName: String = ""
    private var opponentAverage: Double = 0.0
    private var targetType: String = "10-Ring"

    private var currentArrow = 1
    private var isUserTurn = true // True = User, False = Bot
    private var matchStarted = false

    private lateinit var tvTimer: TextView
    private lateinit var tvTurnIndicator: TextView
    private lateinit var tvArrowCount: TextView
    private lateinit var btnNext: Button
    private lateinit var layoutBotArrows: LinearLayout
    private lateinit var layoutUserArrows: LinearLayout
    private lateinit var ivBotMatch: com.google.android.material.imageview.ShapeableImageView

    private var countDownTimer: CountDownTimer? = null
    
    private val botScores = mutableListOf<Int>()
    private val userShots = mutableListOf<String>() // Solo guardamos "?", el score real se calcula al final

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_play)

        opponentId = intent.getIntExtra("OPPONENT_ID", 1)
        opponentName = intent.getStringExtra("OPPONENT_NAME") ?: "Bot"
        opponentAverage = intent.getDoubleExtra("OPPONENT_SCORE", 20.0)
        targetType = intent.getStringExtra("TARGET_TYPE") ?: "10-Ring"

        findViewById<TextView>(R.id.tv_match_header).text = "VS $opponentName"
        findViewById<TextView>(R.id.tv_bot_name_score).text = opponentName

        tvTimer = findViewById(R.id.tv_timer)
        tvTurnIndicator = findViewById(R.id.tv_turn_indicator)
        tvArrowCount = findViewById(R.id.tv_arrow_count)
        btnNext = findViewById(R.id.btn_next_action)
        layoutBotArrows = findViewById(R.id.layout_bot_arrows)
        layoutUserArrows = findViewById(R.id.layout_user_arrows)
        ivBotMatch = findViewById(R.id.iv_bot_match)

        updateUiState()
        
        val avatarRes = when (opponentName) {
            "Flor" -> R.drawable.img_bot_flor_full
            "Facu" -> R.drawable.img_bot_facu_full
            "Kai" -> R.drawable.img_bot_kai
            "Artemisa" -> R.drawable.img_bot_artemisa_full
            "Robin Hood" -> R.drawable.img_bot_robin_full
            "Legolas" -> R.drawable.img_bot_legolas_full
            else -> null
        }
        
        // Initial Ready State
        if (avatarRes != null) {
            ivBotMatch.setImageResource(avatarRes)
            ivBotMatch.visibility = View.VISIBLE
            tvTimer.visibility = View.GONE
        } else {
            ivBotMatch.visibility = View.GONE
            tvTimer.visibility = View.VISIBLE
            tvTimer.textSize = 32f
            tvTimer.text = "READY?"
        }

        btnNext.setOnClickListener {
            if (!matchStarted) {
                matchStarted = true
                startGetReadyTimer()
            } else if (currentArrow > 3) {
                goToResolution()
            } else if (isUserTurn) {
                endUserTurn()
            }
        }
    }

    private fun startGetReadyTimer() {
        ivBotMatch.visibility = View.GONE
        tvTimer.visibility = View.VISIBLE
        tvTimer.textSize = 64f
        tvTurnIndicator.text = "GET READY"
        tvTurnIndicator.setTextColor(Color.parseColor("#FF9800"))
        btnNext.visibility = View.GONE
        
        startTimer(5) {
            startTurn()
        }
    }

    private fun startTurn() {
        tvArrowCount.text = "Arrow $currentArrow of 3"
        tvTimer.textSize = 64f
        
        if (isUserTurn) {
            ivBotMatch.visibility = View.GONE
            tvTimer.visibility = View.VISIBLE
            tvTurnIndicator.text = "YOUR TURN"
            tvTurnIndicator.setTextColor(Color.parseColor("#4CAF50"))
            btnNext.text = "Arrow Shot (Next)"
            btnNext.visibility = View.VISIBLE
            startTimer(30) { endUserTurn() }
        } else {
            val avatarRes = when (opponentName) {
                "Flor" -> R.drawable.img_bot_flor_full
                "Facu" -> R.drawable.img_bot_facu_full
                "Artemisa" -> R.drawable.img_bot_artemisa_full
                "Robin Hood" -> R.drawable.img_bot_robin_full
                "Legolas" -> R.drawable.img_bot_legolas_full
                else -> null
            }
            if (avatarRes != null) {
                ivBotMatch.setImageResource(avatarRes)
                ivBotMatch.visibility = View.VISIBLE
                tvTimer.visibility = View.GONE
            } else {
                ivBotMatch.visibility = View.GONE
                tvTimer.visibility = View.VISIBLE
            }
            
            tvTurnIndicator.text = "${opponentName.uppercase()}'S TURN"
            tvTurnIndicator.setTextColor(Color.parseColor("#F44336"))
            btnNext.visibility = View.GONE
            startTimer(3) { endBotTurn() }
        }
    }

    private fun startTimer(seconds: Int, onFinish: () -> Unit) {
        countDownTimer?.cancel()
        tvTimer.text = seconds.toString()
        countDownTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvTimer.text = (millisUntilFinished / 1000).toString()
            }
            override fun onFinish() {
                tvTimer.text = "0"
                onFinish()
            }
        }.start()
    }

    private fun endUserTurn() {
        countDownTimer?.cancel()
        userShots.add("?")
        drawArrowCircles(layoutUserArrows, userShots)
        isUserTurn = false
        startTurn()
    }

    private fun endBotTurn() {
        countDownTimer?.cancel()
        // Simulate bot shot
        val botScore = simulateBotArrow(opponentAverage)
        botScores.add(botScore)
        drawArrowCircles(layoutBotArrows, botScores.map { it.toString() })
        
        currentArrow++
        isUserTurn = true
        
        if (currentArrow > 3) {
            matchOver()
        } else {
            startGetReadyTimer()
        }
    }

    private fun matchOver() {
        ivBotMatch.visibility = View.GONE
        tvTimer.visibility = View.VISIBLE
        tvTimer.textSize = 64f
        tvTurnIndicator.text = "SET COMPLETE!"
        tvTurnIndicator.setTextColor(Color.parseColor("#FFD700"))
        tvTimer.text = "🏁"
        btnNext.text = "Reveal Scores"
        btnNext.visibility = View.VISIBLE
    }

    private fun goToResolution() {
        val botTotal = botScores.sum()
        val intent = Intent(this, DuelResolutionActivity::class.java).apply {
            putExtra("OPPONENT_ID", opponentId)
            putExtra("OPPONENT_NAME", opponentName)
            putExtra("BOT_TOTAL", botTotal)
            putIntegerArrayListExtra("BOT_SCORES", ArrayList(botScores))
            putExtra("TARGET_TYPE", targetType)
        }
        startActivity(intent)
        finish()
    }

    private fun simulateBotArrow(averageSetScore: Double): Int {
        if (opponentName == "Robin Hood") {
            return if (Math.random() > 0.3) 10 else 9 // 70% chance of 10, 30% chance of 9
        }

        // Average per arrow = averageSetScore / 3
        val avgArrow = averageSetScore / 3.0
        // Basic simulation using some variance
        val variance = (Math.random() * 4) - 2 // -2 to +2
        var shot = (avgArrow + variance).roundToInt()
        shot = max(0, min(10, shot))
        
        if (targetType == "6-Ring" && shot < 5) {
            shot = 0
        }
        
        return shot
    }

    private fun updateUiState() {
        tvTimer.text = "--"
        tvTurnIndicator.text = "READY?"
        tvTurnIndicator.setTextColor(Color.WHITE)
        btnNext.text = "Start Match"
    }
    
    private fun drawArrowCircles(layout: LinearLayout, scores: List<String>) {
        layout.removeAllViews()
        for (scoreStr in scores) {
            val score = scoreStr.toIntOrNull() ?: -1 // -1 for "?"
            val colorHex = if (scoreStr == "?") "#757575" else getRingColor(score)
            
            val displayStr = if (score == 0) "M" else scoreStr

            val tvScore = TextView(this).apply {
                text = displayStr
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

                setTextColor(if (colorHex == "#000000" || colorHex == "#FF0000" || colorHex == "#00B4FF" || colorHex == "#757575") {
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

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
