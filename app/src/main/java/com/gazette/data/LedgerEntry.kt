package com.gazette.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMs: Long,
    val amount: Double,
    val direction: String,
    val originalText: String,
    val packageName: String,
)
