package com.sumududev.wealthmeter

import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import at.favre.lib.crypto.bcrypt.BCrypt
import com.sumududev.wealthmeter.databinding.ActivitySignUpBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val calendar = Calendar.getInstance()
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize encrypted shared preferences
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            "user_credentials",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        setupClickListeners()
        setupDatePicker()
    }

    private fun setupClickListeners() {
        binding.signUpButton.setOnClickListener {
            attemptSignUp()
        }

        binding.loginTextView.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun setupDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            updateDobInView()
        }

        binding.dobEditText.setOnClickListener {
            DatePickerDialog(
                this,
                dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.maxDate = System.currentTimeMillis() // Prevent future dates
            }.show()
        }
    }

    private fun updateDobInView() {
        val myFormat = "dd/MM/yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault())
        binding.dobEditText.setText(sdf.format(calendar.time))
    }

    private fun attemptSignUp() {
        val fullName = binding.fullNameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim().lowercase()
        val mobile = binding.mobileNumberEditText.text.toString().trim()
        val dob = binding.dobEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()
        val termsAccepted = binding.termsCheckBox.isChecked

        when {
            fullName.isEmpty() -> showError(binding.fullNameEditText, "Full name is required")
            !isValidName(fullName) -> showError(binding.fullNameEditText, "Only letters and spaces allowed")
            email.isEmpty() -> showError(binding.emailEditText, "Email is required")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                showError(binding.emailEditText, "Please enter a valid email")
            mobile.isEmpty() -> showError(binding.mobileNumberEditText, "Mobile number is required")
            !isValidMobile(mobile) ->
                showError(binding.mobileNumberEditText, "10-13 digits with optional + prefix")
            dob.isEmpty() -> showError(binding.dobEditText, "Date of birth is required")
            password.isEmpty() -> showError(binding.passwordEditText, "Password is required")
            password.length < 8 ->
                showError(binding.passwordEditText, "Password must be at least 8 characters")
            !isStrongPassword(password) ->
                showError(binding.passwordEditText, "Must contain letters, numbers, and special chars")
            confirmPassword != password ->
                showError(binding.confirmPasswordEditText, "Passwords don't match")
            !termsAccepted ->
                Toast.makeText(this, "Please accept terms and conditions", Toast.LENGTH_SHORT).show()
            else -> registerUser(fullName, email, mobile, dob, password)
        }
    }

    private fun isValidName(name: String): Boolean {
        return name.matches(Regex("[a-zA-Z\\s]+"))
    }

    private fun isValidMobile(mobile: String): Boolean {
        val mobilePattern = "^[+]?[0-9]{10,13}$"
        return Pattern.compile(mobilePattern).matcher(mobile).matches()
    }

    private fun isStrongPassword(password: String): Boolean {
        val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$"
        return Pattern.compile(passwordPattern).matcher(password).matches()
    }

    private fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    private fun showError(editText: EditText, message: String) {
        editText.error = message
        editText.requestFocus()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun registerUser(fullName: String, email: String, mobile: String, dob: String, password: String) {
        // Check if email exists
        if (sharedPreferences.getString("user_email", null) == email) {
            showError(binding.emailEditText, "Email already registered")
            return
        }

        binding.signUpButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Hash password before storage
                val hashedPassword = hashPassword(password)

                // Store credentials securely
                sharedPreferences.edit().apply {
                    putString("user_email", email)
                    putString("user_password", hashedPassword)
                    putString("user_fullname", fullName)
                    putString("user_mobile", mobile)
                    putString("user_dob", dob)
                    apply()
                }

                // Auto-login after registration
                sharedPreferences.edit().putBoolean("is_logged_in", true).apply()

                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })

            } catch (e: Exception) {
                Toast.makeText(this, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.signUpButton.isEnabled = true
            }
        }, 1500)
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}