package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.AdminSubscription
import com.fitnesslemon.app.databinding.ItemAdminSubscriptionBinding

class AdminSubscriptionsAdapter(
    private val subscriptions: List<AdminSubscription>,
    private val onItemClick: (AdminSubscription) -> Unit,
    private val onEditClick: (AdminSubscription) -> Unit,
    private val onDeleteClick: (AdminSubscription) -> Unit,
    private val onSellClick: (AdminSubscription) -> Unit
) : RecyclerView.Adapter<AdminSubscriptionsAdapter.SubscriptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val binding = ItemAdminSubscriptionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SubscriptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        holder.bind(subscriptions[position])
    }

    override fun getItemCount() = subscriptions.size

    inner class SubscriptionViewHolder(
        private val binding: ItemAdminSubscriptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(subscription: AdminSubscription) {
            binding.apply {
                tvTitle.text = subscription.title
                tvDescription.text = subscription.description
                tvWorkoutsCount.text = "${subscription.workoutsCount} тренировок"
                tvPrice.text = "${subscription.price} ₽"
                tvDuration.text = "${subscription.durationDays} дней"
                tvSales.text = "Продано: ${subscription.salesCount}"
                tvRevenue.text = "Выручка: ${subscription.totalRevenue} ₽"

                // Статус активности
                if (subscription.isActive) {
                    tvStatus.text = "Активен"
                    tvStatus.setTextColor(root.context.getColor(R.color.lemon_primary))
                } else {
                    tvStatus.text = "Неактивен"
                    tvStatus.setTextColor(root.context.getColor(R.color.lemon_error))
                }

                // Тип тренировки
                tvWorkoutType.text = subscription.workoutType ?: "Любой тип"

                // Показываем иконку чата если есть
                if (subscription.chatId != null) {
                    // Можно добавить иконку чата если нужно
                }

                // Клики
                root.setOnClickListener {
                    onItemClick(subscription)
                }

                btnEdit.setOnClickListener {
                    onEditClick(subscription)
                }

                btnDelete.setOnClickListener {
                    onDeleteClick(subscription)
                }

                btnSell.setOnClickListener {
                    onSellClick(subscription)
                }
            }
        }
    }
}