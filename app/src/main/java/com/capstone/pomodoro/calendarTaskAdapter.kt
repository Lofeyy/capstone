package com.capstone.pomodoro



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


class CalendartTaskAdapter(private val groupedTasks: Map<String, List<Task>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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
            val view = LayoutInflater.from(parent.context).inflate(R.layout.calendar_item, parent, false)
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

                    // Format the time slot to 12-hour format
                    val originalTimeSlot = task.timeSlot // Assuming task.timeSlot is in 24-hour format, e.g., "22:00"
                    val formattedTimeSlot = try {
                        val originalFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val targetFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val date = originalFormat.parse(originalTimeSlot)
                        targetFormat.format(date)
                    } catch (e: Exception) {
                        originalTimeSlot // Fallback to the original value if parsing fails
                    }
                    holder.taskTimeSlot.text = formattedTimeSlot

                    // Set the priority indicator color
                    when (task.priority) {
                        "Low" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_low_priority)
                        "Medium" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_medium_priority)
                        "High" -> holder.priorityIndicator.setBackgroundResource(R.drawable.circle_high_priority)
                    }

                    // Status handling logic remains unchanged
                    val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                    val taskDated = dateFormat.parse(task.date)
                    val currentDated = Date()

                    val calendartask = Calendar.getInstance()
                    calendartask.time = currentDated
                    calendartask.add(Calendar.DAY_OF_YEAR, -1)
                    val currentDatedMinusOne = calendartask.time

                    if (task.status == "notStarted") {

                            holder.statusText.text = "Pending"

                    } else if (task.status == "Done")  {
                        holder.statusText.text = "Done"
                    }else{
                        holder.statusText.text = "Missed"
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
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val priorityIndicator: View = itemView.findViewById(R.id.priorityIndicator)
        val taskTimeSlot: TextView = itemView.findViewById(R.id.taskTimeSlot)

    }

}
