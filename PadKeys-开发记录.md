# PadKeys 开发记录

> 把联想拯救者 Y900 11 寸平板（TB711FU）变成蓝牙 HID 键盘 / 触控板 / 旋钮的 Android 应用。
> 作者：**老梁 & AI**　版本：**v1.1.0**（versionCode 11）　构建日期：2026-11-11

---

## 一、项目概览

| 项 | 内容 |
|---|---|
| 应用名 | PadKeys（桌面显示「平板键盘」） |
| 包名 | `com.liangxin.btkeyboard` |
| 工程路径 | `/Users/lianxin/Doubao/chats/2026-11-11/new-chat/bt-keyboard` |
| 目标设备 | 联想拯救者 Y900 11 寸平板，型号 TB711FU，adb serial `HA2JD201` |
| 屏幕 | 3840×2560 横屏，density 480（override 408），屏宽约 1506dp |
| 技术栈 | 纯 Java + 原生 View 自绘，**不用 Gradle / Android Studio**，全靠命令行离线构建 |
| 体量 | 主代码约 1300 行（MainActivity）+ 5 个自定义 View，正式 APK 153KB |
| 系统要求 | minSdk 28 / targetSdk 34，强制横屏 `landscape` |

### 它做什么

平板和电脑配对后，自己充当一个**蓝牙 HID 复合设备**（键盘 + 媒体键 + 鼠标），在平板上敲的键直接出现在电脑里；同时屏幕上还有一块触控板、一颗大旋钮（滚动/回车）、一颗语音按钮，以及一堆外观开关（颜色、特效、按键音等）。

---

## 二、需求演进（按用户提出的顺序）

### 1. 做一块能打字的虚拟键盘
- 65% 布局、6 行，键位对照实体键盘保护套（用户提供的实物照片 `IMG_3958.jpg`）逐键还原。
- F 键带中文副标、Q/W/E 下带 Android/Windows/iOS 系统彩标。
- 修饰键（Shift/Ctrl/Alt/Cmd/Fn）**轻点锁存**，可与任意字母组合，打完自动释放。
- 键帽副标首字母大写（用户明确要求）。

### 2. 蓝牙 HID 连接电脑
- 复合描述符三个 Report ID：ID1 键盘（8 字节）、ID2 Consumer 媒体键（2 字节）、ID3 鼠标（4 字节）。
- 连接重试看门狗（7 秒一次、最多 3 次），`HidBridge` 统一封装 `sendKeyboard` / `sendConsumer` / `sendMouse`。
- 设备列表排序：已配对 → 电脑 → 其他，隐藏无名设备。
- 真机验证：Mac TextEdit 收到 `z`，日志 `send id=1 data=AAAdAAAAAAA=`。

### 3. 早期十项功能
电量显示 / 震动（后发现本机无马达）/ RGB 灯效 / 触控板 / 双旋钮 / 键盘上下位置 / 烟花特效（原方案是金鱼，用户否决）/ 深色浅色主题 / 说明关于弹窗 / 布局 mac↔Windows 切换。

### 4. 外观微调
- 强制横屏（不再考虑平板姿态）。
- 三栏比例 30/40/30 → **25/50/25**。
- 旋钮重绘、开关布局收拢、说明字进旋钮盘面。

### 5. 三栏重排
- 中间 50% 给触控板，触控板居中，下方加「左键 / 右键」两枚按键。
- 左右各 25% 放旋钮与功能开关。

### 6. 触控板能力
- 单指移动、点击左键、按住左键拖选（拖动时保持左键按下 → 选中文字）。
- 双指滚动；三指手势：上滑 = Mission Control（keycode ↑0x52）、下滑 = 应用切换（↓0x51）、左右滑 = 桌面切换（←0x50 / →0x4F）。
- **限制**：三指手势与拖选的端到端效果无法用 adb 自动验证（`input swipe` 模拟多指不可靠），只能人工确认。

### 7. 顶栏收拢 + 左右大件（第一版大改）
- 所有开关收进顶栏一行，左侧状态块（`已连接：xxx` + 提示语，点一下换电脑）。
- 左侧 267dp 铝合金大旋钮（旋转 = 滚动，按中心 = 回车）。
- 右侧 267dp 语音按钮，按住说话。
- 删除右侧重复的「已连接」pill。

### 8. 按键音 + 屏幕内视觉反馈（当前版本）
本机没有振动马达（证据见「踩坑」），所以改用两条不依赖马达的反馈，共用一个开关：

