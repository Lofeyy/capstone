package com.capstone.pomodoro

import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var recyclerViewTasks: RecyclerView
    private lateinit var taskAdapter: AnalyticsTaskAdapter
    private lateinit var donutChart: PieChart
    private val tasks: MutableList<Task> = mutableListOf()

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
        taskAdapter = AnalyticsTaskAdapter(emptyMap())
        recyclerViewTasks.adapter = taskAdapter

        donutChart = findViewById(R.id.donutChart)

        // Remove swipe gesture functionality
        // No swipe gesture setup method

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val backButton: ImageButton = findViewById(R.id.back_button)
        backButton.setOnClickListener { onBackPressed() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Fetch and display tasks
        fetchTasks()
    }

    private fun fetchTasks() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        database.orderByChild("userId").equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tasks.clear()
                    var notStartedCount = 0
                    var doneCount = 0
                    var missedCount = 0

                    for (taskSnapshot in snapshot.children) {
                        val task = taskSnapshot.getValue(Task::class.java)
                        task?.let {
                            tasks.add(it)

                            // Count tasks based on their status
                            when (it.status) {
                                "notStarted" -> notStartedCount++
                                "Done" -> doneCount++
                                "Missed" -> missedCount++  // Count as Missed if the status is "missed"
                                else -> {}
                            }
                        }
                    }

                    // Debugging the counts
                    Log.d("AnalyticsActivity", "Missed Count: $missedCount")
                    Log.d("AnalyticsActivity", "Not Started Count: $notStartedCount")
                    Log.d("AnalyticsActivity", "Done Count: $doneCount")

                    // Group and sort tasks by date and priority
                    val groupedTasks = groupTasksByDateAndSortanalytics(tasks)
                    taskAdapter = AnalyticsTaskAdapter(groupedTasks)
                    recyclerViewTasks.adapter = taskAdapter

                    // Update the donut chart with task status counts
                    updateDonutChart(notStartedCount, doneCount, missedCount)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("AnalyticsActivity", "Failed to load tasks", error.toException())
                }
            })
    }

    private fun updateDonutChart(notStartedCount: Int, doneCount: Int, missedCount: Int) {
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()

        // Only add the entry if the count is greater than zero, and assign corresponding colors
        if (notStartedCount > 0) {
            entries.add(PieEntry(notStartedCount.toFloat(), "Pending"))
            colors.add(android.graphics.Color.parseColor("#DCD6FA")) // Color for "Pending"
        }
        if (doneCount > 0) {
            entries.add(PieEntry(doneCount.toFloat(), "Done"))
            colors.add(android.graphics.Color.parseColor("#3E3EA1")) // Color for "Done"
        }
        if (missedCount > 0) {
            entries.add(PieEntry(missedCount.toFloat(), "Missed"))
            colors.add(android.graphics.Color.parseColor("#FF0000")) // Color for "Missed"
        }

        // Check if there are any entries to show in the chart
        if (entries.isNotEmpty()) {
            val dataSet = PieDataSet(entries, "Task Status")
            dataSet.colors = colors // Dynamically assigned colors
            dataSet.valueTextSize = 16f

            // Custom ValueFormatter to switch text color based on slice color
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
                    val label = pieEntry?.label ?: ""
                    return "$label: ${value.toInt()}"
                }

                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }

            val data = PieData(dataSet)
            donutChart.data = data
            donutChart.setUsePercentValues(false)
            donutChart.description.isEnabled = false
            donutChart.isDrawHoleEnabled = true
            donutChart.holeRadius = 50f
            donutChart.setEntryLabelColor(android.graphics.Color.TRANSPARENT)

            // Set legend text color to white
            donutChart.legend.apply {
                textColor = android.graphics.Color.WHITE
                textSize = 8f
            }

            // Refresh the chart
            donutChart.invalidate()
        } else {
            donutChart.clear()  // If no valid entries, clear the chart
        }
    }

}
