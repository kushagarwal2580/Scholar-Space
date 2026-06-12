package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String,
    val createdAt: Long = System.currentTimeMillis(),
    val profilePic: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    val statusMsg: String? = null,
    val password: String? = null
)
