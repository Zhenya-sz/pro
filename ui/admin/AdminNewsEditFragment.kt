package com.fitnesslemon.app.ui.admin

import android.app.Dialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fitnesslemon.app.R
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.api.UpdateNewsRequest
import com.fitnesslemon.app.data.models.News
import com.fitnesslemon.app.databinding.FragmentAdminNewsEditBinding
import com.fitnesslemon.app.ui.admin.adapters.NewsMediaAdapter
import com.fitnesslemon.app.utils.PreferencesManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.*

class AdminNewsEditFragment : Fragment() {

    private var _binding: FragmentAdminNewsEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AdminViewModel
    private var newsId: Int = 0
    private var newsItem: News? = null

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var isUndoRedoOperation = false

    private var autoSaveRunnable: Runnable? = null
    private val autoSaveDelay = 5000L

    private var currentFontSize = 16f

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uploadMedia(uris)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Используем ContextThemeWrapper для правильной темы
        val themedContext = ContextThemeWrapper(
            requireContext(),
            R.style.Theme_FitnessLemon
        )
        val themedInflater = LayoutInflater.from(themedContext)
        _binding = FragmentAdminNewsEditBinding.inflate(themedInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        newsId = arguments?.getInt("news_id") ?: 0
        if (newsId == 0) {
            Toast.makeText(requireContext(), "Ошибка: новость не найдена", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        setupToolbar()
        setupFormattingToolbar()
        setupListeners()
        setupAutoSave()
        loadNewsDetails()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Редактирование новости"
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnSave.setOnClickListener { saveChanges() }
        binding.btnPreviewBottom.setOnClickListener { showPreview() }
        binding.btnUndo.setOnClickListener { undo() }
        binding.btnRedo.setOnClickListener { redo() }
        binding.btnPreview.setOnClickListener { showPreview() }
    }

    private fun setupFormattingToolbar() {
        binding.btnBold.setOnClickListener { toggleBold() }
        binding.btnItalic.setOnClickListener { toggleItalic() }
        binding.btnUnderline.setOnClickListener { toggleUnderline() }
        binding.btnFontSizeDown.setOnClickListener { decreaseFontSize() }
        binding.btnFontSizeUp.setOnClickListener { increaseFontSize() }
        binding.btnBulletList.setOnClickListener { insertText("\n- ") }
        binding.btnNumberList.setOnClickListener { insertText("\n1. ") }
        binding.btnQuote.setOnClickListener { insertText("\n> ") }
        binding.btnLink.setOnClickListener { showInsertLinkDialog() }
        binding.btnDivider.setOnClickListener { insertText("\n---\n") }
    }

    private fun setupListeners() {
        binding.btnAddImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnAddVideo.setOnClickListener { imagePickerLauncher.launch("video/*") }
        binding.btnShare.setOnClickListener { shareNews() }
        binding.btnShareToChat.setOnClickListener { shareToChat() }
    }

    private fun setupAutoSave() {
        binding.etTitle.addTextChangedListener(createAutoSaveWatcher())
        binding.etContent.addTextChangedListener(createAutoSaveWatcher())
        binding.etShortDescription.addTextChangedListener(createAutoSaveWatcher())
    }

    private fun createAutoSaveWatcher(): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUndoRedoOperation) {
                    scheduleAutoSave()
                }
            }
        }
    }

    private fun scheduleAutoSave() {
        autoSaveRunnable?.let { binding.root.removeCallbacks(it) }
        autoSaveRunnable = Runnable {
            if (isAdded && _binding != null) {
                saveAsDraft()
            }
        }
        binding.root.postDelayed(autoSaveRunnable, autoSaveDelay)
    }

    private fun saveAsDraft() {
        val content = getContentWithMarkers()
        if (content.isNotEmpty()) {
            lifecycleScope.launch {
                PreferencesManager.saveDraft(newsId, content)
            }
        }
    }

    // ========== ФОРМАТИРОВАНИЕ ТЕКСТА ==========

    private fun toggleBold() {
        val editText = binding.etContent
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start == end) {
            Toast.makeText(requireContext(), "Выделите текст", Toast.LENGTH_SHORT).show()
            return
        }

        val editable = editText.text ?: return
        val styleSpans = editable.getSpans(start, end, StyleSpan::class.java)
        val isBold = styleSpans.any { it.style == Typeface.BOLD }

        isUndoRedoOperation = true
        saveForUndo(editable.toString())

        if (isBold) {
            styleSpans.filter { it.style == Typeface.BOLD }.forEach { editable.removeSpan(it) }
            Toast.makeText(requireContext(), "Жирный снят", Toast.LENGTH_SHORT).show()
        } else {
            editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            Toast.makeText(requireContext(), "Жирный применён", Toast.LENGTH_SHORT).show()
        }

        isUndoRedoOperation = false
    }

    private fun toggleItalic() {
        val editText = binding.etContent
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start == end) {
            Toast.makeText(requireContext(), "Выделите текст", Toast.LENGTH_SHORT).show()
            return
        }

        val editable = editText.text ?: return
        val styleSpans = editable.getSpans(start, end, StyleSpan::class.java)
        val isItalic = styleSpans.any { it.style == Typeface.ITALIC }

        isUndoRedoOperation = true
        saveForUndo(editable.toString())

        if (isItalic) {
            styleSpans.filter { it.style == Typeface.ITALIC }.forEach { editable.removeSpan(it) }
            Toast.makeText(requireContext(), "Курсив снят", Toast.LENGTH_SHORT).show()
        } else {
            editable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            Toast.makeText(requireContext(), "Курсив применён", Toast.LENGTH_SHORT).show()
        }

        isUndoRedoOperation = false
    }

    private fun toggleUnderline() {
        val editText = binding.etContent
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start == end) {
            Toast.makeText(requireContext(), "Выделите текст", Toast.LENGTH_SHORT).show()
            return
        }

        val editable = editText.text ?: return
        val underlines = editable.getSpans(start, end, UnderlineSpan::class.java)

        isUndoRedoOperation = true
        saveForUndo(editable.toString())

        if (underlines.isNotEmpty()) {
            underlines.forEach { editable.removeSpan(it) }
            Toast.makeText(requireContext(), "Подчёркивание снято", Toast.LENGTH_SHORT).show()
        } else {
            editable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            Toast.makeText(requireContext(), "Подчёркивание применено", Toast.LENGTH_SHORT).show()
        }

        isUndoRedoOperation = false
    }

    private fun increaseFontSize() {
        currentFontSize = (currentFontSize + 2f).coerceAtMost(28f)
        applyFontSizeToSelection()
        Toast.makeText(requireContext(), "Размер шрифта: ${currentFontSize.toInt()}", Toast.LENGTH_SHORT).show()
    }

    private fun decreaseFontSize() {
        currentFontSize = (currentFontSize - 2f).coerceAtLeast(10f)
        applyFontSizeToSelection()
        Toast.makeText(requireContext(), "Размер шрифта: ${currentFontSize.toInt()}", Toast.LENGTH_SHORT).show()
    }

    private fun applyFontSizeToSelection() {
        val editText = binding.etContent
        val start = editText.selectionStart
        val end = editText.selectionEnd
        if (start == end) return

        val editable = editText.text ?: return
        isUndoRedoOperation = true
        saveForUndo(editable.toString())

        val oldSpans = editable.getSpans(start, end, AbsoluteSizeSpan::class.java)
        oldSpans.forEach { editable.removeSpan(it) }

        val sizeInPx = (currentFontSize * resources.displayMetrics.scaledDensity).toInt()
        editable.setSpan(
            AbsoluteSizeSpan(sizeInPx, false),
            start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        isUndoRedoOperation = false
    }

    private fun insertText(text: String) {
        val editText = binding.etContent
        val start = editText.selectionStart
        val content = editText.text.toString()
        val newText = content.substring(0, start) + text + content.substring(start)
        isUndoRedoOperation = true
        saveForUndo(content)
        editText.setText(newText)
        editText.setSelection(start + text.length)
        isUndoRedoOperation = false
    }

    private fun showInsertLinkDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_insert_link, null)
        val etUrl = dialogView.findViewById<TextInputEditText>(R.id.etUrl)
        val etText = dialogView.findViewById<TextInputEditText>(R.id.etText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Вставить ссылку")
            .setView(dialogView)
            .setPositiveButton("Вставить") { _, _ ->
                val url = etUrl.text.toString().trim()
                val text = etText.text.toString().trim().ifEmpty { url }
                if (url.isNotEmpty()) {
                    val editText = binding.etContent
                    val start = editText.selectionStart
                    val content = editText.text.toString()
                    val linkText = "[$text]($url)"
                    val newText = content.substring(0, start) + linkText + content.substring(start)
                    saveForUndo(content)
                    editText.setText(newText)
                    editText.setSelection(start + linkText.length)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun saveForUndo(text: String) {
        undoStack.add(text)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
    }

    private fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = binding.etContent.text.toString()
            redoStack.add(current)
            val previous = undoStack.removeAt(undoStack.lastIndex)
            isUndoRedoOperation = true
            binding.etContent.setText(previous)
            binding.etContent.setSelection(previous.length)
            isUndoRedoOperation = false
            Toast.makeText(requireContext(), "Отменено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = binding.etContent.text.toString()
            undoStack.add(current)
            val next = redoStack.removeAt(redoStack.lastIndex)
            isUndoRedoOperation = true
            binding.etContent.setText(next)
            binding.etContent.setSelection(next.length)
            isUndoRedoOperation = false
            Toast.makeText(requireContext(), "Повторено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadNewsDetails() {
        viewModel.news.observe(viewLifecycleOwner) { list ->
            newsItem = list.find { it.id == newsId }
            newsItem?.let { news ->
                binding.etTitle.setText(news.title)
                binding.etShortDescription.setText(news.shortDescription ?: news.excerpt ?: "")
                binding.tvViewsCount.text = "👁 Просмотров: ${news.viewsCount}"
                binding.tvPublishDate.text = "📅 ${news.formattedDate ?: ""}"
                setupImportanceChips(news)
                setupMediaList(news)

                val rawContent = news.content ?: ""
                val draft = PreferencesManager.getDraft(newsId)
                val textToLoad = if (draft != null && rawContent.isEmpty()) draft else rawContent
                loadContentWithSpans(textToLoad)
            }
        }
        viewModel.loadNews()
    }

    private fun loadContentWithSpans(text: String) {
        val editText = binding.etContent
        isUndoRedoOperation = true

        val cleanText = text
            .replace(Regex("\\{\\{size=\\d+\\}\\}"), "")
            .replace(Regex("\\{\\{/size\\}\\}"), "")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)"), "$1")
            .replace(Regex("__(.*?)__"), "$1")

        editText.setText(cleanText)
        applySpansFromMarkers(editText.text!!, text)
        isUndoRedoOperation = false
    }

    private fun applySpansFromMarkers(editable: Editable, originalText: String) {
        val cleanText = editable.toString()

        Regex("\\{\\{size=(\\d+)\\}\\}(.*?)\\{\\{/size\\}\\}").findAll(originalText).forEach { match ->
            val sizeSp = match.groupValues[1].toIntOrNull() ?: return@forEach
            val markerText = match.groupValues[2]
            val startIndex = cleanText.indexOf(markerText)
            if (startIndex >= 0) {
                val endIndex = startIndex + markerText.length
                val sizePx = (sizeSp * resources.displayMetrics.scaledDensity).toInt()
                editable.setSpan(AbsoluteSizeSpan(sizePx, false), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        Regex("\\*\\*(.*?)\\*\\*").findAll(originalText).forEach { match ->
            val markerText = match.groupValues[1]
            var searchFrom = 0
            while (searchFrom < cleanText.length) {
                val startIndex = cleanText.indexOf(markerText, searchFrom)
                if (startIndex < 0) break
                val endIndex = startIndex + markerText.length
                if (editable.getSpans(startIndex, endIndex, StyleSpan::class.java).none { it.style == Typeface.BOLD }) {
                    editable.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                searchFrom = endIndex
                break
            }
        }

        Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)").findAll(originalText).forEach { match ->
            val markerText = match.groupValues[1]
            var searchFrom = 0
            while (searchFrom < cleanText.length) {
                val startIndex = cleanText.indexOf(markerText, searchFrom)
                if (startIndex < 0) break
                val endIndex = startIndex + markerText.length
                if (editable.getSpans(startIndex, endIndex, StyleSpan::class.java).none { it.style == Typeface.ITALIC }) {
                    editable.setSpan(StyleSpan(Typeface.ITALIC), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                searchFrom = endIndex
                break
            }
        }

        Regex("__(.*?)__").findAll(originalText).forEach { match ->
            val markerText = match.groupValues[1]
            var searchFrom = 0
            while (searchFrom < cleanText.length) {
                val startIndex = cleanText.indexOf(markerText, searchFrom)
                if (startIndex < 0) break
                val endIndex = startIndex + markerText.length
                if (editable.getSpans(startIndex, endIndex, UnderlineSpan::class.java).isEmpty()) {
                    editable.setSpan(UnderlineSpan(), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                searchFrom = endIndex
                break
            }
        }
    }

    private fun setupImportanceChips(news: News) {
        val chips = listOf(
            binding.chipNormal to "normal",
            binding.chipImportant to "important",
            binding.chipPinned to "pinned"
        )
        chips.forEach { (chip, value) ->
            chip.isChecked = (news.importance == value) || (news.importance == null && value == "normal")
            chip.setOnClickListener {
                chips.forEach { it.first.isChecked = false }
                chip.isChecked = true
            }
        }
    }

    private fun setupMediaList(news: News) {
        val mediaItems = mutableListOf<NewsMediaAdapter.MediaItem>()
        news.images?.forEach { url -> mediaItems.add(NewsMediaAdapter.MediaItem(url, "image")) }
        news.videos?.forEach { url -> mediaItems.add(NewsMediaAdapter.MediaItem(url, "video")) }

        binding.rvMedia.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val adapter = NewsMediaAdapter(mediaItems,
            onDelete = { item -> deleteMedia(item) },
            onPlay = { item -> playMedia(item) }
        )
        binding.rvMedia.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from < to) for (i in from until to) Collections.swap(mediaItems, i, i + 1)
                else for (i in from downTo to + 1) Collections.swap(mediaItems, i, i - 1)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.rvMedia)
    }

    // ========== ПРЕДПРОСМОТР ==========

    private fun showPreview() {
        val title = binding.etTitle.text.toString()
        val shortDesc = binding.etShortDescription.text.toString()
        val content = getContentWithMarkers()

        val dialogView = layoutInflater.inflate(R.layout.dialog_news_full_preview, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPreviewTitle)
        val tvShortDesc = dialogView.findViewById<TextView>(R.id.tvPreviewShortDesc)
        val tvContent = dialogView.findViewById<TextView>(R.id.tvPreviewContent)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvPreviewDate)
        val layoutImages = dialogView.findViewById<LinearLayout>(R.id.layoutPreviewImages)
        val ivVideoIcon = dialogView.findViewById<ImageView>(R.id.ivPreviewVideoIcon)
        val layoutMedia = dialogView.findViewById<LinearLayout>(R.id.layoutPreviewMedia)

        tvTitle.text = title.ifEmpty { "Без заголовка" }
        if (shortDesc.isNotEmpty()) { tvShortDesc.visibility = View.VISIBLE; tvShortDesc.text = shortDesc }
        else tvShortDesc.visibility = View.GONE

        applySpansToPreview(tvContent, content)
        tvDate.text = "📅 ${newsItem?.formattedDate ?: "Черновик"}"

        val images = newsItem?.images ?: emptyList()
        val videos = newsItem?.videos ?: emptyList()
        layoutImages.removeAllViews()

        if (images.isNotEmpty()) {
            layoutMedia.visibility = View.VISIBLE
            images.forEach { imageUrl ->
                val imageView = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(120, 80).apply { marginEnd = 8 }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFFF0F0F0.toInt())
                    setOnClickListener {
                        val dlg = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                        dlg.setContentView(R.layout.dialog_full_image)
                        Glide.with(this@AdminNewsEditFragment).load(imageUrl).into(dlg.findViewById<ImageView>(R.id.imageView))
                        dlg.show()
                    }
                }
                Glide.with(this).load(imageUrl).centerCrop().into(imageView)
                layoutImages.addView(imageView)
            }
        }

        if (videos.isNotEmpty()) {
            layoutMedia.visibility = View.VISIBLE; ivVideoIcon.visibility = View.VISIBLE
            ivVideoIcon.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videos.first())).setDataAndType(Uri.parse(videos.first()), "video/*"))
            }
        } else ivVideoIcon.visibility = View.GONE

        if (images.isEmpty() && videos.isEmpty()) layoutMedia.visibility = View.GONE

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Предпросмотр")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setNeutralButton("Сохранить") { _, _ -> saveChanges() }
            .create().show()
    }

    private fun applySpansToPreview(textView: TextView, content: String) {
        val cleanText = content
            .replace(Regex("\\{\\{size=\\d+\\}\\}"), "")
            .replace(Regex("\\{\\{/size\\}\\}"), "")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)"), "$1")
            .replace(Regex("__(.*?)__"), "$1")
            .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "$1 ($2)")
            .replace("---", "───────────────")
            .replace(Regex("(?m)^- "), "  • ")
            .replace(Regex("(?m)^1\\. "), "  1. ")
            .replace(Regex("(?m)^> "), "  ▎")

        val spannable = SpannableStringBuilder(cleanText)

        Regex("\\{\\{size=(\\d+)\\}\\}(.*?)\\{\\{/size\\}\\}").findAll(content).forEach { match ->
            val sizeSp = match.groupValues[1].toIntOrNull() ?: return@forEach
            val markerText = match.groupValues[2]
            val startIndex = cleanText.indexOf(markerText)
            if (startIndex >= 0) {
                val endIndex = startIndex + markerText.length
                val sizePx = (sizeSp * resources.displayMetrics.scaledDensity).toInt()
                spannable.setSpan(AbsoluteSizeSpan(sizePx, false), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        Regex("\\*\\*(.*?)\\*\\*").findAll(content).forEach { match ->
            val markerText = match.groupValues[1]
            val startIndex = cleanText.indexOf(markerText)
            if (startIndex >= 0) {
                val endIndex = startIndex + markerText.length
                spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)").findAll(content).forEach { match ->
            val markerText = match.groupValues[1]
            val startIndex = cleanText.indexOf(markerText)
            if (startIndex >= 0) {
                val endIndex = startIndex + markerText.length
                spannable.setSpan(StyleSpan(Typeface.ITALIC), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        Regex("__(.*?)__").findAll(content).forEach { match ->
            val markerText = match.groupValues[1]
            val startIndex = cleanText.indexOf(markerText)
            if (startIndex >= 0) {
                val endIndex = startIndex + markerText.length
                spannable.setSpan(UnderlineSpan(), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        textView.text = spannable
    }

    // ========== ШЕРИНГ ==========

    private fun shareNews() {
        val title = binding.etTitle.text.toString()
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Новость: $title\n\nЧитайте в приложении Fitness Lemon")
        }, "Поделиться"))
    }

    private fun shareToChat() {
        Toast.makeText(requireContext(), "Выберите чат для отправки", Toast.LENGTH_SHORT).show()
    }

    // ========== ЗАГРУЗКА МЕДИА ==========

    private fun uploadMedia(uris: List<Uri>) {
        val token = PreferencesManager.getToken() ?: return
        val parts = mutableListOf<MultipartBody.Part>()
        for (uri in uris) {
            val file = copyUriToTempFile(uri) ?: continue
            val mimeType = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = mimeTypeToExtension(mimeType)
            val renamedFile = File(file.parentFile, "${file.nameWithoutExtension}.$extension")
            file.renameTo(renamedFile)
            parts.add(MultipartBody.Part.createFormData("media", renamedFile.name, renamedFile.asRequestBody(mimeType.toMediaTypeOrNull())))
        }
        if (parts.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val resp = ApiClient.adminApiService.uploadNewsMedia("Bearer $token", newsId, parts)
                    if (resp.isSuccessful) { Toast.makeText(requireContext(), "Медиа загружено", Toast.LENGTH_SHORT).show(); viewModel.loadNews() }
                    else Toast.makeText(requireContext(), "Ошибка загрузки: ${resp.code()}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun mimeTypeToExtension(mimeType: String) = when (mimeType.lowercase()) {
        "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; "image/bmp" -> "bmp"
        "video/mp4" -> "mp4"; "video/quicktime" -> "mov"; "video/x-msvideo" -> "avi"; "video/webm" -> "webm"; "video/x-matroska" -> "mkv"
        else -> mimeType.substringAfter("/")
    }

    private fun copyUriToTempFile(uri: Uri) = try {
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            File(requireContext().cacheDir, "upload_${System.currentTimeMillis()}.tmp").also { file ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
    } catch (e: Exception) { null }

    private fun deleteMedia(item: NewsMediaAdapter.MediaItem) = lifecycleScope.launch {
        try {
            val resp = ApiClient.adminApiService.deleteNewsMedia("Bearer ${PreferencesManager.getToken()}", newsId, item.url, item.type)
            if (resp.isSuccessful) { Toast.makeText(requireContext(), "Медиа удалено", Toast.LENGTH_SHORT).show(); viewModel.loadNews() }
            else Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun playMedia(item: NewsMediaAdapter.MediaItem) {
        if (item.type == "video") startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).setDataAndType(Uri.parse(item.url), "video/*"))
        else {
            Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                setContentView(R.layout.dialog_full_image)
                Glide.with(this@AdminNewsEditFragment).load(item.url).into(findViewById(R.id.imageView))
                show()
            }
        }
    }

    // ========== СОХРАНЕНИЕ ==========

    private fun saveChanges() {
        val title = binding.etTitle.text.toString().trim()
        val shortDescription = binding.etShortDescription.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "Введите заголовок", Toast.LENGTH_SHORT).show()
            return
        }

        val importance = when {
            binding.chipImportant.isChecked -> "important"
            binding.chipPinned.isChecked -> "pinned"
            else -> "normal"
        }

        val content = getContentWithMarkers()

        lifecycleScope.launch {
            try {
                val resp = ApiClient.adminApiService.updateNews(
                    "Bearer ${PreferencesManager.getToken()}", newsId,
                    UpdateNewsRequest(
                        title = title,
                        content = content,
                        shortDescription = shortDescription.ifEmpty { null },
                        importance = importance,
                        sendPush = binding.switchSendPush.isChecked
                    )
                )
                if (resp.isSuccessful) {
                    lifecycleScope.launch { PreferencesManager.removeDraft(newsId) }
                    Toast.makeText(requireContext(), "✅ Сохранено", Toast.LENGTH_SHORT).show()
                    viewModel.loadNews()
                } else {
                    Toast.makeText(requireContext(), "Ошибка: ${resp.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getContentWithMarkers(): String {
        val editable = binding.etContent.text ?: return ""
        val text = editable.toString()
        if (text.isEmpty()) return ""

        val result = StringBuilder()
        var lastIndex = 0

        val allSpans = editable
            .getSpans(0, editable.length, Any::class.java)
            .filter { it is StyleSpan || it is UnderlineSpan || it is AbsoluteSizeSpan }
            .sortedBy { editable.getSpanStart(it) }

        val topLevelSpans = mutableListOf<Any>()
        for (span in allSpans) {
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            val isNested = topLevelSpans.any { existing ->
                val exStart = editable.getSpanStart(existing)
                val exEnd = editable.getSpanEnd(existing)
                start >= exStart && end <= exEnd && span != existing
            }
            if (!isNested) topLevelSpans.add(span)
        }

        for (span in topLevelSpans) {
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            if (start < lastIndex) continue

            result.append(text.substring(lastIndex, start))
            val innerText = text.substring(start, end)

            var cleanText = innerText
                .replace(Regex("^\\*\\*"), "").replace(Regex("\\*\\*$"), "")
                .replace(Regex("^\\*"), "").replace(Regex("\\*$"), "")
                .replace(Regex("^__"), "").replace(Regex("__$"), "")
                .replace(Regex("^\\{\\{size=(\\d+)\\}\\}"), "")
                .replace(Regex("\\{\\{/size\\}\\}$"), "")

            when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> result.append("**$cleanText**")
                    Typeface.ITALIC -> result.append("*$cleanText*")
                }
                is UnderlineSpan -> result.append("__${cleanText}__")
                is AbsoluteSizeSpan -> {
                    val sizeInSp = (span.size / resources.displayMetrics.scaledDensity).toInt()
                    result.append("{{size=$sizeInSp}}$cleanText{{/size}}")
                }
            }
            lastIndex = end
        }

        result.append(text.substring(lastIndex))
        return result.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}