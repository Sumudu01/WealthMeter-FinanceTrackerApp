import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sumududev.wealthmeter.R
import com.sumududev.wealthmeter.Transaction
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var tvTotalBalance: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpense: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvBudgetStatus: TextView
    private lateinit var tvBudgetProgress: TextView
    private lateinit var etBudgetAmount: EditText
    private lateinit var btnSetBudget: Button
    private val transactionList = mutableListOf<Transaction>()

    // Notification constants
    private val CHANNEL_ID = "budget_alerts_channel"
    private val NOTIFICATION_ID = 101

    // Budget configuration
    private var monthlyBudget = 0.0
    private var hasShownAlertThisSession = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        createNotificationChannel()
        initializeViews(view)
        loadData()
        setupBudgetButton()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initializeViews(view: View) {
        tvTotalBalance = view.findViewById(R.id.tvTotalBalance)
        tvIncome = view.findViewById(R.id.tvIncome)
        tvExpense = view.findViewById(R.id.tvExpense)
        progressBar = view.findViewById(R.id.progressBar)
        tvBudgetStatus = view.findViewById(R.id.tvBudgetStatus)
        tvBudgetProgress = view.findViewById(R.id.tvBudgetProgress)
        etBudgetAmount = view.findViewById(R.id.etBudgetAmount)
        btnSetBudget = view.findViewById(R.id.btnSetBudget)
        view.findViewById<TextView>(R.id.tvGreeting).text = getTimeBasedGreeting()
    }

    private fun setupBudgetButton() {
        btnSetBudget.setOnClickListener {
            val budgetText = etBudgetAmount.text.toString()
            if (budgetText.isNotEmpty()) {
                val proposedBudget = budgetText.toDouble()
                val currentIncome = calculateTotals().first

                if (proposedBudget > currentIncome) {
                    showBudgetExceedsIncomeError(currentIncome)
                } else {
                    monthlyBudget = proposedBudget
                    sharedPreferences.edit().putFloat("monthlyBudget", monthlyBudget.toFloat()).apply()
                    updateBudgetProgress()
                    etBudgetAmount.text.clear()
                    showBudgetSetSuccess()
                }
            } else {
                showBudgetSetError()
            }
        }
    }

    private fun showBudgetExceedsIncomeError(currentIncome: Double) {
        AlertDialog.Builder(requireContext())
            .setTitle("Invalid Budget Amount")
            .setMessage("Your budget cannot exceed your current income (${formatAsLKR(currentIncome)}). " +
                    "Please enter a lower amount.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                etBudgetAmount.requestFocus()
            }
            .show()
    }

    private fun showBudgetSetSuccess() {
        AlertDialog.Builder(requireContext())
            .setTitle("Budget Set")
            .setMessage("Your monthly budget has been set to ${formatAsLKR(monthlyBudget)}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showBudgetSetError() {
        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage("Please enter a valid budget amount")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadData() {
        monthlyBudget = sharedPreferences.getFloat("monthlyBudget", 0f).toDouble()
        loadTransactions()
        updateFinancialSummary()
        updateBudgetProgress()
    }

    private fun getTimeBasedGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good Morning ☀\uFE0F"
            in 12..16 -> "Good Afternoon \uD83C\uDF24"
            in 17..20 -> "Good Evening \uD83C\uDF19"
            else -> "Good Night \uD83C\uDF1A"
        }
    }

    private fun loadTransactions() {
        sharedPreferences.getString("transactions", null)?.let { json ->
            transactionList.clear()
            transactionList.addAll(Gson().fromJson(json, object : TypeToken<MutableList<Transaction>>() {}.type))
        }
    }

    private fun updateFinancialSummary() {
        val (totalIncome, totalExpense) = calculateTotals()
        updateBalanceDisplay(totalIncome, totalExpense)
    }

    private fun updateBalanceDisplay(totalIncome: Double, totalExpense: Double) {
        tvIncome.text = formatAsLKR(totalIncome)
        tvExpense.text = formatAsLKR(totalExpense)
        tvTotalBalance.text = formatAsLKR(totalIncome - totalExpense)
    }

    private fun updateBudgetProgress() {
        val (currentIncome, totalExpense) = calculateTotals()

        if (monthlyBudget > 0) {
            // Ensure budget doesn't exceed income (in case income changed after budget was set)
            if (monthlyBudget > currentIncome) {
                monthlyBudget = currentIncome
                sharedPreferences.edit().putFloat("monthlyBudget", monthlyBudget.toFloat()).apply()
                showBudgetAutoAdjustedNotification(currentIncome)
            }

            val expensePercentage = (totalExpense / monthlyBudget * 100).toInt()
            progressBar.progress = expensePercentage.coerceAtMost(100)

            val statusMessage = when {
                expensePercentage == 0 -> "Monthly Budget: ${formatAsLKR(monthlyBudget)} - No expenses yet"
                expensePercentage < 50 -> "Monthly Budget: ${formatAsLKR(monthlyBudget)} - Great control!"
                expensePercentage < 80 -> "Monthly Budget: ${formatAsLKR(monthlyBudget)} - On track"
                expensePercentage < 100 -> "Monthly Budget: ${formatAsLKR(monthlyBudget)} - Watch spending"
                else -> "Monthly Budget: ${formatAsLKR(monthlyBudget)} - Budget exceeded!"
            }

            tvBudgetStatus.text = statusMessage
            tvBudgetProgress.text = "${formatAsLKR(totalExpense)} of ${formatAsLKR(monthlyBudget)}"

            checkBudgetLimit(totalExpense)
        } else {
            tvBudgetStatus.text = "Monthly Budget: Not Set"
            tvBudgetProgress.text = "Set a budget to track your expenses"
            progressBar.progress = 0
        }
    }

    private fun showBudgetAutoAdjustedNotification(currentIncome: Double) {
        AlertDialog.Builder(requireContext())
            .setTitle("Budget Adjusted")
            .setMessage("Your budget was automatically adjusted to match your current income (${formatAsLKR(currentIncome)})")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkBudgetLimit(totalExpense: Double) {
        if (monthlyBudget <= 0) return

        val expensePercentage = (totalExpense / monthlyBudget * 100).toInt()
        if (expensePercentage >= 80) {
            if (!hasShownAlertThisSession) {
                showBudgetAlert(expensePercentage)
                hasShownAlertThisSession = true
            }
            showBudgetExceedNotification(expensePercentage)
        }
    }

    private fun showBudgetAlert(percentage: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Budget Warning")
            .setMessage("You've used $percentage% of your monthly budget!")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Adjust Budget") { _, _ ->
                etBudgetAmount.requestFocus()
            }
            .setIcon(R.drawable.ic_warning)
            .create()
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun showBudgetExceedNotification(percentage: Int) {
        val builder = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setSmallIcon(R.drawable.wealthmeterlogo)
            .setContentTitle("⚠\uFE0F Budget Alert!")
            .setContentText("You've used $percentage% of your monthly budget")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(requireContext())) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun formatAsLKR(amount: Double): String {
        return DecimalFormat("LKR #,##0.00").format(amount)
    }

    private fun calculateTotals(): Pair<Double, Double> {
        var income = 0.0
        var expense = 0.0

        transactionList.forEach {
            if (it.amount >= 0) income += it.amount else expense += abs(it.amount)
        }

        return Pair(income, expense)
    }

    override fun onResume() {
        super.onResume()
        hasShownAlertThisSession = false
        loadData()
    }
}