package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.AdminUser
import com.fitnesslemon.app.databinding.ItemUserSelectionBinding

class UserSelectionAdapter(
    private val users: List<AdminUser>,
    private val onUserSelected: (AdminUser) -> Unit
) : RecyclerView.Adapter<UserSelectionAdapter.UserViewHolder>(), Filterable {

    private var filteredList: List<AdminUser> = users
    private var originalList: List<AdminUser> = users

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserSelectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(filteredList[position])
    }

    override fun getItemCount() = filteredList.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchQuery = constraint?.toString()?.lowercase() ?: ""
                filteredList = if (searchQuery.isEmpty()) {
                    originalList
                } else {
                    originalList.filter {
                        it.name.lowercase().contains(searchQuery) ||
                        it.email.lowercase().contains(searchQuery) ||
                        (it.phone?.lowercase()?.contains(searchQuery) == true)
                    }
                }
                val results = FilterResults()
                results.values = filteredList
                results.count = filteredList.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as List<AdminUser>
                notifyDataSetChanged()
            }
        }
    }

    inner class UserViewHolder(
        private val binding: ItemUserSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: AdminUser) {
            binding.apply {
                tvUserName.text = user.name
                tvUserEmail.text = user.email
                tvUserPhone.text = user.phone ?: "Телефон не указан"

                if (!user.avatar.isNullOrEmpty()) {
                    Glide.with(root.context)
                        .load(user.avatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .into(ivUserAvatar)
                } else {
                    ivUserAvatar.setImageResource(R.drawable.ic_profile)
                }

                root.setOnClickListener {
                    onUserSelected(user)
                }
            }
        }
    }
}