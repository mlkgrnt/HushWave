# 伽拉库（galaku）小玩具私有 BLE 协议 · 逆向记录

样本：`galaku.apk`
- 包名 `com.wensheng.galakuplay`，版本 `1.0.92`（versionCode 92）
- 编译 SDK 33 / minSdk 23，Kotlin + Java 混合
- 关键类：
  - `com.wensheng.galakuplay.bluecontroller.bluetooth.BleControl`
  - `com.wensheng.galakuplay.bluecontroller.bluetooth.EnDesCommand`
  - `com.wensheng.galakuplay.bluecontroller.bluetooth.BluetoothControl`
  - `com.wensheng.galakuplay.bluecontroller.bluetooth.BroadcastControl`
  - `com.wensheng.galakuplay.bluecontroller.enums.DeviceType`
  - `assets/device_items.json`（153 个型号的型号表）

---

## 1. 两条链路

官方 App 会按型号表里的 `isBleDevice` 分流：

| 路径 | 型号数 | 机制 |
| --- | --- | --- |
| GATT 直连 | 128 | 连接后写特征值，帧加密 |
| 广播控制 | 25 | 手机做 BLE 广播，把命令塞进厂商数据；玩具从扫描结果回传电量/ID |

Pulse 目前实现了 **GATT 直连**。广播型号在列表里标 `广播型号` 提示。

---

## 2. GATT 服务

```
Service        00001000-0000-1000-8000-00805f9b34fb
Write char     00001001-0000-1000-8000-00805f9b34fb   (WRITE, 默认写)
Notify char    00001002-0000-1000-8000-00805f9b34fb
```

连接顺序（与官方一致）：
1. `connectGatt(context, false, cb, TRANSPORT_LE)`
2. `discoverServices()`
3. `requestMtu(512)`
4. 校验 0x1000 服务与 0x1001 特征值存在
5. 对服务的可通知特征值写 CCCD（`ENABLE_NOTIFICATION_VALUE`）
6. 之后即可写命令

官方没有握手、没有鉴权、没有配网。

---

## 3. 帧结构

### 3.1 明文（10 字节）

| 偏移 | 值 | 说明 |
| --- | --- | --- |
| 0 | `0x5A` | 帧头 |
| 1 | `0x00` | 固定 |
| 2 | `0x00` | 固定 |
| 3 | `0x01` | 固定 |
| 4 | 命令字 | 见下表 |
| 5–9 | 负载 | 依命令而定 |

### 3.2 封装（12 字节）

```
buf[0]  = 0x23
buf[1..10] = 明文 10 字节
buf[11] = (buf[0] + ... + buf[10]) & 0xFF     // 前 11 字节之和
```

### 3.3 加密（查表异或）

4 张 12 字节密钥表（按有符号字节原样保留）：

```
K0 = 00 18 98 F7 A5 3D 0D 29 25 50 44 46
K1 = 00 45 6E 6A 6F 78 20 53 2D 31 2E 37
K2 = 00 65 78 20 54 6F 79 73 0A 8E 9D A3
K3 = 00 C5 D6 E7 F8 0A 32 20 6F 62 0D 0A
```

加密（`EnDesCommand.Encrypt`）：

```c
out[0] = in[0];                       // 0x23
for (i = 1; i < 12; i++) {
    k = K[out[i-1] & 3][i];           // 注意：用“已加密”的前一字节选表
    out[i] = (byte)(((k ^ in[0]) ^ in[i]) + k);
}
```

解密：

```c
out[0] = c[0];
for (i = 1; i < 12; i++) {
    k = K[c[i-1] & 3][i];             // 用“密文”的前一字节选表
    out[i] = (byte)(k ^ ((c[i] - k) ^ c[0]));
}
```

实测校验：把 App 发出的 `23 81 BB AB D2 EC 77 23 BB A3 3B E4` 解密，得到
`23 5A 00 00 01 31 44 00 00 00 00 F3` —— 帧头、命令 `0x31`、强度 68、校验和全部正确。

