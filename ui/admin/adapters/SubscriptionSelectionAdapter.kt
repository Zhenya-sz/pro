package com.fitnesslemon.app.ui.admin.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.AdminSubscription
import com.fitnesslemon.app.databinding.ItemSubscriptionSelectionBinding

class SubscriptionSelectionAdapter(
    private val subscriptions: List<AdminSubscription>,
    private val onSubscriptionSelected: (AdminSubscription) -> Unit
) : RecyclerView.Adapter<SubscriptionSelectionAdapter.SubscriptionViewHolder>(), Filterable {

    private var filteredList: List<AdminSubscription> = subscriptions
    private var originalList: List<AdminSubscription> = subscriptions

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val binding = ItemSubscriptionSelectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SubscriptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
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
                        it.title.lowercase().contains(searchQuery) ||
                        it.description.lowercase().contains(searchQuery)
                    }
                }
                val results = FilterResults()
                results.values = filteredList
                results.count = filteredList.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as List<AdminSubscription>
                notifyDataSetChanged()
            }
        }
    }

    inner class SubscriptionViewHolder(
        private val binding: ItemSubscriptionSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(subscription: AdminSubscription) {
            binding.apply {
                tvSubscriptionTitle.text = subscription.title
                tvSubscriptionDescription.text = subscription.description
                tvSubscriptionDetails.text = "${subscription.workoutsCount} тренировок · ${subscription.price} ₽ · ${subscription.durationDays} дней"
                
                root.setOnClickListener {
                    onSubscriptionSelected(subscription)
                }
            }
        }
    }
}