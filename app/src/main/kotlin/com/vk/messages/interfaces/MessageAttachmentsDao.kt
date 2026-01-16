package com.vk.messages.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.vk.messages.models.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}
