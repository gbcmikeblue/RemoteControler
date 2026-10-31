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
| F2 一键预设发射 | `PresetExecutor.execute()` | 一条 `Preset` | Electra 是状态码协议，**发送一帧完整状态**；失败即报错。若预设设了定时，App 内倒计时到点再发一帧关机状态 |
| F3 多预设管理 | `PresetStore` + 列表/编辑页 | 名称、模式、风速、温度 16–30℃、睡眠(静音)、上下/左右扫风、强力、清洁、屏显、自动关机 0–12h | 可保存、选择、重复发射、长按删除；首次启动自带「睡眠 26℃ 2 小时后关」 |
| F5 一键关机 | `MainActivity.powerOff()` | 无 | 沿用最近预设的温度/模式/风速，只把电源位清零；同时撤销已登记的定时 |
| F4 对准引导 | 主界面固定提示区块 | 无 | 常驻文案：对准空调接收窗、3 米内、**听到「嘀」声即成功，App 无法确认结果** |

**数据契约**

- 红外发射：`ConsumerIrManager.transmit(38000 Hz, IntArray)`，数组为交替的微秒 mark/space。
- 帧结构：13 字节（104 bit），首字节 `0xC3`，**单帧连续发送**（不分段），字节内 **LSB 先发**，
  末字节 = 前 12 字节**算术累加和**。脉冲序列 = header + 104 bit + footer，共 106 组 mark/space。
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

## 5. 协议实现与校准

协议参数全部来自 **IRremoteESP8266 v2.9.0** 的 `ir_Electra.h` / `ir_Electra.cpp`，
其支持机型列表包含 **AUX KFR-35GW/BpNFW=3 + YKR-T/011**（奥克斯 YKR 系列遥控器）。

三条在源码里写死、也最容易搞错的事实：

1. 校验 = 前 12 字节**算术累加和**（`sumBytes`），不是异或；
2. 字节内 **LSB 先发**（`sendGeneric(..., MSBfirst = false)`）；
3. **单帧 104 bit 连续发送，不分段**。

`app/src/test/.../ElectraCodecTest.kt` 里的「黄金帧」用例用仓库中真实抓取的一帧
`C3 6F E0 00 A0 00 28 64 00 20 00 1E 7C` 做逐字节比对，可直接证明编码器与权威实现一致。

**协议没有的能力，本工程的处理：**

| 需求 | Electra 现状 | 处理方式 |
|---|---|---|
| 睡眠模式 | 无独立 sleep 字段 | 映射到 **Quiet 位**（byte5 bit7），界面注明"静音近似" |
| 定时关机 | 无定时码 | **App 内倒计时**，到点发一帧关机状态；退出 App 即失效 |

**换机型/换品牌的校准路径**：把实测微秒数组写进预设的 `rawCommands`，
执行器会优先按实测波形逐条发射，完全绕开协议编码（界面会显示「已使用实测波形覆盖协议编码」）。

---

## 6. 已知限制

- 红外单向通信，App 无法感知空调是否执行，也无法读取空调当前状态（需求 3.3）。
- 不做后台定时自动发射（需求 5：Android Doze 下不可靠）。Electra 也没有定时码，
  「定时关机」是 App 前台倒计时，退出 App 后不再生效。
- 协议按 Electra 实现；若实测某型号不属于该协议，需改用 `rawCommands` 导入实测波形。
- 奥克斯不同型号协议可能存在差异，以用户实际遥控器实测为准。
