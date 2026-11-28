package com.pulldock.feature.discovery.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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

/** Filter sheet: sort, providers, and value buttons (seeders/size/date) with input dialogs. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var minSeeders by remember { mutableStateOf(currentMinSeeders) }
    var selectedProviders by remember { mutableStateOf(currentProviderIds) }
    var minMb by remember { mutableStateOf<Int?>(null) }
    var maxMb by remember { mutableStateOf<Int?>(null) }
    var from by remember { mutableStateOf<String?>(null) }
    var to by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(tokens.spacing.md).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            Text("Sort by", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                SortOrder.entries.forEach { order ->
                    FilterChip(selected = order == sort, onClick = { sort = order }, label = { Text(order.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            if (providers.isNotEmpty()) {
                Text("Providers", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                    providers.forEach { p ->
                        FilterChip(
                            selected = p.providerId in selectedProviders,
                            onClick = { selectedProviders = if (p.providerId in selectedProviders) selectedProviders - p.providerId else selectedProviders + p.providerId },
                            label = { Text(p.providerName) },
                        )
                    }
                }
            }
            ValueButton("Minimum seeders", minSeeders?.toString(), numeric = true) { minSeeders = it?.toIntOrNull() }
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                ValueButton("Min MB", minMb?.toString(), numeric = true, modifier = Modifier.weight(1f)) { minMb = it?.toIntOrNull() }
                ValueButton("Max MB", maxMb?.toString(), numeric = true, modifier = Modifier.weight(1f)) { maxMb = it?.toIntOrNull() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                ValueButton("From", from, modifier = Modifier.weight(1f)) { from = it?.ifBlank { null } }
                ValueButton("To", to, modifier = Modifier.weight(1f)) { to = it?.ifBlank { null } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onApply(sort, minSeeders, selectedProviders, minMb, maxMb, from, to) }) { Text("Apply") }
            }
        }
    }
}

/** A button that opens a small input dialog; shows the current value inline. */
@Composable
private fun ValueButton(
    label: String,
    value: String?,
    numeric: Boolean = false,
    modifier: Modifier = Modifier,
    onValue: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value.orEmpty()) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text(if (value.isNullOrBlank()) label else "$label: $value", style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (numeric) it.filter { c -> c.isDigit() } else it },
                    label = { Text(if (numeric) "Number" else "YYYY-MM-DD") },
                    singleLine = true,
                )
            },
            confirmButton = { TextButton(onClick = { onValue(text); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}