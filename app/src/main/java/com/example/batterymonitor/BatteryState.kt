package com.example.batterymonitor

/** 充电方式，对应 BatteryManager.EXTRA_PLUGGED */
enum class PlugType(val label: String) {
    AC("AC 快充 / 直充"),
    USB("USB 接口"),
    WIRELESS("无线充电"),
    DOCK("底座充电"),
    UNPLUGGED("电池放电中")
}

/** 原始电流值的真实单位（各家厂商上报标准不一致） */
enum class CurrentUnit(val label: String, val milliAmpFactor: Double) {
    MICRO_AMP("μA", 0.001),
    MILLI_AMP("mA", 1.0)
}

/** 用户可选的单位策略 */
enum class UnitMode(val label: String) {
    AUTO("自动"),
    MICRO_AMP("μA"),
    MILLI_AMP("mA")
}

/**
 * 界面状态。
 * 注意：voltageMv 为系统上报的“单串电芯电压”，串联双电芯机型需乘以 cellCount 才是整包电压。
 */
data class BatteryState(
    val isPlugged: Boolean = false,
    val isCharging: Boolean = false,
    val plugType: PlugType = PlugType.UNPLUGGED,
    val level: Int = 0,
    val voltageMv: Int = 0,
    val rawCurrent: Int = 0,
    val currentUnit: CurrentUnit = CurrentUnit.MILLI_AMP,
    val unitMode: UnitMode = UnitMode.AUTO,
    val currentMa: Int = 0,
    val powerWatt: Float = 0f,
    val peakPowerWatt: Float = 0f,
    val temperatureCelsius: Float = 0f,
    val isDualCell: Boolean = false,
    val history: List<Float> = emptyList(),
    val hasCurrentReading: Boolean = true
) {
    /** 电芯串数，用于功率/整包电压修正 */
    val cellCount: Int get() = if (isDualCell) 2 else 1

    /** 整包电压（mV） */
    val packVoltageMv: Int get() = voltageMv * cellCount
}
