# HushWave

本地直连的多品牌小玩具控制器。Android / Kotlin + Jetpack Compose，不联网、不需要电脑。

> 名字和图标来自一次很随意的深夜决定：一只捂着嘴的小家伙，戴着会发光的耳机。

## 能做什么

- **多品牌直连**：Galaku、Svakom（v1–v6）、Lovense、We-Vibe（8bit）、MagicMotion（v1–v4）、Kiiroo（v2.1）、Satisfyer
- **玩具记忆**：按蓝牙地址记住品牌 / 协议 / 通道 / 上次设置，下次连上自动恢复
- **通道化控制**：振动、旋转、摆动、气阀、吮吸、抽插、气泵、电击（含频率）、加热、灯效
- **总强度**：一根滑杆推所有通道；单独动过某个通道后自动让位，直到再次拖动
- **波形**：9 条预设 + 手绘曲线，可多通道同时作用（同步 / 按通道错开相位）
- **摇一摇 / 脉冲**：药丸开关选择作用于哪些通道
- **声控**：麦克风 FFT，低频跟拍 + 静音门限，可选通道
- **音乐**：导入本地歌曲，本地解码分析「激昂程度」曲线，按进度驱动通道
- **模式互斥**：波形 / 摇一摇 / 声控 / 音乐同时只会有一个在跑
- **深色 / 浅色**：默认跟随系统，浅色是糖果色，深色是原来的紫夜配色

## 支持的品牌

| 品牌 | 协议 | 备注 |
| --- | --- | --- |
| Galaku 伽拉库 | `galaku` | 官方 App 反编译，支持吮吸/抽插/泵/电击/加热/灯效 |
| Svakom | `svakom-v1..v6` | 六个固件版本；Viviana 额外支持电击（EMS） |
| Lovense | `lovense` | ASCII `Vibrate:N;` / `Rotate:N;` / `Air:Level:N;` |
| We-Vibe | `wevibe-8bit` | 8 字节位掩码 |
| MagicMotion | `magic-motion-1..4` | 12 / 17 字节定长包 |
| Kiiroo | `kiiroo-v21` | 仅振动 |
| Satisfyer | `satisfyer` | 每马达 4 字节，500ms 保活 |

设备定义来自 buttplug 的 `buttplug-device-config-v5`（BSD-3-Clause），抽取成 `app/src/main/assets/toy_catalog.json`；
伽拉库私有协议来自官方 App 反编译。细节见 [docs/PROTOCOL.md](docs/PROTOCOL.md)。

## 构建

```bash
# JDK 17 + Android SDK 34 + Gradle 8.5
gradle :app:assembleDebug
gradle :app:testDebugUnitTest   # 16 个编码回归测试
```

- 工程放在非 ASCII 路径时保留 `android.overridePathCheck=true`
- Gradle 测试任务在中文路径下会 `ClassNotFoundException`，跑测试请把工程复制到纯英文路径

## 目录

```
app/src/main/java/com/pulse/app/
├─ core/
│  ├─ GalakuProtocol.kt   伽拉库帧 / 加密 / 命令
│  ├─ ToyModel.kt         模块、协议目录、识别
│  ├─ ToyMemory.kt        玩具记忆
│  ├─ drivers/            各品牌编码器
│  ├─ BleController.kt    通用 GATT（最新帧组队列、保活）
│  ├─ ToyEngine.kt        通道值 → 驱动 → 字节（100ms 节流）
│  ├─ Patterns.kt         波形模型 / 预设 / 播放器
│  ├─ MusicEngine.kt      麦克风 FFT
│  └─ MusicLibrary.kt     本地曲库与歌曲包络分析
├─ ui/                    Compose 主题与页面
└─ PulseViewModel.kt
```

## 已知限制

- 目录外的型号需要手动选协议 + 勾通道（设置 → 手动添加设备）
- Kiiroo Keon 这类线性设备未适配
- 电量未显示
- 音乐分析依赖设备自带的 MediaCodec，冷门格式可能不支持

## 许可

BSD-3-Clause。品牌协议编码移植自 [buttplug](https://github.com/buttplugio/buttplug)，声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
