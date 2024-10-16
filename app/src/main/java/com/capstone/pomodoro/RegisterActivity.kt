package com.capstone.pomodoro

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.capstone.pomodoro.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.registerButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
            val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

            if (validateInput(username, email, password, confirmPassword)) {
                registerUser(username, email, password)
            }
        }

        binding.loginTextView.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun validateInput(username: String, email: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (username.isEmpty()) {
            binding.usernameEditText.error = "Username is required"
            isValid = false
        }

        if (email.isEmpty()) {
            binding.emailEditText.error = "Email is required"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.passwordEditText.error = "Password is required"
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordEditText.error = "Confirm Password is required"
            isValid = false
        }

        if (password != confirmPassword) {
            binding.confirmPasswordEditText.error = "Passwords do not match"
            isValid = false
        }

        if (!isValid) {
            Toast.makeText(this, "Please fill all the fields", Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    private fun registerUser(username: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        val userId = it.uid
                        val userMap = hashMapOf(
                            "username" to username,
                            "email" to email
                        )

                        database.reference.child("users").child(userId).setValue(userMap)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                                    navigateToLogin()
                                } else {
                                    Toast.makeText(this, "Database error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                } else {
                    Toast.makeText(this, "Authentication error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}
