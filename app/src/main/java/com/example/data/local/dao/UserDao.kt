package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.data.local.entity.User

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsers(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("UPDATE users SET username = :nickname WHERE email = :email")
    suspend fun updateNickname(email: String, nickname: String)

    @Query("UPDATE users SET username = :nickname, profilePic = :profilePic, phone = :phone, bio = :bio, statusMsg = :statusMsg WHERE email = :email")
    suspend fun updateProfile(email: String, nickname: String, profilePic: String?, phone: String?, bio: String?, statusMsg: String?)

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): User?

    @Query("UPDATE users SET phone = :phone WHERE email = :email")
    suspend fun updatePhoneByEmail(email: String, phone: String)

    @Query("UPDATE users SET password = :password WHERE email = :email")
    suspend fun updatePasswordByEmail(email: String, password: String)

    @Query("UPDATE users SET password = :password WHERE phone = :phone")
    suspend fun updatePasswordByPhone(phone: String, password: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM users WHERE email = :email")
    suspend fun deleteUserByEmail(email: String)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: Int)
}
