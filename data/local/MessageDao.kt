package com.fitnesslemon.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /**
     * Получить все сообщения для чата в виде Flow (для реактивного обновления)
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAtTimestamp ASC")
    fun getMessagesForChat(chatId: Int): Flow<List<MessageEntity>>

    /**
     * Получить все сообщения для чата синхронно (для кэширования)
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAtTimestamp ASC")
    suspend fun getMessagesForChatSync(chatId: Int): List<MessageEntity>

    /**
     * Получить сообщение по ID
     */
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Int): MessageEntity?

    /**
     * Получить все ID сообщений для чата
     */
    @Query("SELECT id FROM messages WHERE chatId = :chatId")
    suspend fun getMessageIdsForChat(chatId: Int): List<Int>

    /**
     * Вставить одно сообщение (заменяет при конфликте)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    /**
     * Вставить несколько сообщений (заменяет при конфликте)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * Обновить сообщение
     */
    @Update
    suspend fun updateMessage(message: MessageEntity)

    /**
     * Удалить сообщение
     */
    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    /**
     * Удалить все сообщения чата
     */
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Int)

    /**
     * Удалить старые сообщения (старше указанного времени)
     */
    @Query("DELETE FROM messages WHERE createdAtTimestamp < :timestamp")
    suspend fun deleteOldMessages(timestamp: Long)

    /**
     * Получить количество непрочитанных сообщений в чате
     */
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0 AND isMe = 0")
    suspend fun getUnreadCountForChat(chatId: Int): Int

    /**
     * Получить общее количество сообщений в чате
     */
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun getMessageCount(chatId: Int): Int

    /**
     * Обновить информацию об отправителе для всех его сообщений
     */
    @Query("UPDATE messages SET senderName = :senderName, senderAvatar = :senderAvatar WHERE senderId = :senderId")
    suspend fun updateMessagesBySender(senderId: Int, senderName: String, senderAvatar: String?)

    /**
     * Обновить ID сообщения (для временных сообщений)
     */
    @Query("UPDATE messages SET id = :realId WHERE id = :tempId")
    suspend fun updateMessageId(tempId: Int, realId: Int)

    /**
     * Отметить сообщения как прочитанные
     */
    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND isRead = 0 AND isMe = 0")
    suspend fun markMessagesAsRead(chatId: Int)

    /**
     * Отметить конкретное сообщение как прочитанное
     */
    @Query("UPDATE messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markMessageAsRead(messageId: Int)

    /**
     * Получить последнее сообщение в чате
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAtTimestamp DESC LIMIT 1")
    suspend fun getLastMessage(chatId: Int): MessageEntity?

    /**
     * Получить сообщения с пагинацией
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAtTimestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaginated(chatId: Int, limit: Int, offset: Int): List<MessageEntity>

    /**
     * Поиск сообщений по тексту
     */
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND content LIKE '%' || :query || '%' ORDER BY createdAtTimestamp DESC")
    suspend fun searchMessages(chatId: Int, query: String): List<MessageEntity>

    /**
     * Получить все непрочитанные сообщения пользователя по всем чатам
     */
    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0 AND isMe = 0")
    suspend fun getTotalUnreadCount(): Int

    /**
     * Получить чаты с непрочитанными сообщениями
     */
    @Query("SELECT chatId, COUNT(*) as unreadCount FROM messages WHERE isRead = 0 AND isMe = 0 GROUP BY chatId")
    suspend fun getChatsWithUnread(): List<ChatUnreadCount>

    /**
     * Очистить все сообщения
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    /**
     * Получить все сообщения (для отладки)
     */
    @Query("SELECT * FROM messages ORDER BY createdAtTimestamp DESC")
    suspend fun getAllMessages(): List<MessageEntity>
}

/**
 * Вспомогательный класс для подсчета непрочитанных сообщений по чатам
 */
data class ChatUnreadCount(
    val chatId: Int,
    val unreadCount: Int
)