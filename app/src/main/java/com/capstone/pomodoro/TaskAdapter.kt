package com.capstone.pomodoro

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.PropertyName
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

data class Task(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("taskName") @set:PropertyName("taskName") var title: String = "",
    @get:PropertyName("sessions") @set:PropertyName("sessions") var sessions: String = "0/0",
    @get:PropertyName("priority") @set:PropertyName("priority") var priority: String = "",
    @get:PropertyName("submissionDate") @set:PropertyName("submissionDate") var date: String = "",
    @get:PropertyName("userId") @set:PropertyName("userId") var userId: String = "",
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "N/A",
    @get:PropertyName("suggestedDuration") @set:PropertyName("suggestedDuration") var duration: String = "",
    @get:PropertyName("suggestedTimeSlot") @set:PropertyName("suggestedTimeSlot") var timeSlot: String = "",
    @get:PropertyName("academicTask") @set:PropertyName("academicTask") var academicTask: String = "",
    @get:PropertyName("taskId") @set:PropertyName("taskId") var taskId: String = ""
)

fun groupTasksByDateAndSort(tasks: List<Task>): Map<String, List<Task>> {
    val manilaTimeZone = TimeZone.getTimeZone("Asia/Manila")
    val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).apply {
        timeZone = manilaTimeZone
    }
    val todayDate = dateFormat.format(Date())

    // Group tasks by date
    val groupedTasks = tasks.groupBy { it.date }

    return linkedMapOf<String, List<Task>>().apply {
        // Include today's tasks first
        groupedTasks[todayDate]?.let {
            put(todayDate, sortTasksByPriority(it))
        }

        // Include past tasks too, sorted by date
        groupedTasks.keys
            .sortedBy { dateFormat.parse(it) }
            .forEach { date ->
                put(date, sortTasksByPriority(groupedTasks[date] ?: emptyList()))
            }
    }
}



private fun sortTasksByPriority(tasks: List<Task>): List<Task> {
    return tasks.sortedBy {
        when (it.priority) {
            "High" -> 1
            "Medium" -> 2
            "Low" -> 3
            else -> 4
        }
    }
}

class TaskAdapter(private val groupedTasks: Map<String, List<Task>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val dateList = groupedTasks.keys.toList().sortedBy { date ->
        SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(date) ?: Date()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_TASK = 1
    }

    override fun getItemViewType(position: Int): Int {
        var count = 0
        for (date in dateList) {
            if (count == position) return VIEW_TYPE_HEADER
            count += 1 + (groupedTasks[date]?.size ?: 0)
        }
        return VIEW_TYPE_TASK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_date_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_task, parent, false)
            TaskViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var count = 0
        for (date in dateList) {
            val tasks = groupedTasks[date] ?: emptyList()
            if (count == position) {
                (holder as HeaderViewHolder).bind(date)
                return
            }
            count++
            if (position < count + tasks.size) {
                val task = tasks[position - count]
                (holder as TaskViewHolder).bind(task)
                return
            }
            count += tasks.size
        }
    }

    override fun getItemCount(): Int {
        return groupedTasks.keys.sumOf { 1 + (groupedTasks[it]?.size ?: 0) }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateHeader: TextView = itemView.findViewById(R.id.dateHeader)
        fun bind(date: String) {
            dateHeader.text = date
        }
    }

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        private val taskDuration: TextView = itemView.findViewById(R.id.taskDuration)
        private val taskTimeSlot: TextView = itemView.findViewById(R.id.taskTimeSlot)
        private val priorityIndicator: View = itemView.findViewById(R.id.priorityIndicator)
        private val taskOptions: ImageView = itemView.findViewById(R.id.taskOptions)

        fun bind(task: Task) {
            taskTitle.text = task.title
            taskDuration.text = task.duration

            // Format time slot
            taskTimeSlot.text = try {
                val time24 = SimpleDateFormat("HH:mm", Locale.getDefault()).parse(task.timeSlot)
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(time24)
            } catch (e: Exception) {
                task.timeSlot
            }

            // Set priority indicator
            priorityIndicator.setBackgroundResource(
                when (task.priority) {
                    "Low" -> R.drawable.circle_low_priority
                    "Medium" -> R.drawable.circle_medium_priority
                    "High" -> R.drawable.circle_high_priority
                    else -> 0
                }
            )

            // Task options logic
            taskOptions.apply {
                isEnabled = isTaskPastDue(task.date)
                alpha = if (isEnabled) 1.0f else 0.5f
                setOnClickListener {
                    val context = itemView.context
                    val intent = Intent(context, PomodoroActivity::class.java).apply {
                        putExtra("TASK_DURATION", task.duration.extractDurationInMinutes())
                        putExtra("TASK_ID", task.id)
                        putExtra("ACADEMIC_TASK", task.academicTask)
                        putExtra("PRIORITY", task.priority)
                    }
                    Log.d("PomodoroActivity", "Task: $task")
                    context.startActivity(intent)
                }
            }
        }

        private fun isTaskPastDue(date: String): Boolean {
            return try {
                val taskDate = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(date)
                taskDate?.before(Date()) == true
            } catch (e: Exception) {
                false
            }
        }


        private fun String.extractDurationInMinutes(): String {
            return this.replace("\\D".toRegex(), "").takeIf { it.isNotEmpty() } ?: "25"
        }
    }
}
