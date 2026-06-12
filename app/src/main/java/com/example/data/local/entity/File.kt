package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "files")
data class File(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val folderId: Int? = null,
    val title: String,
    val contentUri: String,
    val createdAt: Long = System.currentTimeMillis()
)
