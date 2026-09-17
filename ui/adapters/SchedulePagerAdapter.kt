package com.fitnesslemon.app.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.fitnesslemon.app.ui.workouts.ScheduleDayFragment
import java.util.Date

class SchedulePagerAdapter(
    fragmentActivity: FragmentActivity,
    private val days: List<Date>,
    private val onClassClick: (Int) -> Unit,
    private val onQuickBook: (Int) -> Unit
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = days.size

    override fun createFragment(position: Int): Fragment {
        return ScheduleDayFragment.newInstance(
            date = days[position],
            onClassClick = onClassClick,
            onQuickBook = onQuickBook,
            isExpanded = true
        )
    }
}