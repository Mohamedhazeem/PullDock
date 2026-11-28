package com.pulldock.feature.discovery.filters

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.ProviderStatus
import com.pulldock.domain.discovery.model.SortOrder

/** Filter sheet: sort, provider selection, min seeders, size range (FR-045/FR-048). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    currentSort: SortOrder,
    currentMinSeeders: Int?,
    providers: List<ProviderStatus>,
    currentProviderIds: Set<String>,
    onApply: (SortOrder, Int?, Set<String>, Int?, Int?, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    var sort by remember { mutableStateOf(currentSort) }
    var minSeeders by remember { mutableStateOf(currentMinSeeders?.toString().orEmpty()) }
    var selectedProviders by remember { mutableStateOf(currentProviderIds) }
    var minMb by remember { mutableStateOf("") }
    var maxMb by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(tokens.spacing.md).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text("Sort", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                SortOrder.entries.forEach { order ->
                    FilterChip(selected = order == sort, onClick = { sort = order }, label = { Text(order.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }

            if (providers.isNotEmpty()) {
                Text("Providers", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                    providers.forEach { p ->
                        FilterChip(
                            selected = p.providerId in selectedProviders,
                            onClick = { selectedProviders = if (p.providerId in selectedProviders) selectedProviders - p.providerId else selectedProviders + p.providerId },
                            label = { Text(p.providerName) },
                        )
                    }
                }
            }

            OutlinedTextField(minSeeders, { minSeeders = it.filter { c -> c.isDigit() } }, label = { Text("Minimum seeders") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                OutlinedTextField(minMb, { minMb = it.filter { c -> c.isDigit() } }, label = { Text("Min MB") }, singleLine = true, modifier = Modifier.fillMaxWidth().weight(1f))
                OutlinedTextField(maxMb, { maxMb = it.filter { c -> c.isDigit() } }, label = { Text("Max MB") }, singleLine = true, modifier = Modifier.fillMaxWidth().weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                OutlinedTextField(from, { from = it }, label = { Text("From (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth().weight(1f))
                OutlinedTextField(to, { to = it }, label = { Text("To (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth().weight(1f))
            }

            TextButton(onClick = { onApply(sort, minSeeders.toIntOrNull(), selectedProviders, minMb.toIntOrNull(), maxMb.toIntOrNull(), from.ifBlank { null }, to.ifBlank { null }) }) { Text("Apply") }
        }
    }
}