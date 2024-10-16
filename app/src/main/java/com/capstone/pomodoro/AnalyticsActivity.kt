package com.capstone.pomodoro

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
private lateinit var firebaseAuth: FirebaseAuth
private lateinit var database: DatabaseReference
private lateinit var recyclerViewTasks: RecyclerView
private lateinit var taskAdapter: AnalyticsTaskAdapter
private val tasks: MutableList<Task> = mutableListOf()
class AnalyticsActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("tasks")

        // Initialize UI components
        recyclerViewTasks = findViewById(R.id.recyclerView)
        recyclerViewTasks.layoutManager = LinearLayoutManager(this)

        // Initialize the adapter and attach it to the RecyclerView (even if empty)
        taskAdapter = AnalyticsTaskAdapter(emptyMap()) // Initially pass an empty map
        recyclerViewTasks.adapter = taskAdapter



        // Fetch and display tasks
        fetchTasks()


    }
    private fun fetchTasks() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        database.orderByChild("userId").equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tasks.clear() // Clear the current list of tasks
                    for (taskSnapshot in snapshot.children) {
                        val task = taskSnapshot.getValue(Task::class.java)
                        task?.let { tasks.add(it) }
                    }

                    // Group and sort tasks by date and priority
                    val groupedTasks = groupTasksByDateAndSort(tasks)

                    // Pass grouped and sorted tasks to adapter
                    taskAdapter = AnalyticsTaskAdapter(groupedTasks)
                    recyclerViewTasks.adapter = taskAdapter


                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CalendarActivity", "Failed to load tasks", error.toException())
                }
            })
    }


}