# HushWave

<img src="docs/icon.png" width="120" align="right" alt="HushWave icon" />

本地直连的多品牌小玩具控制器。Android / Kotlin + Jetpack Compose，不联网、不需要电脑。

> 名字和图标来自一次很随意的深夜决定：一只捂着嘴的小家伙，戴着会发光的耳机。

## 能做什么

- **多品牌直连**：Galaku、Svakom（v1–v6）、Lovense、We-Vibe（8bit）、MagicMotion（v1–v4）、Kiiroo（v2.1）、Satisfyer、小怪兽 MonsterPub、网易春风 TryFun、杰士邦 Jissbon
- **玩具记忆**：按蓝牙地址记住品牌 / 协议 / 通道 / 上次设置，下次连上自动恢复
- **通道化控制**：振动、旋转、摆动、气阀、吮吸、抽插、气泵、电击（含频率）、加热、灯效
- **总强度**：一根滑杆推所有通道；单独动过某个通道后自动让位，直到再次拖动
- **波形**：9 条预设 + 手绘曲线，可多通道同时作用（同步 / 按通道错开相位）
- **摇一摇 / 脉冲**：药丸开关选择作用于哪些通道
- **声控**：麦克风 FFT，低频跟拍 + 静音门限，可选通道
- **音乐**：导入本地歌曲，本地解码分析「激昂程度」曲线，按进度驱动通道
- **模式互斥**：波形 / 摇一摇 / 声控 / 音乐同时只会有一个在跑
- **主题**：跟随系统 / 固定浅色 / 固定深色；浅色与深色可各选一套配色
  （糖果、极光、薄荷、晚霞、海盐、葡萄、素色），支持自定义主色和 Android 12+ 壁纸取色
- **反馈与更新**：一键复制诊断信息（版本 / 机型 / 崩溃栈 / 蓝牙收发），GitHub 检查更新与仓库入口
- **信号**：扫描列表显示实时 dBm

## 支持的品牌

| 品牌 | 协议 | 备注 |
| --- | --- | --- |
| Galaku | `galaku` | 官方 App 反编译，支持吮吸/抽插/泵/电击/加热/灯效 |
| Svakom | `svakom-v1..v6` | 六个固件版本；Viviana 额外支持电击（EMS） |
| Lovense | `lovense` | ASCII `Vibrate:N;` / `Rotate:N;` / `Air:Level:N;` |
| We-Vibe | `wevibe-8bit` | 8 字节位掩码 |
| MagicMotion | `magic-motion-1..4` | 12 / 17 字节定长包 |
| Kiiroo | `kiiroo-v21` | 仅振动 |
| Satisfyer | `satisfyer` | 每马达 4 字节，500ms 保活 |
| 小怪兽 Sistalk | `monsterpub` | 连接时读 `00008001` 拿挑战串、XOR 密钥后写回；双通道 0–100 |
| 网易春风 | `tryfun` | 元系列摆动 / 旋转；TF-SPRAY 用 9 字节振动帧 |
| 网易春风 | `tryfun-meta2` | Meta 2：包头号 + `21 05 08/0B/0E` + 速度 + 校验和 |
| 网易春风 | `tryfun-blackhole` | 黑洞 Plus / Rock：包头号 + `03 09/0C` + 速度 + 校验和 |
| 杰士邦 Jissbon | `jissbon` | 小粉饼 / `G29D` 复用 Galaku OEM 帧，双振动通道；其它型号走手动添加 |

设备定义来自 buttplug 的 `buttplug-device-config-v5`（BSD-3-Clause），抽取成 `app/src/main/assets/toy_catalog.json`；
伽拉库私有协议来自官方 App 反编译。细节见 [docs/PROTOCOL.md](docs/PROTOCOL.md)。

**关于大人糖：** 已向品牌客服确认，该品牌全线没有蓝牙 / 远程控制产品，网上流传的「蓝牙版」也找不到对应实物，
上游 buttplug 的协议列表中同样没有对应条目。因此不再列入协议支持计划，也不做无依据的猜测实现。
若将来确有蓝牙新品，按「广播名 + GATT UUID + 一段控制抓包」的流程接入即可。

其它未收录型号仍可走「设置 → 手动添加设备」指定品牌与通道。

## 构建

```bash
# JDK 17 + Android SDK 34 + Gradle 8.x
gradle :app:assembleDebug
gradle :app:testDebugUnitTest   # 23 个编码回归测试
```

- 工程放在非 ASCII 路径时保留 `android.overridePathCheck=true`
- 跑测试时 `GRADLE_USER_HOME` 不能是中文路径：Gradle 会把 worker 的 classpath 参数文件写在
  `$GRADLE_USER_HOME/.tmp/`，JVM 读不到就会报 `ClassNotFoundException: GradleWorkerMain`。
  可把工程和 `GRADLE_USER_HOME` 都放到纯英文路径（后者也可用目录联接指向原缓存）

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