---

## 4. 命令字

| 命令 | 构造（明文 5–9 位） | 用途 |
| --- | --- | --- |
| `0x31` | `[强度 0-100] 0 0 0 0` | 单电机振动 |
| `0x40` `[5]=0x03` | `03 A B 0 0` | 双电机 |
| `0x40` `[5]=0x07` | `07 A B C 0` | 三电机 |
| `0x32` | `[强度] 0 0 0 0` | 吮吸 |
| `0x71` | `[强度] 0 0 0 0` | 抽插 |
| `0x80` | `[档位] 0 0 0 0` | 加热 |
| `0x15` | `[档位] 0 0 0 0` | 灯效 |
| `0x90` | `[频率 1-10] [强度 0-8] 0 0` | 电击 |
| `0x93` `[5]=0x0F` | `0F A B 频率 强度` | 振动+电击组合 |
| `0x60` `[5]=0x03` | `03 [启停] [强度] 0 0` | 气泵 |
| `0x50` | `[启停] 0 0 0 0` | 气泵（简版） |

### 特殊：AC695X 彩屏泵

型号表里 `localName == "AC695X_1(BLE)"` 的机器**不加密、不用 10 字节帧**，直接写 16 字节：

```
AA 01 0A 03 [强度] [启停] 00 00 00 00 00 00 00 00 00 00
```

---

## 5. 型号 → 能力

`assets/device_items.json` 里的 `deviceType` 决定用哪组命令：

| deviceType | 名称 | 通道 | 命令 |
| --- | --- | --- | --- |
| 0 | DeviceShakeOne | 1 路振动 | `0x31` |
| 1 | DeviceShakeTwo | 2 路振动 | `0x40/3` |
| 2 | DeviceShakeAndPump | 振动 + 气泵 | `0x60/3` |
| 3 | DeviceShakeAndDianji | 振动 + 电击 | `0x90` + `0x31` |
| 4 | DeviceShakeTwoAndDianji | 双振 + 电击 | `0x90` + `0x40/3` |
| 5 | DeviceShakeAndDianji2 | 振动 + 电击 II | `0x93/0F` |
| 6 | DeviceXiAndChouCha | 吮吸 + 抽插 | `0x32` + `0x71` |
| 7 | DeviceChouCha | 抽插 | `0x71` |
| 8 | DeviceShakeThree | 3 路振动 | `0x40/7` |

`isStrong = true` 的型号（渣古飞机杯、机甲飞机杯、双甲飞机杯）做了强度重映射：

```
out = (value <= 0) ? 0 : 40 + value * 0.6
```

---

## 6. 广播协议（未实现，仅记录）

- 手机使用 `BluetoothLeAdvertiser` 广播，Manufacturer ID `0xFFF0`（65520）
- 14 字节载荷：
  ```
  [0] deviceId
  [1] 0x01
  [2] 0x33（强度）/ 0x21（关机）
  [3][4] 强度
  ...
  ```
- XOR 混淆：`key = payload[0] ^ 0x68`，`payload[1..12] ^= key`，`payload[13] = sum(0..12)`
- 玩具在扫描响应的 `scanRecord[12]` 回传 deviceId、`[13]` 回传电量

---

## 7. 官方 App 里没用的东西

- 没有握手/配对码，任何能连上 GATT 的客户端都能直接控制
- 波形播放：每 100ms 一帧，强度 0–1 线性插值
- 摇一摇：`min(1, |ax| / 20)`，100ms 采样
- 音乐律动：按系统音量做 RMS 阈值映射（`VolumnComputer`）

Pulse 的波形引擎用 50ms 一帧、自定义曲线 + 双电机相位差，属于增强而非复刻。

---

# 附：多品牌驱动（Pulse 2.0）

