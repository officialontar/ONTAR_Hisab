package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun FinancialDonutChart(
    sales: Double,
    expenses: Double,
    duesCollected: Double,
    newDues: Double,
    isBn: Boolean,
    colors: ColorScheme
) {
    val totalSum = (sales + expenses + duesCollected + newDues).toFloat()
    
    // Semantic, elegant and high-contrast styling colors matching modern material spec
    val salesColor = Color(0xFF0F9D58) // Green (for cash sales and revenue)
    val expensesColor = Color(0xFFD32F2F) // Deep Red (for store and inventory expenses)
    val duesCollectedColor = Color(0xFF2F80ED) // Blue (for outstanding dues collected)
    val newDuesColor = Color(0xFFF2C94C) // Yellow/Amber (for new custom baki/dues created)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBn) "টাকার হিসাব খতিয়ান চার্ট" else "Financial Ratio Indicator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (totalSum == 0f) {
                // Beautiful placeholder donut empty state
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(150.dp)
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.size(130.dp)) {
                        drawArc(
                            color = Color.LightGray.copy(alpha = 0.3f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 30f)
                        )
                    }
                    Text(
                        text = if (isBn) "কোনো তথ্য নেই" else "No Data",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground.copy(alpha = 0.4f)
                    )
                }
            } else {
                val salesPercentage = sales.toFloat() / totalSum
                val expensesPercentage = expenses.toFloat() / totalSum
                val duesCollectedPercentage = duesCollected.toFloat() / totalSum
                val newDuesPercentage = newDues.toFloat() / totalSum

                val sweepSales = 360f * salesPercentage
                val sweepExpenses = 360f * expensesPercentage
                val sweepCollected = 360f * duesCollectedPercentage
                val sweepNewDues = 360f * newDuesPercentage

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Donut Canvas
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            var startAngle = -90f

                            if (sweepSales > 0) {
                                drawArc(
                                    color = salesColor,
                                    startAngle = startAngle,
                                    sweepAngle = sweepSales,
                                    useCenter = false,
                                    style = Stroke(width = 35f, cap = StrokeCap.Butt)
                                )
                                startAngle += sweepSales
                            }

                            if (sweepExpenses > 0) {
                                drawArc(
                                    color = expensesColor,
                                    startAngle = startAngle,
                                    sweepAngle = sweepExpenses,
                                    useCenter = false,
                                    style = Stroke(width = 35f, cap = StrokeCap.Butt)
                                )
                                startAngle += sweepExpenses
                            }

                            if (sweepCollected > 0) {
                                drawArc(
                                    color = duesCollectedColor,
                                    startAngle = startAngle,
                                    sweepAngle = sweepCollected,
                                    useCenter = false,
                                    style = Stroke(width = 35f, cap = StrokeCap.Butt)
                                )
                                startAngle += sweepCollected
                            }

                            if (sweepNewDues > 0) {
                                drawArc(
                                    color = newDuesColor,
                                    startAngle = startAngle,
                                    sweepAngle = sweepNewDues,
                                    useCenter = false,
                                    style = Stroke(width = 35f, cap = StrokeCap.Butt)
                                )
                            }
                        }

                        // Text content inside donut center
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isBn) "মোট লেনদেন" else "Transactions",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onBackground.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                            Text(
                                text = "৳${String.format(Locale.getDefault(), "%,.0f", totalSum.toDouble())}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = colors.onBackground
                            )
                        }
                    }

                    Divider(color = colors.onBackground.copy(alpha = 0.05f))

                    // Legend Items Row/Grid for modern aesthetics
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LegendItem(
                            color = salesColor,
                            label = if (isBn) "মোট বিক্রি" else "Total Sales",
                            value = sales,
                            percentage = salesPercentage * 100,
                            colors = colors
                        )
                        LegendItem(
                            color = expensesColor,
                            label = if (isBn) "মোট খরচ" else "Total Expenses",
                            value = expenses,
                            percentage = expensesPercentage * 100,
                            colors = colors
                        )
                        LegendItem(
                            color = duesCollectedColor,
                            label = if (isBn) "বাকি পরিশোধ জমা" else "Dues Collected",
                            value = duesCollected,
                            percentage = duesCollectedPercentage * 100,
                            colors = colors
                        )
                        LegendItem(
                            color = newDuesColor,
                            label = if (isBn) "নতুন বাকি যোগ" else "New Dues Added",
                            value = newDues,
                            percentage = newDuesPercentage * 100,
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    value: Double,
    percentage: Float,
    colors: ColorScheme
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${String.format(Locale.getDefault(), "%.1f", percentage)}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground.copy(alpha = 0.5f)
                )
            }
            Text(
                text = "৳${String.format(Locale.getDefault(), "%,.1f", value)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground
            )
        }
    }
}
