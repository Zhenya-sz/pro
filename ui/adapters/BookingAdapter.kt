package com.fitnesslemon.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.data.models.Booking
import com.fitnesslemon.app.databinding.ItemBookingBinding

class BookingAdapter(
    private val bookings: List<Booking>
) : RecyclerView.Adapter<BookingAdapter.BookingViewHolder>() {

    class BookingViewHolder(val binding: ItemBookingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]
        holder.binding.apply {
            tvTitle.text = booking.title
            tvTrainer.text = "Тренер: ${booking.trainerName}"
            tvDateTime.text = "📅 ${booking.formattedDate}"

            // Безопасно проверяем наличие мест
            val current = booking.currentParticipants ?: 0
            val max = booking.maxParticipants ?: 0

            if (current > 0 && max > 0) {
                tvSpots.text = "👥 $current/$max"
                tvSpots.visibility = View.VISIBLE
            } else {
                tvSpots.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = bookings.size
}