package com.example.batterymonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** 一次电池广播解析出来的静态信息 */
data class BatterySnapshot(
    val isPlugged: Boolean,
    val isCharging: Boolean,
    val plugType: PlugType,
    val level: Int,
    val voltageMv: Int,
    val temperatureCelsius: Float
)

/**
 * 电池数据源：
 * 1. ACTION_BATTERY_CHANGED（sticky 广播）拿电压、温度、电量、充电状态；
 * 2. BatteryManager.getIntProperty(CURRENT_NOW) 拿实时电流（需要主动轮询）。
 */
class BatteryRepository(private val context: Context) {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    val snapshot: Flow<BatterySnapshot> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let { trySend(it.toSnapshot()) }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // 系统广播，注册即回传当前状态
        val sticky = ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { trySend(it.toSnapshot()) }

        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    /**
     * 原始电流值。单位为 μA 或 mA，由厂商决定，需上层做单位校准。
     * @return Int.MIN_VALUE 表示该机型不支持读取
     */
    fun readCurrentNow(): Int = runCatching {
        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    }.getOrDefault(Int.MIN_VALUE)

    /** 当前剩余电量（mAh），用于后续充电周期统计 */
    fun readChargeCounter(): Int = runCatching {
        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    }.getOrDefault(-1)

    private fun Intent.toSnapshot(): BatterySnapshot {
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isPlugged = plugged != 0

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            8 -> PlugType.DOCK // BATTERY_PLUGGED_DOCK，部分 ROM 无此常量
            else -> PlugType.UNPLUGGED
        }

        val rawVoltage = getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        // 极少数机型直接返回 V，做个兜底
        val voltageMv = if (rawVoltage in 1..20) rawVoltage * 1000 else rawVoltage

        return BatterySnapshot(
            isPlugged = isPlugged,
            isCharging = isCharging,
            plugType = plugType,
            level = getIntExtra(BatteryManager.EXTRA_LEVEL, 0),
            voltageMv = voltageMv.coerceAtLeast(0),
            temperatureCelsius = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        )
    }

    companion object {
        /** 单位判定阈值：μA 制下 20000μA = 20mA；mA 制下 20000mA = 20A（手机不可能） */
        const val UNIT_SWITCH_THRESHOLD = 20_000
    }
}
