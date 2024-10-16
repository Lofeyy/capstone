package com.capstone.pomodoro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class Question3Fragment : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var userId: String
    private val selectedTasks = mutableListOf<String?>() // To keep track of selected tasks

    // List of all task options
    private val allTasks = listOf("Studying", "Researching", "Reviewing", "Reading", "Writing", "Assignments")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_question3, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Database and get current user ID
        database = FirebaseDatabase.getInstance().reference
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Load existing data from Firebase if available
        loadExistingData()

        // Set up button click listeners
        setupButtonListeners(view)

        // Set up back to Question 2 functionality
        setupBackToQuestion2(view)

        // Set up the Next button functionality
        setupNextButton(view)
    }

    private fun setupButtonListeners(view: View) {
        val buttonStudying = view.findViewById<Button>(R.id.buttonStudying)
        val buttonResearching = view.findViewById<Button>(R.id.buttonResearching)
        val buttonReviewing = view.findViewById<Button>(R.id.buttonReviewing)
        val buttonReading = view.findViewById<Button>(R.id.buttonReading)
        val buttonWriting = view.findViewById<Button>(R.id.buttonWriting)
        val buttonAssignments = view.findViewById<Button>(R.id.buttonAssignments)

        buttonStudying.setOnClickListener { toggleTaskSelection("Studying", buttonStudying) }
        buttonResearching.setOnClickListener { toggleTaskSelection("Researching", buttonResearching) }
        buttonReviewing.setOnClickListener { toggleTaskSelection("Reviewing", buttonReviewing) }
        buttonReading.setOnClickListener { toggleTaskSelection("Reading", buttonReading) }
        buttonWriting.setOnClickListener { toggleTaskSelection("Writing", buttonWriting) }
        buttonAssignments.setOnClickListener { toggleTaskSelection("Assignments", buttonAssignments) }
    }

    private fun toggleTaskSelection(task: String, button: Button) {
        // If the task is already selected, deselect it
        if (selectedTasks.contains(task)) {
            selectedTasks.remove(task)
            button.isSelected = false
            button.setBackgroundColor(resources.getColor(android.R.color.transparent)) // Deselect color
        } else {
            // If not selected, select it and mark the button
            selectedTasks.add(task)
            button.isSelected = true
            button.setBackgroundColor(resources.getColor(android.R.color.holo_blue_light)) // Selected color
        }

        // Save all selected tasks to Firebase
        saveToFirebase()

        // Check if all tasks have been selected to enable the Next button
        checkIfAllTasksSelected()
    }

    private fun loadExistingData() {
        val userScheduleRef = database.child("user_schedule").child(userId)
        userScheduleRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    selectedTasks.clear()
                    for (i in 1..6) {
                        val task = snapshot.child("prio$i").getValue(String::class.java)
                        if (task != null) {
                            selectedTasks.add(task)
                        }
                    }
                    updateButtonsUI(view ?: return)

                    // Check if all tasks have been selected after loading existing data
                    checkIfAllTasksSelected()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveToFirebase() {
        val userScheduleRef = database.child("user_schedule").child(userId)

        // Clear previous priorities
        for (i in 1..6) {
            userScheduleRef.child("prio$i").removeValue()
        }

        // Save selected tasks to Firebase based on their priorities
        selectedTasks.forEachIndexed { index, task ->
            if (index < 6) { // Only save the first 6 tasks
                userScheduleRef.child("prio${index + 1}").setValue(task)
            }
        }
    }

    private fun updateButtonsUI(view: View) {
        val buttons = mapOf(
            "Studying" to view.findViewById<Button>(R.id.buttonStudying),
            "Researching" to view.findViewById<Button>(R.id.buttonResearching),
            "Reviewing" to view.findViewById<Button>(R.id.buttonReviewing),
            "Reading" to view.findViewById<Button>(R.id.buttonReading),
            "Writing" to view.findViewById<Button>(R.id.buttonWriting),
            "Assignments" to view.findViewById<Button>(R.id.buttonAssignments)
        )

        buttons.forEach { (task, button) ->
            if (selectedTasks.contains(task)) {
                button.isSelected = true
                button.setBackgroundColor(resources.getColor(R.color.primary_dark, null))
            } else {
                button.isSelected = false
                button.setBackgroundColor(resources.getColor(R.color.button_color, null))
            }
        }
    }

    private fun checkIfAllTasksSelected() {
        val nextButton = view?.findViewById<Button>(R.id.nextButton)
        if (selectedTasks.size == allTasks.size) {
            nextButton?.visibility = View.VISIBLE // Show the Next button
        } else {
            nextButton?.visibility = View.GONE // Hide the Next button
        }
    }

    private fun setupBackToQuestion2(view: View) {
        val backButton = view.findViewById<Button>(R.id.backButton)
        backButton.setOnClickListener {
            (activity as? QuestionActivity)?.loadFragment(Question2Fragment())
        }
    }

    private fun setupNextButton(view: View) {
        val nextButton = view.findViewById<Button>(R.id.nextButton)
        nextButton.setOnClickListener {
            val intent = Intent(activity, MainActivity::class.java)
            startActivity(intent)
            activity?.finish() // Optional: Finish the current activity to prevent returning to it
        }
    }
}