设备定义（品牌、广播名、服务/特征值、模块与档位范围）来自 buttplug 的
`buttplug-device-config-v5.json`，抽取成 `assets/toy_catalog.json`。
下面是实际移植的帧格式，括号内为驱动 id。

## Lovense（`lovense`）

- 端点：`0000fff0` → tx `0000fff2`（另有 NUS 与多组 50300xxx 备选）
- 命令为 ASCII，**不带结束符以外的包装**：
  - 单马达：`Vibrate:20;`（0–20）
  - 多马达：`Vibrate1:20;` `Vibrate2:20;`
  - 旋转：`Rotate:20;`
  - Max 气阀：`Air:Level:3;`
  - 换向：`RotateChange;`

## Svakom（`svakom-v1` … `svakom-v6`）

- 端点：`0000ffe0` → tx `0000ffe1`，rx `0000ffe2`
- 帧都以 `0x55` 开头，**需要 800ms 重发保活**：

| 版本 | 振动帧 |
| --- | --- |
| v1 | `55 04 03 00 [on] [v]` |
| v2 | 马达0 `55 03 03 00 [on] [v]`；马达1 `55 06 01 00 [v] [v]` |
| v3 | 马达0 `55 03 03 00 [on] [v]`；马达1 `55 09 00 00 [on] [v]`；旋转 `55 08 00 00 [v] FF` |
| v4 | `55 03 [sel] 00 [on] [max] 00`（7 字节） |
| v5 | `55 03 [sel] 00 [on] [max]`（6 字节） |
| v6 | 前两路同 v4；第三路 `55 07 00 00 [on] [v] 00` |

`sel`：两路都为 0 或相等 → `00`；仅马达0 → `01`；仅马达1 → `02`。
所以 v4–v6 无法给两个马达设**不同**档位，只能取最大值（与原厂固件一致）。

## We-Vibe 8bit（`wevibe-8bit`）

- 端点：`f000bb03` → tx `f000c000`，需要写响应
- 8 字节：
  - 关：`0F 00 00 00 00 00 00 00`
  - 开：`0F 03 00 [ext+3] [int+3] [status] 00 00`
  - `status` bit0=内马达开，bit1=外马达开；档位 0–12

## MagicMotion（`magic-motion-1..4`）

- 端点：`78667579-7b48-43db-b8c5-7928a6b0a335`
- v1：`0B FF 04 0A 32 32 00 04 08 [v] 64 00`
- v3：同上，但把 `32 32` 换成 `46 46`
- v2 / v4：17 字节，`10 FF 04 0A 32 32 00 04 08 [v0] 64 00 04 08 [v1] 64 [01/00]`

## Kiiroo v2.1（`kiiroo-v21`）

- 端点：`00001900` → tx `00001902`（或 `a0d70001` → `a0d70002`）
- 振动：`01 [speed 0-100]`

## Satisfyer（`satisfyer`）

- 每个振动马达输出 4 个相同字节，拼接成包；**500ms 重发保活**

## 小怪兽 Sistalk MonsterPub（`monsterpub`）

- 服务 `00006000-0000-1000-8000-00805f9b34fb`
  - `00006003` txvibrate（优先）、`00006001` tx、`00006002` txmode、`0000600a` generic0
- 服务 `00006010` → `00006014` rxblemodel（型号标识串）
- 服务 `00008000-0000-1000-8000-00805f9b34fb` → `00008001` rx（认证）

**连接握手（必须）**：读 `00008001` 得到 `[keyIndex, 15 字节挑战]`，
按 keyIndex 选 4 把重复式密钥（`2IPO` / `LSBB` / `SIS6` / `TALK` 循环补齐 15 字节），
逐字节 XOR 后把 15 字节写回 `00008001`。本项目在下发控制帧前会等握手完成。

控制帧是各通道 0–100 的裸字节序列；用 `tx` 端点时全停改写到 `txmode`，
Tracy's Dog 系列走 `generic0` 且首字节补 `0x03`。

