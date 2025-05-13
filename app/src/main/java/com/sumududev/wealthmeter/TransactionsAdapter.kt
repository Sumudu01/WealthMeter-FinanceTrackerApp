package com.sumududev.wealthmeter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat
import kotlin.math.abs

class TransactionsAdapter(
    private var transactions: List<Transaction>,
    private val onLongClick: (Int) -> Unit,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<TransactionsAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        with(holder) {
            tvTitle.text = transaction.title
            tvDate.text = transaction.date
            tvCategory.text = transaction.category

            if (transaction.type == "income") {
                tvAmount.text = "+LKR ${DecimalFormat("#,##0.00").format(transaction.amount)}"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_dark))
                ivIcon.setImageResource(R.drawable.income)
            } else {
                tvAmount.text = "-LKR ${DecimalFormat("#,##0.00").format(abs(transaction.amount))}"
                tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_dark))
                ivIcon.setImageResource(R.drawable.expense)
            }

            itemView.setOnClickListener { onClick(position) }
            itemView.setOnLongClickListener {
                onLongClick(position)
                true
            }
        }
    }

    fun updateList(newList: List<Transaction>) {
        transactions = newList
        notifyDataSetChanged()
    }

    override fun getItemCount() = transactions.size
}