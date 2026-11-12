package top.deepcommunity.app.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import top.deepcommunity.app.core.common.Resource
import top.deepcommunity.app.data.model.AchievementDto
import top.deepcommunity.app.data.model.BlockedUserDto
import top.deepcommunity.app.data.model.CheckinStatusDetailDto
import top.deepcommunity.app.data.model.PostItemDto
import top.deepcommunity.app.data.model.UserDto
import top.deepcommunity.app.data.repository.AuthRepository
import top.deepcommunity.app.data.repository.UserRepository
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ProfileUiState(
    val user: UserDto? = null,
    val selectedTab: Int = 0, // 0: 我的动态, 1: 我的收藏
    val myPosts: List<PostItemDto> = emptyList(),
    val myCollections: List<PostItemDto> = emptyList(),
    val isCheckedIn: Boolean = false,
    val isLoadingList: Boolean = false,
    val isCheckingIn: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val showEditDialog: Boolean = false,
    val showBlocksSheet: Boolean = false,
    val blockedUsers: List<BlockedUserDto> = emptyList(),
    val isLoadingBlocks: Boolean = false,
    val checkinStatus: CheckinStatusDetailDto? = null,
    val achievements: List<AchievementDto> = emptyList()
)

sealed interface ProfileEvent {
    data object LogoutSuccess : ProfileEvent
    data class ShowToast(val message: String) : ProfileEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("deep_checkin_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            user = authRepository.getSavedUser(),
            isCheckedIn = checkTodayAlreadyCheckedIn()
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<ProfileEvent>()
    val eventFlow: SharedFlow<ProfileEvent> = _eventFlow.asSharedFlow()

    init {
        refreshProfile()
        loadTabContent(0)
        loadCheckinStatus()
        loadAchievements()
    }

    private fun getTodayDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun checkTodayAlreadyCheckedIn(): Boolean {
        val lastDate = prefs.getString("last_checkin_date", null)
        return lastDate == getTodayDateStr()
    }

    private fun saveTodayCheckedIn() {
        prefs.edit().putString("last_checkin_date", getTodayDateStr()).apply()
        _uiState.update { it.copy(isCheckedIn = true) }
    }

    fun setShowEditDialog(show: Boolean) {
        _uiState.update { it.copy(showEditDialog = show) }
    }

    fun selectTab(index: Int) {
        if (_uiState.value.selectedTab != index) {
            _uiState.update { it.copy(selectedTab = index) }
            loadTabContent(index)
        }
    }

    private fun loadTabContent(tab: Int) {
        viewModelScope.launch {
            if (tab == 0) {
                userRepository.getMyPosts().collect { result ->
                    when (result) {
                        is Resource.Loading -> _uiState.update { it.copy(isLoadingList = true) }
                        is Resource.Success -> _uiState.update { it.copy(myPosts = result.data, isLoadingList = false) }
                        is Resource.Error -> _uiState.update { it.copy(isLoadingList = false) }
                    }
                }
            } else {
                userRepository.getMyCollections().collect { result ->
                    when (result) {
                        is Resource.Loading -> _uiState.update { it.copy(isLoadingList = true) }
                        is Resource.Success -> _uiState.update { it.copy(myCollections = result.data, isLoadingList = false) }
                        is Resource.Error -> _uiState.update { it.copy(isLoadingList = false) }
                    }
                }
            }
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "avatar_${System.currentTimeMillis()}.jpg", requestBody)

                userRepository.updateAvatar(part).collect { result ->
                    when (result) {
                        is Resource.Loading -> _uiState.update { it.copy(isUploadingAvatar = true) }
                        is Resource.Success -> {
                            _uiState.update { it.copy(isUploadingAvatar = false) }
                            _eventFlow.emit(ProfileEvent.ShowToast("头像更换成功"))
                            refreshProfile()
                        }
                        is Resource.Error -> {
                            _uiState.update { it.copy(isUploadingAvatar = false) }
                            _eventFlow.emit(ProfileEvent.ShowToast(result.message))
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploadingAvatar = false) }
                _eventFlow.emit(ProfileEvent.ShowToast("选择头像失败"))
            }
        }
    }

    fun updateProfile(nickname: String, bio: String) {
        if (nickname.isBlank()) {
            viewModelScope.launch { _eventFlow.emit(ProfileEvent.ShowToast("昵称不能为空")) }
            return
        }

        viewModelScope.launch {
            userRepository.updateProfile(nickname.trim(), bio.trim()).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isUpdatingProfile = true) }
                    is Resource.Success -> {
                        _uiState.update { it.copy(isUpdatingProfile = false, showEditDialog = false) }
                        _eventFlow.emit(ProfileEvent.ShowToast("个人资料更新成功"))
                        refreshProfile()
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isUpdatingProfile = false) }
                        _eventFlow.emit(ProfileEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }

    fun checkIn() {
        if (_uiState.value.isCheckedIn) return

        viewModelScope.launch {
            userRepository.checkIn().collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isCheckingIn = true) }
                    is Resource.Success -> {
                        saveTodayCheckedIn()
                        _uiState.update { it.copy(isCheckingIn = false) }
                        _eventFlow.emit(ProfileEvent.ShowToast("签到成功！已获得经验奖励"))
                        refreshProfile()
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isCheckingIn = false) }
                        if (result.message.contains("已签到")) {
                            saveTodayCheckedIn()
                            _eventFlow.emit(ProfileEvent.ShowToast("今日已完成签到"))
                        } else {
                            _eventFlow.emit(ProfileEvent.ShowToast(result.message))
                        }
                    }
                }
            }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            authRepository.fetchCurrentUser().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(user = result.data) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout().collect {
                _eventFlow.emit(ProfileEvent.LogoutSuccess)
            }
        }
    }

    fun toggleBlocksSheet(show: Boolean) {
        _uiState.update { it.copy(showBlocksSheet = show) }
        if (show) {
            loadBlockedUsers()
        }
    }

    fun loadBlockedUsers() {
        viewModelScope.launch {
            userRepository.getMyBlocks().collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.update { it.copy(isLoadingBlocks = true) }
                    is Resource.Success -> _uiState.update { it.copy(blockedUsers = result.data, isLoadingBlocks = false) }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isLoadingBlocks = false) }
                        _eventFlow.emit(ProfileEvent.ShowToast(result.message))
                    }
                }
            }
        }
    }

    fun unblockUser(username: String) {
        viewModelScope.launch {
            userRepository.unblockUser(username).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(ProfileEvent.ShowToast("已解除对 @$username 的屏蔽"))
                        loadBlockedUsers()
                    }
                    is Resource.Error -> _eventFlow.emit(ProfileEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    fun loadCheckinStatus() {
        viewModelScope.launch {
            userRepository.getCheckinStatus().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(checkinStatus = result.data) }
                }
            }
        }
    }

    fun makeupCheckin() {
        viewModelScope.launch {
            userRepository.makeupCheckin().collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _eventFlow.emit(ProfileEvent.ShowToast("✓ ${result.data}"))
                        loadCheckinStatus()
                    }
                    is Resource.Error -> _eventFlow.emit(ProfileEvent.ShowToast(result.message))
                    else -> {}
                }
            }
        }
    }

    fun loadAchievements() {
        viewModelScope.launch {
            userRepository.getMyAchievements().collect { result ->
                if (result is Resource.Success) {
                    _uiState.update { it.copy(achievements = result.data) }
                }
            }
        }
    }
}
