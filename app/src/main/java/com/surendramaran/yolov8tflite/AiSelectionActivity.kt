package com.surendramaran.yolov8tflite

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.surendramaran.yolov8tflite.data.AiOpponent

class AiSelectionActivity : AppCompatActivity() {

    private val opponents = listOf(
        AiOpponent(1, "Facu", "Novice Archer", 1, false, 18.2, R.drawable.img_bot_facu),
        AiOpponent(2, "Flor", "Club Archer", 2, false, 23.4, R.drawable.img_bot_flor),
        AiOpponent(3, "Kai", "Regional Archer", 3, false, 27.1, R.drawable.img_bot_kai),
        AiOpponent(4, "Legolas", "Elf Prince", 4, false, 28.6, R.drawable.img_bot_legolas),
        AiOpponent(5, "Artemisa", "Deity", 5, true, 29.3, R.drawable.img_bot_artemisa),
        AiOpponent(6, "Robin Hood", "Legend", 5, true, 29.8, R.drawable.img_bot_robin)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_selection)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        val rv = findViewById<RecyclerView>(R.id.rv_opponents)
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = OpponentAdapter(opponents) { opponent ->
            // Para poder testear, saltamos el bloqueo Premium por ahora:
            /*
            if (opponent.isPremium) {
                showPremiumDialog()
            } else {
                startMatch(opponent)
            }
            */
            startMatch(opponent)
        }
    }
    
    private fun startMatch(opponent: AiOpponent) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.layout_bottom_sheet_target_only, null)
        bottomSheetDialog.setContentView(view)

        var selectedTarget = "10-Ring"

        val card10Ring = view.findViewById<LinearLayout>(R.id.card_10_ring)
        val card6Ring = view.findViewById<LinearLayout>(R.id.card_6_ring)
        val btnStart = view.findViewById<android.widget.Button>(R.id.btn_start_match)

        fun updateUI() {
            card10Ring.setBackgroundResource(if (selectedTarget == "10-Ring") R.drawable.bg_card_target else R.drawable.bg_card_unselected)
            card6Ring.setBackgroundResource(if (selectedTarget == "6-Ring") R.drawable.bg_card_target else R.drawable.bg_card_unselected)
        }

        card10Ring.setOnClickListener { selectedTarget = "10-Ring"; updateUI() }
        card6Ring.setOnClickListener { selectedTarget = "6-Ring"; updateUI() }

        btnStart.setOnClickListener {
            val intent = Intent(this, MatchPlayActivity::class.java).apply {
                putExtra("OPPONENT_ID", opponent.id)
                putExtra("OPPONENT_NAME", opponent.name)
                putExtra("OPPONENT_SCORE", opponent.averageScore)
                putExtra("TARGET_TYPE", selectedTarget)
            }
            bottomSheetDialog.dismiss()
            startActivity(intent)
        }

        bottomSheetDialog.show()
    }

    private fun showPremiumDialog() {
        AlertDialog.Builder(this)
            .setTitle("👑 Premium Opponent")
            .setMessage("Upgrade to Premium to challenge Elite and Legendary archers!")
            .setPositiveButton("Got it") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private inner class OpponentAdapter(
        private val items: List<AiOpponent>,
        private val onClick: (AiOpponent) -> Unit
    ) : RecyclerView.Adapter<OpponentAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPlaceholder: ImageView = view.findViewById(R.id.iv_placeholder)
            val ivAvatar: ImageView = view.findViewById(R.id.iv_avatar)
            val tvName: TextView = view.findViewById(R.id.tv_name)
            val tvTitle: TextView = view.findViewById(R.id.tv_title)
            val tvLevel: TextView = view.findViewById(R.id.tv_level)
            val tvAvgScore: TextView = view.findViewById(R.id.tv_avg_score)
            val layoutStars: LinearLayout = view.findViewById(R.id.layout_stars)
            val overlayPremium: View = view.findViewById(R.id.overlay_premium)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_item_ai_opponent, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val opponent = items[position]
            holder.tvName.text = opponent.name
            holder.tvTitle.text = opponent.title
            holder.tvLevel.text = opponent.id.toString()
            holder.tvAvgScore.text = String.format("%.1f", opponent.averageScore)

            if (opponent.avatarRes != null) {
                holder.ivAvatar.setImageResource(opponent.avatarRes)
                holder.ivAvatar.visibility = View.VISIBLE
                holder.ivPlaceholder.visibility = View.GONE
            } else {
                holder.ivAvatar.visibility = View.GONE
                holder.ivPlaceholder.visibility = View.VISIBLE
            }

            // Estrellas
            holder.layoutStars.removeAllViews()
            for (i in 1..5) {
                val star = TextView(holder.itemView.context).apply {
                    text = "★"
                    textSize = 14f
                    setTextColor(if (i <= opponent.difficultyStars) Color.parseColor("#4D88FF") else Color.parseColor("#1A2035"))
                }
                holder.layoutStars.addView(star)
            }

            if (opponent.isPremium) {
                holder.overlayPremium.visibility = View.VISIBLE
            } else {
                holder.overlayPremium.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(opponent) }
        }

        override fun getItemCount(): Int = items.size
    }
}
