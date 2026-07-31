package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val userName: String = "",
    val profilePicUri: String? = null,
    val themeMode: String = "SYSTEM", // "LIGHT", "DARK", "SYSTEM"
    val isFirstLaunchCompleted: Boolean = false
)
