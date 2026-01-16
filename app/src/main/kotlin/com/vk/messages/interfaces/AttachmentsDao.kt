package com.vk.messages.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.vk.messages.models.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}
