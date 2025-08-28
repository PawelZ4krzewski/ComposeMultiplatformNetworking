package net.bench.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BenchScreen(vm: BenchViewModel = remember { BenchViewModel() }) {
    val state by vm.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.height(400.dp).verticalScroll(rememberScrollState())
        ) {
            Text("Network Bench", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = vm::updateBaseUrl,
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.path,
                onValueChange = vm::updatePath,
                label = { Text("Path") },
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.runs.toString(),
                    onValueChange = { it.toIntOrNull()?.let(vm::updateRuns) },
                    label = { Text("Runs") }
                )
                Row(Modifier.padding(top = 12.dp)) {
                    Checkbox(checked = state.warmup, onCheckedChange = vm::updateWarmup)
                    Text("Warm-up (discard 1st)")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    state.connectMs.toString(),
                    { it.toLongOrNull()?.let(vm::updateConnectMs) },
                    label = { Text("Connect ms") })
                OutlinedTextField(
                    state.sendMs.toString(),
                    { it.toLongOrNull()?.let(vm::updateSendMs) },
                    label = { Text("Send ms") })
                OutlinedTextField(
                    state.receiveMs.toString(),
                    { it.toLongOrNull()?.let(vm::updateReceiveMs) },
                    label = { Text("Receive ms") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enable retry")
                Switch(checked = state.enableRetry, onCheckedChange = vm::updateRetry)
            }
            Button(
                onClick = { vm.runBench() },
                enabled = !state.running
            ) { Text(if (state.running) "Running…" else "Run") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Aggregates: count=${state.aggregates.count} median=${state.aggregates.median} p95=${state.aggregates.p95} min=${state.aggregates.min} max=${state.aggregates.max}")
                Text("Errors: timeout=${state.aggregates.timeout} noInternet=${state.aggregates.noInternet} 4xx=${state.aggregates.http4xx} 5xx=${state.aggregates.http5xx} cancel=${state.aggregates.cancel} unknown=${state.aggregates.unknown}")
                Text("Last payload: ${state.lastPayloadPreview}")
            }
            items(state.results) { r ->
                val statusTxt = r.status?.toString() ?: "-"
                val errTxt = r.kind?.toString() ?: "-"
                Text("ms=${r.ms} status=${statusTxt} err=${errTxt}")
            }
        }
    }
}
