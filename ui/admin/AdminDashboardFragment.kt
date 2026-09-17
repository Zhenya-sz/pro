package com.fitnesslemon.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentAdminDashboardBinding
import com.fitnesslemon.app.ui.admin.adapters.PopularWorkoutsAdapter
import com.fitnesslemon.app.utils.PreferencesManager
import com.fitnesslemon.app.ui.admin.AdminNewsFragment



class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Проверяем права администратора
        if (!PreferencesManager.isAdmin()) {
            Toast.makeText(requireContext(), "У вас нет прав доступа к админ-панели", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
            return
        }

        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadAdminStats()
        viewModel.loadUsers()
        viewModel.loadWorkouts()
        viewModel.loadSubscriptions()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Админ-панель"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        binding.rvPopularWorkouts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPopularWorkouts.setHasFixedSize(true)
    }

    private fun setupListeners() {
        binding.btnUsers.setOnClickListener {
            val fragment = AdminUsersFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_users")
                .commit()
        }

        binding.btnWorkouts.setOnClickListener {
            val fragment = AdminWorkoutsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_workouts")
                .commit()
        }

        binding.btnSubscriptions.setOnClickListener {
            val fragment = AdminSubscriptionsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_subscriptions")
                .commit()
        }

        binding.btnSchedule.setOnClickListener {
            val fragment = AdminScheduleFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_schedule")
                .commit()
        }

        binding.btnReports.setOnClickListener {
            val fragment = AdminReportsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_reports")
                .commit()
        }

        binding.btnSettings.setOnClickListener {
            val fragment = AdminSettingsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_settings")
                .commit()
        }

        binding.btnGroups.setOnClickListener {
            val fragment = AdminGroupsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_groups")
                .commit()
        }

        binding.btnRatings.setOnClickListener {
            val fragment = AdminRatingsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_ratings")
                .commit()
        }

        // ✅ КНОПКА НОВОСТЕЙ — ИСПОЛЬЗУЕТ AdminNewsFragment
        binding.btnNews.setOnClickListener {
            val fragment = AdminNewsFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("admin_news")
                .commit()
        }
    }

    private fun observeViewModel() {
        viewModel.adminStats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                updateUI(stats)
            } else {
                showLoadingError()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun updateUI(stats: com.fitnesslemon.app.data.models.AdminStats) {
        binding.apply {
            tvTodayVisits.text = stats.todayVisits.toString()
            tvTodayChange.text = "${if (stats.todayVisitsChange > 0) "▲" else "▼"} ${Math.abs(stats.todayVisitsChange)}%"
            tvTodayChange.setTextColor(if (stats.todayVisitsChange > 0)
                android.graphics.Color.parseColor("#4CAF50")
            else android.graphics.Color.parseColor("#F44336"))

            tvWeekVisits.text = stats.weekVisits.toString()
            tvWeekChange.text = "${if (stats.weekVisitsChange > 0) "▲" else "▼"} ${Math.abs(stats.weekVisitsChange)}%"
            tvWeekChange.setTextColor(if (stats.weekVisitsChange > 0)
                android.graphics.Color.parseColor("#4CAF50")
            else android.graphics.Color.parseColor("#F44336"))

            tvMonthVisits.text = stats.monthVisits.toString()
            tvMonthChange.text = "${if (stats.monthVisitsChange > 0) "▲" else "▼"} ${Math.abs(stats.monthVisitsChange)}%"
            tvMonthChange.setTextColor(if (stats.monthVisitsChange > 0)
                android.graphics.Color.parseColor("#4CAF50")
            else android.graphics.Color.parseColor("#F44336"))

            if (stats.popularWorkouts.isNotEmpty()) {
                rvPopularWorkouts.adapter = PopularWorkoutsAdapter(stats.popularWorkouts)
            }

            tvRevenueMonth.text = "${String.format("%,d", stats.revenueMonth.toInt())} ₽"
            tvSubscriptionsSold.text = stats.subscriptionsSold.toString()
            tvNewUsers.text = stats.newUsers.toString()
            tvTotalUsers.text = stats.totalUsers.toString()
            tvActiveUsers.text = stats.activeUsers.toString()
        }
    }

    private fun showLoadingError() {
        binding.apply {
            tvTodayVisits.text = "—"
            tvTodayChange.text = ""
            tvWeekVisits.text = "—"
            tvWeekChange.text = ""
            tvMonthVisits.text = "—"
            tvMonthChange.text = ""
            tvRevenueMonth.text = "—"
            tvSubscriptionsSold.text = "—"
            tvNewUsers.text = "—"
            tvTotalUsers.text = "—"
            tvActiveUsers.text = "—"
        }
        Toast.makeText(requireContext(), "Не удалось загрузить статистику", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}