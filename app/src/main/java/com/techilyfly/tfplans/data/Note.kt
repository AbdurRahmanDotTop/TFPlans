package com.techilyfly.tfplans.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

@Keep
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey var id: String = "",
    var title: String = "",
    var content: String = "",
    var color: Int = 0,
    var category: String = "",
    var reminderTime: Long? = null,
    var reminderRepeat: String? = null, // NONE, DAILY, WEEKLY, MONTHLY, YEARLY
    @get:PropertyName("isDone") @set:PropertyName("isDone") var isDone: Boolean = false,
    @get:PropertyName("isPinned") @set:PropertyName("isPinned") var isPinned: Boolean = false,
    @get:PropertyName("isArchived") @set:PropertyName("isArchived") var isArchived: Boolean = false,
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:com.google.firebase.firestore.Exclude @set:com.google.firebase.firestore.Exclude var isSynced: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)
