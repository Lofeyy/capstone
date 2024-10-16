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
        firebaseDatabase = FirebaseDatabase.getInstance().getReference("tasks")


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
    }

    private fun loadTasksCountForToday() {
        val currentDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        firebaseDatabase.orderByChild("userId").equalTo(currentUserId)
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
}