## 网易春风 TryFun（`tryfun` / `tryfun-meta2` / `tryfun-blackhole`）

三条固件共用同一套校验和思路，但包头与命令字不同：

```
sum  = 0xFF
sum -= 除首字节外所有字节   // 按 u8 回绕
sum += count               // 经典 TryFun 从 0 起算，Meta2 / BlackHole 从 1 起算
末尾追加 sum
```

| 协议 | 服务 / tx | 广播名 | 帧 |
| --- | --- | --- | --- |
| `tryfun` | `0000ff10`/`0000fff1` 或 `0000ffac`/`0000ffb5` | `TRYFUN-ONE`, `TF-SPRAY` | 摆动 `AA 02 07 [0-9] [ck]`；旋转 `AA 02 08 [0-9] [ck]` |
| `tryfun` (TF-SPRAY) | 同上 | `TF-SPRAY` | `00 02 00 05 [1/2] [2 或 speed] 01 [1/0] [FD-speed]` |
| `tryfun-meta2` | `0000ffac`/`0000ffb7` | `TF-META2` | `[pid] 02 00 05 21 05 [0B 摆动 / 08 振动 / 0E 旋转] [speed] [ck]` |
| `tryfun-blackhole` | `0000ffac`/`0000ffb7` | `TF-BHPLUS`, `TF-ROCK` | `[pid] 02 00 03 [0C 摆动 / 09 振动] [speed] [ck]` |

`pid` 是每帧自增的包头号。Meta2 的旋转速度按 `-(speed + 1)` 取反（与上游一致）。

以上帧格式来自 buttplug 的 `tryfun.rs` / `tryfun_meta2.rs` / `tryfun_blackhole.rs`（BSD-3-Clause），
**尚未经过实机验证**；如手上有设备，欢迎在 issue 里贴连接记录帮助我们校准。

## 暂未支持：大人糖

没有找到该品牌的公开 BLE 协议资料，上游 buttplug 的 149 个协议中也没有对应条目。
在没有抓包样本前不实现，避免误报「已支持」。补协议需要：广播名、GATT 服务/特征值 UUID、
以及一段「手机 App 控制时」的 HCI 抓包（或 nRF Connect 的写入记录）。

## 识别规则

1. 广播名与型号 identifier 精确相同 → 置信度 0.95
2. 广播名匹配协议的通配（如 `LVS-*`、`LOVE-*`）→ 0.7
3. 扫描响应里的 service UUID 命中协议宣告服务 → 0.8 / 0.75

识别不到时由用户手动选协议，并在「模块」里勾选真实通道，结果写入玩具记忆。

## 补充：Svakom Viviana 的电击（EMS）

Viviana 是「振动 + EMS」双功能蛋。buttplug 只定义了它的振动通道，所以本项目额外用
`assets/model_overrides.json` 把型号能力补成 `VIBRATE + ESTIM`，并在连接时默认开启。

EMS 帧（**待真机确认**）：

```
55 06 01 00 [level] [level]      // level 0..10
```

依据：buttplug 的 `svakom-v2` 协议里第二路马达就是这条 `0x06` 帧，而 Viviana 的第二路正是 EMS。
如果真机上没有反应，候选还有 `55 0A …` / `55 05 …`（不要连续盲发，EMS 属于电刺激，需逐个谨慎试）。

## 型号能力覆盖表

`assets/model_overrides.json` 用来给「buttplug 只知道振动、实际还有别的功能」的型号补通道：

```json
{ "brand": "Svakom", "match": ["Viviana", "Vivian"],
  "modules": [ {"t":"VIBRATE","i":0,"label":"振动","max":10},
               {"t":"ESTIM","i":0,"label":"电击","max":10} ] }
```

命中后整体替换该型号的模块列表，连接后默认全部开启；已知型号的「通道设置」只允许开关，不允许新增。
