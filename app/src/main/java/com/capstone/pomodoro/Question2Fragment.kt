package com.capstone.pomodoro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.capstone.pomodoro.databinding.FragmentQuestion2Binding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Question2Fragment : Fragment() {

    private var _binding: FragmentQuestion2Binding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var userId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuestion2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Database and get current user ID
        database = FirebaseDatabase.getInstance().reference
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Show time picker when the start time TextView is clicked
        binding.weekendStartButton.setOnClickListener {
            showTimePicker(true) // true for start time
        }

        // Show time picker when the end time TextView is clicked
        binding.weekendEndButton.setOnClickListener {
            showTimePicker(false) // false for end time
        }

        binding.nextButton.setOnClickListener {
            // Save the selected times to Firebase
            val startTime = binding.weekendStartText.text.toString()
            val endTime = binding.weekendEndText.text.toString()
            if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                saveToFirebase(startTime, endTime)
                // Load Question3Fragment when the user clicks "Next"
                (activity as? QuestionActivity)?.loadFragment(Question3Fragment())
            }
        }

        // Navigate back to Question1Fragment
        binding.backButton.setOnClickListener {
            (activity as? QuestionActivity)?.loadFragment(Question1Fragment())
        }

        // Check if the user already has a schedule, and pre-fill if exists
        loadExistingSchedule()
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H) // Use 12-hour format
            .setTitleText("Select Time")
            .setHour(12) // Start at 12 AM
            .setMinute(0)
            .build()

        picker.show(parentFragmentManager, "TIME_PICKER")

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute

            // Format hour to display in 12-hour format with AM/PM
            val formattedHour = if (hour == 0) 12 else hour // Convert 0 to 12 for AM/PM
            val period = if (hour < 12) "AM" else "PM" // Determine AM/PM

            // Format time as hh:mm a
            val formattedTime = String.format("%02d:%02d %s", formattedHour, minute, period)

            // Set the selected time to the appropriate TextView
            if (isStartTime) {
                binding.weekendStartText.text = formattedTime
            } else {
                binding.weekendEndText.text = formattedTime
            }
        }
    }

    private fun saveToFirebase(startTime: String, endTime: String) {
        val userScheduleRef = database.child("user_schedule").child(userId)

        // Save selected times directly under user_schedule
        userScheduleRef.child("weekend_start").setValue(startTime) // Save start time
        userScheduleRef.child("weekend_end").setValue(endTime) // Save end time
    }

    private fun loadExistingSchedule() {
        val userScheduleRef = database.child("user_schedule").child(userId)

        userScheduleRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // If schedule data exists, pre-fill the fields
                if (snapshot.exists()) {
                    val weekendStart = snapshot.child("weekend_start").getValue(String::class.java)
                    val weekendEnd = snapshot.child("weekend_end").getValue(String::class.java)

                    if (!weekendStart.isNullOrEmpty()) {
                        binding.weekendStartText.text = weekendStart
                    }

                    if (!weekendEnd.isNullOrEmpty()) {
                        binding.weekendEndText.text = weekendEnd
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the error if necessary
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }
}
