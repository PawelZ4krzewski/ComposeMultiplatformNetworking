package org.example.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bench.ui.BenchScreen
import net.bench.ui.DataScreen

@Composable
fun App() {
    MaterialTheme {
        Scaffold(
            modifier = Modifier.padding(top = 16.dp)
        ) { padding ->
            var selected by remember { mutableIntStateOf(0) }
            
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                TabRow(selectedTabIndex = selected) {
                    Tab(
                        text = { Text("Bench") },
                        selected = selected == 0,
                        onClick = { selected = 0 })
                    Tab(
                        text = { Text("Data") },
                        selected = selected == 1,
                        onClick = { selected = 1 })
                }
                
                if (selected == 0) {
                    BenchScreen()
                } else {
                    DataScreen()
                }
            }
        }
    }
}