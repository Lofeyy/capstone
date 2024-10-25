package com.capstone.pomodoro

import android.app.AlertDialog
import android.content.Context
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
import com.google.firebase.auth.FirebaseAuth
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
    private lateinit var firebaseAuth: FirebaseAuth
    // Session management variables
    private var sessionCount = 0
    private var totalSessions = 0
    private var totalTimeInMillis: Long = 0
    private var pomodoroDuration: Long = 1 * 60 * 1000 // Default: 25 minutes in milliseconds
    private var shortBreakDuration: Long = 1 * 60 * 1000 // 5 minutes break in milliseconds
    private var taskdurationinsecond: Long =  0

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

        // Initialize Firebase authentication and database reference
        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Initialize views
        focusTextView = findViewById(R.id.focusTextView)
        iconImageView = findViewById(R.id.iconImageView)
        pomodoroTimerMinutes = findViewById(R.id.pomodoroTimerMinutes)
        pomodoroTimerSeconds = findViewById(R.id.pomodoroTimerSeconds)
        startButton = findViewById(R.id.startButton)
        academicTask = findViewById(R.id.taskTitle)
        taskId = intent.getStringExtra("TASK_ID").toString()



        // Load pomodoro duration from Firebase
        loadPomodoroDuration()


        // Set the task title
        val taskName = intent.getStringExtra("ACADEMIC_TASK") ?: "Pomodoro" // Default if null
        val priority = intent.getStringExtra("PRIORITY") ?: "No Prio" // Default to Low if not provided


        academicTask.text = taskName

        // Set the priority indicator color based on the priority
        val priorityIndicator = findViewById<View>(R.id.priorityIndicator)
        when (priority) {
            "Low" -> priorityIndicator.setBackgroundResource(R.drawable.circle_low_priority)
            "Medium" -> priorityIndicator.setBackgroundResource(R.drawable.circle_medium_priority)
            "High" -> priorityIndicator.setBackgroundResource(R.drawable.circle_high_priority)
            else -> priorityIndicator.setBackgroundResource(R.drawable.circle_shape)
        }
        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val backButton: ImageButton = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            onBackPressed()
        }
        settingsButton.setOnClickListener {
            // Open settings activity or dialog
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Set click listeners
        startButton.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }
    }

    private fun loadPomodoroDuration() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        Log.d("MainActivity", "Current User ID: $currentUserId") // Log the current user ID

        // Create a query to find the user settings based on user_id
        val query = database.child("user_settings").orderByChild("user_id").equalTo(currentUserId)

        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if the user settings exist
                if (snapshot.exists()) {
                    for (userSnapshot in snapshot.children) {
                        // Get the pomodoroDuration from the user's settings
                        val duration = userSnapshot.child("pomodoroDuration").getValue(Long::class.java)
                        Log.d("MainActivity", "Retrieved pomodoroDuration: $duration") // Log the retrieved duration
                            val durationIntent = intent.getStringExtra("TASK_DURATION")
                        Log.d("MainActivity", "Duration from intent: $durationIntent")
                            if (durationIntent != null) {
                                val intentDuration = durationIntent.toLongOrNull() ?: 25L // Default to 25 if conversion fails
                                Log.d("MainActivity", "Duration from intent: $intentDuration")
                                pomodoroTimerMinutes.text ="25"
                                setupPomodoroSession(intentDuration) // Setup the session with intent duration
                            } else {
                                if (duration != null) {
                                    pomodoroDuration = duration
                                    val totalSeconds = duration / 1000
                                    val minutes = (totalSeconds / 60).toInt()
                                    pomodoroTimerMinutes.text = minutes.toString()
                                    setupPomodoroSession(pomodoroDuration) // Setup the session with retrieved duration
                                } // Set the retrieved duration

                            }

                    }
                } else {
                    Log.d("MainActivity", "No settings found for current user ID")
                    setupPomodoroSession(25L) // Setup with default duration if no settings found
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Failed to load pomodoro duration", error.toException())
            }
        })
    }


    private fun setupPomodoroSession(taskDuration: Long) {
        // Determine the total time and split into sessions
      this.taskdurationinsecond = taskDuration * 60 * 1000
        if (taskdurationinsecond > pomodoroDuration) {
            totalSessions = 2 // 2 sessions (25 + remaining time)
            remainingSessionTime = (taskdurationinsecond - pomodoroDuration) // Remaining time for second session in milliseconds
        } else {
            totalSessions = 1 // Only 1 session
            remainingSessionTime = 0 // No remaining time
        }

        // Set the total time for the first session (25 minutes)
        totalTimeInMillis = pomodoroDuration
        timeLeftInMillis = totalTimeInMillis

        // Set initial state
        updateFocusState()
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
                    showPauseDialog()

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

    private fun showPauseDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Pause")
        builder.setMessage("Are you done?")
        builder.setPositiveButton("Yes") { dialog, _ ->
            val trimmedTaskId = taskId.trim()
            Log.d("UpdateTask", "Attempting to update status for task ID: '$trimmedTaskId'")
            updateTaskStatus(trimmedTaskId, "done")
            stopService(intent)
            dialog.dismiss()
            finish()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
            startButton.setImageResource(R.drawable.ic_arrow)
        }
        builder.create().show()
    }

    private fun updateTaskStatus(taskId: String, status: String) {
        val trimmedTaskId = taskId.trim()
        // Update the reference to point to the tasks node, then the specific task ID
        val taskRef = database.child("tasks").child(trimmedTaskId)

        taskRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    taskRef.child("status").setValue(status)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("UpdateStatus", "Task status updated successfully")
                            } else {
                                Log.e("UpdateStatus", "Failed to update task status: ${task.exception?.message}")
                            }
                        }
                } else {
                    Log.e("UpdateStatus", "Task not found for ID: $trimmedTaskId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UpdateStatus", "Failed to update status: ${error.message}")
            }
        })
    }


}


