package com.capstone.pomodoro

import android.os.Bundle
import android.util.Log
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
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_task_list) // Use your layout here

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance().getReference("tasks")

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
                        // Create a task object from the snapshot
                        val task = taskSnapshot.getValue(Task::class.java)

                        // Assign the Firebase key (task ID) to the task
                        task?.let {
                            it.id = taskSnapshot.key ?: ""  // Assigning the Firebase key to the task ID

                            // Check if the status is not "done"
                            if (it.status != "done") {
                                if (it.date == today) {
                                    tasksToUpdate.add(it) // Collect tasks due today for updating
                                }
                                tasks.add(it)
                                Log.d("TaskActivity", "Loaded Task: ${it.title}, User ID: ${it.userId}, Task ID: ${it.id}")
                            }
                        }
                    }

                    // Update tasks due today
                    updateTaskDates(tasksToUpdate)

                    // Group and sort tasks by date and priority
                    val groupedTasks = groupTasksByDateAndSort(tasks)

                    // Pass grouped and sorted tasks to adapter
                    adapter = TaskAdapter(groupedTasks)
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
