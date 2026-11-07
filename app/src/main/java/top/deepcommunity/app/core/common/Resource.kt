package top.deepcommunity.app.core.common

sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String, val code: Int? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}
