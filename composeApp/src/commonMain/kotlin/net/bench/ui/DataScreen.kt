package net.bench.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bench.network.ApiConfig
import net.bench.network.ApiResult
import net.bench.network.ApiService
import net.bench.network.createHttpClient
import net.bench.repo.DataRepository

data class PostItem(val title: String?, val body: String?)

@Composable
fun DataScreen(
    initialBaseUrl: String = ApiConfig.baseUrl,
    initialPath: String = "/posts"
) {
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var path by remember { mutableStateOf(initialPath) }
    var loading by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(listOf<PostItem>()) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    fun load() {
        loading = true
        error = null
        val client = createHttpClient(baseUrl = baseUrl)
        val repo = DataRepository(ApiService(client))
        scope.launch {
            try {
                when (val res = repo.fetchList(path)) {
                    is ApiResult.Success<List<net.bench.network.SampleDto>> -> {
                        val list = res.data
                        items = list.map { pi -> PostItem(pi.title, pi.body) }
                        loading = false
                    }
                    is ApiResult.NetworkError -> {
                        error = res.kind.toString()
                        loading = false
                    }
                }
            } finally {
                client.close()
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Data", style = MaterialTheme.typography.titleLarge)
        
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Path") },
            modifier = Modifier.fillMaxWidth()
        )
        
        when {
            loading -> CircularProgressIndicator()
            error != null -> {
                Text("Error: ${error}")
                Button(onClick = { load() }) { Text("Retry") }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { it ->
                        OutlinedCard(Modifier.padding(4.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(it.title ?: "", style = MaterialTheme.typography.titleMedium)
                                Text(it.body ?: "", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Button(onClick = { load() }) { Text("Refresh") }
            }
        }
    }
}
