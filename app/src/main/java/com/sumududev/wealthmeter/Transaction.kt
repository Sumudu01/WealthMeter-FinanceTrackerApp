package com.sumududev.wealthmeter


data class Transaction(
    val title: String,
    val amount: Double,
    val date: String,
    val type: String,
    val category: String
)