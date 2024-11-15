package com.capstone.pomodoro

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateTaskDialogFragment : DialogFragment() {

    private lateinit var editTextSubmissionDate: TextInputEditText
    private lateinit var editTextTaskName: TextInputEditText
    private lateinit var spinnerAcademicTask: Spinner
    private lateinit var buttonCreateTask: com.google.android.material.button.MaterialButton
    private lateinit var spinnerPriority: Spinner
    private lateinit var selectedPriority: String
    private lateinit var selectedAcademicTask: String
    private lateinit var firebaseDatabase: DatabaseReference
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateDialog(savedInstanceState: Bundle?): androidx.appcompat.app.AlertDialog {
        val dialogView = requireActivity().layoutInflater.inflate(R.layout.dialog_create_task, null)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance().reference

        editTextSubmissionDate = dialogView.findViewById(R.id.editTextSubmissionDate)
        editTextTaskName = dialogView.findViewById(R.id.taskName)
        spinnerAcademicTask = dialogView.findViewById(R.id.spinnerAcademicTask)
        buttonCreateTask = dialogView.findViewById(R.id.buttonCreateTask)
        spinnerPriority = dialogView.findViewById(R.id.spinnerPriority)

        // Set up the academic tasks spinner
        val academicTaskOptions = arrayOf("Studying", "Researching", "Reviewing", "Reading", "Writing", "Assignments")
        val academicTaskAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, academicTaskOptions)
        academicTaskAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAcademicTask.adapter = academicTaskAdapter

        spinnerAcademicTask.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedAcademicTask = academicTaskOptions[position]
                Log.d("CreateTaskDialog", "Selected Academic Task: $selectedAcademicTask")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedAcademicTask = ""
            }
        }

        // Set up the priority spinner
        val priorityOptions = arrayOf("Low", "Medium", "High")
        val priorityAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, priorityOptions)
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPriority.adapter = priorityAdapter

        spinnerPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedPriority = priorityOptions[position]
                Log.d("CreateTaskDialog", "Selected Priority: $selectedPriority")
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

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Task")
            .setView(dialogView)
            .setNegativeButton("Close") { dialog, _ -> dialog.dismiss() }
            .create()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format("%02d/%02d/%04d", selectedMonth + 1, selectedDay, selectedYear)
                editTextSubmissionDate.setText(formattedDate)
                Log.d("CreateTaskDialog", "Selected Date: $formattedDate")
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
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = firebaseAuth.currentUser?.uid ?: return

        // Fetch the user schedule from Firebase
        firebaseDatabase.child("user_schedule").child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val weekdaysStart = snapshot.child("week_days_start").value.toString()
                val weekdaysEnd = snapshot.child("week_days_end").value.toString()
                val weekendStart = snapshot.child("weekend_start").value.toString()
                val weekendEnd = snapshot.child("weekend_end").value.toString()

                // Check if the submission date falls on a weekend
                val calendar = Calendar.getInstance()
                calendar.time = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(submissionDate)!!
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

                // Determine the correct start and end times based on the day of the week
                val (start, end) = if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                    Pair(weekendStart, weekendEnd) // It's the weekend
                } else {
                    Pair(weekdaysStart, weekdaysEnd) // It's a weekday
                }

                // Calculate suggested duration based on selected priority
                calculateSuggestedDuration(userId, selectedAcademicTask) { suggestedDuration ->
                    // Fetch existing tasks to check for conflicts
                    fetchExistingTasks(userId, submissionDate, start, end, suggestedDuration, taskName)
                }
            } else {
                Toast.makeText(requireContext(), "User schedule not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Failed to fetch user schedule: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchExistingTasks(userId: String, submissionDate: String, start: String, end: String, suggestedDuration: String, taskName: String) {
        firebaseDatabase.child("tasks").orderByChild("submissionDate").equalTo(submissionDate).get()
            .addOnSuccessListener { snapshot ->
                var newSuggestedTimeSlot = start // Default to start time
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
                            // Adjust suggested time slot (e.g., add the existing duration)
                            newSuggestedTimeSlot = suggestNextAvailableTimeSlot(existingSuggestedTimeSlot, existingDurationInMinutes)
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
                                newSuggestedTimeSlot = suggestNextAvailableTimeSlot(newSuggestedTimeSlot, existingDurationInMinutes)
                                break // Exit the loop to re-check all tasks
                            }
                        }
                    }

                    // Save task to database with the adjusted time slot
                    saveTaskToDatabase(taskName, newSuggestedTimeSlot, suggestedDuration, submissionDate, userId)
                } else {
                    // No existing tasks, safe to proceed with the suggested time slot
                    val adjustedTimeSlot = applyRandomAdjustment(start) // Apply adjustment when no conflicts
                    saveTaskToDatabase(taskName, adjustedTimeSlot, suggestedDuration, submissionDate, userId)
                }
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to fetch existing tasks: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to suggest the next available time slot based on existing tasks
    private fun suggestNextAvailableTimeSlot(existingSlot: String, existingDuration: Int): String {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val date = timeFormat.parse(existingSlot)

        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.MINUTE, existingDuration + 10)

        return timeFormat.format(calendar.time)
    }

    // Function to apply random adjustment (0, 5, 10, or 20 minutes) to the time slot
    private fun applyRandomAdjustment(timeSlot: String): String {
        val adjustment = randomOf(2, 5, 10, 20) // Random adjustment in minutes
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()) // Change to 12-hour format with AM/PM
        val date = timeFormat.parse(timeSlot)

        // Add the random adjustment to the existing time
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.MINUTE, adjustment)

        return timeFormat.format(calendar.time)
    }

    private fun randomOf(vararg values: Int): Int {
        return values[(values.indices).random()] // Pick a random value from the input array
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
                    snapshot.child("prio5").value.toString(),
                    snapshot.child("prio6").value.toString()
                )

                // Map to hold the dynamic durations based on priority
                val durationMapping = mapOf(
                    "prio1" to (50..63),
                    "prio2" to (45..50),
                    "prio3" to (35..40),
                    "prio4" to (30..35),
                    "prio5" to (25..25),
                    "prio6" to (25..25)
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
                Toast.makeText(requireContext(), "User schedule not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Failed to fetch user schedule: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun saveTaskToDatabase(taskName: String, suggestedTimeSlot: String, suggestedDuration: String, submissionDate: String, userId: String) {
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
                Toast.makeText(requireContext(), "Task created successfully", Toast.LENGTH_SHORT).show()
                dismiss() // Close the dialog
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to create task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
