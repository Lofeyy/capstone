package com.capstone.pomodoro

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        val toolbar: Toolbar = findViewById(R.id.custom_toolbar)
        setSupportActionBar(toolbar)
        val backButton: ImageButton = findViewById(R.id.back_button)
        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val titleText: TextView = findViewById(R.id.title_text)

        backButton.setOnClickListener {
            // Handle back button action
            onBackPressed() // Or you can finish the activity if desired
        }

        // Settings button click listener
        settingsButton.setOnClickListener {
            // Open settings activity or dialog
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
}