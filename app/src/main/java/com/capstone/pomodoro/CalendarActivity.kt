package com.capstone.pomodoro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.widget.CalendarView
import android.widget.ImageButton
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var recyclerViewTasks: RecyclerView
    private lateinit var taskAdapter: CalendartTaskAdapter
    private lateinit var calendarView: CalendarView

    private val tasks: MutableList<Task> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("tasks")

        // Initialize UI components
        recyclerViewTasks = findViewById(R.id.recyclerViewTasks)
        recyclerViewTasks.layoutManager = LinearLayoutManager(this)

        // Initialize the adapter and attach it to the RecyclerView (even if empty)
        taskAdapter = CalendartTaskAdapter(emptyMap()) // Initially pass an empty map
        recyclerViewTasks.adapter = taskAdapter

        calendarView = findViewById(R.id.calendarView)

        // Fetch and display tasks
        fetchTasks()
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
        // Handle date selection on the CalendarView
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // Format the selected date
            val selectedDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                .format(Date(year - 1900, month, dayOfMonth))
            displayTasksForDate(selectedDate, groupTasksByDateAndSort(tasks))
        }
    }

    private fun fetchTasks() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        database.orderByChild("userId").equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tasks.clear() // Clear the current list of tasks
                    for (taskSnapshot in snapshot.children) {
                        val task = taskSnapshot.getValue(Task::class.java)
                        task?.let { tasks.add(it) } // Add task to the list if it's not null
                    }

                    // Group and sort tasks by date and priority
                    val groupedTasks = groupTasksByDateAndSortanalytics(tasks)

                    // Initially display tasks for today's date
                    val todayDate = getTodayDate()
                    displayTasksForDate(todayDate, groupedTasks)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CalendarActivity", "Failed to load tasks", error.toException())
                }
            })
    }

    private fun displayTasksForDate(date: String, groupedTasks: Map<String, List<Task>>) {
        // Check if tasks exist for the selected date
        val tasksForSelectedDate = groupedTasks[date] ?: emptyList()

        // Pass tasks for the selected date to the adapter and notify it of data change
        taskAdapter = CalendartTaskAdapter(mapOf(date to tasksForSelectedDate))
        recyclerViewTasks.adapter = taskAdapter
        taskAdapter.notifyDataSetChanged()
    }

    private fun groupTasksByDateAndSort(tasks: MutableList<Task>): Map<String, List<Task>> {
        return tasks
            .groupBy { it.date }
            .mapValues { entry ->
                entry.value.sortedBy { task ->
                    when (task.priority) {
                        "High" -> 1
                        "Medium" -> 2
                        "Low" -> 3
                        else -> 4
                    }
                }
            }
    }

    private fun getTodayDate(): String {
        val today = Calendar.getInstance().time
        return SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(today)
    }
}
