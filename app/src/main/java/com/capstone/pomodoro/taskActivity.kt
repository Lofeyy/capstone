package com.capstone.pomodoro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class TaskActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TaskAdapter
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: DatabaseReference
    private val tasks = mutableListOf<Task>()
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_task_list)

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance().getReference("tasks")

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
        // Load the tasks
        loadTasks()
    }

    private fun loadTasks() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        firebaseDatabase.orderByChild("userId").equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tasks.clear()
                    val today = dateFormat.format(Date()) // Get today's date as a string in "MM/dd/yyyy" format
                    val tasksToUpdate = mutableListOf<Task>()

                    for (taskSnapshot in snapshot.children) {
                        val task = taskSnapshot.getValue(Task::class.java)

                        // Assign the Firebase key (task ID) to the task
                        task?.let {
                            it.id = taskSnapshot.key ?: ""  // Assigning the Firebase key to the task ID

                            // Ensure that task.date is in the "MM/dd/yyyy" format for comparison
                            val taskDate = formatTaskDate(it.date)  // Method to extract only the date part

                            // Only process tasks with status "notStarted" or "done"
                            if (it.status == "notStarted" ) {
                                // Check if the task is not done and if the task date has passed
                                if (it.status == "notStarted" && taskDate.isNotEmpty()) {
                                    // Parse the task's date for comparison (without time)
                                    val taskDateParsed = dateFormat.parse(taskDate)
                                    val currentDate = dateFormat.parse(today)

                                    // If taskDate is before current date, update status to "Missed"
                                    if (taskDateParsed != null && taskDateParsed.before(currentDate)) {
                                        // Update the status to "Missed" in Firebase
                                        val taskRef = firebaseDatabase.child(taskSnapshot.key ?: "")
                                        taskRef.child("status").setValue("Missed")

                                        // Add to tasksToUpdate list to refresh UI if needed
                                        tasksToUpdate.add(it)
                                    }
                                }

                                // Add tasks to the list for UI update
                                tasks.add(it)
                            }
                        }
                    }

                    // Group tasks by date and sort them
                    val groupedTasks = groupTasksByDateAndSort(tasks)

                    // Filter out empty date groups
                    val filteredGroupedTasks = groupedTasks.filter { it.value.isNotEmpty() }

                    // Pass the filtered and grouped tasks to the adapter
                    adapter = TaskAdapter(filteredGroupedTasks)
                    recyclerView.adapter = adapter
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TaskActivity", "Failed to load tasks", error.toException())
                }
            })
    }



    // Helper method to format task date to "MM/dd/yyyy"
    private fun formatTaskDate(taskDate: String): String {
        return try {
            val taskDateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

            // Directly parse the taskDate if it's already in MM/dd/yyyy format.
            val parsedDate = taskDateFormat.parse(taskDate)

            // Return formatted date without time
            taskDateFormat.format(parsedDate ?: Date())
        } catch (e: Exception) {
            e.printStackTrace()
            ""  // Return empty string if parsing fails
        }
    }




}
