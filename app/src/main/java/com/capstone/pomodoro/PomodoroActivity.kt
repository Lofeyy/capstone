package com.capstone.pomodoro

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PomodoroActivity : AppCompatActivity() {

    private lateinit var focusTextView: TextView
    private lateinit var iconImageView: ImageView
    private lateinit var pomodoroTimerMinutes: TextView
    private lateinit var pomodoroTimerSeconds: TextView
    private lateinit var academicTask: TextView
    private lateinit var startButton: ImageButton

    private var timer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private lateinit var database: DatabaseReference // Firebase database reference

    // Session management variables
    private var sessionCount = 0
    private var totalSessions = 0
    private var totalTimeInMillis: Long = 0
    private var pomodoroDuration: Long = 25 * 60 * 1000 // 25 minutes in milliseconds
    private var shortBreakDuration: Long = 5 * 60 * 1000 // 5 minutes break in milliseconds
    private var remainingSessionTime: Long = 0
    private var isTimerRunning = false
    private var taskId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pomodoro)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase database reference
        database = FirebaseDatabase.getInstance().reference.child("tasks")

        // Initialize views
        focusTextView = findViewById(R.id.focusTextView)
        iconImageView = findViewById(R.id.iconImageView)
        pomodoroTimerMinutes = findViewById(R.id.pomodoroTimerMinutes)
        pomodoroTimerSeconds = findViewById(R.id.pomodoroTimerSeconds)
        startButton = findViewById(R.id.startButton)
        academicTask = findViewById(R.id.taskTitle)
        taskId = intent.getStringExtra("TASK_ID").toString()
        Log.d("UpdateStatus", "Status updated successfully for task ID: $taskId")

        // Get the task duration from the intent
        val taskDuration = intent.getStringExtra("TASK_DURATION")?.toLongOrNull() ?: 25 // Default to 25 minutes

        // Determine the total time and split into sessions
        if (taskDuration > 25) {
            totalSessions = 2 // 2 sessions (25 + remaining time)
            remainingSessionTime = (taskDuration - 25) * 60 * 1000 // Remaining time for second session in milliseconds
        } else {
            totalSessions = 1 // Only 1 session
            remainingSessionTime = 0 // No remaining time
        }

        // Set the total time for the first session (25 minutes)
        totalTimeInMillis = pomodoroDuration
        timeLeftInMillis = totalTimeInMillis

        // Set initial state
        updateFocusState()

        // Set click listeners
        startButton.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        val taskName = intent.getStringExtra("ACADEMIC_TASK") ?: "Pomodoro" // Default if null
        val priority = intent.getStringExtra("PRIORITY") ?: "No Prio" // Default to Low if not provided


        // Set the task title
        academicTask.text = taskName

        // Set the priority indicator color based on the priority
        val priorityIndicator = findViewById<View>(R.id.priorityIndicator)
        when (priority) {
            "Low" -> priorityIndicator.setBackgroundResource(R.drawable.circle_low_priority)
            "Medium" -> priorityIndicator.setBackgroundResource(R.drawable.circle_medium_priority)
            "High" -> priorityIndicator.setBackgroundResource(R.drawable.circle_high_priority)
            else -> priorityIndicator.setBackgroundResource(R.drawable.circle_shape) // Default resource if priority is unrecognized
        }
    }

    private fun startTimer() {
        timer?.cancel()

        // Start the service in the foreground
        val intent = Intent(this, PomodoroService::class.java).apply {
            putExtra("TIME_LEFT", timeLeftInMillis) // Pass the remaining time
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        isTimerRunning = true // Set the timer running state to true

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimer()
            }

            override fun onFinish() {
                updateTimer()
                if (sessionCount < totalSessions - 1) {
                    sessionCount++
                    startNextSession()
                } else {
                    val trimmedTaskId = taskId.trim()
                    updateTaskStatus(trimmedTaskId, "done")
                    stopService(intent) // Stop the service when the timer finishes
                }
            }
        }.start()

        startButton.setImageResource(R.drawable.ic_pause) // Change icon to pause
    }

    private fun startNextSession() {
        if (sessionCount % 2 == 0) { // Work session
            timeLeftInMillis = if (sessionCount == totalSessions - 1) remainingSessionTime else pomodoroDuration
            updateFocusState() // Update to work state
        } else { // Break session
            timeLeftInMillis = shortBreakDuration
            updateBreakState() // Update to break state
        }
        updateTimer() // Update UI with the new timer values
        startTimer() // Start the timer for the next session
    }

    private fun pauseTimer() {
        timer?.cancel()
        isTimerRunning = false
        showPauseDialog()
    }

    private fun updateTimer() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        pomodoroTimerMinutes.text = minutes.toString()
        pomodoroTimerSeconds.text = String.format("%02d", seconds)
    }

    private fun updateFocusState() {
        focusTextView.text = "Focus"
        iconImageView.setImageResource(R.drawable.brain)
    }

    private fun updateBreakState() {
        focusTextView.text = "Break"
        iconImageView.setImageResource(R.drawable.coffee)
    }
    private fun showPauseDialogYesOnly() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Pause")
        builder.setMessage("Are you done?")
        builder.setPositiveButton("Yes") { dialog, _ ->
            val trimmedTaskId = taskId.trim()
            Log.d("UpdateTask", "Attempting to update status for task ID: '$trimmedTaskId'")
            updateTaskStatus(trimmedTaskId, "done")

            dialog.dismiss()
            finish()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
            startButton.setImageResource(R.drawable.ic_arrow)
        }
        builder.create().show()
    }
    private fun showPauseDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Pause")
        builder.setMessage("Are you done?")
        builder.setPositiveButton("Yes") { dialog, _ ->
            val trimmedTaskId = taskId.trim()
            Log.d("UpdateTask", "Attempting to update status for task ID: '$trimmedTaskId'")
            updateTaskStatus(trimmedTaskId, "done")

            dialog.dismiss()
            finish()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
            startButton.setImageResource(R.drawable.ic_arrow)
        }
        builder.create().show()
    }

    // Method to update the task status in Firebase
    private fun updateTaskStatus(taskId: String, status: String) {
        val trimmedTaskId = taskId.trim()
        Log.d("UpdateStatus", "Attempting to update status for task ID: '$trimmedTaskId'")

        val taskRef = database.child("tasks").child(trimmedTaskId)

        // Check if the task exists
        taskRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    Log.d("UpdateStatus", "Task exists for ID: $trimmedTaskId")
                    taskRef.child("status").setValue(status)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("UpdateStatus", "Status updated successfully for task ID: $trimmedTaskId")
                            } else {
                                Log.e("UpdateStatus", "Failed to update status for task ID: $trimmedTaskId", task.exception)
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("UpdateStatus", "Error updating status for task ID: $trimmedTaskId: ${exception.message}")
                        }
                } else {
                    Log.e("UpdateStatus", "Task does not exist for ID: $trimmedTaskId")
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("UpdateStatus", "Database error: ${databaseError.message}")
            }
        })
    }


}


