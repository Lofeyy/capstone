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
        setContentView(R.layout.fragment_task_list) // Use your layout here

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
                    val today = dateFormat.format(Date()) // Get today's date as a string
                    val tasksToUpdate = mutableListOf<Task>()

                    for (taskSnapshot in snapshot.children) {
                        val task = taskSnapshot.getValue(Task::class.java)

                        // Assign the Firebase key (task ID) to the task
                        task?.let {
                            it.id = taskSnapshot.key ?: ""  // Assigning the Firebase key to the task ID

                            // Only add tasks that are not "done"
                            if (it.status != "done") {
                                tasks.add(it)
                                if (it.date == today) {
                                    tasksToUpdate.add(it) // Collect tasks due today for updating
                                }
                            }
                        }
                    }

//                    // Update tasks due today
//                    updateTaskDates(tasksToUpdate)

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

    private fun updateTaskDates(tasksToUpdate: List<Task>) {
        for (task in tasksToUpdate) {
            val newDate = Calendar.getInstance().apply {
                time = dateFormat.parse(task.date) ?: Date()
                add(Calendar.DAY_OF_YEAR, 1) // Increment date by 1 day
            }.time

            val updatedDateString = dateFormat.format(newDate)

            // Update the task in the database
            firebaseDatabase.child(task.id).child("submissionDate").setValue(updatedDateString)
                .addOnSuccessListener {
                    Log.d("TaskActivity", "Updated Task: ${task.title} to new date: $updatedDateString")
                }
                .addOnFailureListener { e ->
                    Log.e("TaskActivity", "Failed to update task date", e)
                }
        }
    }
}
