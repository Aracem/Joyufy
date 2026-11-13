package com.aracem.joyufy.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aracem.joyufy.ui.components.formatCurrency
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.localizedCategoryLabel
import com.aracem.joyufy.ui.theme.joyufyColors

@Composable
internal fun ThisMonthCard(summary: MonthlySummary?, isLoading: Boolean, hasError: Boolean, onOpen: () -> Unit) {
    val strings = LocalStrings.current
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.joyufyColors.monthly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.currentMonth, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = strings.monthlyDetail)
            }
            if (hasError) {
                Text(strings.monthlyLoadError, color = MaterialTheme.colorScheme.error)
            } else if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                MonthSummaryValues(summary?.income ?: 0.0, summary?.expenses ?: 0.0, summary?.net ?: 0.0)
            }
            Text(strings.monthlyDetail, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MonthSummaryValues(income: Double, expenses: Double, net: Double) {
    val strings = LocalStrings.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 380.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthSummaryValue(strings.income, income)
                MonthSummaryValue(strings.expenses, expenses)
                MonthSummaryValue(strings.net, net)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthSummaryValue(strings.income, income, Modifier.weight(1f))
                MonthSummaryValue(strings.expenses, expenses, Modifier.weight(1f))
                MonthSummaryValue(strings.net, net, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MonthSummaryValue(label: String, value: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.joyufyColors.contentSecondary)
        Text(value.formatCurrency(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun MonthlyAnalysisDialog(
    analysis: MonthlyAnalysis?,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 700.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.monthlyDetail, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(strings.close) }
                }
                if (hasError) {
                    Text(strings.monthlyLoadError, color = MaterialTheme.colorScheme.error)
                } else if (analysis == null) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPreviousMonth, enabled = analysis.canGoPrevious) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.previousMonth)
                        }
                        Text(
                            "%02d / %04d".format(analysis.month.month, analysis.month.year),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onNextMonth, enabled = analysis.canGoNext) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = strings.nextMonth)
                        }
                    }
                    if (analysis.isCurrentMonth) {
                        Text(strings.currentMonthIncomplete, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.joyufyColors.contentSecondary)
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MonthSummaryValues(analysis.income, analysis.expenses, analysis.cashFlow)
                        if (analysis.previousExpenses == null) {
                            Text(strings.monthlyComparisonUnavailable, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.joyufyColors.contentSecondary)
                        } else {
                            Text("${strings.vsPreviousMonth}: ${strings.income} ${analysis.previousIncome!!.formatCurrency()} · " +
                                "${strings.expenses} ${analysis.previousExpenses.formatCurrency()}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.joyufyColors.contentSecondary)
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth()) {
                            Text(strings.category, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text(strings.expenses, style = MaterialTheme.typography.labelLarge)
                        }
                        if (analysis.categories.isEmpty()) {
                            Text(strings.noCategorySpending, color = MaterialTheme.joyufyColors.contentSecondary)
                        }
                        analysis.categories.forEach { category ->
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(category.category?.localizedCategoryLabel(strings) ?: strings.uncategorized, modifier = Modifier.weight(1f))
                                    Text(category.current.formatCurrency())
                                }
                                category.previous?.let { previous ->
                                    Text("${strings.vsPreviousMonth}: ${previous.formatCurrency()}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.joyufyColors.contentSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
