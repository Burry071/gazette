package com.gazette.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gazette.data.LedgerEntry
import com.gazette.engine.ModelStatus
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun StatementScreen(
    entries: List<LedgerEntry>,
    reading: Boolean,
    readingText: String?,
    modelStatus: ModelStatus,
    listenerGranted: Boolean,
    onEnableAccess: () -> Unit,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val readingVisible by readingRowVisible(reading)
    Box(
        Modifier
            .fillMaxSize()
            .background(GazetteColors.Surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = insets.calculateTopPadding() + 24.dp,
                bottom = insets.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "head") { StatementHead() }
            item(key = "totals") {
                Spacer(Modifier.height(24.dp))
                TotalsBlock(entries)
            }
            item(key = "rule") {
                Spacer(Modifier.height(24.dp))
                ClosingRule()
            }

            if (readingVisible) {
                item(key = "reading") {
                    Spacer(Modifier.height(24.dp))
                    ReadingRow(readingText)
                }
            }

            when {
                !listenerGranted -> item(key = "grant") {
                    Spacer(Modifier.height(24.dp))
                    ParagraphAction(
                        sentence = "Notification access is off. Gazette can only read payment alerts after you allow access.",
                        action = "ENABLE ACCESS",
                        onAction = onEnableAccess,
                    )
                }

                modelStatus == ModelStatus.MISSING && entries.isEmpty() -> item(key = "setup") {
                    Spacer(Modifier.height(24.dp))
                    Paragraph("Gazette needs its offline reading file. See the README for the one-step setup.")
                }

                entries.isEmpty() -> item(key = "empty") {
                    Spacer(Modifier.height(24.dp))
                    Paragraph("No payments yet. New payment notifications will appear here.")
                }

                else -> ledgerItems(entries)
            }
        }
    }
}

private fun LazyListScope.ledgerItems(entries: List<LedgerEntry>) {
    val today = LocalDate.now(ZoneId.systemDefault())
    for ((date, rows) in entries.groupBy { localDateOf(it.epochMs) }) {
        item(key = "day-$date") {
            Spacer(Modifier.height(32.dp))
            DayLabel(dayLabel(date, today))
        }
        item(key = "day-rule-$date") { Hairline(Modifier.padding(top = 8.dp)) }
        items(rows, key = { it.id }) { entry ->
            EntryRow(entry)
            if (entry != rows.last()) Hairline()
        }
    }
}

private fun localDateOf(epochMs: Long): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "TODAY"
    today.minusDays(1) -> "YESTERDAY"
    else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())).uppercase(Locale.getDefault())
}

@Composable
private fun StatementHead() {
    val month = remember {
        LocalDate.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Gazette", style = GazetteType.wordmark, color = GazetteColors.Ink)
        Text(month, style = GazetteType.label, color = GazetteColors.InkSoft)
    }
}

@Composable
private fun TotalsBlock(entries: List<LedgerEntry>) {
    // Totals are the current month's statement, matching the month label.
    val monthEntries = remember(entries) {
        val now = LocalDate.now(ZoneId.systemDefault())
        entries.filter {
            val d = localDateOf(it.epochMs)
            d.year == now.year && d.month == now.month
        }
    }
    val received = remember(monthEntries) { monthEntries.filter { it.direction == "received" }.sumOf { it.amount } }
    val sent = remember(monthEntries) { monthEntries.filter { it.direction == "sent" }.sumOf { it.amount } }
    Row(Modifier.fillMaxWidth()) {
        TotalColumn("RECEIVED", received, GazetteColors.Accent, Modifier.weight(1f))
        TotalColumn("SENT", sent, GazetteColors.Ink, Modifier.weight(1f))
    }
}

@Composable
private fun TotalColumn(label: String, amount: Double, figureColor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = GazetteType.label, color = GazetteColors.InkSoft)
        Spacer(Modifier.height(8.dp))
        MoneyFigure(
            amount = amount,
            digitsStyle = GazetteType.total,
            digitsColor = figureColor,
            prefixSize = GazetteType.total.fontSize * 0.62f,
            animated = true,
        )
    }
}

