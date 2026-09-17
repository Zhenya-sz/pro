package com.fitnesslemon.app.ui.profile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.fitnesslemon.app.R
import com.fitnesslemon.app.databinding.FragmentProfileBinding
import com.fitnesslemon.app.databinding.DialogAvatarPreviewBinding
import com.fitnesslemon.app.data.models.Booking
import com.fitnesslemon.app.data.models.User
import com.fitnesslemon.app.ui.adapters.HistoryAdapter
import com.fitnesslemon.app.ui.auth.LoginActivity
import com.fitnesslemon.app.utils.PreferencesManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by lazy { ProfileViewModel() }

    companion object {
        private const val REQUEST_CAMERA = 100
        private const val REQUEST_CROP = 101
    }

    private var cameraPhotoUri: Uri? = null
    private var currentPhotoPath: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            startCrop(it)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Нет доступа к камере", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(requireContext(), "Нет доступа к хранилищу", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
        setupLogoutButton()
        setupEditButtons()
        observeUpdateState()
        setupAvatarClick()
    }

    override fun onResume() {
        super.onResume()
        clearGlideCacheBeforeLoad()
        viewModel.loadProfile()
        updateRemainingWorkoutsFromPreferences()
    }

    private fun updateRemainingWorkoutsFromPreferences() {
        val savedRemaining = PreferencesManager.getRemainingWorkouts()
        if (savedRemaining > 0) {
            binding.tvRemainingWorkouts.text = savedRemaining.toString()
            println("📊 [ProfileFragment] Остаток из Preferences: $savedRemaining")
        }
    }

    private fun setupAvatarClick() {
        binding.ivAvatar.setOnClickListener {
            val currentState = viewModel.profileState.value
            if (currentState is ProfileState.Success && !currentState.user.avatar.isNullOrEmpty()) {
                showAvatarPreview(currentState.user.avatar!!)
            }
        }
    }

    private fun showAvatarPreview(imageUrl: String) {
        val dialogBinding = DialogAvatarPreviewBinding.inflate(layoutInflater)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        Glide.with(this)
            .load(imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(dialogBinding.photoView)

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupRecyclerView() {
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.setHasFixedSize(true)
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupEditButtons() {
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.btnChangeAvatar.setOnClickListener {
            showAvatarOptions()
        }
    }

    private fun showLogoutConfirmation() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Да") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun performLogout() {
        viewModel.logout()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showEditProfileDialog() {
        val currentState = viewModel.profileState.value
        if (currentState !is ProfileState.Success) return

        val user = currentState.user

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etBirthDate = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etBirthDate)
        val etAddress = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAddress)

        etBirthDate.setText(user.birthDate ?: "")
        etAddress.setText(user.address ?: "")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Редактировать профиль")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newBirthDate = etBirthDate.text.toString()
                val newAddress = etAddress.text.toString()
                viewModel.updateProfile(newBirthDate, newAddress)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAvatarOptions() {
        val options = arrayOf("Сделать фото", "Выбрать из галереи", "Удалить аватар")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Изменить аватар")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                    2 -> deleteAvatar()
                }
            }
            .show()
    }

    private fun openCamera() {
        if (checkCameraPermission()) {
            try {
                val photoFile = createImageFile()
                currentPhotoPath = photoFile.absolutePath
                cameraPhotoUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    photoFile
                )

                println("📸 Camera URI: $cameraPhotoUri")
                println("📸 Photo path: $currentPhotoPath")
                println("📸 File exists: ${photoFile.exists()}")

                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)

                if (cameraIntent.resolveActivity(requireContext().packageManager) != null) {
                    startActivityForResult(cameraIntent, REQUEST_CAMERA)
                } else {
                    Toast.makeText(requireContext(), "Камера не найдена", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Ошибка при создании фото: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openGallery() {
        if (checkStoragePermission()) {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            requestStoragePermissionLauncher.launch(permission)
        }
    }

    private fun deleteAvatar() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Удалить аватар")
            .setMessage("Вы уверены, что хотите удалить аватар?")
            .setPositiveButton("Да") { _, _ ->
                viewModel.deleteAvatar()
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        println("📌 onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        when (requestCode) {
            REQUEST_CAMERA -> {
                if (resultCode == Activity.RESULT_OK) {
                    println("📸 Камера вернула RESULT_OK")

                    cameraPhotoUri?.let { uri ->
                        val file = File(uri.path ?: "")
                        println("📸 File path: ${file.absolutePath}")
                        println("📸 File exists: ${file.exists()}")
                        println("📸 File length: ${file.length()}")

                        if (!file.exists() && currentPhotoPath != null) {
                            val fallbackFile = File(currentPhotoPath!!)
                            println("📸 Fallback file exists: ${fallbackFile.exists()}")
                            if (fallbackFile.exists() && fallbackFile.length() > 0) {
                                val fallbackUri = Uri.fromFile(fallbackFile)
                                println("📸 Используем fallback URI: $fallbackUri")
                                startCrop(fallbackUri)
                                return@let
                            }
                        }

                        if (file.exists() && file.length() > 0) {
                            println("📸 Фото сделано успешно: ${file.absolutePath}")
                            println("📸 Размер файла: ${file.length()} байт")
                            startCrop(uri)
                        } else {
                            Toast.makeText(requireContext(), "Ошибка: файл фото не найден или пуст", Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Toast.makeText(requireContext(), "Ошибка: URI фото не сохранен", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Фото не сделано", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CROP -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        val resultUri = data?.let { UCrop.getOutput(it) }
                        resultUri?.let { uri ->
                            println("📁 UCrop результат: $uri")
                            uploadCroppedImage(uri)
                        } ?: run {
                            Toast.makeText(requireContext(), "Не удалось получить обрезанное изображение", Toast.LENGTH_SHORT).show()
                        }
                    }
                    UCrop.RESULT_ERROR -> {
                        val error = data?.let { UCrop.getError(it) }
                        error?.printStackTrace()
                        Toast.makeText(requireContext(), "Ошибка обрезки: ${error?.message}", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        println("📁 UCrop отменен пользователем")
                    }
                }
            }
        }
    }

    private fun startCrop(sourceUri: Uri) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val destinationFile = File(requireContext().cacheDir, "cropped_${timeStamp}.jpg")
            val destinationUri = Uri.fromFile(destinationFile)

            println("📁 Source URI: $sourceUri")
            println("📁 Destination URI: $destinationUri")
            println("📁 Source URI scheme: ${sourceUri.scheme}")

            val options = UCrop.Options().apply {
                setCircleDimmedLayer(true)
                setShowCropFrame(false)
                setShowCropGrid(false)
                setCompressionQuality(90)
                setMaxBitmapSize(500)
                setToolbarColor(resources.getColor(R.color.lemon_primary, null))
                setStatusBarColor(resources.getColor(R.color.lemon_primary_dark, null))
                setHideBottomControls(false)
                setFreeStyleCropEnabled(false)
            }

            UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(500, 500)
                .withOptions(options)
                .start(requireContext(), this, REQUEST_CROP)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка открытия редактора: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadCroppedImage(uri: Uri) {
        try {
            val file = File(uri.path ?: return)

            if (!file.exists()) {
                Toast.makeText(requireContext(), "Файл не найден: ${uri.path}", Toast.LENGTH_SHORT).show()
                return
            }

            if (file.length() == 0L) {
                Toast.makeText(requireContext(), "Файл пустой", Toast.LENGTH_SHORT).show()
                return
            }

            println("📁 Загружаем файл: ${file.absolutePath}")
            println("📁 Размер файла: ${file.length()} байт")

            val requestFile = file.asRequestBody("image/jpeg".toMediaType())
            val body = MultipartBody.Part.createFormData("avatar", file.name, requestFile)

            viewModel.uploadAvatar(body)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Ошибка обработки изображения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().cacheDir
        val imageFile = File.createTempFile(
            "IMG_${timeStamp}_",
            ".jpg",
            storageDir
        )
        currentPhotoPath = imageFile.absolutePath
        return imageFile
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profileState.collect { state ->
                    when (state) {
                        is ProfileState.Loading -> {}
                        is ProfileState.Success -> {
                            updateUI(state.user, state.history)
                            PreferencesManager.saveRemainingWorkouts(state.user.remainingWorkouts)
                        }
                        is ProfileState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalVisits.collect { total ->
                    binding.tvTotalVisits.text = total.toString()
                }
            }
        }
    }

    private fun observeUpdateState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.updateState.collect { state ->
                    when (state) {
                        is UpdateState.Loading -> {
                            binding.btnChangeAvatar.isEnabled = false
                        }
                        is UpdateState.Success -> {
                            binding.btnChangeAvatar.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()

                            if (state.message.contains("аватар", ignoreCase = true)) {
                                clearGlideCacheInBackground()
                                val currentState = viewModel.profileState.value
                                if (currentState is ProfileState.Success) {
                                    loadAvatarWithNoCache(currentState.user.avatar)
                                }
                            }

                            viewModel.resetUpdateState()
                        }
                        is UpdateState.Error -> {
                            binding.btnChangeAvatar.isEnabled = true
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            viewModel.resetUpdateState()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun clearGlideCacheBeforeLoad() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Glide.get(requireContext()).clearDiskCache()

                withContext(Dispatchers.Main) {
                    Glide.get(requireContext()).clearMemory()
                }

                println("✅ Кэш Glide очищен перед загрузкой профиля")
            } catch (e: Exception) {
                println("❌ Ошибка очистки кэша Glide: ${e.message}")
            }
        }
    }

    private fun clearGlideCacheInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Glide.get(requireContext()).clearDiskCache()

                withContext(Dispatchers.Main) {
                    Glide.get(requireContext()).clearMemory()
                }

                println("✅ Кэш Glide очищен после обновления")

                withContext(Dispatchers.Main) {
                    viewModel.loadProfile()
                }
            } catch (e: Exception) {
                println("❌ Ошибка очистки кэша Glide: ${e.message}")
            }
        }
    }

    private fun loadAvatarWithNoCache(avatarUrl: String?) {
        if (avatarUrl.isNullOrEmpty()) {
            binding.ivAvatar.setImageResource(R.drawable.ic_profile)
            return
        }

        val urlWithTimestamp = if (avatarUrl.contains("?")) {
            "$avatarUrl&t=${System.currentTimeMillis()}"
        } else {
            "$avatarUrl?t=${System.currentTimeMillis()}"
        }

        println("🔍 Загружаем аватар с URL: $urlWithTimestamp")

        Glide.with(this)
            .load(urlWithTimestamp)
            .circleCrop()
            .placeholder(R.drawable.ic_profile)
            .error(R.drawable.ic_profile)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(binding.ivAvatar)
    }

    private fun updateUI(user: User, history: List<Booking>) {
        // Используем name или fullName
        val displayName = user.name.ifEmpty { user.fullName }
        binding.tvFullName.text = displayName
        binding.tvPhone.text = "📞 ${user.phone ?: "Не указан"}"
        binding.tvEmail.text = "✉️ ${user.email}"

        println("🔍 Обновляем UI с аватаром: ${user.avatar}")
        loadAvatarWithNoCache(user.avatar)

        // Показываем остаток из профиля
        val remaining = user.remainingWorkouts
        binding.tvRemainingWorkouts.text = remaining.toString()
        println("📊 [ProfileFragment] Остаток из профиля: $remaining")

        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(user.registered ?: "")
            binding.tvMemberSince.text = outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            binding.tvMemberSince.text = "Н/Д"
        }

        if (history.isEmpty()) {
            binding.rvHistory.visibility = View.GONE
            binding.tvEmptyHistory.visibility = View.VISIBLE
        } else {
            binding.rvHistory.visibility = View.VISIBLE
            binding.tvEmptyHistory.visibility = View.GONE
            binding.rvHistory.adapter = HistoryAdapter(history)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}