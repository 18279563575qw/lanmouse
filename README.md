# LanMouse

用手机当无线触控板的局域网小工具：Android 端把鼠标移动、点击、滚轮指令通过 UDP 发给 Windows 端，Windows 端用 Win32 `SendInput` 驱动鼠标。

*Use your Android phone as a wireless touchpad for Windows over LAN.*

- **电脑端**：单个 exe，只用系统自带的 .NET Framework 4.x（Windows 10/11 默认包含），无需安装驱动
- **手机端**：只用 Android SDK 原生 API，无第三方依赖（minSdk 23 / targetSdk 35）
- **免配置发现**：手机可自动搜索同一局域网内的服务端，连接状态有明确反馈

## 功能

| 手势 | 动作 |
| --- | --- |
| 单指滑动 | 移动指针 |
| 轻触触控板 | 左键单击 |
| 长按后拖动 | 左键拖动 |
| 双指轻触 | 右键单击 |
| 双指上下滑动 | 滚轮 |

- 指针灵敏度调节
- 深色专业工具风界面，连接状态、输入框和操作按钮有明确视觉层级
- 震动反馈开关：左键、右键、长按拖动和滚动使用系统精细触感，默认开启
- 自动搜索局域网内的服务端（UDP 8765 广播）
- 连接确认：收到服务端 `pong` 才显示“已连接”，令牌错误、端口不对、防火墙拦截都有可读提示
- 服务端 5 秒无数据自动释放按键，降低断线后左键卡住的风险
- 服务端启动时列出本机局域网 IPv4 地址

## 目录结构

```text
.
├─ windows/                    Windows 服务端（C#，单文件）
│  ├─ src/LanMouseServer.cs
│  ├─ build.ps1                用系统自带 csc.exe 编译
│  ├─ run.ps1                  启动脚本，可改端口 / 令牌
│  ├─ start-server.bat         双击启动
│  ├─ allow-firewall.ps1/.bat  一键放行防火墙（自动请求管理员权限）
│  ├─ package.ps1              组装 dist/LanMouse-Windows.zip
│  ├─ 使用说明-分发包.txt       打包进分发包的使用者说明
│  └─ LanMouseServer.exe       本地编译产物（不入库）
├─ android/                    Android 工程（Java，无第三方依赖）
│  ├─ app/src/main/…           源码与 AndroidManifest
│  ├─ build-debug.ps1          命令行构建脚本（支持 -Release）
│  └─ settings.gradle
└─ dist/                       本地生成的分发包（不入库）
   ├─ LanMouse-debug.apk
   ├─ LanMouse-debug.apk.sha256
   ├─ LanMouse-Windows.zip     电脑端免安装分发包（含手机端 APK）
   └─ LanMouse-Windows.zip.sha256
```

## 快速开始

### 1. 电脑端（Windows 10/11）

双击 `windows/start-server.bat`，终端会打印：

```text
监听地址 : 0.0.0.0:8765
认证令牌 : lanmouse
局域网 IP:
  192.168.1.100
```

把这个 IP 和令牌填进手机 App。首次使用若手机连不上，用管理员 PowerShell 放行防火墙：

```powershell
cd windows
.\allow-firewall.ps1 -Port 8765
```

修改端口或令牌：

```powershell
.\run.ps1 -Port 9000 -Token "<你的令牌>"
```

`run.ps1` 参数：`-Bind`（默认 `0.0.0.0`）、`-Port`（默认 `8765`）、`-Token`（默认 `lanmouse`）、`-NoAuth`（关闭认证，不建议）。

### 2. 手机端（Android 6.0+）

