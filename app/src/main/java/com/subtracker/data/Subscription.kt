package com.subtracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Cycle(val label: String, val months: Int) {
    WEEKLY("Weekly", 0),
    MONTHLY("Monthly", 1),
    QUARTERLY("Quarterly", 3),
    HALF_YEARLY("Half-yearly", 6),
    YEARLY("Yearly", 12),
}

enum class Status(val label: String) {
    ACTIVE("Active"),
    TRIAL("Trial"),
    PAUSED("Paused"),
    CANCELLED("Cancelled"),
}

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    /** Amount charged each billing cycle, in NOK. */
    val price: Double,
    val cycle: Cycle,
    /** Any known charge date (epoch day). Future charges are derived from it. */
    val anchorEpochDay: Long,
    val status: Status,
    /** ARGB colour, e.g. 0xFF1DB954. */
    val color: Long,
    val notes: String = "",
)
