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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        setContentView(R.layout.activity_create_task) // Update with actual layout file name

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance().reference

        editTextSubmissionDate = findViewById(R.id.editTextSubmissionDate)
        editTextTaskName = findViewById(R.id.taskName)
        spinnerAcademicTask = findViewById(R.id.spinnerAcademicTask)
        buttonCreateTask = findViewById(R.id.buttonCreateTask)
        spinnerPriority = findViewById(R.id.spinnerPriority)

        // Set up the academic tasks spinner
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

        // Set the adapter to the spinner
        spinnerPriority.adapter = priorityAdapter

        // Set up the academic task spinner
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

        if (taskName.isEmpty() || submissionDate.isEmpty() || selectedAcademicTask.isEmpty() || selectedPriority.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = firebaseAuth.currentUser?.uid ?: return

        // Fetch tasks where status is "done" and count them
        firebaseDatabase.child("tasks").orderByChild("userId").equalTo(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val doneTasks = snapshot.children.filter {
                    it.child("status").value == "done" &&
                            it.child("academicTask").value == selectedAcademicTask
                }
                if (doneTasks.size >= 10) {
                    // If there are 10 or more "done" tasks, calculate average session time if academicTask exists
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
        // Fetch the user schedule from Firebase
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

                fetchExistingTasks(userId, submissionDate, start, end, suggestedDuration, taskName)
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
                    if (taskAcademic == academicTask && taskStatus == "done") {
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
    private fun fetchExistingTasks(userId: String, submissionDate: String, start: String, end: String, suggestedDuration: String, taskName: String) {
        val todayDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // Define adjusted start time, defaulting to the provided start time
        var adjustedStart = start

        // Validate if submission date is today
        if (submissionDate == todayDate) {
            // Ensure the start time is not in the past
            if (start <= currentTime) {
                adjustedStart = currentTime
            }
        }

        firebaseDatabase.child("tasks").orderByChild("submissionDate").equalTo(submissionDate).get()
            .addOnSuccessListener { snapshot ->
                var newSuggestedTimeSlot = adjustedStart // Default to adjusted start time
                var isConflict = false

                if (snapshot.exists()) {
                    val existingTasks = snapshot.children

                    // Check if there's a conflict with existing tasks
                    for (task in existingTasks) {
                        val existingSuggestedTimeSlot = task.child("suggestedTimeSlot").value.toString()
                        val existingSuggestedDuration = task.child("suggestedDuration").value.toString()
                        val existingDurationInMinutes = existingSuggestedDuration.replace(" mins", "").toInt()

                        // Check for time slot conflict
                        if (existingSuggestedTimeSlot == newSuggestedTimeSlot) {
                            isConflict = true
                            // Adjust suggested time slot (e.g., add the existing duration)
                            newSuggestedTimeSlot = suggestNextAvailableTimeSlot(existingSuggestedTimeSlot, existingDurationInMinutes, suggestedDuration.replace(" mins", "").toInt())
                        }
                    }

                    // If there's still a conflict after adjustments, keep adjusting until we find a free slot
                    while (isConflict) {
                        isConflict = false
                        for (task in existingTasks) {
                            val existingSuggestedTimeSlot = task.child("suggestedTimeSlot").value.toString()
                            val existingSuggestedDuration = task.child("suggestedDuration").value.toString()
                            val existingDurationInMinutes = existingSuggestedDuration.replace(" mins", "").toInt()

                            // Check for time slot conflict again
                            if (existingSuggestedTimeSlot == newSuggestedTimeSlot) {
                                isConflict = true
                                newSuggestedTimeSlot = suggestNextAvailableTimeSlot(newSuggestedTimeSlot, existingDurationInMinutes, suggestedDuration.replace(" mins", "").toInt())
                                break // Exit the loop to re-check all tasks
                            }
                        }
                    }

                    // Save task to database with the adjusted time slot
                    saveTaskToDatabase(taskName, newSuggestedTimeSlot, suggestedDuration, submissionDate, userId)
                } else {
                    // No existing tasks, safe to proceed with the suggested time slot
                    val adjustedTimeSlot = suggestedDuration + applyRandomAdjustment(start)
                    saveTaskToDatabase(taskName, adjustedTimeSlot, suggestedDuration, submissionDate, userId)
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch existing tasks: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyRandomAdjustment(time: String): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = dateFormat.parse(time)!!

        // Generate a random number between 10 and 16 minutes
        val randomMinutes = (10..16).random()
        calendar.add(Calendar.MINUTE, randomMinutes)

        return dateFormat.format(calendar.time)
    }

    // Function to suggest the next available time slot based on existing tasks
    private fun suggestNextAvailableTimeSlot(
        existingTimeSlot: String,
        existingDurationInMinutes: Int,
        suggestedDurationInMinutes: Int
    ): String {
        val timeFormat24Hour = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()

        // Parse the existing time slot and add the existing task's duration
        calendar.time = timeFormat24Hour.parse(existingTimeSlot)!!
        calendar.add(Calendar.MINUTE, existingDurationInMinutes)

        // Add the suggested task's duration to find the next available time
        calendar.add(Calendar.MINUTE, suggestedDurationInMinutes)
        return timeFormat24Hour.format(calendar.time)
    }


    private fun randomOf(vararg values: Int): Int {
        return values[(values.indices).random()]
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
                var suggestedDuration = "2 mins" // Fallback duration

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
