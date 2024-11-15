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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CreateTaskActivity : AppCompatActivity() {

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

                calculateSuggestedDuration(userId, selectedAcademicTask) { suggestedDuration ->
                    fetchUserScheduleAndSuggestTask(userId, submissionDate, taskName, suggestedDuration)}
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

    private fun fetchExistingTasks(
        userId: String,
        submissionDate: String,
        start: String,
        end: String,
        suggestedDuration: String,
        taskName: String
    ) {
        val manilaTimeZone = TimeZone.getTimeZone("Asia/Manila")
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val timeFormat24Hour = SimpleDateFormat("HH:mm", Locale.getDefault())

        dateFormat.timeZone = manilaTimeZone
        timeFormat24Hour.timeZone = manilaTimeZone

        val todayDate = dateFormat.format(Date())
        val currentTime = timeFormat24Hour.format(Date())

        // Parse start time
        val startTimeWithoutAmPm = start.replace(Regex("\\s?(AM|PM|am|pm)"), "")
        val startDate = timeFormat24Hour.parse(startTimeWithoutAmPm)

        var newSuggestedTimeSlot = start

        firebaseDatabase.child("tasks").orderByChild("submissionDate").equalTo(submissionDate).get()
            .addOnSuccessListener { snapshot ->
                var isConflict = false

                if (snapshot.exists()) {
                    val existingTasks = snapshot.children

                    for (task in existingTasks) {
                        val existingSuggestedTimeSlot = task.child("suggestedTimeSlot").value.toString()
                        val existingSuggestedDuration = task.child("suggestedDuration").value.toString()
                        val existingDurationInMinutes = existingSuggestedDuration.replace(" mins", "").toInt()

                        // Check for time slot conflict
                        if (existingSuggestedTimeSlot == newSuggestedTimeSlot) {
                            isConflict = true
                            newSuggestedTimeSlot = suggestNextAvailableTimeSlot(
                                existingSuggestedTimeSlot,
                                existingDurationInMinutes
                            )
                        }
                    }

                    // Adjust time slots if there's still a conflict
                    while (isConflict) {
                        isConflict = false
                        for (task in existingTasks) {
                            val existingSuggestedTimeSlot = task.child("suggestedTimeSlot").value.toString()
                            val existingSuggestedDuration = task.child("suggestedDuration").value.toString()
                            val existingDurationInMinutes = existingSuggestedDuration.replace(" mins", "").toInt()

                            // Re-check for conflicts
                            if (existingSuggestedTimeSlot == newSuggestedTimeSlot) {
                                isConflict = true
                                newSuggestedTimeSlot = suggestNextAvailableTimeSlot(
                                    newSuggestedTimeSlot,
                                    existingDurationInMinutes
                                )
                                break
                            }
                        }
                    }
                }

                // Save task with adjusted or original time slot
                saveTaskToDatabase(taskName, newSuggestedTimeSlot, suggestedDuration, submissionDate, userId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch existing tasks: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun suggestNextAvailableTimeSlot(
        currentTimeSlot: String,
        existingDuration: Int
    ): String {
        val timeFormat24Hour = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeFormat24Hour.timeZone = TimeZone.getTimeZone("Asia/Manila") // Set to Manila timezone

        // Parse the current time slot accurately
        val currentSlotTime = try {
            timeFormat24Hour.parse(currentTimeSlot)
        } catch (e: ParseException) {
            null
        }

        if (currentSlotTime == null) {
            // If parsing fails, return the original time slot as a fallback
            return currentTimeSlot
        }

        // Explicitly set Calendar instance with parsed hour and minute
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
            time = currentSlotTime
            set(Calendar.HOUR_OF_DAY, get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, get(Calendar.MINUTE))
            add(Calendar.MINUTE, existingDuration + (15..17).random()) // Add duration and a random gap
        }

        return timeFormat24Hour.format(calendar.time)
    }



    private fun calculateSuggestedDuration(userId: String, academicTask: String, callback: (String) -> Unit) {
        firebaseDatabase.child("user_schedule").child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val priorities = listOf(
                    snapshot.child("prio1").value.toString(),
                    snapshot.child("prio2").value.toString(),
                    snapshot.child("prio3").value.toString(),
                    snapshot.child("prio4").value.toString(),
                    snapshot.child("prio5").value.toString()
                )

                val durationMapping = mapOf(
                    "prio1" to (50..65),
                    "prio2" to (45..50),
                    "prio3" to (35..40),
                    "prio4" to (30..35),
                    "prio5" to (25..25)
                )

                var suggestedDuration = "25 mins"

                priorities.forEachIndexed { index, priority ->
                    if (priority == academicTask) {
                        val range = durationMapping["prio${index + 1}"]
                        if (range != null) {
                            suggestedDuration = "${range.last} mins"
                        }
                    }
                }

                callback(suggestedDuration)
            } else {
                Toast.makeText(this, "User schedule not found", Toast.LENGTH_SHORT).show()
            }
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
