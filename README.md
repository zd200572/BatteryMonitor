# 充电功率检测 (BatteryMonitor)

<p align="center"><img src="docs/icon.png" width="120" alt="icon"></p>

基于 Kotlin + Coroutines + Jetpack Compose (Material 3) 的安卓充电功率检测 App，
通过系统原生 `BatteryManager` 采集电芯电压 / 电流 / 温度，实时计算净输入功率，
并用 Compose Canvas 绘制近 30 秒功率曲线。

## 功能

- 实时功率主看板（W），自动区分充电 / 放电
- 近 30 秒功率折线图（Canvas 手绘，无第三方图表库；纵轴量程随峰值自适应）
- 电流 / 电压 / 温度 / 电量四宫格
- 电流单位自动识别（μA ↔ mA），支持手动指定
- 串联双电芯校准开关（功率 ×2）
- 峰值功率统计、插拔自动重置、界面不可见时自动停止采样

## 工程结构

```
BatteryMonitor/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/{strings,themes}.xml
        └── java/com/example/batterymonitor/
            ├── MainActivity.kt          # 入口，绑定 ViewModel
            ├── BatteryState.kt          # UI 状态模型、枚举
            ├── BatteryRepository.kt     # 广播 + BatteryManager 数据源
            ├── BatteryViewModel.kt      # 单位校准、功率计算、采样循环
            └── ui/
                ├── BatteryDashboardScreen.kt  # 仪表盘
                ├── PowerChart.kt              # Canvas 实时折线图
                └── theme/Theme.kt
```

## 编译运行

1. 解压后用 Android Studio (Ladybug 及以上) `Open` 根目录 `BatteryMonitor`；
2. 首次同步会按 `gradle-wrapper.properties` 下载 Gradle 8.11.1（若提示缺少 wrapper jar，
   直接用 IDE 的 "Use Gradle from: gradle-wrapper.properties" 或本机已安装的 Gradle 即可）；
3. 连接安卓真机运行（模拟器无法返回真实 `CURRENT_NOW`，电流恒为 0）。

要求：JDK 17、AGP 8.7.3、compileSdk 35、minSdk 26。无需任何运行时权限。

命令行编译（Windows）：

```bat
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
gradle assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 数值口径说明

| 项目 | 说明 |
| --- | --- |
| 功率 | `P(W) = U(mV) × I(mA) / 1_000_000`，串联双电芯时电压翻倍 |
| 电流 | `CURRENT_NOW` 为电池端净电流（充电器输入 − 整机功耗），亮屏明显偏低 |
| 单位 | 谷歌规范为 μA；`|raw| > 20000` 判定为 μA（20A 的 mA 值不可能出现），并在会话内锁定 |
| 符号 | 统一为充电正、放电负（各厂商原始符号相反） |
| 电压 | 系统上报的是单串电芯电压（约 3.7~4.5V），双电芯需 ×2 才是整包电压 |
| 刷新率 | 部分机型内核 1~3 秒才更新电流寄存器，界面会按硬件节奏跳变 |

## 后续可加（第三阶段）

- 前台服务 + 常驻通知显示实时功率（息屏也能看）
- Room 持久化充电周期：耗时、充入 mAh（`CHARGE_COUNTER` 差值）、温升极值
- 充满提醒、充电报告分享
