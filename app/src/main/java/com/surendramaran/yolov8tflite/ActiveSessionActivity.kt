package com.surendramaran.yolov8tflite

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.surendramaran.yolov8tflite.data.ArcheryDatabase
import com.surendramaran.yolov8tflite.data.Round
import java.util.concurrent.Executors

class ActiveSessionActivity : AppCompatActivity() {

    private var sessionId: Long = -1L
    private var targetType: String = "10-Ring"
    private var expectedArrows: Int = 3
    private lateinit var rvRounds: RecyclerView
    private lateinit var tvTotalScore: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_session)

        sessionId = intent.getLongExtra("SESSION_ID", -1L)
        if (sessionId == -1L) {
            finish()
            return
        }

        tvTotalScore = findViewById(R.id.tv_total_score)
        rvRounds = findViewById(R.id.rv_rounds)
        rvRounds.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btn_finish_session).setOnClickListener {
            // Regresar al menú principal
            val intent = Intent(this, MenuActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }

        findViewById<View>(R.id.btn_add_end).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("SESSION_ID", sessionId)
                putExtra("TARGET_TYPE", targetType)
                putExtra("EXPECTED_ARROWS", expectedArrows)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_view_heatmap).setOnClickListener {
            val intent = Intent(this, HeatmapActivity::class.java).apply {
                putExtra("SESSION_ID", sessionId)
            }
            startActivity(intent)
        }

        loadSessionData()
    }

    override fun onResume() {
        super.onResume()
        // Recargar datos por si volvimos de la cámara
        loadSessionData()
    }

    private fun loadSessionData() {
        val db = ArcheryDatabase.getDatabase(this)
        Executors.newSingleThreadExecutor().execute {
            val session = db.archeryDao().getSession(sessionId)
            val rounds = db.archeryDao().getRoundsForSession(sessionId)

            runOnUiThread {
                if (session != null) {
                    targetType = session.targetType
                    expectedArrows = session.expectedArrows
                    tvTotalScore.text = "Total Score: ${session.totalScore}"
                }
                rvRounds.adapter = RoundsAdapter(rounds,
                    onEditClick = { round ->
                        session?.let { showEditRoundDialog(round, it) }
                    },
                    onLongClick = { round ->
                        session?.let { showDeleteRoundDialog(round, it) }
                    }
                )
            }
        }
    }

    private fun showEditRoundDialog(round: Round, session: com.surendramaran.yolov8tflite.data.Session) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_round, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.tv_dialog_title).text = "Edit End ${round.roundNumber}"
        
        val layoutSpinners = dialogView.findViewById<LinearLayout>(R.id.layout_spinners_container)
        val scoresList = round.scoresCsv.split(",").filter { it.isNotEmpty() }
        
        val scoreOptions = arrayOf("10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "M")
        val spinners = mutableListOf<android.widget.Spinner>()

        for ((index, scoreStr) in scoresList.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
                gravity = Gravity.CENTER_VERTICAL
            }
            
            val label = TextView(this).apply {
                text = "Arrow ${index + 1}: "
                setTextColor(Color.WHITE)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val spinner = android.widget.Spinner(this).apply {
                adapter = android.widget.ArrayAdapter(this@ActiveSessionActivity, android.R.layout.simple_spinner_item, scoreOptions).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                val currentValue = if (scoreStr == "0") "M" else scoreStr
                val selectedIndex = scoreOptions.indexOf(currentValue)
                if (selectedIndex >= 0) setSelection(selectedIndex)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            
            spinners.add(spinner)
            row.addView(label)
            row.addView(spinner)
            layoutSpinners.addView(row)
        }

        dialogView.findViewById<View>(R.id.btn_cancel_edit).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btn_save_edit).setOnClickListener {
            val newScores = spinners.map { 
                val value = it.selectedItem.toString()
                if (value == "M") "0" else value
            }
            val newScoresCsv = newScores.joinToString(",")
            
            val oldRoundScore = scoresList.sumOf { it.toIntOrNull() ?: 0 }
            val newRoundScore = newScores.sumOf { it.toIntOrNull() ?: 0 }
            
            val db = ArcheryDatabase.getDatabase(this)
            Executors.newSingleThreadExecutor().execute {
                val updatedRound = round.copy(scoresCsv = newScoresCsv)
                db.archeryDao().updateRound(updatedRound)
                
                session.totalScore = session.totalScore - oldRoundScore + newRoundScore
                db.archeryDao().updateSession(session)
                
                runOnUiThread {
                    dialog.dismiss()
                    loadSessionData()
                }
            }
        }
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showDeleteRoundDialog(round: Round, session: com.surendramaran.yolov8tflite.data.Session) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete End")
            .setMessage("Are you sure you want to delete End ${round.roundNumber}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRound(round, session)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRound(round: Round, session: com.surendramaran.yolov8tflite.data.Session) {
        val db = ArcheryDatabase.getDatabase(this)
        Executors.newSingleThreadExecutor().execute {
            db.archeryDao().deleteRound(round.id)
            
            // Recalculate total score
            val roundScore = round.scoresCsv.split(",").filter { it.isNotEmpty() }.sumOf { it.toIntOrNull() ?: 0 }
            session.totalScore -= roundScore
            db.archeryDao().updateSession(session)
            
            loadSessionData() // Reload the list
        }
    }

    private inner class RoundsAdapter(
        private val rounds: List<Round>,
        private val onEditClick: (Round) -> Unit,
        private val onLongClick: (Round) -> Unit
    ) : RecyclerView.Adapter<RoundsAdapter.RoundViewHolder>() {

        inner class RoundViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvRoundNumber: TextView = view.findViewById(R.id.tv_round_number)
            val tvRoundScore: TextView = view.findViewById(R.id.tv_round_score)
            val layoutArrows: LinearLayout = view.findViewById(R.id.layout_arrows)
            val btnEdit: ImageView = view.findViewById(R.id.btn_edit_round)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoundViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_item_round, parent, false)
            return RoundViewHolder(view)
        }

        override fun onBindViewHolder(holder: RoundViewHolder, position: Int) {
            val round = rounds[position]
            holder.tvRoundNumber.text = "End ${round.roundNumber}"
            
            // scoresCsv = "10,9,0" (0 es Miss)
            val scoresList = round.scoresCsv.split(",").filter { it.isNotEmpty() }
            
            var roundTotal = 0
            holder.layoutArrows.removeAllViews()

            for (scoreStr in scoresList) {
                val score = scoreStr.toIntOrNull() ?: 0
                roundTotal += score
                
                val colorHex = getRingColor(score)
                val text = if (score == 0) "M" else score.toString()

                val tvScore = TextView(holder.itemView.context).apply {
                    this.text = text
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER

                    val size = (36 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        setMargins(8, 0, 8, 0)
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
                holder.layoutArrows.addView(tvScore)
            }

            holder.tvRoundScore.text = "Total: $roundTotal"
            holder.btnEdit.setOnClickListener { onEditClick(round) }
            holder.itemView.setOnLongClickListener {
                onLongClick(round)
                true
            }
        }

        override fun getItemCount(): Int = rounds.size
        
        private fun getRingColor(score: Int): String {
            return when (score) {
                10, 9 -> "#FFD700" // Amarillo/Oro
                8, 7 -> "#FF0000" // Rojo
                6, 5 -> "#00B4FF" // Azul
                4, 3 -> "#000000" // Negro
                2, 1 -> "#FFFFFF" // Blanco
                else -> "#757575" // Miss (Gris)
            }
        }
    }
}
