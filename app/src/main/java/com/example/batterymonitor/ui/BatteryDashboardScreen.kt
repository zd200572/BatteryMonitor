package com.example.batterymonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.batterymonitor.BatteryState
import com.example.batterymonitor.BatteryViewModel
import com.example.batterymonitor.UnitMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDashboardScreen(viewModel: BatteryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 只在界面可见时采样，退到后台自动停，避免无谓耗电
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runSampling()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("充电功率检测") },
                actions = {
                    TextButton(onClick = viewModel::resetPeak) { Text("峰值清零") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PowerHeadline(state)

            PowerChartCard(
                history = state.history,
                peakWatt = state.peakPowerWatt,
                isCharging = state.isCharging
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "实时电流",
                    value = String.format(Locale.US, "%d mA", state.currentMa),
                    hint = if (state.hasCurrentReading) {
                        "原始值 ${state.rawCurrent} ${state.currentUnit.label}"
                    } else {
                        "该机型不支持读取电流"
                    },
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "电池电压",
                    value = String.format(Locale.US, "%.3f V", state.packVoltageMv / 1000f),
                    hint = if (state.isDualCell) "串联双芯合计" else "单电芯",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "电池温度",
                    value = String.format(Locale.US, "%.1f °C", state.temperatureCelsius),
                    hint = when {
                        state.temperatureCelsius >= 43f -> "温度偏高，建议散热"
                        state.temperatureCelsius >= 38f -> "温热"
                        else -> "正常"
                    },
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "当前电量",
                    value = "${state.level} %",
                    hint = if (state.isPlugged) "已接入电源" else "未接电源",
                    modifier = Modifier.weight(1f)
                )
            }

            CalibrationCard(
                state = state,
                onDualCellChange = viewModel::setDualCell,
                onUnitModeChange = viewModel::setUnitMode
            )

            Text(
                text = "提示：非 Root 应用只能读取电池端净功率（充电器输入 − 手机自身功耗），" +
                        "亮屏时数值会明显低于息屏快充；部分机型内核 1~3 秒才刷新一次电流寄存器。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PowerHeadline(state: BatteryState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isCharging) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.plugType.label,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format(Locale.US, "%.2f W", state.powerWatt),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    !state.hasCurrentReading -> "无法读取电流，功率不可用"
                    state.isCharging -> "当前净输入功率"
                    state.isPlugged -> "已接电源，电池未充电"
                    else -> "当前放电功率"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun CalibrationCard(
    state: BatteryState,
    onDualCellChange: (Boolean) -> Unit,
    onUnitModeChange: (UnitMode) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "串联双电芯校准 (×2)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "百瓦快充机型功率显示减半时开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.isDualCell, onCheckedChange = onDualCellChange)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = "电流单位（当前自动识别为 ${state.currentUnit.label}）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.unitMode == mode,
                        onClick = { onUnitModeChange(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "谷歌规范为 μA，但部分三星 / 联发科机型直接返回 mA，数值偏差 1000 倍时可手动指定。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
