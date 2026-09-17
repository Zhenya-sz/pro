package com.fitnesslemon.app.ui.admin

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.models.News
import com.fitnesslemon.app.databinding.FragmentAdminNewsBinding
import com.fitnesslemon.app.ui.admin.adapters.AdminNewsAdapter
import com.fitnesslemon.app.ui.admin.adapters.NewsMediaAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

// ❌ УДАЛИТЕ ОТСЮДА:
// sealed class DashboardState { ... }
// class MainViewModel : ViewModel { ... }

class AdminNewsFragment : Fragment() {

    private var _binding: FragmentAdminNewsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private val newsAdapter = AdminNewsAdapter(
        onEditClick = { news -> openEditNewsFragment(news.id) },
        onDeleteClick = { news -> confirmDeleteNews(news) }
    )

    private lateinit var imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var videoPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            handleSelectedMedia(uris, false)
        }
        videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            handleSelectedMedia(uris, true)
        }
    }

    private val tempImages = mutableListOf<Uri>()
    private val tempVideos = mutableListOf<Uri>()
    private val tempMediaItems = mutableListOf<NewsMediaAdapter.MediaItem>()
    private var previewAdapter: NewsMediaAdapter? = null
    private var rvMediaPreview: RecyclerView? = null

    private var isGridView = false
    private var currentSort = "date_desc"
    private var filterCategory: String? = null
    private var filterImportance: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupSearch()
        setupFilters()
        observeViewModel()
        viewModel.loadNews()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Управление новостями"
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnViewMode.setOnClickListener { toggleViewMode() }
        binding.btnSort.setOnClickListener { showSortDialog() }
    }

    private fun setupRecyclerView() {
        binding.rvNews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNews.adapter = newsAdapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener { showCreateNewsDialog() }
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            viewModel.searchNews(binding.etSearch.text.toString())
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadNews() }
    }

    private fun setupFilters() {
        binding.btnFilterCategory.setOnClickListener { showCategoryFilter() }
        binding.btnFilterImportance.setOnClickListener { showImportanceFilter() }
        binding.btnFilterDate.setOnClickListener { showDateFilter() }
    }

    private fun observeViewModel() {
        viewModel.news.observe(viewLifecycleOwner) { list ->
            newsAdapter.submitList(list)
            updateEmptyView(list.isEmpty())
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                when {
                    message.startsWith("Новость создана") -> {
                        val parts = message.split("|")
                        if (parts.size == 2) {
                            val newsId = parts[1].toIntOrNull()
                            if (newsId != null) {
                                Toast.makeText(requireContext(), "Новость создана", Toast.LENGTH_SHORT).show()
                                viewModel.loadNews()
                                openEditNewsFragment(newsId)
                            }
                        }
                        viewModel.clearError()
                    }
                    message.isNotEmpty() && !message.startsWith("Новость создана") -> {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        viewModel.clearError()
                    }
                }
            }
        }

        viewModel.createdNewsId.observe(viewLifecycleOwner) { newsId ->
            if (newsId != null && newsId > 0) {}
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvNews.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun toggleViewMode() {
        isGridView = !isGridView
        binding.rvNews.layoutManager = if (isGridView) {
            GridLayoutManager(requireContext(), 2)
        } else {
            LinearLayoutManager(requireContext())
        }
    }

    private fun showSortDialog() {
        val items = arrayOf("По дате (новые)", "По дате (старые)", "По популярности")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Сортировка")
            .setItems(items) { _, which ->
                currentSort = when (which) {
                    0 -> "date_desc"
                    1 -> "date_asc"
                    else -> "popularity"
                }
                applyFiltersAndSort()
            }
            .show()
    }

    private fun showCategoryFilter() {
        val categories = listOf("Все", "Акции и скидки", "Новые программы", "События и мероприятия", "Советы тренеров", "Изменения в расписании", "Общие новости")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Категория")
            .setItems(categories.toTypedArray()) { _, which ->
                filterCategory = if (which == 0) null else categories[which]
                binding.btnFilterCategory.text = if (which == 0) "Категория ▾" else categories[which]
                applyFiltersAndSort()
            }
            .show()
    }

    private fun showImportanceFilter() {
        val items = arrayOf("Все", "Обычные", "⭐ Важные", "📌 Закрепленные")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Важность")
            .setItems(items) { _, which ->
                filterImportance = when (which) {
                    0 -> null
                    1 -> "normal"
                    2 -> "important"
                    else -> "pinned"
                }
                binding.btnFilterImportance.text = if (which == 0) "Важность ▾" else items[which]
                applyFiltersAndSort()
            }
            .show()
    }

    private fun showDateFilter() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val date = String.format("%04d-%02d-%02d", year, month + 1, day)
            binding.btnFilterDate.text = date
            applyFiltersAndSort(date)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun applyFiltersAndSort(dateFilter: String? = null) {
        viewModel.news.value?.let { allNews ->
            var filtered = allNews

            filterCategory?.let { category ->
                filtered = filtered.filter { it.categoryName == category }
            }

            filterImportance?.let { importance ->
                filtered = filtered.filter { it.importance == importance }
            }

            dateFilter?.let { date ->
                filtered = filtered.filter { it.formattedDate?.contains(date) == true }
            }

            filtered = when (currentSort) {
                "date_asc" -> filtered.sortedBy { it.date }
                "popularity" -> filtered.sortedByDescending { it.viewsCount }
                else -> filtered.sortedByDescending { it.date }
            }

            newsAdapter.submitList(filtered)
            updateEmptyView(filtered.isEmpty())
        }
    }

    private fun showCreateNewsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_news, null)

        val chipNormal = dialogView.findViewById<Chip>(R.id.chipNormal)
        val chipImportant = dialogView.findViewById<Chip>(R.id.chipImportant)
        val chipPinned = dialogView.findViewById<Chip>(R.id.chipPinned)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etTitle)
        val etShortDescription = dialogView.findViewById<TextInputEditText>(R.id.etShortDescription)
        val etContent = dialogView.findViewById<TextInputEditText>(R.id.etContent)
        val switchSendPush = dialogView.findViewById<SwitchMaterial>(R.id.switchSendPush)
        val btnAddImage = dialogView.findViewById<MaterialButton>(R.id.btnAddImage)
        val btnAddVideo = dialogView.findViewById<MaterialButton>(R.id.btnAddVideo)
        rvMediaPreview = dialogView.findViewById(R.id.rvMediaPreview)

        tempImages.clear()
        tempVideos.clear()
        tempMediaItems.clear()

        val chips = listOf(chipNormal, chipImportant, chipPinned)
        chips.forEach { chip ->
            chip.setOnClickListener {
                chips.forEach { it.isChecked = false }
                chip.isChecked = true
            }
        }

        rvMediaPreview?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        previewAdapter = NewsMediaAdapter(
            tempMediaItems,
            onDelete = { item ->
                tempMediaItems.remove(item)
                val uri = Uri.parse(item.url)
                tempImages.remove(uri)
                tempVideos.remove(uri)
                previewAdapter?.notifyDataSetChanged()
                rvMediaPreview?.visibility = if (tempMediaItems.isEmpty()) View.GONE else View.VISIBLE
            },
            onPlay = {}
        )
        rvMediaPreview?.adapter = previewAdapter

        btnAddImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        btnAddVideo.setOnClickListener { videoPickerLauncher.launch("video/*") }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Новая новость")
            .setView(dialogView)
            .setPositiveButton("Создать и открыть редактор") { _, _ ->
                val title = etTitle.text.toString().trim()
                val content = etContent.text.toString().trim()
                val shortDescription = etShortDescription.text.toString().trim()

                if (title.isNotEmpty()) {
                    val importance = when {
                        chipImportant.isChecked -> "important"
                        chipPinned.isChecked -> "pinned"
                        else -> "normal"
                    }

                    viewModel.createNews(
                        title = title,
                        content = content,
                        shortDescription = shortDescription,
                        importance = importance,
                        sendPush = switchSendPush.isChecked,
                        images = tempImages.toList(),
                        videos = tempVideos.toList()
                    )
                } else {
                    Toast.makeText(requireContext(), "Введите заголовок", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun handleSelectedMedia(uris: List<Uri>, isVideo: Boolean) {
        uris.forEach { uri ->
            if (isVideo) {
                tempVideos.add(uri)
                tempMediaItems.add(NewsMediaAdapter.MediaItem(uri.toString(), "video"))
            } else {
                tempImages.add(uri)
                tempMediaItems.add(NewsMediaAdapter.MediaItem(uri.toString(), "image"))
            }
        }
        previewAdapter?.notifyDataSetChanged()
        rvMediaPreview?.visibility = if (tempMediaItems.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openEditNewsFragment(newsId: Int) {
        val fragment = AdminNewsEditFragment().apply {
            arguments = Bundle().apply { putInt("news_id", newsId) }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack("admin_news_edit")
            .commit()
    }

    private fun confirmDeleteNews(news: News) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление новости")
            .setMessage("Удалить \"${news.title}\"?")
            .setPositiveButton("Удалить") { _, _ -> viewModel.deleteNews(news.id) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}