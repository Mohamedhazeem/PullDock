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

/** Filter sheet: sort, providers, min seeders, and From/To year chips. */
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
    var minSeeders by remember { mutableStateOf(currentMinSeeders?.toString().orEmpty()) }
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

            OutlinedTextField(minSeeders, { minSeeders = it.filter { c -> c.isDigit() } }, label = { Text("Minimum seeders") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("From year", style = MaterialTheme.typography.titleSmall)
            YearChips(years, fromYear) { fromYear = it }
            Text("To year", style = MaterialTheme.typography.titleSmall)
            YearChips(years, toYear) { toYear = it }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onApply(sort, minSeeders.toIntOrNull(), selectedProviders, null, null, fromYear, toYear) }) { Text("Apply") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearChips(years: List<Int>, selected: String?, onSelect: (String?) -> Unit) {
    val tokens = LocalPdTokens.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        years.forEach { y ->
            FilterChip(selected = selected == y.toString(), onClick = { onSelect(if (selected == y.toString()) null else y.toString()) }, label = { Text(y.toString()) })
        }
    }
}