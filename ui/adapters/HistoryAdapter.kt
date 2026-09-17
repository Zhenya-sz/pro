package com.fitnesslemon.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.data.models.Booking
import com.fitnesslemon.app.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val history: List<Booking>
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = history[position]
        holder.binding.apply {
            tvTitle.text = item.title
            tvTrainer.text = "Тренер: ${item.trainerName}"
            tvDateTime.text = "📅 ${item.formattedDate}"
        }
    }

    override fun getItemCount() = history.size
}