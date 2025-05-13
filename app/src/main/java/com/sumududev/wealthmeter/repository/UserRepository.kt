package com.sumududev.wealthmeter.repository

import com.sumududev.wealthmeter.data.dao.UserDao
import com.sumududev.wealthmeter.data.entity.User
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {
    suspend fun insertUser(user: User) = userDao.insertUser(user)
    suspend fun updateUser(user: User) = userDao.updateUser(user)
    suspend fun deleteUser(user: User) = userDao.deleteUser(user)
    suspend fun getUserByEmail(email: String) = userDao.getUserByEmail(email)
    fun getUserByEmailFlow(email: String) = userDao.getUserByEmailFlow(email)
    fun getAllUsers() = userDao.getAllUsers()
} 