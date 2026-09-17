package com.fitnesslemon.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitnesslemon.app.databinding.FragmentAdminRatingsBinding
import com.fitnesslemon.app.ui.admin.adapters.RatingsAdapter
import com.fitnesslemon.app.data.models.Rating

class AdminRatingsFragment : Fragment() {

    private var _binding: FragmentAdminRatingsBinding? = null
    private val binding get() = _binding!!

    private val ratingsAdapter = RatingsAdapter(
        onApprove = { rating -> approveRating(rating) },
        onDelete = { rating -> deleteRating(rating) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminRatingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        loadRatings()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Отзывы"
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupRecyclerView() {
        binding.rvRatings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRatings.adapter = ratingsAdapter
    }

    private fun loadRatings() {
        // TODO: загрузка с сервера, пока тестовые данные
        val demoRatings = listOf(
            Rating(1, "Анна", 5, "Отличный клуб!", "2024-01-15", true),
            Rating(2, "Иван", 4, "Хорошие тренеры", "2024-01-14", false)
        )
        ratingsAdapter.submitList(demoRatings)
        updateEmptyView(demoRatings.isEmpty())
    }

    private fun approveRating(rating: Rating) {
        Toast.makeText(requireContext(), "Отзыв одобрен", Toast.LENGTH_SHORT).show()
        // обновить список
    }

    private fun deleteRating(rating: Rating) {
        Toast.makeText(requireContext(), "Отзыв удалён", Toast.LENGTH_SHORT).show()
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvRatings.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}