package com.capstone.pomodoro

import android.annotation.SuppressLint
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
import android.os.Handler
import android.os.Looper
import android.media.MediaPlayer
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
    private lateinit var sessionTextView: TextView
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
    private var pomodoroDuration: Long = 25 * 60 * 1000 // Default: 25 minutes in milliseconds
    private var shortBreakDuration: Long = 25 * 60 * 1000 // 5 minutes break in milliseconds
    private var taskdurationinsecond: Long =  0
    private var isBreakSession = true
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
        sessionTextView = findViewById(R.id.sessionText)
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
        Log.d("MainActivity", "Current User ID: $currentUserId")
        val query = database.child("user_settings").orderByChild("user_id").equalTo(currentUserId)

        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) =
                // Check if the user settings exist
                if (snapshot.exists()) {
                    for (userSnapshot in snapshot.children) {
                        // Get the pomodoroDuration from the user's settings
                        val duration = userSnapshot.child("pomodoroDuration").getValue(Long::class.java)
                        val breakDuration = userSnapshot.child("shortBreakDuration").getValue(Long::class.java)
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
                                if (breakDuration != null) {
                                    shortBreakDuration = breakDuration
                                }
                                val totalSeconds = duration / 1000
                                val minutes = (totalSeconds / 60).toInt()
                                pomodoroTimerMinutes.text = String.format("%02d", minutes)
                                setupPomodoroSession(pomodoroDuration)
                            } // Set the retrieved duration

                        }

                    }
                } else {
                    Log.d("MainActivity", "No settings found for current user ID")
                    setupPomodoroSession(25L) // Setup with default duration if no settings found
                }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Failed to load pomodoro duration", error.toException())
            }
        })
    }


    @SuppressLint("SuspiciousIndentation")
    private fun setupPomodoroSession(taskDuration: Long) {
        val durationInMillis = taskDuration * 60 * 1000


        // Calculate total sessions
        totalSessions = (durationInMillis / pomodoroDuration).toInt() // Number of full 25-minute sessions
        if (durationInMillis % pomodoroDuration > 0) {
            totalSessions += 1 // Add one session for any leftover time
        }

        // Set initial session state
        sessionCount = 1
        sessionTextView.text = "$sessionCount/$totalSessions"

        // Calculate remaining time for the current session
        remainingSessionTime = if (durationInMillis > pomodoroDuration) {
            durationInMillis % pomodoroDuration
        } else {
            0 // No remaining time if task fits in one session
        }

        // Set the total time for the first session (25 minutes or less if it's the last session)
        totalTimeInMillis = if (taskDuration <= 25) durationInMillis else pomodoroDuration
        timeLeftInMillis = totalTimeInMillis

        // Set initial state
        updateFocusState()
    }
    @SuppressLint("SuspiciousIndentation")
    private fun setupPomodoroSessionDefault(taskDuration: Long) {

        if (taskDuration > pomodoroDuration) {
            totalSessions = 2 // 2 sessions (25 + remaining time)
            sessionTextView.text= "1/$totalSessions"
            remainingSessionTime = (taskDuration - pomodoroDuration) // Remaining time for second session in milliseconds
        } else {
            totalSessions = 1 // Only 1 session
            sessionTextView.text= "1/$totalSessions"
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
//        val intent = Intent(this, PomodoroService::class.java).apply {
//            putExtra("TIME_LEFT", timeLeftInMillis) // Pass the remaining time
//        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(intent)
//        } else {
//            startService(intent)
//        }

        isTimerRunning = true

        val breakThresholdMillis = 1000

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimer(timeLeftInMillis)

                // Check if we are within the threshold for ending the session
                if (timeLeftInMillis <= breakThresholdMillis ) {
                    breakSound() // Play break sound
                }
            }
            override fun onFinish() {
                updateTimer(timeLeftInMillis)
                Log.d("onFinish", "Session Count: $sessionCount")
                Log.d("onFinish", "timeLeftInMillis: $timeLeftInMillis")
                // Check if we need to play sound and start break session
                if (isBreakSession) {

                    startBreakSession()
                } else {
                    showPauseDialog()
                }
            }
        }.start()

        startButton.setImageResource(R.drawable.ic_pause)
    }
    private fun breakSound() {
        // Create the MediaPlayer instance
        val mediaPlayer = MediaPlayer.create(this, R.raw.breaknotif)

        mediaPlayer.start() // Start playing the sound

        // Create a handler to stop the sound after 7 seconds (if needed)
        Handler(Looper.getMainLooper()).postDelayed({
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop() // Stop the sound if it's still playing
            }
            mediaPlayer.release() // Release resources
        }, 3000) // Duration in milliseconds (7 seconds)
    }


    private fun startNextSession() {

        timeLeftInMillis = if (sessionCount == totalSessions - 1) {
            remainingSessionTime
        }else{
            pomodoroDuration
        }
        Log.d("startnextsessionbefore", "timeLeftInMillis: $timeLeftInMillis")
        updateTimer(timeLeftInMillis)

        Log.d("startnextsessionafter", "timeLeftInMillis: $timeLeftInMillis")
        // Update session count and session text view
        sessionCount++
        sessionTextView.text = "$sessionCount/$totalSessions"

        // Update focus or break state and reset timer display
        updateFocusState()
        isBreakSession = true

        // Calculate minutes and seconds for the display


        // Start the timer for the new session
        startTimer()
    }

private fun startBreakSession(){
    timeLeftInMillis = shortBreakDuration
    updateBreakState()
    isBreakSession = false

    updateTimer(timeLeftInMillis)
    startTimer()

}
    private fun pauseTimer() {
        timer?.cancel()
        isTimerRunning = false
        showPauseDialog()
    }

    private fun updateTimer(timeLeftInMillis: Long) {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        pomodoroTimerMinutes.text = String.format("%02d", minutes)
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
            addSessionToFirebase(trimmedTaskId,sessionCount ,totalSessions)
            updateTaskStatus(trimmedTaskId, "Done")
            stopService(intent)
            dialog.dismiss()
            finish()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
            startButton.setImageResource(R.drawable.ic_arrow)

                startNextSession()
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
    private fun addSessionToFirebase(taskId: String, sessionCount: Int, totalSessions: Int) {
        val trimmedTaskId = taskId.trim()
        val sessionData = "$sessionCount/$totalSessions"

        // Reference the 'tasks' node and the specific task ID
        val taskRef = database.child("tasks").child(trimmedTaskId)

        taskRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    taskRef.child("sessions").setValue(sessionData)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("AddSession", "Session updated successfully")
                            } else {
                                Log.e("AddSession", "Failed to update session: ${task.exception?.message}")
                            }
                        }
                } else {
                    Log.e("AddSession", "Task not found for ID: $trimmedTaskId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AddSession", "Failed to update session: ${error.message}")
            }
        })
    }


}


