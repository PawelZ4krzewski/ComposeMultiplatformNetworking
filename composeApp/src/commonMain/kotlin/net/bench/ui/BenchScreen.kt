package net.bench.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import net.bench.bench.BenchScenarios
import net.bench.bench.ScenarioId

@Composable
fun BenchScreen(vm: BenchViewModel = remember { BenchViewModel() }) {
    val state by vm.state.collectAsState()
    var showAttempts by remember { mutableStateOf(false) }
    var activePresetId by remember { mutableStateOf<ScenarioId?>(null) }
    var exportDialogOpen by remember { mutableStateOf(false) }
    var exportDialogLabel by remember { mutableStateOf("") }
    var exportDialogContent by remember { mutableStateOf("") }
    var batchResults by remember { mutableStateOf(listOf<Pair<net.bench.bench.ScenarioPreset, Aggregates>>()) }
    var batchRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        val chipsPerRow = 3
        val presets = BenchScenarios.all
        val rows = (presets.size + chipsPerRow - 1) / chipsPerRow
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(rows) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val start = rowIndex * chipsPerRow
                    val end = minOf(start + chipsPerRow, presets.size)
                    for (i in start until end) {
                        val preset = presets[i]
                        val selected = activePresetId == preset.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                activePresetId = if (selected) null else preset.id
                                if (!selected) {
                                    vm.updateBaseUrl(preset.baseUrl)
                                    vm.updatePath(preset.path)
                                    vm.updateConnectMs(preset.connectTimeoutMs.toLong())
                                    vm.updateSendMs(preset.sendTimeoutMs.toLong())
                                    vm.updateReceiveMs(preset.receiveTimeoutMs.toLong())
                                    vm.updateRetry(preset.enableRetry)
                                    vm.updateWarmup(true)
                                }
                            },
                            label = { Text(preset.title) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = vm::updateBaseUrl,
                    label = { Text("BASE_URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.path,
                    onValueChange = vm::updatePath,
                    label = { Text("PATH") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.runs.toString(),
                    onValueChange = { it.toIntOrNull()?.let(vm::updateRuns) },
                    label = { Text("Runs (N)") }
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Checkbox(
                        checked = state.warmup,
                        onCheckedChange = vm::updateWarmup
                    ); Text("Warm-up (discard 1st)")
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = state.connectMs.toString(),
                        onValueChange = { it.toLongOrNull()?.let(vm::updateConnectMs) },
                        label = { Text("Connect timeout (ms)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.sendMs.toString(),
                        onValueChange = { it.toLongOrNull()?.let(vm::updateSendMs) },
                        label = { Text("Send timeout (ms)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.receiveMs.toString(),
                        onValueChange = { it.toLongOrNull()?.let(vm::updateReceiveMs) },
                        label = { Text("Receive timeout (ms)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = state.enableRetry, onCheckedChange = vm::updateRetry)
                    Spacer(Modifier.width(8.dp))
                    Text("Enable retry")
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.runBench() },
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.running) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Running...")
                        }
                    } else Text("Run")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (!state.running && !batchRunning) {
                            coroutineScope.launch {
                                batchRunning = true
                                batchResults = emptyList()
                                activePresetId = null
                                BenchScenarios.all.forEach { preset ->
                                    println("BATCH_SCENARIO_START ${preset.id}")
                                    activePresetId = preset.id
                                    vm.runPreset(preset, runs = state.runs, warmup = true)
                                    var waited = 0
                                    while (!vm.state.value.running && waited < 2000) {
                                        delay(10)
                                        waited += 10
                                    }
                                    while (vm.state.value.running) delay(50)
                                    batchResults = batchResults + (preset to vm.state.value.aggregates)
                                }
                                batchRunning = false
                            }
                        }
                    },
                    enabled = !state.running && !batchRunning,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (batchRunning) "Running All..." else "Run All (S1–S6)") }
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                        if (state.results.isNotEmpty()) {
                            val preset = BenchScenarios.all.firstOrNull { it.id == activePresetId }
                            exportDialogLabel = "CSV"
                            exportDialogContent = vm.buildCsv(preset)
                            exportDialogOpen = true
                        }
                    }, enabled = state.results.isNotEmpty()) { Text("Copy CSV") }
                    TextButton(onClick = {
                        if (state.results.isNotEmpty()) {
                            val preset = BenchScenarios.all.firstOrNull { it.id == activePresetId }
                            exportDialogLabel = "Markdown"
                            exportDialogContent = vm.buildMarkdown(preset)
                            exportDialogOpen = true
                        }
                    }, enabled = state.results.isNotEmpty()) { Text("Copy MD") }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = showAttempts,
                        onClick = { showAttempts = !showAttempts },
                        label = { Text(if (showAttempts) "Hide attempts" else "Show attempts") }
                    )
                }
                if (batchResults.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = {
                            val b = StringBuilder()
                            b.appendLine("scenario,N,median,p95,min,max,errors")
                            batchResults.forEach { (preset, aggs) ->
                                val errors = aggs.timeout + aggs.noInternet + aggs.http4xx + aggs.http5xx + aggs.cancel + aggs.unknown
                                b.appendLine("${preset.title},${aggs.count},${aggs.median},${aggs.p95},${aggs.min},${aggs.max},$errors")
                            }
                            println("BATCH_RESULTS_CSV:\n${b.toString().trim()}")
                        }) { Text("Log Batch CSV") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            batchResults = emptyList()
                            println("BATCH_RESULTS_CSV_CLEARED")
                        }) { Text("Clear Batch Results") }
                    }
                }
            }
        }

        if (state.results.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Results", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Count: ${state.aggregates.count}")
                    Text("Min: ${state.aggregates.min}ms")
                    Text("Max: ${state.aggregates.max}ms")
                    Text("Median: ${state.aggregates.median}ms")
                    Text("P95: ${state.aggregates.p95}ms")
                    if (activePresetId == ScenarioId.S6_HEADERS) {
                        val lp = state.lastPayloadPreview
                        val ua = lp.lineSequence().firstOrNull { it.contains("user-agent", true) }
                        val cc =
                            lp.lineSequence().firstOrNull { it.contains("cache-control", true) }
                        val acc = lp.lineSequence().firstOrNull { it.contains("accept", true) }
                        Spacer(Modifier.height(8.dp))
                        ua?.let { Text(it) }
                        cc?.let { Text(it) }
                        acc?.let { Text(it) }
                    }
                    if (showAttempts) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.height(300.dp)) {
                            LazyColumn {
                                items(state.results.indices.toList()) { idx ->
                                    val r = state.results[idx]
                                    val ok = r.kind == null
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("${idx + 1}", modifier = Modifier.width(32.dp))
                                        Text("${r.ms}ms", modifier = Modifier.width(72.dp))
                                        Text(r.kind?.name ?: "", modifier = Modifier.weight(1f))
                                        val color =
                                            if (ok) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(
                                                0xFFC62828
                                            )
                                        Text(r.status?.toString() ?: "", color = color)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (batchResults.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Batch Results (S1–S6)", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Scenario","N","Median","P95","Min","Max","Errors").forEach { h ->
                            Text(h, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    batchResults.forEach { (preset, aggs) ->
                        val errors = aggs.timeout + aggs.noInternet + aggs.http4xx + aggs.http5xx + aggs.cancel + aggs.unknown
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(preset.title, modifier = Modifier.weight(1f))
                            Text("${aggs.count}", modifier = Modifier.weight(1f))
                            Text("${aggs.median}ms", modifier = Modifier.weight(1f))
                            Text("${aggs.p95}ms", modifier = Modifier.weight(1f))
                            Text("${aggs.min}", modifier = Modifier.weight(1f))
                            Text("${aggs.max}", modifier = Modifier.weight(1f))
                            Text(errors.toString(), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (exportDialogOpen) {
            AlertDialog(
                onDismissRequest = { exportDialogOpen = false },
                title = { Text(exportDialogLabel) },
                text = {
                    SelectionContainer {
                        Box(Modifier.width(400.dp)) {
                            Text(
                                exportDialogContent,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        exportDialogOpen = false
                    }) { Text("Close") }
                }
            )
        }
    }
}
