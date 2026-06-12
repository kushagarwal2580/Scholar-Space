package com.example.data.repository

import com.example.data.local.dao.UserDao
import com.example.data.local.entity.User

class UserRepository(private val userDao: UserDao) {
    suspend fun saveGoogleUser(email: String, displayName: String?, profilePic: String? = null) {
        val existingUser = userDao.getUserByEmail(email)
        if (existingUser == null) {
            val user = User(
                username = displayName ?: "Unknown",
                email = email,
                profilePic = profilePic
            )
            userDao.insertUser(user)
        }
    }

    suspend fun updateNickname(email: String, nickname: String) {
        userDao.updateNickname(email, nickname)
    }

    suspend fun updateProfile(email: String, nickname: String, profilePic: String?, phone: String?, bio: String?, statusMsg: String?) {
        userDao.updateProfile(email, nickname, profilePic, phone, bio, statusMsg)
    }

    suspend fun getUserByEmail(email: String): User? {
        val user = userDao.getUserByEmail(email)
        return if (user?.profilePic?.startsWith("http") == true) {
            userDao.updateProfile(user.email, user.username, null, user.phone, user.bio, user.statusMsg)
            user.copy(profilePic = null)
        } else {
            user
        }
    }

    suspend fun getUserByPhone(phone: String): User? {
        return userDao.getUserByPhone(phone)
    }

    suspend fun registerUser(username: String, email: String, phone: String?, password: String) {
        val user = com.example.data.local.entity.User(
            username = username,
            email = email,
            phone = phone,
            password = password
        )
        userDao.insertUser(user)
    }

    suspend fun updatePasswordByEmail(email: String, password: String) {
        userDao.updatePasswordByEmail(email, password)
    }
    
    suspend fun updatePhoneByEmail(email: String, phone: String) {
        userDao.updatePhoneByEmail(email, phone)
    }

    suspend fun updatePasswordByPhone(phone: String, password: String) {
        userDao.updatePasswordByPhone(phone, password)
    }

    suspend fun clearUsers() {
        userDao.deleteAllUsers()
    }

    suspend fun deleteUserByEmail(email: String) {
        userDao.deleteUserByEmail(email)
    }

    fun getAllUsers() = userDao.getAllUsers()
}
