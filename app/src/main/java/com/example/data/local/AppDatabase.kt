package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entity.CalendarEvent
import com.example.data.local.entity.File
import com.example.data.local.entity.Folder
import com.example.data.local.entity.Tag
import com.example.data.local.entity.User

@Database(
    entities = [User::class, Folder::class, File::class, Tag::class, CalendarEvent::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scholar_space_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
