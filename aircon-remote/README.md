# 空调一键预设（aircon-remote）

按 `doc/需求文档.txt` 从零实现的独立 Android 工程：面向非智能老款奥克斯空调，
点一下就把空调设为「开机 + 睡眠模式 + 指定温度 + 定时关机」。

与原仓库的万能遥控器是**两个产品**，因此本工程为新建，不改动原 `app/`、`greendao/`、
`controllerserver/` 任何代码。

---

## 1. 构建

| 项 | 值 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| AGP / Gradle | 8.7.3 / 8.9 |
| compileSdk / minSdk / targetSdk | 36 / 19 / 36 |
| JDK | 17+（本机验证使用 JDK 21） |

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1+1'
.\gradlew.bat assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat testDebugUnitTest    # 协议层结构性单测（7 例）
```

两点环境说明：

- `gradle-wrapper.properties` 默认指向腾讯镜像（官方 `services.gradle.org` 在部分网络下不可达），
  可直连时可改回官方地址。
- `local.properties` 保存本机 `sdk.dir`，已加入 `.gitignore`。

---

## 2. 目录结构

```
app/src/main/java/com/zjianhao/aircon/
├── ir/
│   ├── IrTransmitter.kt          ConsumerIrManager 封装：hasIrEmitter 检测、发射、结果封装
│   ├── PulsePattern.kt           脉冲数组校验（成对、正数、总时长 < 2s）
│   └── electra/
│       ├── ElectraSpec.kt        协议时序 + 位域【唯一校准入口】
│       ├── ElectraState.kt       意图状态、模式/风速枚举
│       └── ElectraCodec.kt       状态 → 13 字节帧 → 38kHz 脉冲 IntArray
├── preset/
│   ├── Preset.kt                 预设模型（含实测波形覆盖字段）
│   ├── PresetStore.kt            SharedPreferences + JSON 本地持久化
│   └── PresetExecutor.kt         组合指令编排与顺序发射
└── ui/
    ├── MainActivity.kt           红外检测、对准引导、预设列表、一键发射
    ├── PresetAdapter.kt
    └── PresetEditActivity.kt     预设编辑
```

---

## 3. 功能清单（对应需求第 3 节）

| 需求 | 实现 | 输入 | 输出 / 预期行为 |
|---|---|---|---|
| F1 红外硬件检测 | `IrTransmitter.availability()` | 无 | `Available` / `UnsupportedApi`（<API19）/ `NoEmitter`；主界面顶部卡片明确提示，无硬件时禁用发射 |
| F2 一键预设发射 | `PresetExecutor.execute()` | 一条 `Preset` | 按 `开机 → 睡眠 → 温度 → 定时` 顺序发射，步骤实时回显；任一帧失败即中止并报错 |
| F3 多预设管理 | `PresetStore` + 列表/编辑页 | 名称、模式、风速、温度 16–30℃、定时 0–12h、睡眠开关 | 可保存、选择、重复发射、长按删除；首次启动自带「睡眠 26℃ 2 小时后关」 |
| F4 对准引导 | 主界面固定提示区块 | 无 | 常驻文案：对准空调接收窗、3 米内、**听到「嘀」声即成功，App 无法确认结果** |

**数据契约**

- 红外发射：`ConsumerIrManager.transmit(38000 Hz, IntArray)`，数组为交替的微秒 mark/space。
- 帧结构：13 字节（104 bit），首字节 `0xC3`，按 34/34/36 分三段发送，段间 30 ms。
- 本地存储：`SharedPreferences("aircon_presets")` 下的 JSON 数组。

---

## 4. 与原仓库的差异（为什么是新建而非删减）

需求文档描述的产品与旧代码几乎无交集，逐条对照：

| 需求点 | 旧代码现状 |
|---|---|
| Kotlin | 全 Java，无 kotlin 插件 |
| 红外码本地生成 | 无协议代码，全部走 `controllerserver` 远程码库（showapi 1183-7） |
| 睡眠模式 | `AirCondition` 模式集为 自动/制冷/制热/送风/加湿，没有睡眠 |
| 定时关机 | `AIR_CMD` 只有 `(device_id, cmd, temp)`，无定时维度 |
| 多预设 | 只有 `DEVICE`/`KEYAS`/`AIR_CMD` 三张表，无预设概念 |
| 一键组合发射 | 每次点击只发一帧 |
| 无红外提示 | `InfraredUtil.send()` 仅 `println("not support")`，界面静默 |
| 单帧 < 2 秒校验 | 无 |
| `uses-feature consumerir` | Manifest 缺失 |
| minSdk 19 | minSdk 15 |

结论：旧代码**没有**需求所需的核心能力，无法通过裁剪得到，故新建工程；
`controllerserver` 在本需求下整体不需要（需求明确排除服务端码库与万能遥控数据）。

---

## 5. M2 校准指南

协议移植属于「结构正确、参数待实测」状态。所有需要校准的量都集中在
`ir/electra/ElectraSpec.kt`，逻辑代码里没有裸数字：

1. `SECTION_BIT_LENGTHS` —— 分帧长度，合计必须等于 104
2. `POWER / MODE / TEMPERATURE / FAN / SLEEP / TIMER_OFF_HOURS` —— 字节下标与位偏移
3. `ElectraCodec.checksum()` —— 校验字节算法（当前为前 12 字节逐位异或）
4. `PresetExecutor.commandIntervalMs` —— 多帧之间的间隔（当前 300ms）

校准方式有两种，互不影响：

- 改参数：量出来后直接改上述常量。
- 不改代码：把实测的微秒数组写进预设的 `rawCommands`，执行器会**优先按实测波形顺序发射**，
  完全绕开协议编码（界面会显示「已使用实测波形覆盖协议编码」）。

单测只覆盖结构性正确（帧长、位域、脉冲合法性、2 秒上限），协议是否被真机接受必须实测。

---

## 6. 已知限制

- 红外单向通信，App 无法感知空调是否执行，也无法读取空调当前状态（需求 3.3）。
- 不做后台定时自动发射（需求 5：Android Doze 下不可靠），定时关机以红外指令形式下发。
- 奥克斯不同型号协议可能存在差异，以用户实际遥控器实测为准。
