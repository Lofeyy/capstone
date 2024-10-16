package com.capstone.pomodoro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.capstone.pomodoro.databinding.FragmentQuestion1Binding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class Question1Fragment : Fragment() {

    private var _binding: FragmentQuestion1Binding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var userId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuestion1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Database and get current user ID
        database = FirebaseDatabase.getInstance().reference
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        binding.weekDaysStartButton.setOnClickListener {
            showTimePicker(true) // true for start time
        }

        binding.weekDaysEndButton.setOnClickListener {
            showTimePicker(false) // false for end time
        }

        binding.nextButton.setOnClickListener {
            // Save the selected times to Firebase
            val startTime = binding.weekDaysStartText.text.toString()
            val endTime = binding.weekDaysEndText.text.toString()
            if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                saveToFirebase(startTime, endTime)
                // Load Question2Fragment when the user clicks "Next"
                (activity as? QuestionActivity)?.loadFragment(Question2Fragment())
            }
        }
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H) // Use 12-hour format
            .setTitleText("Select Time")
            .setHour(4) // You can set this to any default starting hour
            .setMinute(0)
            .build()

        picker.show(parentFragmentManager, "TIME_PICKER")

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute

            // Format hour to display in 12-hour format with AM/PM
            val formattedHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
            val period = if (hour < 12) "AM" else "PM" // Determine AM/PM

            // Format time as hh:mm a
            val formattedTime = String.format("%02d:%02d %s", formattedHour, minute, period)

            // Set the selected time to the appropriate TextView
            if (isStartTime) {
                binding.weekDaysStartText.text = formattedTime
            } else {
                binding.weekDaysEndText.text = formattedTime
            }
        }
    }

    private fun saveToFirebase(startTime: String, endTime: String) {
        val userScheduleRef = database.child("user_schedule").child(userId)

        // Create a map to save selected times and user ID
        val timeData = hashMapOf(
            "week_days_start" to startTime,
            "week_days_end" to endTime,
            "user_id" to userId // Add the user ID to the saved data
        )

        userScheduleRef.setValue(timeData) // Save the selected times to Firebase
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }
}
