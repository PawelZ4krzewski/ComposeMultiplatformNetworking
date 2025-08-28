package net.bench.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.bench.network.ApiResult
import net.bench.network.SampleDto
import net.bench.repo.SampleRepository

sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    data class Success(val statusCode: Int, val durationMs: Long, val payloadPreview: String) : UiState()
    data class Error(val label: String) : UiState()
}

class NetworkViewModel(private val repo: SampleRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun sendRequest(path: String = "/todos/1") {
        _state.value = UiState.Loading
        scope.launch {
            when (val res = repo.fetch(path)) {
                is ApiResult.Success -> {
                    val json = buildString {
                        append("{")
                        append("\"id\":").append(res.data.id)
                        append(", \"title\":\"").append(res.data.title ?: "").append("\"")
                        append(", \"body\":\"").append(res.data.body ?: "").append("\"")
                        append("}")
                    }
                    val preview = json.take(200)
                    _state.value = UiState.Success(res.statusCode, res.durationMs, preview)
                }
                is ApiResult.NetworkError -> {
                    val label = when (res.kind) {
                        ApiResult.NetworkError.Kind.Timeout -> "Timeout"
                        ApiResult.NetworkError.Kind.NoInternet -> "NoInternet"
                        ApiResult.NetworkError.Kind.Http4xx -> "Http4xx"
                        ApiResult.NetworkError.Kind.Http5xx -> "Http5xx"
                        ApiResult.NetworkError.Kind.Cancel -> "Cancel"
                        ApiResult.NetworkError.Kind.Unknown -> "Unknown"
                    }
                    _state.value = UiState.Error(label)
                }
            }
        }
    }
}
