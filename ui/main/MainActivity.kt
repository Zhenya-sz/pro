package com.fitnesslemon.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.ActivityMainBinding
import com.fitnesslemon.app.ui.admin.AdminDashboardFragment
import com.fitnesslemon.app.ui.auth.LoginActivity
import com.fitnesslemon.app.ui.chat.ChatListFragment
import com.fitnesslemon.app.ui.main.HomeFragment
import com.fitnesslemon.app.ui.notifications.NotificationViewModel
import com.fitnesslemon.app.ui.notifications.NotificationsFragment
import com.fitnesslemon.app.ui.notifications.UnreadCountState
import com.fitnesslemon.app.ui.profile.ProfileFragment
import com.fitnesslemon.app.ui.trainers.TrainersFragment
import com.fitnesslemon.app.ui.workouts.WorkoutsFragment
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val notificationViewModel: NotificationViewModel by lazy { NotificationViewModel() }

    private var notificationMenuItem: MenuItem? = null
    private var adminMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ ПРОВЕРЯЕМ АВТОРИЗАЦИЮ
        val token = try {
            PreferencesManager.getToken()
        } catch (e: Exception) {
            println("❌ Ошибка получения токена: ${e.message}")
            null
        }

        val isLoggedIn = try {
            PreferencesManager.isLoggedIn()
        } catch (e: Exception) {
            false
        }

        // ✅ ЕСЛИ НЕТ ТОКЕНА ИЛИ НЕТ СТАТУСА ВХОДА - ПЕРЕХОДИМ НА ЛОГИН
        if (token.isNullOrEmpty() || !isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupToolbar()
        setupBottomNavigation()
        observeNotificationCount()
        observeUserRole()

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        notificationViewModel.loadUnreadCount()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        notificationMenuItem = menu.findItem(R.id.action_notifications)
        updateNotificationBadge(0)

        try {
            if (PreferencesManager.isAdmin()) {
                adminMenuItem = menu.add(Menu.NONE, R.id.action_admin, 1, "Админ")
                adminMenuItem?.setIcon(R.drawable.ic_admin)
                adminMenuItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        } catch (e: Exception) {
            println("❌ Ошибка проверки прав администратора: ${e.message}")
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notifications -> {
                loadFragment(NotificationsFragment())
                true
            }
            R.id.action_admin -> {
                loadFragment(AdminDashboardFragment())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        adminMenuItem?.let { menu.removeItem(it.itemId) }
        adminMenuItem = null

        try {
            if (PreferencesManager.isAdmin()) {
                adminMenuItem = menu.add(Menu.NONE, R.id.action_admin, 1, "Админ")
                adminMenuItem?.setIcon(R.drawable.ic_admin)
                adminMenuItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        } catch (e: Exception) {
            println("❌ Ошибка проверки прав администратора: ${e.message}")
        }

        return super.onPrepareOptionsMenu(menu)
    }

    fun updateNotificationBadge(count: Int) {
        notificationMenuItem?.let { menuItem ->
            if (count > 0) {
                menuItem.setIcon(R.drawable.ic_notification_with_badge)
                menuItem.title = "Уведомления ($count)"
            } else {
                menuItem.setIcon(R.drawable.ic_notification)
                menuItem.title = "Уведомления"
            }
        }
    }

    private fun observeNotificationCount() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                notificationViewModel.unreadCountState.collect { state ->
                    when (state) {
                        is UnreadCountState.Success -> updateNotificationBadge(state.count)
                        else -> {}
                    }
                }
            }
        }
    }

    private fun observeUserRole() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    PreferencesManager.getUserRoleFlow().collect {
                        invalidateOptionsMenu()
                    }
                } catch (e: Exception) {
                    println("❌ Ошибка получения роли пользователя: ${e.message}")
                }
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setTitle("Fitness Lemon")
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.navigation_workouts -> {
                    loadFragment(WorkoutsFragment())
                    true
                }
                R.id.navigation_trainers -> {
                    loadFragment(TrainersFragment())
                    true
                }
                R.id.navigation_chat -> {
                    loadFragment(ChatListFragment())
                    true
                }
                R.id.navigation_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
        binding.bottomNavigation.selectedItemId = R.id.navigation_home
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        notificationViewModel.loadUnreadCount()
        invalidateOptionsMenu()

        val token = try {
            PreferencesManager.getToken()
        } catch (e: Exception) {
            null
        }

        val isLoggedIn = try {
            PreferencesManager.isLoggedIn()
        } catch (e: Exception) {
            false
        }

        if (token.isNullOrEmpty() || !isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        notificationViewModel.loadUnreadCount()
    }
}