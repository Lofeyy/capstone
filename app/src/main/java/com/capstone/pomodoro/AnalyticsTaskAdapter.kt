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
import java.util.Date
import java.util.Locale





fun groupTasksByDateAndSortanalytics(tasks: List<Task>): Map<String, List<Task>> {
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
//


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

class AnalyticsTaskAdapter(private val groupedTasks: Map<String, List<Task>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val dateList = groupedTasks.keys.toList().sortedDescending() // Sort the dates in descending order

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_TASK = 1
    }

    override fun getItemViewType(position: Int): Int {
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
            val view = LayoutInflater.from(parent.context).inflate(R.layout.analytics_task_item, parent, false)
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

            if (position < count) {
                val taskIndex = position - (count - tasksForDate.size) // Calculate task index

                if (taskIndex >= 0 && taskIndex < tasksForDate.size) {
                    val task = tasksForDate[taskIndex]
                    holder as TaskViewHolder // Cast holder to TaskViewHolder

                    // Set dynamic task title
                    holder.taskTitle.text = task.title // Dynamically set the task title
                    holder.sessionsCount.text = task.sessions // Dynamically set the task title
                    holder.taskDuration.text = task.duration

                    // Set a click listener on the task item
                    holder.itemView.setOnClickListener {
                        val intent = Intent(it.context, ViewTaskActivity::class.java)
                        intent.putExtra("TASK_ID", task.taskId ?: "unknown_id")
                        Log.d("TaskAnalytics", "Task clicked, ID: ${task.taskId}") // Log the task ID for debugging
                        it.context.startActivity(intent) // Start the new activity
                    }
                }
                return // Exit since we handled this position
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
        val taskTitle: TextView = itemView.findViewById(R.id.taskTitle) // Only retain taskTitle
        val sessionsCount: TextView = itemView.findViewById(R.id.sessionsCount)
        val taskDuration: TextView = itemView.findViewById(R.id.duration)
    }

    fun getTaskAtPosition(position: Int): Task? {
        var count = 0
        for (date in dateList) {
            count++ // Count the header
            val tasksForDate = groupedTasks[date] ?: emptyList()
            count += tasksForDate.size // Count tasks for the current date

            if (position < count) {
                val taskIndex = position - (count - tasksForDate.size) // Calculate task index
                return if (taskIndex >= 0 && taskIndex < tasksForDate.size) {
                    tasksForDate[taskIndex]
                } else {
                    null
                }
            }
        }
        return null
    }

}
