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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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

/** Filter sheet: sort, providers, min-seeders slider, and From/To year dropdowns. */
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
    var minSeeders by remember { mutableStateOf(currentMinSeeders ?: 1) }
    var selectedProviders by remember { mutableStateOf(currentProviderIds) }
    var fromYear by remember { mutableStateOf<String?>(null) }
    var toYear by remember { mutableStateOf<String?>(null) }
    val years = remember { (java.time.Year.now().value downTo 1990).toList() }

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

            Text("Minimum seeders: $minSeeders", style = MaterialTheme.typography.titleSmall)
            Slider(value = minSeeders.toFloat(), onValueChange = { minSeeders = it.toInt() }, valueRange = 1f..100f)

            YearDropdown("From year", years, fromYear) { fromYear = it }
            YearDropdown("To year", years, toYear) { toYear = it }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onApply(sort, minSeeders, selectedProviders, null, null, fromYear, toYear) }) { Text("Apply") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearDropdown(label: String, years: List<Int>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected ?: "Any",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Any") }, onClick = { onSelect(null); expanded = false })
            years.forEach { y ->
                DropdownMenuItem(text = { Text(y.toString()) }, onClick = { onSelect(y.toString()); expanded = false })
            }
        }
    }
}