@Composable
private fun ClosingRule() {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GazetteColors.Rule)
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GazetteColors.Rule)
        )
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GazetteColors.Rule)
    )
}

@Composable
private fun ReadingRow(candidateText: String?) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("READING", style = GazetteType.label, color = GazetteColors.InkSoft)
            ShimmerBar()
        }
        if (!candidateText.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                candidateText,
                style = GazetteType.body,
                color = GazetteColors.InkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShimmerBar() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Box(
        Modifier
            .width(90.dp)
            .height(14.dp)
            .alpha(shimmerAlpha)
            .background(GazetteColors.Shimmer, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun DayLabel(text: String) {
    Text(text, style = GazetteType.label, color = GazetteColors.InkSoft)
}

@Composable
private fun EntryRow(entry: LedgerEntry) {
    val context = LocalContext.current
    val time = remember(entry.id) {
        DateFormat.getTimeFormat(context).format(Date(entry.epochMs))
    }
    val received = entry.direction == "received"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (received) GazetteColors.Accent else GazetteColors.InkSoft)) {
                        append(if (received) "RECEIVED" else "SENT")
                    }
                    withStyle(SpanStyle(color = GazetteColors.InkSoft, letterSpacing = 0.sp)) {
                        append(" · $time")
                    }
                },
                style = GazetteType.label,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            MoneyFigure(
                amount = entry.amount,
                digitsStyle = GazetteType.amount,
                digitsColor = if (received) GazetteColors.Accent else GazetteColors.Ink,
                prefixSize = GazetteType.amount.fontSize * 0.62f,
                animated = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            entry.originalText,
            style = GazetteType.body,
            color = GazetteColors.InkSoft,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoneyFigure(
    amount: Double,
    digitsStyle: TextStyle,
    digitsColor: Color,
    prefixSize: TextUnit,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val digits = remember(amount) { formatAmount(amount) }
    if (animated) {
        AnimatedContent(
            targetState = digits,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
            label = "totalFigure",
            modifier = modifier,
        ) { target ->
            FigureText(target, digitsStyle, digitsColor, prefixSize)
        }
    } else {
        FigureText(digits, digitsStyle, digitsColor, prefixSize, modifier)
    }
}

@Composable
private fun FigureText(
    digits: String,
    digitsStyle: TextStyle,
    digitsColor: Color,
    prefixSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        Text(
            "Rs",
            style = TextStyle(
                fontFamily = SchibstedGrotesk,
                fontSize = prefixSize,
                fontWeight = FontWeight.Medium,
            ),
            color = GazetteColors.InkSoft,
            modifier = Modifier
                .alignByBaseline()
                .padding(end = 3.dp),
        )
        Text(
            digits,
            style = digitsStyle,
            color = digitsColor,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun Paragraph(sentence: String) {
    Text(sentence, style = GazetteType.body, color = GazetteColors.InkSoft)
}

@Composable
private fun ParagraphAction(sentence: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(sentence, style = GazetteType.body, color = GazetteColors.InkSoft)
        Spacer(Modifier.height(16.dp))
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Text(
            action,
            style = GazetteType.label.copy(textDecoration = TextDecoration.Underline),
            color = GazetteColors.Ink,
            modifier = Modifier
                .alpha(if (pressed) 0.6f else 1f)
                .clickable(interactionSource = interaction, indication = null, onClick = onAction),
        )
    }
}

// The reading row only materializes if the pipeline is still working after
// 200ms — gated junk resolves faster and never flashes a row.
@Composable
private fun readingRowVisible(reading: Boolean): State<Boolean> =
    produceState(initialValue = false, key1 = reading) {
        // produceState keeps the old value across key changes, so the
        // not-reading branch must clear it or the row sticks forever.
        if (reading) {
            delay(200)
            value = true
        } else {
            value = false
        }
    }

private val amountFormat = NumberFormat.getInstance(Locale.getDefault()).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
}

private fun formatAmount(amount: Double): String = synchronized(amountFormat) { amountFormat.format(amount) }
