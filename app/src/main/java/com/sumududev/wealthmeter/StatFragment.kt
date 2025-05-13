import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.sumududev.wealthmeter.R
import com.sumududev.wealthmeter.Transaction
import com.sumududev.wealthmeter.databinding.FragmentStatBinding
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class StatFragment : Fragment() {

    private var _binding: FragmentStatBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences
    private val EXPORTS_DIR = "transaction_exports"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatBinding.inflate(inflater, container, false)
        sharedPreferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        loadAndDisplayData()
        setupExportButton()
    }

    private fun setupViews() {
        binding.incomeProgress.apply {
            max = 100
            setIndicatorSize(150)
            trackThickness = 8
            trackColor = requireContext().getColor(R.color.red_dark)
            setIndicatorColor(requireContext().getColor(R.color.green_dark))
        }
    }

    private fun setupExportButton() {
        binding.btnExport.setOnClickListener {
            exportTransactionsToInternalStorage()
        }
    }

    private fun exportTransactionsToInternalStorage() {
        val transactions = loadTransactions()
        if (transactions.isEmpty()) {
            Snackbar.make(binding.root, "No transactions to export", Snackbar.LENGTH_SHORT).show()
            return
        }

        try {
            // Create or access the app-specific exports directory
            val exportsDir = File(requireContext().filesDir, EXPORTS_DIR)
            if (!exportsDir.exists()) {
                exportsDir.mkdir()
            }

            // Generate filename with timestamp
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "transactions_${dateFormat.format(Date())}.txt"
            val exportFile = File(exportsDir, fileName)

            // Write transaction data to the file
            FileWriter(exportFile).use { writer ->
                // Header
                writer.write("WEALTH METER TRANSACTION EXPORT\n")
                writer.write("Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\n\n")

                // Transaction details
                writer.write("TRANSACTION HISTORY\n")
                writer.write("Date\t\tTitle\t\tAmount\t\tCategory\t\tType\n")
                writer.write("--------------------------------------------------\n")

                transactions.sortedByDescending { it.date }.forEach { transaction ->
                    writer.write("${transaction.date}\t${transaction.title}\t${formatAsLKR(transaction.amount)}\t${transaction.category}\t${transaction.type}\n")
                }

                // Summary section
                val (totalIncome, totalExpense) = calculateTotals(transactions)
                writer.write("\nSUMMARY\n")
                writer.write("Total Income:\t${formatAsLKR(totalIncome)}\n")
                writer.write("Total Expenses:\t${formatAsLKR(totalExpense)}\n")
                writer.write("Net Balance:\t${formatAsLKR(totalIncome - totalExpense)}\n")

                // Category breakdown
                writer.write("\nEXPENSE CATEGORY BREAKDOWN\n")
                getCategoryBreakdown(transactions).forEach { (category, amount) ->
                    writer.write("$category:\t${formatAsLKR(amount)}\n")
                }
            }

            // Show success message with file location
            val internalPath = "${requireContext().filesDir.absolutePath}/$EXPORTS_DIR/$fileName"
            Snackbar.make(binding.root,
                "Exported to: $internalPath",
                Snackbar.LENGTH_LONG)
                .setAction("OPEN") {
                    openExportedFile(exportFile)
                }
                .show()

        } catch (e: Exception) {
            Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun openExportedFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Cannot open file: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun loadAndDisplayData() {
        val transactions = loadTransactions()
        updateIncomeExpenseProgress(transactions)
        updateCategoryBreakdown(transactions)
    }

    private fun loadTransactions(): List<Transaction> {
        val gson = Gson()
        val json = sharedPreferences.getString("transactions", null)
        val type = object : TypeToken<List<Transaction>>() {}.type

        return if (json != null) {
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun updateIncomeExpenseProgress(transactions: List<Transaction>) {
        if (transactions.isEmpty()) {
            showEmptyState()
            return
        }

        val (totalIncome, totalExpense) = calculateTotals(transactions)
        val total = totalIncome + totalExpense

        if (total > 0) {
            val incomePercent = (totalIncome / total * 100).toInt()
            val expensePercent = 100 - incomePercent

            binding.incomeProgress.setProgressCompat(incomePercent, true)
            binding.tvIncomePercent.text = getString(R.string.percentage_format, incomePercent)
            binding.tvExpensePercent.text = getString(R.string.percentage_format, expensePercent)
            binding.tvIncomeAmount.text = formatAsLKR(totalIncome)
            binding.tvExpenseAmount.text = formatAsLKR(totalExpense)
            updateProgressDescription(incomePercent)
        } else {
            showEmptyState()
        }
    }

    private fun updateCategoryBreakdown(transactions: List<Transaction>) {
        val container = binding.categoryBreakdownContainer
        container.removeAllViews()

        val breakdown = getCategoryBreakdown(transactions)
        if (breakdown.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = "No expense categories"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            }
            container.addView(emptyView)
            return
        }

        breakdown.forEach { (category, amount) ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_category_breakdown, container, false) as ViewGroup

            row.findViewById<TextView>(R.id.tvCategory).text = category
            row.findViewById<TextView>(R.id.tvAmount).text = formatAsLKR(amount)

            container.addView(row)
        }
    }

    private fun getCategoryBreakdown(transactions: List<Transaction>): Map<String, Double> {
        val breakdown = mutableMapOf<String, Double>()

        transactions.filter { it.amount < 0 }.forEach { transaction ->
            val category = transaction.category
            val amount = abs(transaction.amount)
            breakdown[category] = breakdown.getOrDefault(category, 0.0) + amount
        }

        return breakdown.toList()
            .sortedByDescending { (_, amount) -> amount }
            .toMap()
    }

    private fun calculateTotals(transactions: List<Transaction>): Pair<Double, Double> {
        var totalIncome = 0.0
        var totalExpense = 0.0

        transactions.forEach { transaction ->
            if (transaction.amount >= 0) {
                totalIncome += transaction.amount
            } else {
                totalExpense += abs(transaction.amount)
            }
        }

        return Pair(totalIncome, totalExpense)
    }

    private fun updateProgressDescription(incomePercent: Int) {
        val description = when {
            incomePercent > 70 -> "High income ratio"
            incomePercent > 40 -> "Balanced finances"
            else -> "High expense ratio"
        }
        binding.tvProgressDescription.text = description
    }

    private fun formatAsLKR(amount: Double): String {
        val prefix = if (amount >= 0) "+" else "-"
        return "$prefix LKR ${DecimalFormat("#,##0.00").format(abs(amount))}"
    }

    private fun showEmptyState() {
        binding.incomeProgress.setProgressCompat(0, true)
        binding.tvIncomePercent.text = getString(R.string.zero_percent)
        binding.tvExpensePercent.text = getString(R.string.zero_percent)
        binding.tvIncomeAmount.text = formatAsLKR(0.0)
        binding.tvExpenseAmount.text = formatAsLKR(0.0)
        binding.tvProgressDescription.text = "No transactions yet"
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}