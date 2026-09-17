package com.fitnesslemon.app.ui.trainers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesslemon.app.data.api.ApiClient
import com.fitnesslemon.app.data.models.Trainer
import com.fitnesslemon.app.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class TrainersState {
    object Loading : TrainersState()
    data class Success(val trainers: List<Trainer>) : TrainersState()
    data class Error(val message: String) : TrainersState()
}

class TrainersViewModel : ViewModel() {

    private val _trainersState = MutableStateFlow<TrainersState>(TrainersState.Loading)
    val trainersState: StateFlow<TrainersState> = _trainersState

    init {
        loadTrainers()
    }

    fun loadTrainers() {
        viewModelScope.launch {
            _trainersState.value = TrainersState.Loading

            try {
                println("🔍 Загружаем список тренеров")

                val response = ApiClient.apiService.getTrainers()

                println("🔍 Статус ответа: ${response.code()}")

                if (response.isSuccessful) {
                    val trainersResponse = response.body()
                    if (trainersResponse != null && trainersResponse.success) {
                        val trainers = trainersResponse.data
                        println("✅ Загружено тренеров: ${trainers.size}")
                        trainers.forEachIndexed { index, trainer ->
                            println("   ${index + 1}. ${trainer.name} - ${trainer.specialization}")
                        }
                        _trainersState.value = TrainersState.Success(trainers)
                    } else {
                        println("❌ Пустой ответ от сервера")
                        _trainersState.value = TrainersState.Error("Пустой ответ от сервера")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ Ошибка загрузки ${response.code()}: $errorBody")
                    _trainersState.value = TrainersState.Error("Ошибка загрузки: ${response.code()}")
                }
            } catch (e: Exception) {
                println("❌ Ошибка сети: ${e.message}")
                e.printStackTrace()
                _trainersState.value = TrainersState.Error("Ошибка сети: ${e.message}")
            }
        }
    }

    fun refresh() {
        loadTrainers()
    }
}