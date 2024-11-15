package com.capstone.pomodoro

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ViewTaskActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference

    // Define UI elements
    private lateinit var academicTaskTextView: TextView
    private lateinit var priorityTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var estimatedPomodoroTextView: TextView
    private lateinit var actualPomodoroTextView: TextView
    private lateinit var estimatedDurationTextView: TextView  // Added for estimated duration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_task)

        // Initialize the UI elements
        academicTaskTextView = findViewById(R.id.academicTaskView)
        priorityTextView = findViewById(R.id.priorityTextView)
        statusTextView = findViewById(R.id.statustextview)
        dateTextView = findViewById(R.id.dateText)
        estimatedPomodoroTextView = findViewById(R.id.estimatedpomodorotextview)
        actualPomodoroTextView = findViewById(R.id.actualpomodoroTextview)  // Update this line to the correct ID
        estimatedDurationTextView = findViewById(R.id.estimatedDurationValue)  // Added this view

        // Get the task ID from the Intent
        val taskId = intent.getStringExtra("TASK_ID") ?: return
        Log.d("TaskActivity", "Retrieved taskId: $taskId")
        // Initialize Firebase database reference
        database = FirebaseDatabase.getInstance().reference

        // Fetch the task details from Firebase
        database.child("tasks").child(taskId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Extract task details from the snapshot
                val academicTask = snapshot.child("academicTask").value.toString()
                val priority = snapshot.child("priority").value.toString()
                val sessions = snapshot.child("sessions").value.toString()
                val status = snapshot.child("status").value.toString()
                val submissionDate = snapshot.child("submissionDate").value.toString()
                val suggestedDuration = snapshot.child("suggestedDuration").value.toString()

                // Log for debugging purposes
                Log.d("ViewTaskActivity", "Sessions: $sessions")
                Log.d("ViewTaskActivity", "Suggested Duration: $suggestedDuration")

                // Update the UI
                academicTaskTextView.text = academicTask
                priorityTextView.text = priority
                statusTextView.text = status
                dateTextView.text = submissionDate

                // Check if sessions is valid
                if (sessions.isNotEmpty() && sessions.contains("/")) {
                    val sessionParts = sessions.split("/")
                    if (sessionParts.size == 2) {
                        try {
                            val actualPomodoros = sessionParts[0].toInt()
                            val estimatedPomodoros = sessionParts[1].toInt()

                            // Calculate the actual duration (25 minutes per pomodoro)
                            val actualDuration = actualPomodoros * 25

                            // Update the pomodoro widgets
                            estimatedPomodoroTextView.text = "$estimatedPomodoros"
                            actualPomodoroTextView.text = "$actualPomodoros"

                        } catch (e: NumberFormatException) {
                            // Handle invalid number format
                            Toast.makeText(this, "Invalid session data format", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Invalid session format", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Session data is missing or malformed", Toast.LENGTH_SHORT).show()
                }

                // Handle empty or invalid suggestedDuration
                if (suggestedDuration.isNotEmpty() && suggestedDuration.isDigitsOnly()) {
                    estimatedDurationTextView.text = "$suggestedDuration mins"
                } else {
                    estimatedDurationTextView.text = "Estimated Duration: Not Available"
                }

            } else {
                // Handle case where task doesn't exist
                Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            // Handle any errors that occurred while fetching data
            Toast.makeText(this, "Error retrieving task data", Toast.LENGTH_SHORT).show()
        }
    }
}
