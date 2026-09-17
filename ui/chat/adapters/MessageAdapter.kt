package com.fitnesslemon.app.ui.chat.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.Message
import com.fitnesslemon.app.databinding.ItemMessageMeBinding
import com.fitnesslemon.app.databinding.ItemMessageOtherBinding
import com.fitnesslemon.app.ui.chat.utils.MediaSaveHelper
import com.fitnesslemon.app.utils.PreferencesManager
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private var messages: List<Message>,
    private val currentUserId: Int,
    private val onAudioPlayClick: (Message, Boolean) -> Unit,
    private val onImageClick: (Message) -> Unit,
    private val onSeekBarTouch: (Message, Boolean) -> Unit,
    private val onSeekBarProgress: (Message, Int) -> Unit,
    private val onMessageLongClick: (Message) -> Unit,
    private val chatType: String = "private"
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ME = 0
        private const val TYPE_OTHER = 1
        private const val TAG = "MessageAdapter"

        private var cachedUserId = 0
    }

    private val playingStates = mutableMapOf<Int, Boolean>()
    private val seekBarProgress = mutableMapOf<Int, Int>()
    private val audioDurations = mutableMapOf<Int, String>()
    private val waveformStates = mutableMapOf<Int, Int>()

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]

        var userId = currentUserId

        if (userId == 0) {
            if (cachedUserId == 0) {
                userId = PreferencesManager.getUserId()
                if (userId != 0) {
                    cachedUserId = userId
                    Log.d(TAG, "✅ User ID получен из Preferences: $userId")
                } else {
                    val token = PreferencesManager.getToken()
                    if (!token.isNullOrEmpty()) {
                        try {
                            val parts = token.split(".")
                            if (parts.size > 1) {
                                val payloadJson = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                                val json = JSONObject(payloadJson)
                                cachedUserId = json.optInt("user_id", 0)
                                if (cachedUserId == 0) {
                                    cachedUserId = json.optInt("id", 0)
                                }
                                userId = cachedUserId
                                Log.d(TAG, "✅ User ID восстановлен из токена: $userId")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка парсинга токена: ${e.message}")
                        }
                    }
                }
            } else {
                userId = cachedUserId
            }
        }

        val isMe = message.senderId == userId
        Log.d(TAG, "📩 Сообщение ${message.id}: senderId=${message.senderId}, userId=$userId, isMe=$isMe")

        return if (isMe) TYPE_ME else TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ME -> {
                val binding = ItemMessageMeBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                MessageMeViewHolder(binding, onAudioPlayClick, onImageClick, onSeekBarTouch, onSeekBarProgress, onMessageLongClick)
            }
            else -> {
                val binding = ItemMessageOtherBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                MessageOtherViewHolder(binding, onAudioPlayClick, onImageClick, onSeekBarTouch, onSeekBarProgress, onMessageLongClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is MessageMeViewHolder -> {
                holder.bind(message)
                val isPlaying = playingStates[message.id] ?: false
                holder.updatePlayButtonIcon(isPlaying)
                val progress = seekBarProgress[message.id] ?: 0
                holder.updateSeekBarProgress(progress)
                val durationText = audioDurations[message.id] ?: formatDuration(message.duration ?: 0)
                holder.updateAudioDurationText(durationText)
                holder.updateDuration(message.duration ?: 0)
                val waveformAmplitude = waveformStates[message.id] ?: 0
                holder.updateWaveform(waveformAmplitude)
            }
            is MessageOtherViewHolder -> {
                holder.bind(message, chatType)
                val isPlaying = playingStates[message.id] ?: false
                holder.updatePlayButtonIcon(isPlaying)
                val progress = seekBarProgress[message.id] ?: 0
                holder.updateSeekBarProgress(progress)
                val durationText = audioDurations[message.id] ?: formatDuration(message.duration ?: 0)
                holder.updateAudioDurationText(durationText)
                holder.updateDuration(message.duration ?: 0)
                val waveformAmplitude = waveformStates[message.id] ?: 0
                holder.updateWaveform(waveformAmplitude)
            }
        }
    }

    override fun getItemCount() = messages.size

    fun clearMessages() {
        Log.d(TAG, "🧹 Очищаем адаптер сообщений")
        messages = emptyList()
        playingStates.clear()
        seekBarProgress.clear()
        audioDurations.clear()
        waveformStates.clear()
        notifyDataSetChanged()
    }

    fun updateMessages(newMessages: List<Message>) {
        if (newMessages.isEmpty()) {
            clearMessages()
            return
        }

        val chatId = newMessages.firstOrNull()?.chatId
        if (chatId == null) {
            clearMessages()
            return
        }

        val currentChatId = messages.firstOrNull()?.chatId
        if (currentChatId != null && currentChatId != chatId) {
            Log.d(TAG, "🔄 Чат изменился (${currentChatId} -> ${chatId})")
            messages = emptyList()
            playingStates.clear()
            seekBarProgress.clear()
            audioDurations.clear()
            waveformStates.clear()
        }

        val filteredMessages = newMessages.filter { it.id > 0 }

        if (filteredMessages.isEmpty()) {
            clearMessages()
            return
        }

        if (messages.size == filteredMessages.size &&
            messages.zip(filteredMessages).all { (old, new) -> old.id == new.id && old.content == new.content }) {
            Log.d(TAG, "📡 Сообщения не изменились, пропускаем обновление")
            return
        }

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = messages.size
            override fun getNewListSize() = filteredMessages.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return messages[oldItemPosition].id == filteredMessages[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = messages[oldItemPosition]
                val new = filteredMessages[newItemPosition]
                return old.content == new.content &&
                        old.type == new.type &&
                        old.isRead == new.isRead &&
                        old.senderName == new.senderName &&
                        old.senderAvatar == new.senderAvatar &&
                        old.localUri == new.localUri &&
                        old.duration == new.duration
            }
        })

        messages = filteredMessages
        diffResult.dispatchUpdatesTo(this)

        Log.d(TAG, "✅ Обновлено ${messages.size} сообщений для чата $chatId")
    }

    fun updateMessagesWithoutAnimation(newMessages: List<Message>) {
        if (newMessages.isEmpty()) {
            clearMessages()
            return
        }

        val chatId = newMessages.firstOrNull()?.chatId
        if (chatId == null) {
            clearMessages()
            return
        }

        val currentChatId = messages.firstOrNull()?.chatId
        if (currentChatId != null && currentChatId != chatId) {
            Log.d(TAG, "🔄 Чат изменился (${currentChatId} -> ${chatId})")
            messages = emptyList()
            playingStates.clear()
            seekBarProgress.clear()
            audioDurations.clear()
            waveformStates.clear()
        }

        val filteredMessages = newMessages.filter { it.id > 0 }

        if (filteredMessages.isEmpty()) {
            clearMessages()
            return
        }

        if (messages.size == filteredMessages.size &&
            messages.zip(filteredMessages).all { (old, new) -> old.id == new.id && old.content == new.content }) {
            Log.d(TAG, "📡 Сообщения не изменились, пропускаем обновление")
            return
        }

        messages = filteredMessages
        notifyDataSetChanged()

        Log.d(TAG, "✅ Обновлено без анимации ${messages.size} сообщений для чата $chatId")
    }

    fun updatePlayButtonState(messageId: Int, isPlaying: Boolean) {
        playingStates[messageId] = isPlaying
        val position = messages.indexOfFirst { it.id == messageId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun updateSeekBarProgress(messageId: Int, progress: Int, duration: Int) {
        seekBarProgress[messageId] = progress
        val remaining = duration - progress
        audioDurations[messageId] = formatDuration(remaining)
        val position = messages.indexOfFirst { it.id == messageId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun updateSeekBarProgress(messageId: Int, progress: Int) {
        seekBarProgress[messageId] = progress
        val position = messages.indexOfFirst { it.id == messageId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun updateWaveform(messageId: Int, amplitude: Int) {
        waveformStates[messageId] = amplitude
        val position = messages.indexOfFirst { it.id == messageId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun clearPlaybackStates() {
        playingStates.clear()
        seekBarProgress.clear()
        audioDurations.clear()
        waveformStates.clear()
        notifyDataSetChanged()
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds < 0) return "00:00"
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    // ✅ ИСПРАВЛЕННЫЙ МЕТОД - добавляем .jpg
    private fun getThumbnailUrl(message: Message): String {
        val content = message.content
        val fileName = if (content.contains("/")) {
            content.substringAfterLast("/")
        } else {
            content
        }
        return "http://94.159.117.241/video-thumbnail/$fileName.jpg"
    }

    // ===== VIEW HOLDER ДЛЯ СВОИХ СООБЩЕНИЙ =====
    class MessageMeViewHolder(
        private val binding: ItemMessageMeBinding,
        private val onAudioPlayClick: (Message, Boolean) -> Unit,
        private val onImageClick: (Message) -> Unit,
        private val onSeekBarTouch: (Message, Boolean) -> Unit,
        private val onSeekBarProgress: (Message, Int) -> Unit,
        private val onMessageLongClick: (Message) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentMessage: Message? = null
        private var isUserSeeking = false

        fun bind(message: Message) {
            currentMessage = message
            binding.apply {
                when (message.type) {
                    "text" -> {
                        tvMessageText.text = message.content
                        tvMessageText.visibility = View.VISIBLE
                        ivMessageImage.visibility = View.GONE
                        audioContainer.visibility = View.GONE
                        videoContainer.visibility = View.GONE
                    }
                    "image" -> {
                        val imageUrl = message.content
                        Log.d(TAG, "🖼️ Загрузка изображения: $imageUrl")

                        Glide.with(root.context)
                            .load(imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(ivMessageImage)

                        ivMessageImage.visibility = View.VISIBLE
                        tvMessageText.visibility = View.GONE
                        audioContainer.visibility = View.GONE
                        videoContainer.visibility = View.GONE
                    }
                    "video" -> {
                        videoContainer.visibility = View.VISIBLE
                        tvMessageText.visibility = View.GONE
                        audioContainer.visibility = View.GONE
                        ivMessageImage.visibility = View.VISIBLE

                        Log.d(TAG, "🎬 Загрузка видео: ${message.content}")

                        // Получаем URL для превью через PHP эндпоинт (с .jpg)
                        val thumbnailUrl = getThumbnailUrl(message)
                        Log.d(TAG, "🖼️ Thumbnail URL: $thumbnailUrl")

                        Glide.with(root.context)
                            .load(thumbnailUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_video_placeholder)
                            .error(R.drawable.ic_video_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(ivMessageImage)

                        // Иконка воспроизведения всегда видна
                        ivPlayVideo.visibility = View.VISIBLE

                        // Длительность видео
                        val duration = message.duration ?: 0
                        tvVideoDuration.text = formatDuration(duration)
                        tvVideoDuration.visibility = View.VISIBLE
                    }
                    "audio" -> {
                        audioContainer.visibility = View.VISIBLE
                        tvMessageText.visibility = View.GONE
                        ivMessageImage.visibility = View.GONE
                        videoContainer.visibility = View.GONE

                        val duration = message.duration ?: 0
                        val durationText = formatDuration(duration)
                        tvAudioDuration.text = durationText
                        audioSeekBar.max = if (duration > 0) duration else 100

                        waveform.setProgress(0)

                        btnPlayAudio.setOnClickListener {
                            onAudioPlayClick(message, true)
                        }

                        audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                if (fromUser && isUserSeeking) {
                                    onSeekBarProgress(message, progress)
                                }
                            }
                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                                isUserSeeking = true
                                onSeekBarTouch(message, true)
                            }
                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                                isUserSeeking = false
                                onSeekBarTouch(message, false)
                            }
                        })
                    }
                }

                tvSenderName.visibility = View.GONE

                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val date = inputFormat.parse(message.createdAt)
                    tvMessageTime.text = outputFormat.format(date ?: Date())
                } catch (e: Exception) {
                    tvMessageTime.text = ""
                }

                ivMessageRead.visibility = if (message.isRead) View.VISIBLE else View.GONE

                root.setOnLongClickListener {
                    showContextMenu(message)
                    true
                }

                root.setOnClickListener {
                    when (message.type) {
                        "image" -> {
                            onImageClick(message)
                        }
                        "video" -> {
                            onImageClick(message)
                        }
                        "audio" -> {
                            onAudioPlayClick(message, true)
                        }
                        else -> {
                            showContextMenu(message)
                        }
                    }
                }
            }
        }

        private fun getThumbnailUrl(message: Message): String {
            val content = message.content
            val fileName = if (content.contains("/")) {
                content.substringAfterLast("/")
            } else {
                content
            }
            return "http://94.159.117.241/video-thumbnail/$fileName.jpg"
        }

        private fun showContextMenu(message: Message) {
            val options = mutableListOf<String>()
            when (message.type) {
                "image" -> {
                    options.add("Сохранить в галерею")
                    options.add("Удалить")
                }
                "video" -> {
                    options.add("Сохранить в галерею")
                    options.add("Удалить")
                }
                "audio" -> {
                    options.add("Сохранить в музыку")
                    options.add("Удалить")
                }
                "text" -> {
                    options.add("Удалить")
                }
            }
            android.app.AlertDialog.Builder(binding.root.context)
                .setTitle("Действия")
                .setItems(options.toTypedArray()) { _, which ->
                    when (options[which]) {
                        "Сохранить в галерею" -> saveImageToGallery(message)
                        "Сохранить в музыку" -> saveAudioToGallery(message)
                        "Удалить" -> onMessageLongClick(message)
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        private fun saveImageToGallery(message: Message) {
            val imageUrl = message.content
            Toast.makeText(binding.root.context, "Скачивание изображения...", Toast.LENGTH_SHORT).show()
            MediaSaveHelper.downloadAndSaveImage(binding.root.context, imageUrl) { success, msg ->
                Toast.makeText(binding.root.context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        private fun saveAudioToGallery(message: Message) {
            var audioPath = message.content
            if (!message.localUri.isNullOrEmpty()) {
                val localFile = File(message.localUri)
                if (localFile.exists()) audioPath = message.localUri
            }
            val file = File(audioPath)
            if (!file.exists()) {
                Toast.makeText(binding.root.context, "Аудиофайл не найден", Toast.LENGTH_SHORT).show()
                return
            }
            MediaSaveHelper.saveAudioToGallery(binding.root.context, audioPath) { success, msg ->
                Toast.makeText(binding.root.context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        fun updatePlayButtonIcon(isPlaying: Boolean) {
            binding.btnPlayAudio.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }

        fun updateSeekBarProgress(progress: Int) {
            if (!isUserSeeking) {
                binding.audioSeekBar.progress = progress
            }
        }

        fun updateAudioDurationText(durationText: String) {
            binding.tvAudioDuration.text = durationText
        }

        fun updateDuration(duration: Int) {
            if (binding.audioSeekBar.max == 0 && duration > 0) {
                binding.audioSeekBar.max = duration
            }
        }

        fun updateWaveform(amplitude: Int) {
            binding.waveform.setProgress(amplitude)
        }

        private fun formatDuration(seconds: Int): String {
            if (seconds < 0) return "00:00"
            val minutes = seconds / 60
            val secs = seconds % 60
            return String.format("%02d:%02d", minutes, secs)
        }
    }

    // ===== VIEW HOLDER ДЛЯ ЧУЖИХ СООБЩЕНИЙ =====
    class MessageOtherViewHolder(
        private val binding: ItemMessageOtherBinding,
        private val onAudioPlayClick: (Message, Boolean) -> Unit,
        private val onImageClick: (Message) -> Unit,
        private val onSeekBarTouch: (Message, Boolean) -> Unit,
        private val onSeekBarProgress: (Message, Int) -> Unit,
        private val onMessageLongClick: (Message) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentMessage: Message? = null
        private var isUserSeeking = false

        fun bind(message: Message, chatType: String) {
            currentMessage = message
            binding.apply {
                tvSenderName.visibility = View.VISIBLE
                ivSenderAvatar.visibility = View.VISIBLE

                val senderName = if (message.senderName.isNotEmpty()) {
                    message.senderName
                } else {
                    "Пользователь ${message.senderId}"
                }
                tvSenderName.text = senderName

                if (!message.senderAvatar.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(message.senderAvatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(36, 36)
                        .into(ivSenderAvatar)
                } else {
                    ivSenderAvatar.setImageResource(R.drawable.ic_profile)
                }

                when (message.type) {
                    "text" -> {
                        tvMessageText.text = message.content
                        tvMessageText.visibility = View.VISIBLE
                        ivMessageImage.visibility = View.GONE
                        audioContainer.visibility = View.GONE
                        videoContainer.visibility = View.GONE
                    }
                    "image" -> {
                        val imageUrl = message.content
                        Log.d(TAG, "🖼️ Загрузка изображения: $imageUrl")

                        Glide.with(root.context)
                            .load(imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(ivMessageImage)

                        ivMessageImage.visibility = View.VISIBLE
                        tvMessageText.visibility = View.GONE
                        audioContainer.visibility = View.GONE
                        videoContainer.visibility = View.GONE
                    }
                    "video" -> {
                        videoContainer.visibility = View.VISIBLE
                        tvMessageText.visibility = View.GONE
                        audioContainer.visibility = View.GONE
                        ivMessageImage.visibility = View.VISIBLE

                        Log.d(TAG, "🎬 Загрузка видео: ${message.content}")

                        // Получаем URL для превью через PHP эндпоинт (с .jpg)
                        val thumbnailUrl = getThumbnailUrl(message)
                        Log.d(TAG, "🖼️ Thumbnail URL: $thumbnailUrl")

                        Glide.with(root.context)
                            .load(thumbnailUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_video_placeholder)
                            .error(R.drawable.ic_video_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(ivMessageImage)

                        // Иконка воспроизведения всегда видна
                        ivPlayVideo.visibility = View.VISIBLE

                        // Длительность видео
                        val duration = message.duration ?: 0
                        tvVideoDuration.text = formatDuration(duration)
                        tvVideoDuration.visibility = View.VISIBLE
                    }
                    "audio" -> {
                        audioContainer.visibility = View.VISIBLE
                        tvMessageText.visibility = View.GONE
                        ivMessageImage.visibility = View.GONE
                        videoContainer.visibility = View.GONE

                        val duration = message.duration ?: 0
                        val durationText = formatDuration(duration)
                        tvAudioDuration.text = durationText
                        audioSeekBar.max = if (duration > 0) duration else 100

                        waveform.setProgress(0)

                        btnPlayAudio.setOnClickListener {
                            onAudioPlayClick(message, true)
                        }

                        audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                if (fromUser && isUserSeeking) {
                                    onSeekBarProgress(message, progress)
                                }
                            }
                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                                isUserSeeking = true
                                onSeekBarTouch(message, true)
                            }
                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                                isUserSeeking = false
                                onSeekBarTouch(message, false)
                            }
                        })
                    }
                }

                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val date = inputFormat.parse(message.createdAt)
                    tvMessageTime.text = outputFormat.format(date ?: Date())
                } catch (e: Exception) {
                    tvMessageTime.text = ""
                }

                root.setOnLongClickListener {
                    showContextMenu(message)
                    true
                }

                root.setOnClickListener {
                    when (message.type) {
                        "image" -> {
                            onImageClick(message)
                        }
                        "video" -> {
                            onImageClick(message)
                        }
                        "audio" -> {
                            onAudioPlayClick(message, true)
                        }
                        else -> {
                            showContextMenu(message)
                        }
                    }
                }
            }
        }

        private fun getThumbnailUrl(message: Message): String {
            val content = message.content
            val fileName = if (content.contains("/")) {
                content.substringAfterLast("/")
            } else {
                content
            }
            return "http://94.159.117.241/video-thumbnail/$fileName.jpg"
        }

        private fun showContextMenu(message: Message) {
            val options = mutableListOf<String>()
            when (message.type) {
                "image" -> {
                    options.add("Сохранить в галерею")
                    options.add("Удалить")
                }
                "video" -> {
                    options.add("Сохранить в галерею")
                    options.add("Удалить")
                }
                "audio" -> {
                    options.add("Сохранить в музыку")
                    options.add("Удалить")
                }
                "text" -> {
                    options.add("Удалить")
                }
            }
            android.app.AlertDialog.Builder(binding.root.context)
                .setTitle("Действия")
                .setItems(options.toTypedArray()) { _, which ->
                    when (options[which]) {
                        "Сохранить в галерею" -> saveImageToGallery(message)
                        "Сохранить в музыку" -> saveAudioToGallery(message)
                        "Удалить" -> onMessageLongClick(message)
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        private fun saveImageToGallery(message: Message) {
            val imageUrl = message.content
            Toast.makeText(binding.root.context, "Скачивание изображения...", Toast.LENGTH_SHORT).show()
            MediaSaveHelper.downloadAndSaveImage(binding.root.context, imageUrl) { success, msg ->
                Toast.makeText(binding.root.context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        private fun saveAudioToGallery(message: Message) {
            var audioPath = message.content
            if (!message.localUri.isNullOrEmpty()) {
                val localFile = File(message.localUri)
                if (localFile.exists()) audioPath = message.localUri
            }
            val file = File(audioPath)
            if (!file.exists()) {
                Toast.makeText(binding.root.context, "Аудиофайл не найден", Toast.LENGTH_SHORT).show()
                return
            }
            MediaSaveHelper.saveAudioToGallery(binding.root.context, audioPath) { success, msg ->
                Toast.makeText(binding.root.context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        fun updatePlayButtonIcon(isPlaying: Boolean) {
            binding.btnPlayAudio.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }

        fun updateSeekBarProgress(progress: Int) {
            if (!isUserSeeking) {
                binding.audioSeekBar.progress = progress
            }
        }

        fun updateAudioDurationText(durationText: String) {
            binding.tvAudioDuration.text = durationText
        }

        fun updateDuration(duration: Int) {
            if (binding.audioSeekBar.max == 0 && duration > 0) {
                binding.audioSeekBar.max = duration
            }
        }

        fun updateWaveform(amplitude: Int) {
            binding.waveform.setProgress(amplitude)
        }

        private fun formatDuration(seconds: Int): String {
            if (seconds < 0) return "00:00"
            val minutes = seconds / 60
            val secs = seconds % 60
            return String.format("%02d:%02d", minutes, secs)
        }
    }
}