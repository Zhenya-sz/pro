package com.fitnesslemon.app.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.SnapHelper
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentNewsFeedBinding
import com.fitnesslemon.app.ui.adapters.NewsCardAdapter
import com.fitnesslemon.app.utils.SpacingItemDecoration
import com.fitnesslemon.app.viewmodel.NewsViewModel
import kotlinx.coroutines.launch

class NewsFeedFragment : Fragment() {

    private var _binding: FragmentNewsFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NewsViewModel
    private lateinit var adapter: NewsCardAdapter
    private val snapHelper: SnapHelper = PagerSnapHelper()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[NewsViewModel::class.java]

        setupRecyclerView()
        setupSwipeRefresh()
        setupToolbar()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_news_feed)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_mode_card -> {
                    Toast.makeText(requireContext(), "Режим: Карточки", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_mode_list -> {
                    Toast.makeText(requireContext(), "Режим: Список", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_mode_grid -> {
                    Toast.makeText(requireContext(), "Режим: Плитка", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_filter -> {
                    showFilterDialog()
                    true
                }
                else -> false
            }
        }
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = NewsCardAdapter(
            onLikeClick = { news ->
                viewModel.toggleLike(news.id)
            },
            onCommentClick = { news ->
                Toast.makeText(requireContext(), "Комментарии к: ${news.title}", Toast.LENGTH_SHORT).show()
            },
            onShareClick = { news ->
                shareNews(news)
            },
            onSaveClick = { news ->
                viewModel.toggleSave(news.id)
            },
            onReadMoreClick = { news ->
                showFullNews(news)
            },
            onMediaClick = { news, position ->
                openFullscreenMedia(news, position)
            }
        )

        binding.rvNews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NewsFeedFragment.adapter
            snapHelper.attachToRecyclerView(this)
            // Добавляем отступы между карточками
            addItemDecoration(SpacingItemDecoration(8))
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshNews()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.newsState.collect { state ->
                binding.swipeRefresh.isRefreshing = false

                when (state) {
                    is NewsViewModel.NewsState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is NewsViewModel.NewsState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        adapter.submitList(state.news)
                        adapter.setLikedNews(state.likedNewsIds)
                        adapter.setSavedNews(state.savedNewsIds)
                        binding.tvEmpty.visibility = if (state.news.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvNews.visibility = if (state.news.isEmpty()) View.GONE else View.VISIBLE
                    }
                    is NewsViewModel.NewsState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearToastMessage()
            }
        }
    }

    private fun showFilterDialog() {
        val categories = arrayOf("Все", "Акции", "Новости клуба", "Советы тренеров")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Фильтр по категориям")
            .setItems(categories) { _, which ->
                if (which == 0) {
                    viewModel.filterByCategory(null)
                } else {
                    viewModel.filterByCategory(categories[which])
                }
            }
            .show()
    }

    private fun shareNews(news: com.fitnesslemon.app.data.models.News) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, news.title)
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "${news.title}\n\n${news.content}\n\nЧитайте в приложении Fitness Lemon"
            )
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться"))
    }

    private fun showFullNews(news: com.fitnesslemon.app.data.models.News) {
        Toast.makeText(requireContext(), "Открыть новость: ${news.title}", Toast.LENGTH_SHORT).show()
    }

    private fun openFullscreenMedia(news: com.fitnesslemon.app.data.models.News, position: Int) {
        Toast.makeText(requireContext(), "Полноэкранный режим: ${news.title}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}