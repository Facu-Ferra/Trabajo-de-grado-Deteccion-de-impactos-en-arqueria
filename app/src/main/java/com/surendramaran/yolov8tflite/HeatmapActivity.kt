package com.surendramaran.yolov8tflite

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.surendramaran.yolov8tflite.data.ArcheryDatabase
import java.util.concurrent.Executors
import kotlin.math.*

class HeatmapActivity : AppCompatActivity() {

    private var sessionId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heatmap)

        sessionId = intent.getLongExtra("SESSION_ID", -1L)
        
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        loadHeatmap()
    }

    private fun loadHeatmap() {
        if (sessionId == -1L) return

        val db = ArcheryDatabase.getDatabase(this)
        Executors.newSingleThreadExecutor().execute {
            val session = db.archeryDao().getSession(sessionId)
            val rounds = db.archeryDao().getRoundsForSession(sessionId)
            val allSessions = db.archeryDao().getAllSessions()

            var previousAvg = 0f
            var foundPrev = false
            for (i in allSessions.indices) {
                if (allSessions[i].id == sessionId && i + 1 < allSessions.size) {
                    val prevSession = allSessions[i + 1]
                    val prevRounds = db.archeryDao().getRoundsForSession(prevSession.id)
                    val prevScores = prevRounds.flatMap { it.scoresCsv.split(",") }.mapNotNull { it.toIntOrNull() }
                    if (prevScores.isNotEmpty()) {
                        previousAvg = prevScores.average().toFloat()
                        foundPrev = true
                    }
                    break
                }
            }

            val validScores = mutableListOf<Int>()
            val validCoords = mutableListOf<Pair<Float, Float>>()

            for (round in rounds) {
                val scoreStrings = round.scoresCsv.split(",").filter { it.isNotEmpty() }
                val coordStrings = round.coordsCsv.split(",").filter { it.isNotEmpty() }
                
                for (i in scoreStrings.indices) {
                    val score = scoreStrings[i].toIntOrNull() ?: continue
                    validScores.add(score)
                    
                    if (i < coordStrings.size) {
                        val coordPair = coordStrings[i]
                        if (coordPair == "null" || !coordPair.contains("|")) continue
                        val parts = coordPair.split("|")
                        val nx = parts[0].toFloatOrNull() ?: continue
                        val ny = parts[1].toFloatOrNull() ?: continue
                        if (nx != 0f || ny != 0f) {
                            validCoords.add(Pair(nx, ny))
                        }
                    }
                }
            }

            val totalArrows = validScores.size
            val avgScore = if (totalArrows > 0) validScores.average().toFloat() else 0f
            val progression = if (foundPrev) avgScore - previousAvg else 0f
            
            // Calculate Consistency
            var consistency = 0
            if (validCoords.size > 1) {
                val cx = validCoords.sumOf { it.first.toDouble() } / validCoords.size
                val cy = validCoords.sumOf { it.second.toDouble() } / validCoords.size
                
                var varianceSum = 0.0
                for (coord in validCoords) {
                    val dx = coord.first - cx
                    val dy = coord.second - cy
                    varianceSum += (dx * dx + dy * dy)
                }
                val stdDev = sqrt(varianceSum / validCoords.size)
                val cScore = max(0.0, 100.0 - (stdDev * 150.0))
                consistency = cScore.toInt()
            } else if (validCoords.size == 1) {
                consistency = 100
            }

            runOnUiThread {
                if (session == null) return@runOnUiThread

                findViewById<TextView>(R.id.tv_stat_arrows).text = totalArrows.toString()
                findViewById<TextView>(R.id.tv_stat_avg).text = String.format("%.1f", avgScore)
                findViewById<TextView>(R.id.tv_stat_consistency).text = "${consistency}%"
                
                val tvProg = findViewById<TextView>(R.id.tv_stat_progression)
                if (foundPrev) {
                    val sign = if (progression > 0) "+" else ""
                    tvProg.text = "$sign${String.format("%.1f", progression)}"
                    tvProg.setTextColor(if (progression >= 0) Color.parseColor("#00FF00") else Color.parseColor("#FF0000"))
                } else {
                    tvProg.text = "-"
                }

                val ivTarget = findViewById<ImageView>(R.id.iv_heatmap_target)
                if (session.targetType == "6-Ring") {
                    ivTarget.setImageResource(R.drawable.ic_wireframe_6_ring)
                } else {
                    ivTarget.setImageResource(R.drawable.ic_wireframe_10_ring)
                }

                val dotsLayer = findViewById<FrameLayout>(R.id.heatmap_dots_layer)
                
                dotsLayer.post {
                    val width = dotsLayer.width.toFloat()
                    val height = dotsLayer.height.toFloat()
                    val cX = width / 2f
                    val cY = height / 2f
                    val totalRadius = width / 2f

                    for (coord in validCoords) {
                        val dotX = cX + (coord.first * totalRadius)
                        val dotY = cY + (coord.second * totalRadius)

                        val dotSize = (24 * resources.displayMetrics.density).toInt() // 24dp for glow
                        
                        val dotView = View(this@HeatmapActivity).apply {
                            layoutParams = FrameLayout.LayoutParams(dotSize, dotSize)
                            setBackgroundResource(R.drawable.bg_dot_glow)
                            x = dotX - (dotSize / 2f)
                            y = dotY - (dotSize / 2f)
                        }
                        
                        dotsLayer.addView(dotView)
                    }
                }
            }
        }
    }
}
