package com.example.healthmanager.data.repository

import com.example.healthmanager.data.dao.UserDao
import com.example.healthmanager.data.entity.User
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {

    suspend fun register(user: User): Result<Long> {
        val exists = userDao.isUsernameExists(user.username)
        if (exists > 0) return Result.failure(Exception("用户名已存在"))
        val id = userDao.insertUser(user)
        return Result.success(id)
    }

    suspend fun login(username: String, password: String): User? {
        return userDao.login(username, password)
    }

    fun getUserById(userId: Int): Flow<User?> = userDao.getUserById(userId)

    suspend fun updateUser(user: User) = userDao.updateUser(user)
}