| 项 | 内容 |
|---|---|
| 开关 | 顶栏 `主题` 之后的 **`反馈 开/关`**，**默认开启**，存 SharedPreferences 的 `feedback` |
| 按键音 | `SoundPool` 播 `res/raw/key_tap.wav`（自合成 18ms 点击声：噪声脉冲 + 1.45kHz 成分 + 指数衰减），`USAGE_ASSISTANCE_SONIFICATION`，音量 0.42，最多 4 条并发流 |
| 视觉反馈 | 按下的键 150ms 内轻微内缩（最多 3.2dp）+ 白光（alpha 95→0）回弹，`postInvalidateOnAnimation()` 驱动 |
| 震动降级 | 启动时检测 `VibratorManager.getVibratorIds()`，无马达则顶栏显示灰色「震动 无马达」且不可点 |

### 其他顶栏调整
- App 名 `PadKeys` 从顶栏移除，只在「说明 / 关于」里出现。
- 默认值：主题 = **深色**、反馈 = **开**、键盘位置 = **在上**。

---

## 三、技术架构

### 文件职责

| 文件 | 职责 |
|---|---|
| `MainActivity.java`（~1300 行） | 全部 UI 装配（`buildUi()`）、顶栏开关、状态块、对话框、语音识别、按键反馈调度、SharedPreferences |
| `KeyboardView.java` | 65% 键盘自绘：键位表、按下集合、RGB 灯效、按键音/动画、HID 上报 |
| `TouchpadView.java` | 触控板：单指移动、拖选（按住左键保持）、双指滚动、三指手势 |
| `KnobView.java` | 铝合金旋钮：滚花外圈、径向渐变盘面、中心回车键、说明字画在盘内下半部 |
| `VoiceButton.java` | 磨砂玻璃语音按钮：径向渐变 + 高光/暗影弧 + 弧线麦克风图标 + 按住呼吸圈 |
| `FireworksView.java` | 烟花特效 |
| `Theme.java` | 深/浅两套配色（keyCap、keyMod、keyPressed、zoneBg、textMain、textSub…） |
| `Layouts.java` | 65% 键位表定义 |
| `HidBridge.java` | 蓝牙 HID 描述符、`registerApp`、`sendKeyboard/sendConsumer/sendMouse`、连接状态回调 |

### HID 关键数值

- 键盘 Report ID 1，8 字节：`[modifiers, reserved, k1..k6]`
- Consumer Report ID 2，2 字节；鼠标 Report ID 3，4 字节（buttons, dx, dy, wheel）
- Modifier 位：左 Ctrl `0x01`、左 Shift `0x02`、Win/GUI `0x08`
- 常用 usage：Enter `0x28`、Tab `0x2B`、Esc `0x29`、D `0x07`
- Consumer：静音 `0xE2`、音量加 `0xE9`、音量减 `0xEA`、亮度减 `0x70`、亮度加 `0x6F`
- 三指手势用到的 keycode：↑ `0x52`、↓ `0x51`、← `0x50`、→ `0x4F`

### SharedPreferences（`getPreferences(MODE_PRIVATE)`）

`mac_mode` / `light` / `vib` / `rgb` / `kb_bottom` / `fx` / `feedback`

默认值：`light=false`（深色）、`feedback=true`（开）、`kb_bottom=false`（键盘在上）、`mac_mode=true`。

---

## 四、构建与签名

### 构建流水线（`./build.sh`，无 Gradle）

1. `aapt2 compile --dir res` → `res.zip`
2. `aapt2 link`（`--java build/gen` 生成 `R.java`）
3. `javac`（classpath = `android.jar` + `libs/*.jar`）
4. `d8 --release --min-api 28`
5. `zipalign -f -p 4`
6. `apksigner`

环境：`JAVA_HOME=/opt/homebrew/opt/openjdk@17`，SDK `~/Library/Android/sdk`，build-tools 34.0.0，`platforms/android-34/android.jar`。

### 签名

- 存在 `keys/padkeys-release.jks` 时走**正式签名**，产出 `build/PadKeys-v1.1.0.apk`；
- 否则退回系统 debug keystore，产出 `build/padkeys-<yyyyMMdd-HHmm>.apk`（开发用）。
- 正式签名口令：store/key 密码均为 `padkeys2026`，alias `padkeys`，证书 DN `CN=PadKeys, OU=LaoLiang and AI`。
- **注意**：换了签名后与旧 debug 版互斥，必须先卸载旧版再装新版（`adb uninstall com.liangxin.btkeyboard`）。

### 装到真机

```bash
adb install -r build/PadKeys-v1.1.0.apk
adb shell am force-stop com.liangxin.btkeyboard
adb shell am start -n com.liangxin.btkeyboard/.MainActivity
```

权限（正常流程是 App 自己弹窗请求，用户点允许；adb 批量授权见「踩坑 5」）：

`BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` / `BLUETOOTH_ADVERTISE` / `ACCESS_FINE_LOCATION` / `RECORD_AUDIO`

---

## 五、真机验证方法（可复用）

