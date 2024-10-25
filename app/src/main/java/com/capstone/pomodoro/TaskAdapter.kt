package com.capstone.pomodoro

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.PropertyName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


data class Task(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("taskName") @set:PropertyName("taskName") var title: String = "",
    @get:PropertyName("priority") @set:PropertyName("priority") var priority: String = "",
    @get:PropertyName("submissionDate") @set:PropertyName("submissionDate") var date: String = "",
    @get:PropertyName("userId") @set:PropertyName("userId") var userId: String = "",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "",
    @get:PropertyName("suggestedDuration") @set:PropertyName("suggestedDuration") var duration: String = "",
    @get:PropertyName("suggestedTimeSlot") @set:PropertyName("suggestedTimeSlot") var timeSlot: String = "",
    @get:PropertyName("academicTask") @set:PropertyName("academicTask") var academicTask: String = "" // Add this field if needed
)


fun groupTasksByDateAndSort(tasks: List<Task>): Map<String, List<Task>> {
    val inputDateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    val todayDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())

    // Log today's date for debugging
    Log.d("Today's Date", todayDate)

    // Group tasks by date
    val groupedTasks = tasks.groupBy { it.date }

    // Log the grouped tasks
    Log.d("Grouped Tasks", groupedTasks.toString())

    // Create a new map that includes today's tasks at the top
    val sortedMap = linkedMapOf<String, List<Task>>()

    // First, add today's tasks to the sortedMap if any
    if (groupedTasks.containsKey(todayDate)) {
        sortedMap[todayDate] = groupedTasks[todayDate]!!
    }

    // Now sort remaining dates, excluding today
    groupedTasks.keys
        .filter { it != todayDate } // Exclude today’s date
        .filter { key ->
            // Filter out invalid date keys
            try {
                inputDateFormat.parse(key) != null
            } catch (e: Exception) {
                false
            }
        }
        // Sort remaining dates in descending order
        .sortedByDescending { inputDateFormat.parse(it) }
        .forEach { date ->
            sortedMap[date] = groupedTasks[date] ?: emptyList()
        }

    // Log the sorted map before returning
    Log.d("Sorted Map", sortedMap.toString())

    // Sort tasks by priority within each group
    return sortedMap.mapValues { (_, tasks) -> sortTasksByPriority(tasks) }
}



// Function to sort tasks by priority
private fun sortTasksByPriority(tasks: List<Task>): List<Task> {
    return tasks.sortedBy {
        when (it.priority) {
            "High" -> 1
            "Medium" -> 2
            "Low" -> 3
            else -> 4 // Default case if priority is not recognized
        }
    }
}

class TaskAdapter(private val groupedTasks: Map<String, List<Task>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val dateList = groupedTasks.keys.toList().sortedDescending() // Sort the dates in descending order

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_TASK = 1
    }

    override fun getItemViewType(position: Int): Int {
        // If the position corresponds to a header (which is at every first position of tasks for a date)
        var count = 0
        for (date in dateList) {
            if (count == position) {
                return VIEW_TYPE_HEADER // This is a header
            }
            count++ // Count the header

            val tasksForDate = groupedTasks[date] ?: emptyList()
            count += tasksForDate.size // Count tasks for the current date
        }
        return VIEW_TYPE_TASK // Fallback (this should generally not happen)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            TaskViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var count = 0
        for (date in dateList) {
            if (count == position) {
                // Bind header data
                (holder as HeaderViewHolder).dateHeader.text = date
                return // Done with binding header
            }
            count++ // Count the header

            val tasksForDate = groupedTasks[date] ?: emptyList()
            count += tasksForDate.size // Count tasks for the current date

            // Check if the current position is within the task range
            if (position < count) {
                val taskIndex = position - (count - tasksForDate.size) // Calculate task index

                if (taskIndex >= 0 && taskIndex < tasksForDate.size) {
                    val task = tasksForDate[taskIndex]
                    holder as TaskViewHolder // Cast holder to TaskViewHolder
                    holder.taskTitle.text = task.title
                    holder.taskDuration.text = task.duration
                    holder.taskTimeSlot.text = task.timeSlot

                    // Set the priority indicator color
                    when (task.priority) {
                        "Low" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_low_priority)
                        "Medium" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_medium_priority)
                        "High" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_high_priority)
                    }

                    // Parse the task date (MM/dd/yyyy format)
                    val taskDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(task.date) ?: Date()

// Get the current date
                    val currentDate = Date()
                    val calendar = Calendar.getInstance()

// Set to the start of today (00:00:00)
                    calendar.time = currentDate
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

// Get the start of today
                    val startOfToday = calendar.time

// Set to the end of today (23:59:59)
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)

// Get the end of today
                    val endOfToday = calendar.time

// Compare taskDate with startOfToday and endOfToday
                    if (taskDate.before(startOfToday) || taskDate.after(endOfToday)) {
                        // Disable task options for dates outside of today
                        holder.taskOptions.isEnabled = false
                        holder.taskOptions.alpha = 0.5f // Make it look disabled
                    } else {
                        // Enable task options for today
                        holder.taskOptions.isEnabled = true
                        holder.taskOptions.alpha = 1.0f
                        holder.taskOptions.setOnClickListener {
                            val context = holder.itemView.context
                            val intent = Intent(context, PomodoroActivity::class.java)

                            // Extract the numeric part from the task duration
                            val durationInMinutes = task.duration.replace("\\D".toRegex(), "").toLongOrNull() ?: 25
                            intent.putExtra("TASK_DURATION", durationInMinutes.toString())

                            // Pass the task ID
                            intent.putExtra("TASK_ID", task.id ?: "unknown_id")

                            // Pass academicTask and priority
                            intent.putExtra("ACADEMIC_TASK", task.academicTask)
                            intent.putExtra("PRIORITY", task.priority)
                            Log.d("PomodoroActivity", "Task ID: ${task.id}")
                            Log.d("PomodoroActivity", "Academic Task: ${task.academicTask}")
                            Log.d("PomodoroActivity", "Priority: ${task.priority}")
                            context.startActivity(intent)
                        }
                    }


                    return // Exit since we handled this position
                }
            }
        }
    }

    override fun getItemCount(): Int {
        var count = 0
        for (date in dateList) {
            count++ // Count the header
            count += groupedTasks[date]?.size ?: 0 // Count tasks for the current date
        }
        return count
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateHeader: TextView = itemView.findViewById(R.id.dateHeader)
    }

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        val taskDuration: TextView = itemView.findViewById(R.id.taskDuration)
        val taskTimeSlot: TextView = itemView.findViewById(R.id.taskTimeSlot)
        val priorityIndicator: View = itemView.findViewById(R.id.priorityIndicator)
        val taskOptions: ImageView = itemView.findViewById(R.id.taskOptions)
    }
}
