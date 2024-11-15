package com.capstone.pomodoro

import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.components.LegendEntry
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
        setupSwipeGesture()
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
                            val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                            val taskDate = dateFormat.parse(task.date)
                            val currentDate = Date()

// Subtract one day from the current date to check for missed tasks
                            val calendar = Calendar.getInstance()
                            calendar.time = currentDate
                            calendar.add(Calendar.DAY_OF_YEAR, -1)
                            val currentDateMinusOne = calendar.time

                            if (it.status == "notStarted") {
                                // Check if the task date is before the current date minus one day
                                if (taskDate != null && taskDate.before(currentDateMinusOne)) {
                                    missedCount++
                                } else {
                                    notStartedCount++
                                }
                            } else {
                                doneCount++
                            }


                        }
                    }

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
        val entries = mutableListOf(
            PieEntry(notStartedCount.toFloat(), "Pending"),
            PieEntry(doneCount.toFloat(), "Done"),
            PieEntry(missedCount.toFloat(), "Missed")
        )

        val dataSet = PieDataSet(entries, "Task Status")
        dataSet.colors = listOf(
            android.graphics.Color.parseColor("#DCD6FA"), // Color for "Pending" slice
            android.graphics.Color.parseColor("#3E3EA1"), // Color for "Done" slice
            android.graphics.Color.parseColor("#FF0000")  // Color for "Missed" slice (Red)
        )
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
    }

    private fun setupSwipeGesture() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (viewHolder is AnalyticsTaskAdapter.TaskViewHolder) {
                    val position = viewHolder.adapterPosition
                    val task = taskAdapter.getTaskAtPosition(position)

                    task?.let {
                        val intent = Intent(this@AnalyticsActivity, ViewTaskActivity::class.java)
                        intent.putExtra("TASK_ID", it.id)
                        startActivity(intent)
                    }
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerViewTasks)
    }
}
