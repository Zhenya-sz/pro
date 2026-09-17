package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.AdminUser
import com.fitnesslemon.app.databinding.ItemAdminUserBinding

class AdminUsersAdapter(
    private val users: List<AdminUser>,
    private val onItemClick: (AdminUser) -> Unit,
    private val onEditClick: (AdminUser) -> Unit,
    private val onResetPasswordClick: (AdminUser) -> Unit,
    private val onDeleteClick: (AdminUser) -> Unit
) : RecyclerView.Adapter<AdminUsersAdapter.UserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemAdminUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size

    inner class UserViewHolder(
        private val binding: ItemAdminUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: AdminUser) {
            binding.apply {
                tvName.text = user.name
                tvEmail.text = user.email
                tvPhone.text = user.phone ?: "Телефон не указан"
                tvRole.text = when (user.role) {
                    "administrator" -> "Администратор"
                    "fitness_trainer" -> "Тренер"
                    else -> "Пользователь"
                }
                
                tvSubscription.text = user.subscriptionName ?: "Нет абонемента"
                tvVisits.text = "Посещений: ${user.visitsCount}"
                
                tvSubscriptionExpiry.text = user.subscriptionExpiry?.let {
                    "до $it"
                } ?: ""

                // Статус активности
                if (user.isActive) {
                    ivActiveStatus.setImageResource(R.drawable.ic_active)
                    ivActiveStatus.setColorFilter(root.context.getColor(R.color.lemon_primary))
                } else {
                    ivActiveStatus.setImageResource(R.drawable.ic_inactive)
                    ivActiveStatus.setColorFilter(root.context.getColor(R.color.lemon_error))
                }

                // Аватар
                if (!user.avatar.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(user.avatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .into(ivAvatar)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_profile)
                }

                // Клики
                root.setOnClickListener {
                    onItemClick(user)
                }

                btnEdit.setOnClickListener {
                    onEditClick(user)
                }

                btnResetPassword.setOnClickListener {
                    onResetPasswordClick(user)
                }

                btnDelete.setOnClickListener {
                    onDeleteClick(user)
                }
            }
        }
    }
}