package com.example.batterymonitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application)

    private val _state = MutableStateFlow(BatteryState())
    val state: StateFlow<BatteryState> = _state.asStateFlow()

    /** 单位自动识别结果：一次会话内锁定，遇到更大数量级时可单向升级为 μA */
    private var lockedUnit: CurrentUnit? = null

    init {
        viewModelScope.launch {
            repository.snapshot.collect(::onSnapshot)
        }
    }

    /**
     * 采样循环。由界面在 repeatOnLifecycle(STARTED) 中调用，
     * 退到后台时自动取消，避免无意义耗电（后台常驻需前台服务，见 README）。
     */
    suspend fun runSampling(intervalMs: Long = SAMPLE_INTERVAL_MS) {
        while (currentCoroutineContext().isActive) {
            sampleOnce()
            delay(intervalMs)
        }
    }

    fun setDualCell(enabled: Boolean) {
        _state.update {
            it.copy(isDualCell = enabled, peakPowerWatt = 0f, history = emptyList())
        }
        sampleOnce()
    }

    fun setUnitMode(mode: UnitMode) {
        lockedUnit = null
        _state.update {
            it.copy(unitMode = mode, peakPowerWatt = 0f, history = emptyList())
        }
        sampleOnce()
    }

    fun resetPeak() {
        _state.update { it.copy(peakPowerWatt = it.powerWatt) }
    }

    private fun onSnapshot(snapshot: BatterySnapshot) {
        _state.update { old ->
            val sessionStarted = snapshot.isPlugged && !old.isPlugged
            old.copy(
                isPlugged = snapshot.isPlugged,
                isCharging = snapshot.isCharging,
                plugType = snapshot.plugType,
                level = snapshot.level,
                voltageMv = snapshot.voltageMv,
                temperatureCelsius = snapshot.temperatureCelsius,
                // 新的一次插电：重置峰值与曲线
                peakPowerWatt = if (sessionStarted) 0f else old.peakPowerWatt,
                history = if (sessionStarted) emptyList() else old.history
            )
        }
    }

    private fun sampleOnce() {
        val raw = repository.readCurrentNow()
        if (raw == Int.MIN_VALUE) {
            _state.update { it.copy(hasCurrentReading = false) }
            return
        }

        val current = _state.value
        val unit = resolveUnit(raw)
        val magnitudeMa = abs(raw) * unit.milliAmpFactor
        val signedMa = if (current.isCharging) magnitudeMa else -magnitudeMa

        // P(W) = U(mV) × I(mA) / 1_000_000，串联双电芯时电压翻倍
        val watt = current.packVoltageMv * magnitudeMa.toFloat() / 1_000_000f

        _state.update { old ->
            old.copy(
                rawCurrent = raw,
                currentUnit = unit,
                currentMa = signedMa.toInt(),
                powerWatt = watt,
                peakPowerWatt = max(old.peakPowerWatt, watt),
                hasCurrentReading = true,
                history = (old.history + watt).takeLast(MAX_HISTORY_POINTS)
            )
        }
    }

    private fun resolveUnit(raw: Int): CurrentUnit {
        when (_state.value.unitMode) {
            UnitMode.MICRO_AMP -> return CurrentUnit.MICRO_AMP
            UnitMode.MILLI_AMP -> return CurrentUnit.MILLI_AMP
            UnitMode.AUTO -> Unit // 走下面的自动判定
        }

        val magnitude = abs(raw)
        val threshold = BatteryRepository.UNIT_SWITCH_THRESHOLD
        val locked = lockedUnit
        // 只在“首次采样”或“出现不可能属于 mA 制的大数值”时才重新判定
        if (locked == null || (locked == CurrentUnit.MILLI_AMP && magnitude > threshold)) {
            lockedUnit = if (magnitude > threshold) CurrentUnit.MICRO_AMP else CurrentUnit.MILLI_AMP
        }
        return lockedUnit ?: CurrentUnit.MILLI_AMP
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 500L
        const val MAX_HISTORY_POINTS = 60 // 500ms × 60 = 30 秒
    }
}
