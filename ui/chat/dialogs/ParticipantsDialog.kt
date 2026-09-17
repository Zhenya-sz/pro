package com.fitnesslemon.app.ui.chat.dialogs

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.ChatParticipant

class ParticipantsDialog(
    private val context: Context,
    private val participants: List<ChatParticipant>,
    private val participantsCount: Int
) {

    private val TAG = "ParticipantsDialog"

    fun show() {
        Log.d(TAG, "========== show() ==========")
        Log.d(TAG, "Участников для отображения: ${participants.size}")

        if (participants.isEmpty()) {
            Log.w(TAG, "⚠️ Список участников пуст")
            AlertDialog.Builder(context)
                .setTitle("Участники группы")
                .setMessage("Список участников пуст")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        participants.forEachIndexed { index, participant ->
            Log.d(TAG, "  [$index] ${participant.name} (${participant.role})")
        }

        val adapter = ParticipantsAdapter(participants)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Участники группы ($participantsCount)")
            .setAdapter(adapter, null)
            .setPositiveButton("Закрыть", null)
            .create()

        dialog.show()

        // Устанавливаем высоту диалога (70% от экрана)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.7).toInt()
        )

        Log.d(TAG, "✅ Диалог показан")
    }
}

class ParticipantsAdapter(private val participants: List<ChatParticipant>) :
    BaseAdapter() {

    private val TAG = "ParticipantsAdapter"

    override fun getCount(): Int {
        Log.d(TAG, "getCount = ${participants.size}")
        return participants.size
    }

    override fun getItem(position: Int): Any {
        return participants[position]
    }

    override fun getItemId(position: Int): Long {
        return participants[position].id.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant, parent, false)

        val participant = participants[position]

        val ivAvatar = view.findViewById<ImageView>(R.id.ivParticipantAvatar)
        val tvName = view.findViewById<TextView>(R.id.tvParticipantName)
        val tvRole = view.findViewById<TextView>(R.id.tvParticipantRole)

        Log.d(TAG, "getView position=$position: ${participant.name}")

        tvName.text = participant.name

        tvRole.text = when (participant.role) {
            "admin" -> "Администратор"
            "moderator" -> "Модератор"
            else -> "Участник"
        }

        val avatarUrl = participant.getAvatarUrlValue()
        Log.d(TAG, "  Аватар URL для ${participant.name}: $avatarUrl")

        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(view.context)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_profile)
        }

        return view
    }
}