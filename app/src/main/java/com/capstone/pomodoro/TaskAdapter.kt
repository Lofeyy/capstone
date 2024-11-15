package com.capstone.pomodoro

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.PropertyName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.ParseException

data class Task(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("taskName") @set:PropertyName("taskName") var title: String = "",
    @get:PropertyName("sessions") @set:PropertyName("sessions") var session: String = "0/0",
    @get:PropertyName("priority") @set:PropertyName("priority") var priority: String = "",
    @get:PropertyName("submissionDate") @set:PropertyName("submissionDate") var date: String = "",
    @get:PropertyName("userId") @set:PropertyName("userId") var userId: String = "",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "N/A",
    @get:PropertyName("suggestedDuration") @set:PropertyName("suggestedDuration") var duration: String = "",
    @get:PropertyName("suggestedTimeSlot") @set:PropertyName("suggestedTimeSlot") var timeSlot: String = "",
    @get:PropertyName("academicTask") @set:PropertyName("academicTask") var academicTask: String = "" // Add this field if needed
)


fun groupTasksByDateAndSort(tasks: List<Task>): Map<String, List<Task>> {
    // Update date format to MM/dd/yy
    val inputDateFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
    val todayDate = SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date())

    // Log today's date for debugging
    Log.d("Today's Date", todayDate)

    // Filter out tasks that have already passed (i.e., submission date is before today)
    val upcomingTasks = tasks.filter { task ->
        try {
            val taskDate = inputDateFormat.parse(task.date) ?: Date()
            val currentDate = Date()
            taskDate.after(currentDate) || task.date == todayDate
        } catch (e: ParseException) {
            false
        }
    }

    // Group tasks by date
    val groupedTasks = upcomingTasks.groupBy { it.date }

    // Log the grouped tasks
    Log.d("Grouped Tasks", groupedTasks.toString())

    // Create a new map and sort the dates in descending order (latest date first)
    val sortedMap = linkedMapOf<String, List<Task>>()

    // Sort the grouped tasks by date in descending order
    groupedTasks.keys
        .sortedByDescending { inputDateFormat.parse(it) }  // Change to descending order
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

    // Sort the dates in descending order here
    private val dateList = groupedTasks.keys.toList().sortedBy { date ->
        SimpleDateFormat("MM/dd/yy", Locale.getDefault()).parse(date) ?: Date()
    }


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

                    // Convert timeSlot to 12-hour format with AM/PM
                    val timeFormat24 = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val timeFormat12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    val formattedTimeSlot = try {
                        val date = timeFormat24.parse(task.timeSlot)
                        timeFormat12.format(date)
                    } catch (e: ParseException) {
                        task.timeSlot // Fallback to original if parsing fails
                    }
                    holder.taskTimeSlot.text = formattedTimeSlot

                    // Set the priority indicator color
                    when (task.priority) {
                        "Low" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_low_priority)
                        "Medium" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_medium_priority)
                        "High" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_high_priority)
                    }

                    // Check if submission date is in the future and disable the options button if true
                    val inputDateFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
                    try {
                        val taskDate = inputDateFormat.parse(task.date) ?: Date()
                        val currentDate = Date()
//                        if (taskDate.after(currentDate)) {
//                            // Disable the task options button if the date is in the future
//                            holder.taskOptions.isEnabled = false
//                            holder.taskOptions.alpha = 0.5f // Optional: make it look disabled
//                        } else {
//                            // Enable the task options button if the date is not in the future
//                            holder.taskOptions.isEnabled = true
//                            holder.taskOptions.alpha = 1.0f
//                        }
                    } catch (e: ParseException) {
                        e.printStackTrace()
                        // Default to enabling the button if there's an issue parsing the date
                        holder.taskOptions.isEnabled = true
                        holder.taskOptions.alpha = 1.0f
                    }

                    // Set the task options and handle today's tasks as before
                    holder.taskOptions.setOnClickListener {
                        val context = holder.itemView.context
                        val intent = Intent(context, PomodoroTest::class.java)
                        val durationInMinutes = task.duration.replace("\\D".toRegex(), "").toLongOrNull() ?: 25
                        intent.putExtra("TASK_DURATION", durationInMinutes.toString())
                        intent.putExtra("TASK_ID", task.id ?: "unknown_id")
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