从 [GitHub Releases](https://github.com/TAo-1022/lanmouse/releases) 下载并安装 `LanMouse-debug.apk`，或使用本地构建产物：

```bash
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

也可以把 APK 传到手机点击安装（需允许“安装未知来源的应用”）。然后：

1. 点“搜索 Windows 设备”，或手动填写电脑端打印的 IP；
2. 端口填 `8765`，令牌填 `lanmouse`（与服务端保持一致）；
3. 点“应用并启用触控板”，状态栏显示“已连接 …”后即可使用。

> `v1.1.0` 发布包使用 Android Debug 签名；从后续版本开始可使用 `keystore.properties` 生成稳定签名的 Release APK。

### 3. 免安装分发包（推荐转发给他人）

`dist/LanMouse-Windows.zip` 解压后得到 `LanMouse-Windows/`：

```text
LanMouse-Windows/
├─ start-server.bat        双击启动服务端
├─ allow-firewall.bat      一键放行防火墙（会自动请求管理员权限）
├─ allow-firewall.ps1
├─ LanMouseServer.exe
├─ run.ps1
├─ build.ps1 + src/        源码与编译脚本
├─ 使用说明-先看这个.txt    面向使用者的步骤与排错
└─ android-apk/            手机端 APK
```

使用方只需三步：启动 `start-server.bat` → 需要时运行 `allow-firewall.bat` → 手机安装 `android-apk` 中的 APK，填入窗口显示的 IP、端口与令牌。

重新生成分发包（会同步刷新 `dist/LanMouse-Windows.zip.sha256`）：

```powershell
powershell -ExecutionPolicy Bypass -File windows\package.ps1
```

## 构建

### Windows 服务端

```powershell
cd windows
.\build.ps1
```

只用系统自带的 `csc.exe`（.NET Framework 4.x），不引入第三方构建工具。

### Android 端

环境要求：JDK 17、Android SDK Platform 35 与 Build-Tools、Gradle 8.9（构建脚本在缺失时会自动下载并校验 SHA-256）。

```powershell
cd android
.\build-debug.ps1
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`。
`android/local.properties` 由脚本自动生成（记录本机 SDK 路径），已在 `.gitignore` 中忽略。

正式签名构建需要先复制 `android/keystore.properties.example` 为 `android/keystore.properties`，填写 Keystore 路径、密码和别名，然后执行：

```powershell
.\build-debug.ps1 -Release
```

产物：`android/app/build/outputs/apk/release/app-release.apk`。`keystore.properties` 和密钥文件不会提交到 Git。

## 协议

UDP 上的 UTF-8 JSON，默认端口 `8765`。控制包都带 `v`、`type`、`token`；发现请求不带令牌。

请求（手机 → 电脑）：

```json
{"v":1,"type":"discover"}
{"v":1,"type":"ping","token":"lanmouse"}
{"v":1,"type":"move","token":"lanmouse","dx":1.5,"dy":-0.8}
{"v":1,"type":"button","token":"lanmouse","button":"left","action":"click"}
{"v":1,"type":"button","token":"lanmouse","button":"left","action":"down"}
{"v":1,"type":"button","token":"lanmouse","button":"right","action":"click"}
{"v":1,"type":"scroll","token":"lanmouse","delta":120}
```

响应（电脑 → 手机）：

```json
{"v":1,"type":"discovery","name":"DESKTOP-PC","port":8765}
{"type":"pong","v":1}
```

细节：

- `move` 的单包位移在服务端被限制在 ±1000，并在服务端累计小数位移后再调用 `SendInput`，避免细微移动被舍入丢失。
- `button` 支持 `left` / `right` / `middle` 与 `down` / `up` / `click`。
- `scroll` 的 `delta` 限制在 ±1200，服务端按 120 为一格换算。
- 令牌不匹配时服务端丢弃该包，并按 2 秒节流打印警告。
- 服务端 5 秒未收到有效包时释放所有按键。

## 安全说明

- 明文 UDP + 共享令牌，**没有加密，也没有防重放**，仅适用于可信局域网。
- 不要在路由器或系统防火墙上做端口映射 / 转发，不要暴露到公网。
- 发现请求只返回机器名与端口，不返回令牌。
- 同一局域网内的抓包者可以拿到令牌，敏感场景请等待加密方案或改用 `-NoAuth` 之外的强化配置。

## 已知限制与后续计划

- [ ] 键盘输入与媒体键
- [ ] 多显示器与绝对定位
- [ ] TLS/DTLS 或“会话密钥 + HMAC”认证
- [ ] Windows 托盘程序与开机自启
- [ ] 多台设备同时连接
- 当前仍需人工确认 IP：自动发现可用，但多网卡 / 跨网段环境需要手动填写

## v1.2.0 Release 校验

| 文件 | 大小 | SHA-256 |
| --- | --- | --- |
| `LanMouse-debug.apk` | 42,618 B | `e7c17e9e8d43d428cf7100a70914b6f6b698d4e03dd5d6ba1de18e480951ee5f` |
| `LanMouse-Windows.zip` | 51,351 B | `94b1c9bef31f549e1aff1c37ae394ad65037262f61a21a23031afec1d713c730` |

## v1.1.0 Release 校验

| 文件 | 大小 | SHA-256 |
| --- | --- | --- |
| `LanMouse-debug.apk` | 42,967 B | `3f80688809712490aaf99681506861d1e03c6a93f0cb165c856c7fc4114e76fa` |
| `LanMouse-Windows.zip` | 52,333 B | `cc7c7f1d8c8871aa905a4b51ec2561f9fd20febc091326ee6c6d29e815502ce1` |

正式产物统一发布到 GitHub Releases；本地构建仍会在 `dist/` 生成同名 `.sha256` 文件。

## 更新日志

### 1.2.0

**Changed**

- Android：触控板（控制区域）区域放大，根布局中触控板与设置区的权重由原来的 0.44 / 0.56 调整为 0.56 / 0.44，触控板高度约增加四分之一；设置区仍可滚动，内容不受影响。
- Android：移除触控板中心的装饰圆点（原先绘制的 3dp 与 10dp 两个同心圆及其画笔），网格、按下时的跟随光晕和边框高亮保持不变。
- 清理未使用的 Android 网络状态权限、公开方法和界面字段。
- 合并重复的 Gradle 版本入口，删除未使用的 Wrapper 配置。
- 停止在 Git 中跟踪 APK、ZIP 和服务端 exe，发布产物统一由 GitHub Releases 管理。
- 合并防火墙脚本并移除功能重复的 `setup-firewall.ps1`。
- Android 新增正式签名构建配置，支持通过 `keystore.properties` 生成 Release APK。

### 1.1.0

**Added**

- Android：重做深色界面，连接表单、状态提示、按钮、灵敏度和触控板均使用统一的深色视觉体系。
- Android：新增自适应应用图标、旧版回退图标和 Android 13+ 主题图标。
- Android：新增默认开启的“震动反馈”开关；左键、右键、长按拖动和滚动分别使用系统精细触感，偏好设置会持久保存。

**Fixed**

- Android：修复部分 Wi-Fi 环境丢弃 `255.255.255.255` 广播后“搜索 Windows 设备”找不到服务端、但手工填写 IP 可以连接的问题。发现请求现在会同时发送到各网卡的子网广播地址、受限广播地址，以及当前已填写的主机；有自定义端口时也会优先探测该端口。
- Android：修复发送指令时状态栏显示“发送移动指令失败: null”的问题。原因是触控板回调运行在主线程，而 `UdpMouseClient` 在主线程调用 `DatagramSocket.send()` / `InetAddress.getByName()`，触发 `NetworkOnMainThreadException`，该异常没有 message，于是被拼成了“null”。
  - `UdpMouseClient` 新增后台发送线程 `LanMouse-Sender`，地址解析与收发全部移出主线程；指令改为写入上限 256 的队列，队列满时丢弃最旧的移动包，避免延迟累积。
  - 连接改为异步：后台解析地址并发送 `ping`，1.5 秒内收到 `pong` 才回调 `onReady`；失败回调 `onError` 并给出可读提示（令牌错误、端口不对、防火墙拦截均可区分）。
  - 异常信息兜底：`getMessage()` 为空时显示异常类名，不再出现“null”。
- Android：`TouchpadView` 被禁用时补发左键抬起，避免拖动过程中被中断导致左键一直按下。
- 构建：`android/build-debug.ps1` 不再同时设置 `ANDROID_PREFS_ROOT`，否则 AGP 8.7 会因 `AndroidLocationsException` 而构建失败。

**Distribution**

- Windows：新增 `allow-firewall.ps1` / `allow-firewall.bat`，一键添加防火墙规则；当前网络被系统归类为“公用网络”时会提示并询问是否临时放行。
- 新增 `dist/LanMouse-Windows.zip` 免安装分发包与面向使用者的中文说明。

### 1.0.0

- 首个版本：鼠标移动、左键单击 / 拖动、右键单击、滚轮，灵敏度调节，UDP 令牌认证，局域网自动发现，断线 5 秒自动释放按键。

## 许可证

[MIT](LICENSE) © 2026 Jay
