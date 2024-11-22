package com.capstone.pomodoro

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CreateTaskTest : AppCompatActivity() {

    private lateinit var editTextSubmissionDate: EditText
    private lateinit var editTextTaskName: EditText
    private lateinit var spinnerAcademicTask: Spinner
    private lateinit var buttonCreateTask: MaterialButton
    private lateinit var spinnerPriority: Spinner
    private lateinit var selectedPriority: String
    private lateinit var selectedAcademicTask: String
    private lateinit var firebaseDatabase: DatabaseReference
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_task)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance().reference

        editTextSubmissionDate = findViewById(R.id.editTextSubmissionDate)
        editTextTaskName = findViewById(R.id.taskName)
        spinnerAcademicTask = findViewById(R.id.spinnerAcademicTask)
        buttonCreateTask = findViewById(R.id.buttonCreateTask)
        spinnerPriority = findViewById(R.id.spinnerPriority)


        val priorityOptions = arrayOf("Low", "Medium", "High")
        val priorityAdapter = object : ArrayAdapter<String>(this, R.layout.spinner_item_material, priorityOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                return view
            }
        }


        spinnerPriority.adapter = priorityAdapter


        val academicTaskOptions = arrayOf("Researching", "Reviewing", "Reading", "Writing", "Assignments")
        val academicTaskAdapter = object : ArrayAdapter<String>(this, R.layout.spinner_item_material, academicTaskOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                return view
            }
        }

        // Set the adapter to the spinner
        spinnerAcademicTask.adapter = academicTaskAdapter

        val settingsButton: ImageButton = findViewById(R.id.settings_button)
        val backButton: ImageButton = findViewById(R.id.back_button)
        backButton.setOnClickListener { onBackPressed() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        spinnerAcademicTask.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedAcademicTask = academicTaskOptions[position]
                Log.d("CreateTaskActivity", "Selected Academic Task: $selectedAcademicTask")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedAcademicTask = ""
            }
        }



        spinnerPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedPriority = priorityOptions[position]
                Log.d("CreateTaskActivity", "Selected Priority: $selectedPriority")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedPriority = ""
            }
        }

        editTextSubmissionDate.setOnClickListener {
            showDatePicker()
        }

        buttonCreateTask.setOnClickListener {
            suggestScheduleAndSaveTask()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format("%02d/%02d/%04d", selectedMonth + 1, selectedDay, selectedYear)
                editTextSubmissionDate.setText(formattedDate)
                Log.d("CreateTaskActivity", "Selected Date: $formattedDate")
            },
            year, month, day
        )

        // Set the minimum date to today
        datePickerDialog.datePicker.minDate = calendar.timeInMillis
        datePickerDialog.show()
    }

    private fun suggestScheduleAndSaveTask() {
        val taskName = editTextTaskName.text.toString().trim()
        val submissionDate = editTextSubmissionDate.text.toString().trim()
        val priority = selectedPriority
        if (taskName.isEmpty() || submissionDate.isEmpty() || selectedAcademicTask.isEmpty() || selectedPriority.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = firebaseAuth.currentUser?.uid ?: return

        // Fetch tasks where status is "done" and count them
        firebaseDatabase.child("tasks").orderByChild("userId").equalTo(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val doneTasks = snapshot.children.filter {
                    it.child("status").value == "Done" &&
                            it.child("academicTask").value == selectedAcademicTask
                }
                if (doneTasks.size >= 10) {
                    calculateSuggestedDurationFromSessions(userId, selectedAcademicTask) { suggestedDuration ->
                        fetchUserScheduleAndSuggestTask(userId, submissionDate, taskName, suggestedDuration)
                    }
                } else {
                    // Fallback to regular suggested duration calculation based on priority
                    calculateSuggestedDuration(userId, selectedAcademicTask) { suggestedDuration ->
                        fetchUserScheduleAndSuggestTask(userId, submissionDate, taskName, suggestedDuration)
                    }
                }
            } else {
                // Default behavior if no tasks found
                calculateSuggestedDuration(userId, selectedAcademicTask) { suggestedDuration ->
                    fetchUserScheduleAndSuggestTask(userId, submissionDate, taskName, suggestedDuration)
                }
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to fetch tasks: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun fetchUserScheduleAndSuggestTask(userId: String, submissionDate: String, taskName: String, suggestedDuration: String) {

        firebaseDatabase.child("user_schedule").child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val weekdaysStart = snapshot.child("week_days_start").value.toString()
                val weekdaysEnd = snapshot.child("week_days_end").value.toString()
                val weekendStart = snapshot.child("weekend_start").value.toString()
                val weekendEnd = snapshot.child("weekend_end").value.toString()

                val calendar = Calendar.getInstance()
                calendar.time = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(submissionDate)!!
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

                val (start, end) = if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                    Pair(weekendStart, weekendEnd)
                } else {
                    Pair(weekdaysStart, weekdaysEnd)
                }

                fetchExistingTasks(userId, submissionDate, start, end, suggestedDuration, taskName ,selectedPriority)
            } else {
                Toast.makeText(this, "User schedule not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to fetch user schedule: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateSuggestedDurationFromSessions(userId: String, academicTask: String, callback: (String) -> Unit) {
        firebaseDatabase.child("tasks").orderByChild("userId").equalTo(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                var totalPomodoroSessions = 0.0
                var sessionCount = 0
                var totalTaskCount = 0 // To keep track of the total number of tasks

                for (task in snapshot.children) {
                    val taskAcademic = task.child("academicTask").value.toString()
                    val taskStatus = task.child("status").value.toString()

                    // Check if task matches academicTask and has "done" status
                    if (taskAcademic == academicTask && taskStatus == "Done") {
                        totalTaskCount++ // Increment the total task count
                        val sessions = task.child("sessions").value?.toString() ?: "0" // Default to "0" if sessions is null
                        if (sessions.isNotEmpty() && sessions.contains("/")) { // Check if sessions is in the correct format
                            val parts = sessions.split("/")
                            val actualSessions = parts.getOrNull(0)?.toIntOrNull() ?: 0 // Safely get the first number
                            totalPomodoroSessions += actualSessions // Add to the total pomodoro sessions
                            sessionCount++
                        }
                    }
                }


                if (sessionCount > 0 && totalTaskCount > 0) {
                    val averageSessions = totalPomodoroSessions / sessionCount
                    Log.d("Pomodoro", "Average Sessions: $averageSessions")
                    val averageSessionMinutes = averageSessions * 25.0
                    Log.d("Pomodoro", "Average Session Minutes: $averageSessionMinutes")

                    val adjustment = randomOf(5, 11)
                    val adjustedDuration = averageSessionMinutes + adjustment
                    Log.d("Pomodoro", "Adjusted Duration: $adjustedDuration")
                    callback("${adjustedDuration.toInt()} mins")
                } else {
                    // Fallback duration if no sessions found for academicTask with "done" status
                    callback("25 mins")
                }
            } else {
                callback("25 mins") // Default if no tasks found
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to fetch tasks: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun fetchExistingTasks(
        userId: String,
        submissionDate: String,
        start: String,
        end: String,
        suggestedDuration: String,
        taskName: String,
        priority: String
    ) {
        val manilaTimeZone = TimeZone.getTimeZone("Asia/Manila")
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).apply { timeZone = manilaTimeZone }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = manilaTimeZone }

        val todayDate = dateFormat.format(Date())
        val currentTime = timeFormat.format(Date())
        var adjustedStart = convertTo24HourFormat(start)
        val suggestedDurationInMinutes = suggestedDuration.toIntOrNull() ?: 0

        // Adjust start time if submission date is today and start time is in the past
        if (submissionDate == todayDate && adjustedStart <= currentTime) {
            adjustedStart = currentTime
        }

        // Calculate the adjusted end time
        val adjustedStartDate = timeFormat.parse(adjustedStart)
        val adjustedEndDate = Calendar.getInstance().apply {
            time = adjustedStartDate
            add(Calendar.MINUTE, suggestedDurationInMinutes)
        }.time
        val adjustedEnd = timeFormat.format(adjustedEndDate)

        // Check if the adjusted end time exceeds the provided end time or if the duration is too long
//        if (adjustedEnd > convertTo24HourFormat(end)) {
//            Toast.makeText(
//                this,
//                "No available timeslot for today. Suggested duration exceeds available time.",
//                Toast.LENGTH_SHORT
//            ).show()
//            return
//        }

        firebaseDatabase.child("tasks").orderByChild("submissionDate").equalTo(submissionDate).get()
            .addOnSuccessListener { snapshot ->
                var newSuggestedTimeSlot = adjustedStart

                if (snapshot.exists()) {
                    val tasksByPriority = mapOf(
                        "High" to mutableListOf<DataSnapshot>(),
                        "Medium" to mutableListOf<DataSnapshot>(),
                        "Low" to mutableListOf<DataSnapshot>()
                    )

                    snapshot.children.forEach { task ->
                        val taskPriority = task.child("priority").value.toString()
                        tasksByPriority[taskPriority]?.add(task)
                    }

                    // Adjust time slots for tasks based on priority
                    if (priority == "High") {
                        newSuggestedTimeSlot = adjustPriorityTimeSlots(
                            tasksByPriority["High"]!!, newSuggestedTimeSlot, false, "earlier"
                        )
                        newSuggestedTimeSlot = adjustPriorityTimeSlots(
                            tasksByPriority["Medium"]!!, newSuggestedTimeSlot, false, "after"
                        )
                    }

                    // For Medium priority
                    if (priority == "Medium") {
                        newSuggestedTimeSlot = adjustPriorityTimeSlots(
                            tasksByPriority["Medium"]!!, newSuggestedTimeSlot, false, "after"
                        )
                    }

                    // For Low priority tasks, check if there are existing tasks with the same priority
                    if (priority == "Low") {
                        newSuggestedTimeSlot = adjustLowPriorityTasks(tasksByPriority["Low"]!!, newSuggestedTimeSlot, false)
                    }

                    saveTaskToDatabase(taskName, newSuggestedTimeSlot, suggestedDuration, submissionDate, userId)
                } else {
                    saveTaskToDatabase(taskName, newSuggestedTimeSlot, suggestedDuration, submissionDate, userId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch existing tasks: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun adjustPriorityTimeSlots(
        tasks: List<DataSnapshot>,
        initialTimeSlot: String,
        isPassTime: Boolean,
        priorityOrder: String
    ): String {
        var adjustedTimeSlot = initialTimeSlot
        var isConflict = false

        // Ensure tasks are placed in the correct order with no conflicts
        tasks.forEach { task ->
            val taskDuration = task.child("suggestedDuration").value.toString().replace(" mins", "").toInt()
            val taskStartTime = task.child("suggestedTimeSlot").value.toString()

            when (priorityOrder) {
                "earlier" -> {
                    if (adjustedTimeSlot > taskStartTime) {
                        adjustedTimeSlot = subtractDurationFromTime(taskStartTime, taskDuration)
                        task.ref.child("suggestedTimeSlot").setValue(adjustedTimeSlot)
                    }
                }
                "after" -> {
                    // Move the new task after the current task's end time
                    val taskEndTime = addDurationToTime(taskStartTime, taskDuration)
                    adjustedTimeSlot = addDurationToTime(taskEndTime, taskDuration) // Adding 5 minutes buffer to avoid overlap

                    // Ensure no conflicts with existing tasks
                    isConflict = checkForConflict(tasks, adjustedTimeSlot, taskDuration)
                    while (isConflict) {
                        adjustedTimeSlot = addDurationToTime(adjustedTimeSlot, taskDuration) // Increment by task duration
                        isConflict = checkForConflict(tasks, adjustedTimeSlot, taskDuration)
                    }
                }
            }
        }

        return adjustedTimeSlot
    }

    private fun checkForConflict(
        existingTasks: List<DataSnapshot>,
        newSuggestedTimeSlot: String,
        taskDuration: Int
    ): Boolean {
        val newStartMinutes = convertTimeSlotToMinutes(newSuggestedTimeSlot)
        val newEndMinutes = newStartMinutes + taskDuration

        for (task in existingTasks) {
            val existingSuggestedTimeSlot = task.child("suggestedTimeSlot").value.toString()
            val existingSuggestedDuration = task.child("suggestedDuration").value.toString()
            val existingDurationInMinutes = existingSuggestedDuration.replace(" mins", "").toInt()

            val existingStartMinutes = convertTimeSlotToMinutes(existingSuggestedTimeSlot)
            val existingEndMinutes = existingStartMinutes + existingDurationInMinutes

            if (newEndMinutes > existingStartMinutes && newStartMinutes < existingEndMinutes) {
                return true
            }
        }
        return false
    }

    private fun addDurationToTime(time: String, duration: Int): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = timeFormat.parse(time)!!
        calendar.add(Calendar.MINUTE, duration)
        calendar.add(Calendar.MINUTE, 15)
        return timeFormat.format(calendar.time)
    }

    private fun subtractDurationFromTime(time: String, duration: Int): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = timeFormat.parse(time)!!
        calendar.add(Calendar.MINUTE, -duration)
        return timeFormat.format(calendar.time)
    }
    private fun adjustLowPriorityTasks(
        existingLowPriorityTasks: List<DataSnapshot>,
        initialTimeSlot: String,
        isPassTime: Boolean
    ): String {
        var adjustedTimeSlot = initialTimeSlot

        // Check if any tasks with Low priority exist
        if (existingLowPriorityTasks.isNotEmpty()) {
            val lastLowPriorityTask = existingLowPriorityTasks.last()
            val lastLowPriorityTaskDuration = lastLowPriorityTask.child("suggestedDuration").value.toString().replace(" mins", "").toInt()
            val lastLowPriorityTaskEndTime = addDurationToTime(
                lastLowPriorityTask.child("suggestedTimeSlot").value.toString(),
                lastLowPriorityTaskDuration
            )

            // Insert new Low priority task after the last Low priority task
            adjustedTimeSlot = lastLowPriorityTaskEndTime
        }

        return adjustedTimeSlot
    }

    private fun convertTimeSlotToMinutes(timeSlot: String): Int {
        val (hours, minutes) = timeSlot.split(":").map { it.toInt() }
        return hours * 60 + minutes
    }

    private fun randomOf(vararg values: Int): Int {
        return values[(values.indices).random()]
    }

    private fun convertTo24HourFormat(timeSlot: String): String {
        // Define the expected time format (24-hour format)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date: Date

        try {
            // Parse the time slot string into a Date object
            date = sdf.parse(timeSlot) ?: throw ParseException("Invalid time format", 0)
        } catch (e: ParseException) {
            throw ParseException("Unparseable date: $timeSlot", 0)
        }

        // Return the formatted time as a string in 24-hour format
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }


    private fun calculateSuggestedDuration(userId: String, academicTask: String, callback: (String) -> Unit) {
        firebaseDatabase.child("user_schedule").child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Get the user's priorities
                val priorities = listOf(
                    snapshot.child("prio1").value.toString(),
                    snapshot.child("prio2").value.toString(),
                    snapshot.child("prio3").value.toString(),
                    snapshot.child("prio4").value.toString(),
                    snapshot.child("prio5").value.toString()
                )

                // Map to hold the dynamic durations based on priority
                val durationMapping = mapOf(
                    "prio1" to (50..65),
                    "prio2" to (45..50),
                    "prio3" to (35..40),
                    "prio4" to (30..35),
                    "prio5" to (25..25)
                )

                // Initialize variable to track the suggested duration
                var suggestedDuration = "25 mins" // Fallback duration

                // Determine the suggested duration based on the academic task and priorities
                priorities.forEachIndexed { index, priority ->
                    if (priority == academicTask) {
                        val range = durationMapping["prio${index + 1}"]
                        if (range != null) {
                            suggestedDuration = "${range.last} mins" // Get the maximum duration for this priority
                        }
                    }
                }

                // Return the suggested duration via callback
                callback(suggestedDuration)
            } else {
                Toast.makeText(this, "User schedule not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to fetch user schedule: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun saveTaskToDatabase(
        taskName: String,
        suggestedTimeSlot: String,
        suggestedDuration: String,
        submissionDate: String,
        userId: String
    ) {
        val taskId = firebaseDatabase.child("tasks").push().key ?: return

        val taskData = mapOf(
            "taskId" to taskId,
            "taskName" to taskName,
            "academicTask" to selectedAcademicTask,
            "submissionDate" to submissionDate,
            "suggestedTimeSlot" to suggestedTimeSlot,
            "suggestedDuration" to suggestedDuration,
            "priority" to selectedPriority,
            "status" to "notStarted",
            "userId" to userId
        )

        firebaseDatabase.child("tasks").child(taskId).setValue(taskData)
            .addOnSuccessListener {
                Toast.makeText(this, "Task created successfully", Toast.LENGTH_SHORT).show()
                // Redirect to TaskActivity
                val intent = Intent(this, TaskActivity::class.java)
                startActivity(intent)
                finish() // Optional: Call this if you want to close the current activity
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}