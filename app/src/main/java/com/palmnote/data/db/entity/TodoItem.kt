package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items", indices = [
    Index(value = ["isDeleted", "isCompleted"], name = "idx_todo_status"),
    Index(value = ["dueDate"], name = "idx_todo_due"),
    Index(value = ["planId"], name = "idx_todo_plan"),
    Index(value = ["lifeItemId"], name = "idx_todo_life_item")
])
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val dueDate: Long? = null,
    val priority: String = "MEDIUM",
    val category: String = "OTHER",
    val sortOrder: Int = 0,
    val planId: Long? = null,
    val parentId: Long? = null,
    val attachments: String = "[]",
    val recurring: String = "",
    val recurringEndType: String = "NEVER",
    val recurringEndCount: Int = 0,
    val recurringEndDate: Long? = null,
    val lifeItemId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
