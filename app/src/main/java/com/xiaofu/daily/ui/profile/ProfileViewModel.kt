package com.xiaofu.daily.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.data.RecordRepository
import com.xiaofu.daily.data.local.entity.ProfileEntity
import com.xiaofu.daily.data.local.model.Gender
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: RecordRepository) : ViewModel() {

    val profile: StateFlow<ProfileEntity?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<Map<String, String>> = repo.observeSettings()
        .map { rows -> rows.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun saveProfile(
        current: ProfileEntity?,
        name: String,
        breed: String?,
        gender: Gender?,
        birthdayDay: Long?,
        newPhotoUri: Uri? = null,
    ) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val imported = newPhotoUri?.let { repo.importPhoto(it) }
            val entity = (current ?: ProfileEntity(name = "小福", createdAt = now, updatedAt = now)).copy(
                name = name.trim().ifBlank { "小福" },
                breed = breed?.trim()?.ifBlank { null },
                gender = gender,
                birthdayDay = birthdayDay,
                photoFile = imported ?: current?.photoFile,
                updatedAt = now,
            )
            repo.updateProfile(entity)
        }
    }

    fun setSetting(key: String, value: String) {
        viewModelScope.launch { repo.putSetting(key, value) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(AppContainer.repository) as T
        }
    }
}
