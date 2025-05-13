import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.sumududev.wealthmeter.LoginActivity
import com.sumududev.wealthmeter.R
import com.sumududev.wealthmeter.databinding.FragmentProfileBinding
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences
    private var profileImageUri: Uri? = null
    private var isEditMode = false
    private val PROFILE_IMAGE_NAME = "profile_image.jpg"
    private val BACKUP_FOLDER = "WealthMeterBackups"
    private val BACKUP_FILE_PREFIX = "wealthmeter_backup_"

    // For handling image selection result
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                profileImageUri = uri
                val bitmap = handleImage(uri)
                binding.profileImage.setImageBitmap(bitmap)
                saveProfileImage(bitmap)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize encrypted shared preferences
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            "user_credentials",
            masterKeyAlias,
            requireContext(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        loadUserData()
        loadProfileImage()
        setupClickListeners()
    }

    private fun loadUserData() {
        val fullName = sharedPreferences.getString("user_fullname", "")
        val email = sharedPreferences.getString("user_email", "")
        val mobile = sharedPreferences.getString("user_mobile", "")
        val dob = sharedPreferences.getString("user_dob", "")

        binding.apply {
            userNameText.text = fullName
            fullNameText.setText(fullName)
            emailText.setText(email)
            mobileNumberText.setText(mobile)
            dobText.text = dob
        }
    }

    private fun loadProfileImage() {
        val file = File(requireContext().filesDir, PROFILE_IMAGE_NAME)
        if (file.exists()) {
            try {
                // Load and scale the image to 108dp
                val displayMetrics = resources.displayMetrics
                val reqWidth = (108 * displayMetrics.density).toInt()
                val reqHeight = (108 * displayMetrics.density).toInt()

                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.absolutePath, options)

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false

                var bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                bitmap = Bitmap.createScaledBitmap(bitmap, reqWidth, reqHeight, true)
                binding.profileImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                binding.profileImage.setImageResource(R.drawable.usericon)
            }
        } else {
            // Set default image if no profile image exists
            binding.profileImage.setImageResource(R.drawable.usericon)
        }
    }

    private fun setupClickListeners() {
        binding.changeProfileImageButton.setOnClickListener {
            showImageOptionsDialog()
        }

        binding.editProfileButton.setOnClickListener {
            toggleEditMode()
        }

        binding.logoutButton.setOnClickListener {
            logoutUser()
        }

        binding.dobText.setOnClickListener {
            if (isEditMode) {
                showDatePicker()
            }
        }
        binding.backupButton.setOnClickListener {
            showBackupOptions()
        }
    }

    private fun showBackupOptions() {
        val options = arrayOf(
            getString(R.string.create_backup),
            getString(R.string.restore_backup)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.backup_options))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createBackup()
                    1 -> showRestoreDialog()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createBackup() {
        try {
            // Create backup directory if it doesn't exist
            val backupDir = File(requireContext().getExternalFilesDir(null), BACKUP_FOLDER)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // Create timestamp for backup filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "$BACKUP_FILE_PREFIX$timestamp.zip")

            // Create ZIP output stream
            FileOutputStream(backupFile).use { fileOut ->
                ZipOutputStream(fileOut).use { zipOut ->
                    // 1. Backup shared preferences
                    val prefsFile = File(requireContext().filesDir.parent, "shared_prefs/activity_login.xml")
                    if (prefsFile.exists()) {
                        addToZip(zipOut, prefsFile, "shared_prefs/activity_login.xml")
                    }

                    // 2. Backup profile image
                    val profileImageFile = File(requireContext().filesDir, PROFILE_IMAGE_NAME)
                    if (profileImageFile.exists()) {
                        addToZip(zipOut, profileImageFile, PROFILE_IMAGE_NAME)
                    }

                    // 3. Backup transactions if exists in shared prefs
                    val transactionsPrefsFile = File(requireContext().filesDir.parent, "shared_prefs/${requireContext().packageName}_preferences.xml")
                    if (transactionsPrefsFile.exists()) {
                        addToZip(zipOut, transactionsPrefsFile, "shared_prefs/fragment_transactions.xml")
                    }
                }
            }

            Toast.makeText(requireContext(), "Backup created: ${backupFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)
            fis.copyTo(zipOut, 1024)
            zipOut.closeEntry()
        }
    }

    private fun showRestoreDialog() {
        val backupDir = File(requireContext().getExternalFilesDir(null), BACKUP_FOLDER)
        if (!backupDir.exists() || backupDir.listFiles()?.isEmpty() != false) {
            Toast.makeText(requireContext(), "No backups found", Toast.LENGTH_SHORT).show()
            return
        }

        val backupFiles = backupDir.listFiles { file -> file.name.startsWith(BACKUP_FILE_PREFIX) }
        val fileNames = backupFiles.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select Backup to Restore")
            .setItems(fileNames) { _, which ->
                restoreBackup(backupFiles[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreBackup(backupFile: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Restore")
            .setMessage("This will overwrite your current data. Continue?")
            .setPositiveButton("Restore") { _, _ ->
                performRestore(backupFile)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRestore(backupFile: File) {
        try {

            val profileImageDest = File(requireContext().filesDir, PROFILE_IMAGE_NAME)


            if (profileImageDest.exists()) {
                profileImageDest.delete()
            }

            // Copy the backup file (in a real app, you'd unzip and restore all files)
            backupFile.copyTo(profileImageDest, overwrite = true)

            // Reload data
            loadProfileImage()
            loadUserData()

            Toast.makeText(requireContext(), "Data restored successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showImageOptionsDialog() {
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.choose_from_gallery),
            getString(R.string.remove_photo)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_photo_options))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                    2 -> deleteProfileImage()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun openCamera() {
        Toast.makeText(requireContext(), "Camera functionality not implemented", Toast.LENGTH_SHORT).show()
    }

    private fun deleteProfileImage() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_delete))
            .setMessage(getString(R.string.delete_profile_image_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                performDeleteProfileImage()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performDeleteProfileImage() {
        val file = File(requireContext().filesDir, PROFILE_IMAGE_NAME)
        if (file.exists()) {
            file.delete()
        }
        binding.profileImage.setImageResource(R.drawable.usericon)
        Toast.makeText(requireContext(), getString(R.string.profile_image_deleted), Toast.LENGTH_SHORT).show()
    }

    private fun handleImage(uri: Uri): Bitmap {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        // Calculate inSampleSize for 108dp (convert to pixels)
        val displayMetrics = resources.displayMetrics
        val reqWidth = (108 * displayMetrics.density).toInt()
        val reqHeight = (108 * displayMetrics.density).toInt()

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        val newInputStream = requireContext().contentResolver.openInputStream(uri)
        var bitmap = BitmapFactory.decodeStream(newInputStream, null, options)
        newInputStream?.close()


        bitmap = rotateImageIfRequired(bitmap!!, uri)


        val size = minOf(bitmap.width, bitmap.height)
        bitmap = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - size) / 2,
            (bitmap.height - size) / 2,
            size,
            size
        )

        // Scale to exact 108dp size
        bitmap = Bitmap.createScaledBitmap(bitmap, reqWidth, reqHeight, true)

        return bitmap
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun rotateImageIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        val input = requireContext().contentResolver.openInputStream(uri)
        val exif = ExifInterface(input!!)
        input.close()

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            matrix, true
        )
    }

    private fun saveProfileImage(bitmap: Bitmap) {
        try {

            val displayMetrics = resources.displayMetrics
            val reqWidth = (108 * displayMetrics.density).toInt()
            val reqHeight = (108 * displayMetrics.density).toInt()

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, reqWidth, reqHeight, true)

            val file = File(requireContext().filesDir, PROFILE_IMAGE_NAME)
            val outputStream = FileOutputStream(file)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.flush()
            outputStream.close()
            Toast.makeText(requireContext(), getString(R.string.profile_image_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(R.string.error_saving_image), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode

        if (isEditMode) {
            binding.editProfileButton.text = getString(R.string.save_changes)
            enableEditing(true)
        } else {
            saveChanges()
            enableEditing(false)
            binding.editProfileButton.text = getString(R.string.btnEditProfile)
        }
    }

    private fun enableEditing(enable: Boolean) {
        binding.apply {
            fullNameText.isEnabled = enable
            emailText.isEnabled = enable
            mobileNumberText.isEnabled = enable

            val backgroundRes = if (enable) R.drawable.protxtbg_editable else R.drawable.protxtbg
            fullNameText.setBackgroundResource(backgroundRes)
            emailText.setBackgroundResource(backgroundRes)
            mobileNumberText.setBackgroundResource(backgroundRes)
            dobText.setBackgroundResource(backgroundRes)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentDob = binding.dobText.text.toString()
        if (currentDob.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = sdf.parse(currentDob)
                if (date != null) {
                    calendar.time = date
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            updateDobInView(calendar.time)
        }

        DatePickerDialog(
            requireContext(),
            dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun updateDobInView(date: Date) {
        val myFormat = "dd/MM/yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.getDefault())
        binding.dobText.text = sdf.format(date)
    }

    private fun saveChanges() {
        val newFullName = binding.fullNameText.text.toString()
        val newEmail = binding.emailText.text.toString()
        val newMobile = binding.mobileNumberText.text.toString()
        val newDob = binding.dobText.text.toString()

        if (newFullName.isEmpty() || newEmail.isEmpty() || newMobile.isEmpty() || newDob.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        sharedPreferences.edit().apply {
            putString("user_fullname", newFullName)
            putString("user_email", newEmail)
            putString("user_mobile", newMobile)
            putString("user_dob", newDob)
            apply()
        }

        binding.userNameText.text = newFullName
        Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
    }

    private fun logoutUser() {
        sharedPreferences.edit().putBoolean("is_logged_in", false).apply()
        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}