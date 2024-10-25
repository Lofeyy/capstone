package com.capstone.pomodoro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.capstone.pomodoro.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: DatabaseReference
    private lateinit var userSettings: DatabaseReference
    private lateinit var pomodoroTimer: TextView // Declare pomodoroTimer TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Toolbar
        val toolbar: Toolbar = findViewById(R.id.custom_toolbar)
        setSupportActionBar(toolbar)

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val titleText: TextView = findViewById(R.id.title_text)

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance().reference

        // Initialize the pomodoroTimer TextView
        pomodoroTimer = findViewById(R.id.pomodoroTimer) // Make sure you have this in your layout XML

        // Settings button click listener
        settingsButton.setOnClickListener {
            // Open settings activity or dialog
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Set onClickListener for pomodoroWidget
        binding.pomodoroWidget.setOnClickListener {
            val intent = Intent(this, PomodoroActivity::class.java)
            startActivity(intent)
        }

        // Set current month and day in calendarWidget
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
        val currentMonth = monthFormat.format(calendar.time)
        val currentDay = dayFormat.format(calendar.time)

        binding.monthTextView.text = currentMonth
        binding.dayTextView.text = currentDay

        // Set onClickListener for calendarWidget
        binding.calendarWidget.setOnClickListener {
            val intent = Intent(this, CalendarActivity::class.java)
            startActivity(intent)
        }

        // Set onClickListener for taskWidget
        binding.taskWidget.setOnClickListener {
            val dialog = CreateTaskDialogFragment()
            dialog.show(supportFragmentManager, "CreateTaskDialog")
        }

        binding.viewTask.setOnClickListener {
            val intent = Intent(this, TaskActivity::class.java)
            startActivity(intent)
        }

        binding.analyticsWidget1.setOnClickListener {
            val intent = Intent(this, AnalyticsActivity::class.java)
            startActivity(intent)
        }

        // Load task count for today
        loadTasksCountForToday()

        // Load pomodoro duration for the current user
        loadPomodoroDuration()
    }

    private fun loadTasksCountForToday() {
        val currentDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
        val currentUserId = firebaseAuth.currentUser?.uid ?: return


        firebaseDatabase.child("tasks").orderByChild("userId").equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var taskCountToday = 0
                    for (taskSnapshot in snapshot.children) {
                        val task = taskSnapshot.getValue(Task::class.java)
                        if (task?.date == currentDate) {
                            taskCountToday++
                        }
                    }
                    binding.numberOftask.text = "$taskCountToday" // Update number of tasks
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("MainActivity", "Failed to load tasks", error.toException())
                }
            })
    }

    private fun loadPomodoroDuration() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        Log.d("MainActivity", "Current User ID: $currentUserId") // Log the current user ID

        // Create a query to find the user settings based on user_id
        val query = firebaseDatabase.child("user_settings").orderByChild("user_id").equalTo(currentUserId)

        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if the user settings exist
                if (snapshot.exists()) {
                    for (userSnapshot in snapshot.children) {
                        // Get the pomodoroDuration from the user's settings
                        val duration = userSnapshot.child("pomodoroDuration").getValue(Long::class.java)
                        Log.d("MainActivity", "Retrieved pomodoroDuration: $duration") // Log the retrieved duration

                        if (duration != null) {
                            // Convert milliseconds to minutes and seconds
                            val totalSeconds = duration / 1000
                            val minutes = (totalSeconds / 60).toInt()
                            val seconds = (totalSeconds % 60).toInt()

                            // Format to MM:SS
                            val formattedDuration = String.format("%02d:%02d", minutes, seconds)
                            pomodoroTimer.text = formattedDuration // Update the TextView with formatted duration
                            Log.d("MainActivity", "Formatted Duration: $formattedDuration") // Log the formatted duration
                        } else {
                            pomodoroTimer.text = "25:00"
                            Log.d("MainActivity", "Duration not set, using default: 25:00") // Log when default duration is used
                        }
                    }
                } else {
                    Log.d("MainActivity", "No settings found for current user ID")
                    pomodoroTimer.text = "25:00" // Default duration if no settings found
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Failed to load pomodoro duration", error.toException())
            }
        })
    }





}
