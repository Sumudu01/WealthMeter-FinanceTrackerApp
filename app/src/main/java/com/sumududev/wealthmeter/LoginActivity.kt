package com.sumududev.wealthmeter

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import at.favre.lib.crypto.bcrypt.BCrypt

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var signUpTextView: TextView
    private lateinit var forgotPasswordTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize encrypted shared preferences
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            "user_credentials",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Initialize views
        emailEditText = findViewById(R.id.edtemaillog)
        passwordEditText = findViewById(R.id.edtpslog)
        loginButton = findViewById(R.id.btnlogin)
        signUpTextView = findViewById(R.id.tvsignup)
        forgotPasswordTextView = findViewById(R.id.tvforgotps)

        // Check if user is already logged in
        if (isUserLoggedIn()) {
            startMainActivity()
            finish()
        }

        // Set click listeners
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (validateInputs(email, password)) {
                attemptLogin(email, password)
            }
        }

        signUpTextView.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        forgotPasswordTextView.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            emailEditText.requestFocus()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Please enter a valid email"
            emailEditText.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            passwordEditText.requestFocus()
            return false
        }

        if (password.length < 8) {
            passwordEditText.error = "Password must be at least 8 characters"
            passwordEditText.requestFocus()
            return false
        }

        return true
    }

    private fun attemptLogin(email: String, password: String) {
        loginButton.isEnabled = false

        // Retrieve stored credentials
        val storedEmail = sharedPreferences.getString("user_email", null)
        val storedHashedPassword = sharedPreferences.getString("user_password", null)

        when {
            storedEmail == null || storedHashedPassword == null -> {
                Toast.makeText(this, "No registered user found. Please sign up first.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SignUpActivity::class.java))
            }
            email != storedEmail -> {
                emailEditText.error = "Email not registered"
                emailEditText.requestFocus()
            }
            !BCrypt.verifyer().verify(password.toCharArray(), storedHashedPassword).verified -> {
                passwordEditText.error = "Incorrect password"
                passwordEditText.requestFocus()
            }
            else -> {
                // Successful login
                onLoginSuccess(email)
            }
        }

        loginButton.isEnabled = true
    }

    private fun onLoginSuccess(email: String) {
        // Save login state
        sharedPreferences.edit().apply {
            putBoolean("is_logged_in", true)
            putString("current_user_email", email)
            apply()
        }

        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
        startMainActivity()
    }

    private fun isUserLoggedIn(): Boolean {
        return sharedPreferences.getBoolean("is_logged_in", false)
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}