package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class DuelGameModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_duel_mode)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.cardDuelMode).setOnClickListener {
            val intent = Intent(this, AiSelectionActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.cardTournamentMode).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("👑 Tournament Mode")
                .setMessage("Upgrade to Premium to unlock Tournament Mode and climb the ranks!")
                .setPositiveButton("Got it") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}
