package com.sumududev.wealthmeter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val email: String,
    val fullName: String,
    val mobile: String,
    val dob: String,
    val passwordHash: String,
    val profileImagePath: String? = null
) 