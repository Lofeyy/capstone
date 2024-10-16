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


data class TaskAnalytics(
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


fun groupTasksByDateAndSortAnalytics(tasks: List<Task>): Map<String, List<Task>> {
    val inputDateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    val outputDateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

    return tasks.groupBy {
        // Parse the date string from Firebase
        val date = inputDateFormat.parse(it.date)
        date?.let { outputDateFormat.format(it) } ?: "Unknown Date" // Handle parsing issues
    }
        .filterKeys { key ->
            // Filter out invalid date keys
            try {
                inputDateFormat.parse(key) != null
            } catch (e: Exception) {
                false
            }
        }
        // Sort the map by date in descending order
        .toSortedMap(compareByDescending { inputDateFormat.parse(it) })
        // Sort tasks by priority within each group
        .mapValues { (_, tasks) -> sortTasksByPriority(tasks) }
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

                    // Note: Removed taskDuration, taskTimeSlot, priorityIndicator, taskOptions handling
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
    }
}
