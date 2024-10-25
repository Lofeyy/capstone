package com.capstone.pomodoro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var pomodoroInput: EditText
    private lateinit var shortBreakInput: EditText
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // Initialize Firebase Database reference
        database = FirebaseDatabase.getInstance().reference

        // Initialize views
        val toolbar: Toolbar = findViewById(R.id.custom_toolbar)
        setSupportActionBar(toolbar)

        val saveButton: MaterialButton = findViewById(R.id.saveButton)
        val logoutButton: MaterialButton = findViewById(R.id.logutButton)  // Logout button

        pomodoroInput = findViewById(R.id.editTextCustom)
        shortBreakInput = findViewById(R.id.shortBreak)

        // Load existing settings
        loadSettingsFromFirebase()
        val backButton: ImageButton = findViewById(R.id.back_button)
        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        // Back button listener
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Save button listener
        saveButton.setOnClickListener {
            saveSettingsToFirebase()
        }

        // Logout button listener
        logoutButton.setOnClickListener {
            logoutUser()
        }
    }

    private fun loadSettingsFromFirebase() {
        // Get the current user's ID from Firebase Auth
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not authenticated. Please log in.", Toast.LENGTH_SHORT).show()
            return
        }

        // Retrieve the user settings from Firebase
        database.child("user_settings").orderByChild("user_id").equalTo(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (settingSnapshot in snapshot.children) {
                        val pomodoroDuration = settingSnapshot.child("pomodoroDuration").getValue(Long::class.java) ?: 0
                        val shortBreakDuration = settingSnapshot.child("shortBreakDuration").getValue(Long::class.java) ?: 0

                        // Log the durations for debugging
                        Log.d("SettingsActivity", "Pomodoro Duration (ms): $pomodoroDuration, Short Break Duration (ms): $shortBreakDuration")

                        // Convert milliseconds back to minutes for display
                        pomodoroInput.setText((pomodoroDuration / 60000).toString())
                        shortBreakInput.setText((shortBreakDuration / 60000).toString())
                        break // Exit after the first entry
                    }
                } else {
                    Toast.makeText(this@SettingsActivity, "No settings found for this user.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SettingsActivity, "Failed to load settings: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveSettingsToFirebase() {
        // Get input from the EditTexts
        val pomodoroText = pomodoroInput.text.toString()
        val shortBreakText = shortBreakInput.text.toString()

        // Validate input (make sure it's not empty or invalid)
        val pomodoroMinutes = pomodoroText.toLongOrNull()
        val shortBreakMinutes = shortBreakText.toLongOrNull()

        if (pomodoroMinutes == null || shortBreakMinutes == null || pomodoroMinutes <= 0 || shortBreakMinutes <= 0) {
            // Show error message if input is invalid
            Toast.makeText(this, "Please enter valid numbers for both Pomodoro and Break durations.", Toast.LENGTH_SHORT).show()
            return
        }

        // Get the current user's ID from Firebase Auth
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not authenticated. Please log in.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check for existing settings before saving
        database.child("user_settings").orderByChild("user_id").equalTo(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (settingSnapshot in snapshot.children) {
                        // Update existing settings
                        val settings = mapOf(
                            "pomodoroDuration" to (pomodoroMinutes!! * 60 * 1000), // Store as milliseconds
                            "shortBreakDuration" to (shortBreakMinutes!! * 60 * 1000) // Store as milliseconds
                        )

                        // Update the existing settings
                        database.child("user_settings").child(settingSnapshot.key!!).updateChildren(settings)
                            .addOnSuccessListener {
                                Toast.makeText(this@SettingsActivity, "Settings updated!", Toast.LENGTH_SHORT).show()
                                finish() // Close the activity and return to the previous screen
                            }
                            .addOnFailureListener { exception ->
                                Toast.makeText(this@SettingsActivity, "Failed to update settings: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        return // Exit after updating
                    }
                } else {
                    // Create new settings if none exist
                    val userSettingsId = database.child("user_settings").push().key ?: return
                    val settings = mapOf(
                        "userSettingsId" to userSettingsId,
                        "user_id" to userId,
                        "pomodoroDuration" to (pomodoroMinutes!! * 60 * 1000), // Store as milliseconds
                        "shortBreakDuration" to (shortBreakMinutes!! * 60 * 1000) // Store as milliseconds
                    )

                    // Save the settings to Firebase under the unique user ID
                    database.child("user_settings").child(userSettingsId).setValue(settings)
                        .addOnSuccessListener {
                            Toast.makeText(this@SettingsActivity, "Settings saved!", Toast.LENGTH_SHORT).show()
                            finish() // Close the activity and return to the previous screen
                        }
                        .addOnFailureListener { exception ->
                            Toast.makeText(this@SettingsActivity, "Failed to save settings: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SettingsActivity, "Failed to check existing settings: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun logoutUser() {
        auth.signOut()  // Sign out from Firebase

        // Redirect to the login activity (assuming LoginActivity is your login screen)
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()  // Finish current activity to remove it from the back stack

        // Show feedback
        Toast.makeText(this, "You have been logged out", Toast.LENGTH_SHORT).show()
    }
}
