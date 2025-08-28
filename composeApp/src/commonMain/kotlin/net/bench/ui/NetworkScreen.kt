package net.bench.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bench.network.ApiService
import net.bench.network.createHttpClient
import net.bench.repo.SampleRepository
import net.bench.network.ApiConfig

@Composable
fun NetworkScreen() {
    val vm = remember { NetworkViewModel(SampleRepository(ApiService(createHttpClient(ApiConfig.baseUrl)))) }
    val state by vm.state.collectAsState()

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                is UiState.Idle -> {
                    Button(onClick = { vm.sendRequest() }) { Text("Wyślij żądanie") }
                }
                is UiState.Loading -> {
                    Text("Loading")
                    Spacer(Modifier.height(16.dp))
                }
                is UiState.Success -> {
                    val s = state as UiState.Success
                    Text("Success")
                    Text("status: ${s.statusCode}")
                    Text("durationMs: ${s.durationMs}")
                    Text(s.payloadPreview)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.sendRequest() }) { Text("Ponów") }
                }
                is UiState.Error -> {
                    val e = state as UiState.Error
                    Text("Error: ${e.label}")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.sendRequest() }) { Text("Ponów") }
                }
            }
        }
    }
}