- **截图核对**：`adb shell screencap -p /sdcard/x.png` + `adb pull` + `sips -Z 1200` 缩放后查看。
- **控件坐标**：`adb shell uiautomator dump` + 正则 `text="…"[^>]*bounds="…"` 取精确中心，粗估坐标会点到隔壁。
- **点击开关**：`adb shell input swipe x y x y 80`（80ms 按压）比 `input tap` 稳定；`input tap` 偶发被丢弃。
- **抓短动画中间帧**：把动画时长临时拉长（如 150ms → 1200ms），抓帧后再改回。
- **验证 HID 是否真的发键**：`adb logcat -s HidBridge:*` 看 `send id=1 data=…`；电脑端用 AX 读 TextEdit 的「文本输入区 Value」。
- **验证 App 图标**：`am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:…` 看应用信息页图标。

---

## 六、踩坑与解决

1. **HID 会话卡死**（App 显示已连接但电脑收不到键）
   判据：Mac 上 `hidutil list --matching '{"Transport":"Bluetooth"}'` 仍能看到 `UsagePage 1 / Usage 6`。
   恢复：平板 `svc bluetooth disable/enable` → Mac `blueutil --power 0/1` → `am force-stop` 重启 App。
   经验文档：`experience/android-hid-device-stuck-connection.md`。

2. **本机没有振动马达**
   `dumpsys vibrator_manager` 里 `vibratorIds = []`、`Vibrators:` 段为空，`cmd vibrator` 报 `Can't find service: vibrator`，所有 haptic 请求被判 `ignored_unsupported`。
   处理：App 启动自动检测，无马达就把开关显示成灰色「震动 无马达」并禁用；另做按键音 + 视觉反馈替代。

3. **adb 授权后 HID 注册仍失败**
   日志：`BluetoothUtil: Need android.permission.BLUETOOTH_CONNECT permission … HidDeviceServiceBinder`。
   原因：`pm grant` 只改权限位不改 **AppOps**，蓝牙服务按 AppOps 判 `ignore`。
   解决：`adb shell appops set --uid com.liangxin.btkeyboard BLUETOOTH_CONNECT allow`（SCAN / ADVERTISE 同理）。让 App 自己弹权限窗则不会踩这个坑。

4. **`sips` 输出的 PNG 其实是 JPEG**
   `sips -Z 512 in.jpg --out out.png` 只改扩展名不改编码 → `aapt2` 报 `failed to read PNG signature`。
   解决：显式 `sips -s format png -Z 512 …`。

5. **`pm grant` 连续执行偶发不生效**
   安装后立刻批量 grant 可能落空，需逐条执行并 `dumpsys package` 复核 `granted=true`。

6. **`sips -c h w --cropOffset Y X` 偏移语义反直觉**
   容易裁到隔壁区域（曾裁成键盘区）。裁图后必须回看确认。

7. **其他验证手段不可用**（记录备查）
   `adb shell input swipe` 模拟拖选/三指不可靠；AppleScript 读 TextEdit 反复 `-1712`；Mac `screencapture -x` 报 `could not create image from display`；`blueutil --pair` 挂起、`--discoverable 1` 失败、`--unpair` 无效。

---

## 七、已知问题 / 待办

- **语音输入中文上屏未解决**：HID 键盘只能发键码，汉字无法直出。方案三选一（内置常用汉字拼音表 / 电脑端接收器 / 只做语音转英文）等用户拍板；UI 与识别链路已就绪。
- 三指手势、拖选的效果仍需人工确认（adb 无法自动验证）。
- 早期回归中 `\` 键有一次未到达 Mac，未定位。
- 语音按钮目前仅支持按住录音 → 识别 → 英文/ASCII 上屏。

---

## 八、版本序列（第 11 月 11 日）

`1101 / 1102 / 1104 / 1105 / 1106 / 1109 / 1121 / 1139 / 1140 / 1141 / 1142 / 1143(调试) / 1144(调试) / 1145 / 1147 / 1150 / 1218 / 1220 / 1221 / 1224 / 1226 / 1231 / 1233`
→ 正式版 **`build/PadKeys-v1.1.0.apk`**（正式签名，versionCode 11，153KB）

工程内验收截图：`screenshot-v11-topbar.png`（整体深色）、`screenshot-v11-knob.png`（旋钮近看）、`screenshot-v11-feedback.png`（按键视觉反馈抓帧）、`screenshot-v11-nomotor.png`（无马达降级）、`screenshot-v11-voice.png`、`screenshot-v11-light-voice.png`、`screenshot-v11-kbup.png`、`screenshot-v11-rgb.png`、`screenshot-v11-fireworks.png`、`screenshot-v11-help.png`。

---

*本文档由老梁 & AI 维护，与 README 互为补充：README 面向使用者，本文档面向开发与调试。*
