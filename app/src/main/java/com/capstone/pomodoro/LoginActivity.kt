package com.capstone.pomodoro

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.*

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var googleSignInButton: com.google.android.gms.common.SignInButton
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var usersRef: DatabaseReference
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        googleSignInButton = findViewById(R.id.googleSignInButton)

        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        usersRef = database.reference.child("users")

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            handleLogin(email, password)
        }

        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        findViewById<TextView>(R.id.textView6).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.textView8).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    override fun onStart() {
        super.onStart()
        // Check if the user is already logged in
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            fetchUsernameFromDatabase(currentUser)
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
            Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    val user = firebaseAuth.currentUser
                    user?.let {
                        saveUserToDatabase(it)
                    }
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToDatabase(user: FirebaseUser) {
        val userId = user.uid
        val email = user.email
        val username = user.displayName
        val userRef = usersRef.child(userId)

        userRef.child("email").setValue(email)
        userRef.child("username").setValue(username).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                navigateToMainActivity(email ?: "", username ?: "")
            } else {
                Toast.makeText(this, "Failed to save user data.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleLogin(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    val user = firebaseAuth.currentUser
                    user?.let {
                        fetchUsernameFromDatabase(user)
                    }
                } else {
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun fetchUsernameFromDatabase(user: FirebaseUser) {
        val userId = user.uid

        // Check user information first
        usersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val username = snapshot.child("username").getValue(String::class.java) ?: ""

                // Check for user schedule data
                val userScheduleRef = database.reference.child("user_schedule").child(userId)
                userScheduleRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(scheduleSnapshot: DataSnapshot) {
                        if (scheduleSnapshot.exists()) {
                            // Check if all required fields in user_schedule are filled
                            val hasCompleteSchedule = listOf(
                                "prio1", "prio2", "prio3", "prio4", "prio5", "prio6",
                                "week_days_start", "week_days_end", "weekend_start", "weekend_end"
                            ).all { key ->
                                val value = scheduleSnapshot.child(key).getValue(String::class.java)
                                !value.isNullOrEmpty()
                            }

                            if (hasCompleteSchedule) {
                                // Navigate to MainActivity if all fields are filled
                                navigateToMainActivity(user.email ?: "", username)
                            } else {
                                // Navigate to QuestionActivity if any field is missing
                                navigateToQuestionActivity(user.email ?: "", username)
                            }
                        } else {
                            // If no data in user_schedule, navigate to QuestionActivity
                            navigateToQuestionActivity(user.email ?: "", username)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Handle error if needed
                        navigateToQuestionActivity(user.email ?: "", username)
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                navigateToQuestionActivity(user.email ?: "", "")
            }
        })
    }


    private fun navigateToQuestionActivity(email: String, username: String) {
        val intent = Intent(this, QuestionActivity::class.java)
        intent.putExtra("userEmail", email)
        intent.putExtra("userName", username)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun navigateToMainActivity(email: String, username: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("userEmail", email)
        intent.putExtra("userName", username)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}
