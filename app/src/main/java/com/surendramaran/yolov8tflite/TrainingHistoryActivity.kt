package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.surendramaran.yolov8tflite.data.ArcheryDatabase
import com.surendramaran.yolov8tflite.data.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class TrainingHistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private var sessionsList: MutableList<Session> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_training_history)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        rvHistory = findViewById(R.id.rv_history)
        rvHistory.layoutManager = LinearLayoutManager(this)

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        val db = ArcheryDatabase.getDatabase(this)
        Executors.newSingleThreadExecutor().execute {
            val sessions = db.archeryDao().getTrainingSessions()
            
            runOnUiThread {
                sessionsList.clear()
                sessionsList.addAll(sessions)
                rvHistory.adapter = HistoryAdapter(sessionsList, 
                    onClick = { session ->
                        val intent = Intent(this, ActiveSessionActivity::class.java).apply {
                            putExtra("SESSION_ID", session.id)
                        }
                        startActivity(intent)
                    },
                    onLongClick = { session, position ->
                        showDeleteConfirmationDialog(session, position)
                    }
                )
            }
        }
    }

    private fun showDeleteConfirmationDialog(session: Session, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Training Session")
            .setMessage("Are you sure you want to permanently delete this training session and all its arrows?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSession(session, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSession(session: Session, position: Int) {
        val db = ArcheryDatabase.getDatabase(this)
        Executors.newSingleThreadExecutor().execute {
            db.archeryDao().deleteSession(session.id)
            runOnUiThread {
                sessionsList.removeAt(position)
                rvHistory.adapter?.notifyItemRemoved(position)
                android.widget.Toast.makeText(this, "Session deleted", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class HistoryAdapter(
        private val sessions: List<Session>,
        private val onClick: (Session) -> Unit,
        private val onLongClick: (Session, Int) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.iv_target_icon)
            val tvDate: TextView = view.findViewById(R.id.tv_date)
            val tvInfo: TextView = view.findViewById(R.id.tv_arrows_info)
            val tvScore: TextView = view.findViewById(R.id.tv_session_score)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_item_history_session, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val session = sessions[position]
            
            holder.tvDate.text = dateFormat.format(Date(session.dateTimestamp))
            holder.tvInfo.text = "${session.expectedArrows} arrows per end"
            holder.tvScore.text = session.totalScore.toString()
            
            if (session.targetType == "10-Ring") {
                holder.ivIcon.setImageResource(R.drawable.ic_target_10_ring)
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_target_6_ring)
            }

            holder.itemView.setOnClickListener { onClick(session) }
            holder.itemView.setOnLongClickListener { 
                onLongClick(session, position)
                true // consumed
            }
        }

        override fun getItemCount(): Int = sessions.size
    }
}
