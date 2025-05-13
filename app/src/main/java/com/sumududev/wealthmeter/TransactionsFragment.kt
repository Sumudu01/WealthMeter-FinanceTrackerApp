import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sumududev.wealthmeter.databinding.FragmentTransactionsBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sumududev.wealthmeter.R
import com.sumududev.wealthmeter.Transaction
import com.sumududev.wealthmeter.TransactionsAdapter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TransactionsAdapter
    private val transactionList = mutableListOf<Transaction>()
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private val channelId = "transaction_notifications"
    private val reminderChannelId = "reminder_notifications"
    private var notificationId = 101
    private lateinit var alarmManager: AlarmManager
    private lateinit var reminderPendingIntent: PendingIntent
    private val reminderRequestCode = 1001
    private val REMINDER_INTERVAL = AlarmManager.INTERVAL_DAY // 24 hours

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNotificationChannels()
        initializePreferences()
        setupRecyclerView()
        setupCategoryFilter()
        setupFAB()
        scheduleDailyReminder()
    }

    private fun setupNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Transaction notifications channel
            val transactionChannel = NotificationChannel(
                channelId,
                "Transaction Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notifications for transaction updates"
            }

            // Reminder notifications channel
            val reminderChannel = NotificationChannel(
                reminderChannelId,
                "Reminder Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows daily expense reminders"
            }

            notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(transactionChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    private fun initializePreferences() {
        sharedPreferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        loadTransactions()
    }

    private fun setupRecyclerView() {
        adapter = TransactionsAdapter(transactionList,
            onLongClick = { position -> showDeleteDialog(position) },
            onClick = { position -> showEditDialog(position) }
        )

        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TransactionsFragment.adapter
        }
    }

    private fun setupCategoryFilter() {
        val categories = listOf("All Categories") + getTransactionCategories()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spCategoryFilter.adapter = adapter
        binding.spCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterTransactions(categories[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupFAB() {
        binding.fabAddTransaction.setOnClickListener {
            showAddTransactionDialog()
        }
    }

    private fun scheduleDailyReminder() {
        alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), ReminderReceiver::class.java)
        reminderPendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            reminderRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set the alarm to trigger at 8 PM daily
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 13) // 8 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // If it's already past 8 PM, set for next day
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Set the repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            REMINDER_INTERVAL,
            reminderPendingIntent
        )
    }

    fun checkForTodaysExpenses(): Boolean {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        return transactionList.any { transaction ->
            val transactionDate = transaction.date.substring(0, 10) // Extract date part
            transactionDate == today && transaction.type == "expense"
        }
    }

    fun showReminderNotification() {
        if (!checkForTodaysExpenses()) {
            val notification = NotificationCompat.Builder(requireContext(), reminderChannelId)
                .setSmallIcon(R.drawable.wealthmeterlogo)
                .setContentTitle("Daily Expense Reminder")
                .setContentText("You haven't recorded any expenses today!")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("""
                        Don't forget to record your daily expenses.
                        Tap to add your expenses now.
                    """.trimIndent()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        requireContext(),
                        0,
                        Intent(requireContext(), requireActivity()::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()

            notificationManager.notify(notificationId++, notification)
        }
    }

    private fun getTransactionCategories(): List<String> {
        return resources.getStringArray(R.array.transaction_categories).toList()
    }

    private fun filterTransactions(category: String) {
        val filtered = if (category == "All Categories") {
            transactionList
        } else {
            transactionList.filter { it.category == category }
        }
        adapter.updateList(filtered)
    }

    private fun showAddTransactionDialog() {
        showTransactionDialog(null) { transaction ->
            transactionList.add(transaction)
            saveTransactions()
            adapter.notifyItemInserted(transactionList.size - 1)
            showTransactionNotification(transaction, "Added")
        }
    }

    private fun showEditDialog(position: Int) {
        val transaction = transactionList[position]
        showTransactionDialog(transaction) { updatedTransaction ->
            transactionList[position] = updatedTransaction
            saveTransactions()
            adapter.notifyItemChanged(position)
            showTransactionNotification(updatedTransaction, "Updated")
        }
    }

    private fun showTransactionDialog(
        existingTransaction: Transaction?,
        onSave: (Transaction) -> Unit
    ) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_transaction, null)

        val dialogTitle = if (existingTransaction == null) "Add Transaction" else "Edit Transaction"

        val categories = getTransactionCategories()
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        val categoryInput = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.actvCategory)
        categoryInput.setAdapter(categoryAdapter)

        existingTransaction?.let { transaction ->
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTitle).setText(transaction.title)
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAmount).setText(abs(transaction.amount).toString())
            categoryInput.setText(transaction.category, false)

            val radioButton = if (transaction.type == "income")
                dialogView.findViewById<RadioButton>(R.id.rbIncome)
            else
                dialogView.findViewById<RadioButton>(R.id.rbExpense)
            radioButton.isChecked = true
        }

        val titleView = TextView(requireContext()).apply {
            text = dialogTitle
            textSize = 20f
            gravity = View.TEXT_ALIGNMENT_CENTER
            setTextColor(resources.getColor(R.color.white, requireContext().theme))
            setPadding(140, 32, 0, 16)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setCustomTitle(titleView)
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setBackgroundColor(resources.getColor(R.color.btnbg, requireContext().theme))
            saveButton.setTextColor(resources.getColor(R.color.white, requireContext().theme))

            val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            cancelButton.setTextColor(resources.getColor(R.color.btnbg, requireContext().theme))

            saveButton.setOnClickListener {
                val title = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTitle).text.toString()
                val amountStr = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAmount).text.toString()
                val category = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.actvCategory).text.toString()
                val isIncome = dialogView.findViewById<RadioButton>(R.id.rbIncome).isChecked

                if (title.isEmpty() || amountStr.isEmpty() || category.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val amount = amountStr.toDouble()
                val transaction = Transaction(
                    title = title,
                    amount = if (isIncome) amount else -amount,
                    category = category,
                    type = if (isIncome) "income" else "expense",
                    date = existingTransaction?.date ?: SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())
                )

                onSave(transaction)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDeleteDialog(position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                val deletedTransaction = transactionList.removeAt(position)
                saveTransactions()
                adapter.notifyItemRemoved(position)
                showTransactionNotification(deletedTransaction, "Deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTransactionNotification(transaction: Transaction, action: String) {
        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(R.drawable.wealthmeterlogo)
            .setContentTitle("Transaction $action")
            .setContentText("${transaction.category}: ${transaction.title}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("""
                    ${transaction.type.capitalize()}: ${transaction.title}
                    Amount: ${formatCurrency(transaction.amount)}
                    Category: ${transaction.category}
                    Date: ${transaction.date}
                """.trimIndent()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId++, notification)
    }

    private fun formatCurrency(amount: Double): String {
        val prefix = if (amount >= 0) "+" else "-"
        return "$prefix LKR ${DecimalFormat("#,##0.00").format(abs(amount))}"
    }

    private fun loadTransactions() {
        sharedPreferences.getString("transactions", null)?.let { json ->
            transactionList.clear()
            transactionList.addAll(Gson().fromJson(json, object : TypeToken<MutableList<Transaction>>() {}.type))
        }
    }

    private fun saveTransactions() {
        sharedPreferences.edit()
            .putString("transactions", Gson().toJson(transactionList))
            .apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fragment = (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
            ?.findFragmentById(R.id.flFragment)
            ?.childFragmentManager?.fragments?.firstOrNull { it is TransactionsFragment } as? TransactionsFragment

        fragment?.showReminderNotification()

        // Reschedule the next reminder if the app isn't running
        if (fragment == null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val reminderIntent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 13) // 8 PM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}