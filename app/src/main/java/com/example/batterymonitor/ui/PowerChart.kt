package com.example.batterymonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

/**
 * 实时功率折线图（Compose Canvas 手绘，无第三方图表库依赖）。
 * 固定 60 个采样槽位，新数据从右向左推进，纵轴量程随峰值自适应。
 */
@Composable
fun PowerChartCard(
    history: List<Float>,
    peakWatt: Float,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isCharging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val yMax = remember(peakWatt) { niceCeil(max(peakWatt * 1.15f, 5f)) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "实时功率曲线 · 近 30 秒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format(Locale.US, "峰值 %.1f W", peakWatt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PowerChartCanvas(
                history = history,
                yMax = yMax,
                lineColor = lineColor,
                gridColor = MaterialTheme.colorScheme.outlineVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                maxPoints = 60,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "采样间隔 0.5s ｜ 纵轴单位 W",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PowerChartCanvas(
    history: List<Float>,
    yMax: Float,
    lineColor: Color,
    gridColor: Color,
    labelColor: Color,
    maxPoints: Int,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    Canvas(modifier = modifier) {
        val gutter = 40.dp.toPx()
        val plotLeft = gutter
        val plotWidth = (size.width - gutter).coerceAtLeast(1f)
        val plotBottom = size.height
        val rows = 4
        val step = yMax / rows
        val stepIsWhole = abs(step - round(step)) < 0.01f

        // 1. 横向网格 + 纵轴刻度
        for (i in 0..rows) {
            val ratio = i.toFloat() / rows
            val y = plotBottom * (1f - ratio)
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            // 刻度可能是 2.5 / 7.5 这类小数，按步长决定小数位，避免出现 0/3/5/8/10 的跳号
            val label = if (stepIsWhole) {
                String.format(Locale.US, "%.0f", yMax * ratio)
            } else {
                String.format(Locale.US, "%.1f", yMax * ratio)
            }
            val layout = textMeasurer.measure(label, style = labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = plotLeft - layout.size.width - 8.dp.toPx(),
                    y = y - layout.size.height / 2f
                )
            )
        }

        if (history.size < 2) return@Canvas

        // 2. 计算坐标（右对齐，最新点贴右边）
        val stepX = plotWidth / (maxPoints - 1)
        val linePath = Path()
        val fillPath = Path()

        history.forEachIndexed { index, value ->
            val x = plotLeft + plotWidth - (history.lastIndex - index) * stepX
            val normalized = (value / yMax).coerceIn(0f, 1f)
            val y = plotBottom - normalized * plotBottom

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, plotBottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(plotLeft + plotWidth, plotBottom)
        fillPath.close()

        // 3. 渐变填充
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent),
                startY = 0f,
                endY = plotBottom
            )
        )

        // 4. 折线本体
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 5. 末端高亮点
        val lastX = plotLeft + plotWidth
        val lastY = plotBottom - (history.last() / yMax).coerceIn(0f, 1f) * plotBottom
        drawCircle(color = lineColor.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
    }
}

private fun niceCeil(value: Float): Float {
    val v = value.coerceAtLeast(1f)
    val step = when {
        v <= 10f -> 2f
        v <= 30f -> 5f
        v <= 100f -> 10f
        v <= 300f -> 25f
        else -> 50f
    }
    return ceil(v / step) * step
}
