# 第三方协议来源与许可

> 本项目主体自 2026-11-14 起采用 **GPL-3.0-or-later**（全文见 `LICENSE`）。
> 本文件记录的是**第三方组件原本的许可与版权声明**，不因本项目改用 GPL 而失效：
> BSD-3-Clause 与 GPL-3.0 兼容，可以并入并以 GPL 整体再授权，但其版权声明、
> 许可条件与免责声明必须随源码和二进制分发原样保留。
> 下方上游声明里提到的 Joycon 相关组件本项目并未直接使用，保留仅为来源完整。
>
> 两个公开 release 的旧附件已于同日备份下架、以相同版本号重新以 GPL-3.0-or-later 发布；
> `1.0.1` 起的 APK 在 `assets/GPL-3.0.txt` 内置许可全文，满足二进制分发时随附许可文本的要求。

本项目的多品牌协议编码（lovense / svakom / wevibe / magic-motion / kiiroo / satisfyer）
参考并移植自 [buttplug](https://github.com/buttplugio/buttplug) 的 `crates/buttplug_server/src/device/protocol_impl`
与 `crates/buttplug_server_device_config/device-config`（设备定义）。仅移植了“字节怎么拼”的规则，
没有引用其运行时、网络协议或 UI 代码。

伽拉库（Galaku）协议来自对官方 App `com.wensheng.galakuplay` 1.0.92 的协议分析：
只提取帧格式、密钥表与校验算法等事实性信息，未复制其应用代码。
该部分的法律状态与上游闭源 App 相关，若用于商业分发请自行评估。

其余运行期依赖：AndroidX / Compose / Kotlin / OkHttp 为 Apache-2.0；
JUnit 4（EPL-1.0）只在本地单元测试中使用，不进入分发产物。

---

Copyright (c) 2016-2023, Nonpolynomial Labs, LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of buttplug nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

---

Joycon support in Buttplug uses code with the following licenses

---

Joycon-rs by Kaisei Yokoyama

Licensed under Apache License, Version 2.0 (LICENSE or http://www.apache.org/licenses/LICENSE-2.0)

---

hid-async by Swift Technologies

Copyright 2020 Shift Cryptosecurity AG

Licensed under Apache License, Version 2.0 (LICENSE or http://www.apache.org/licenses/LICENSE-2.0)
