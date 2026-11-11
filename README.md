# 平板键盘 · PadKeys v1.1.0

把联想拯救者 Y900 11 寸平板（Android，TB711FU）变成**通用蓝牙键盘 + 触控板**（HID Device 角色）的 APK。
对端电脑（macOS / Windows / Linux / Android）**不需要装任何软件**，系统直接把它认成标准蓝牙键盘 + 鼠标。

- 包名：`com.liangxin.btkeyboard`
- 版本：v1.1.0（2026-11-11 构建，`build/padkeys-20261111-1024.apk`，48K）
- 作者：老梁 & AI
- minSdk 28（Android 9）/ targetSdk 34

## v1.1.0 新增（十项）

| # | 功能 | 说明 |
|---|---|---|
| 1 | 左上角电量 | `ACTION_BATTERY_CHANGED` 实时刷新，显示 `100%`，充电时带 ⚡ |
| 2 | 按键震动 | 每键按下 `VibrationEffect.createOneShot(14ms)`，无马达时回退系统触感；有开关 |
| 3 | RGB 灯效 | 每个键帽彩虹描边 + 三层外发光 + 呼吸流动；有开关 |
| 4 | 触控板 | 屏幕右侧（竖屏为下方大面积）。单指移动光标、轻点左键、双指轻点右键、双指上下滑滚轮 |
| 5 | 两个旋钮 | 尺寸 178dp（竖屏 200dp），仿铝合金音量旋钮：滚花外圈 + 放射拉丝顶盖 + 中心回车键。左旋钮顺时针=上滑、逆时针=下滑（HID wheel），中心键=回车；右旋钮暂留空 |
| 6 | 键盘位置 | 开=键盘在屏幕下方（控制区在上），关=键盘在上（控制区在下），即时重建布局并持久化 |
| 7 | 特效 | 开时键盘 RGB 关闭，全屏随机位置不断升空爆开的烟花（带拖尾/光晕），按键高亮与 Caps/fn 指示灯照常工作 |
| 8 | 竖屏适配 | 旋钮行 → 开关 2 列网格 → 状态 → 大面积触控板，键盘自动压缩到上半屏；横竖屏都即时重建 |
| 9 | 深浅两套色系 | 深色（默认）/ 浅色，键帽、机身、开关、触控板、旋钮全套配色；有开关并持久化 |
| 10 | 说明/版本/作者 | 顶栏【说明 / 关于】弹窗：版本号、作者、包名、连接步骤、键盘/触控板/旋钮/开关说明、常见问题 |

## v1.1.0 后续微调

- 旋钮整体放大到 2 倍以上并重绘（参考真实铝合金音量旋钮：滚花齿、金属顶盖斜向高光、发光指示线、中心凸起回车键）
- 所有开关改「一行 2 个」排布，整体下移，把上方空间让给两个大旋钮（横屏同步改成两列网格）
- 键帽修饰键副标首字母大写：`Control` / `Command` / `Windows` / `Option`，主标 `Fn`、`Cmd`、`Space`
- **运行时强制横屏**：Manifest 固定 `screenOrientation="landscape"`，不再跟随平板姿态，竖屏适配代码下线
- **键盘上方区域三栏重构**：中间 40% 为触控板（居中）并在其下方新增【左键】【右键】实体按钮；左右各 30%，左侧放大旋钮① + 震动/RGB/键盘位置开关 + 状态，右侧放大旋钮② + 特效/主题开关
- 【键盘位置】切换时，键盘与整个控制区（旋钮、开关、触控板、左右键、状态）整体互换上下位置
- 键盘上方区域改为 **25% / 50% / 25%**：中间 50% 是居中触控板，其下是【左键】【右键】；左侧 25% 放一切功能开关（震动/RGB/键盘位置/特效/主题）+ 旋钮① + 状态，右侧 25% 只放旋钮②
- **触控板支持按住拖动**：触控板单指按住后拖动 = 左键按下拖动；按住【左键】按钮再滑动触控板同样有效 —— 即"按住左键滑动选中文字"
- **触控板三指手势**：三指上/下/左/右滑 → 系统手势。因 BLE HID 鼠标报告只能表达单点，这里用键盘快捷键等效实现：
  macOS → `Ctrl+↑` Mission Control、`Ctrl+↓` App Exposé、`Ctrl+←/→` 切换桌面；
  Windows → `Win+Tab` 任务视图、`Win+D` 显示桌面、`Win+Ctrl+←/→` 虚拟桌面

## 基础能力（v1.0 已交付）

| 能力 | 状态 |
|---|---|
| 蓝牙 HID 键盘（65% 布局 6 行，键位对齐原装键盘保护套） | 已完成 |
| macOS / Windows 两种布局切换（cmd↔Win、Option↔Alt），持久化 | 已完成 |
| F1~F12 主功能键 + fn 层多媒体副功能（亮度/音量/静音/播放/锁屏/调度中心等） | 已完成 |
| 修饰键「点一下锁存」：不按住也能做 Shift+A、⌘+Space 这类组合 | 已完成 |
| Caps Lock + 主机 LED 状态回传（平板侧显示绿点） | 已完成 |
| HID 鼠标（ID3，5 键 + dx/dy/wheel） | v1.1 起由触控板与旋钮驱动 |


## 正式版与源码包（v1.1.0）

| 产物 | 说明 |
|---|---|
| `build/PadKeys-v1.1.0.apk` | **正式签名**（`keys/padkeys-release.jks`，alias `padkeys`，口令 `padkeys2026`），versionCode 11、versionName 1.1.0，153KB，可直接分发到其他 Android 设备安装 |
| `PadKeys-源码-20261111.zip` | 完整源码（src / res / build.sh / AndroidManifest.xml / README / 开发记录），解压后 `./build.sh` 即可复现构建 |
| `PadKeys-开发记录.md` | 面向开发的完整记录：需求演进、架构、HID 关键数值、构建签名、真机验证方法、踩坑清单 |
| `app-icon.png` | 应用图标原图（512×512），已生成 mdpi～xxxhdpi 五套 mipmap |

> **签名变更提醒**：正式签名与早期 debug 签名互斥，已装 debug 版的机器必须先 `adb uninstall com.liangxin.btkeyboard` 再装正式版。
> 安装后首次启动请让 App 自己弹权限窗并点「允许」；若用 adb 批量授权，还需要 `appops set --uid … BLUETOOTH_CONNECT allow`，否则蓝牙 HID 会注册失败（详见开发记录「踩坑 3」）。

## 构建（无需 Android Studio / Gradle）

Mac 上只要有 JDK17 + Android SDK（platforms/android-34 + build-tools/34.0.0）即可：

```bash
cd bt-keyboard
./build.sh          # 产出 build/padkeys-<时间戳>.apk
```

流水线：`aapt2 compile` → `aapt2 link`（生成 R.java）→ `javac`（-classpath android.jar）→ classes 打 jar →
`d8 --release --min-api 28 --lib android.jar` → 把 classes.dex 塞进 base.apk → `zipalign` → `apksigner`（debug.keystore）。

安装（重装后必须重打权限，否则蓝牙发现会被静默拒绝）：

```bash
adb install -r build/padkeys-*.apk
for p in BLUETOOTH_CONNECT BLUETOOTH_SCAN BLUETOOTH_ADVERTISE ACCESS_FINE_LOCATION; do
  adb shell pm grant com.liangxin.btkeyboard android.permission.$p
done
```

## 连接步骤（第一次）

1. 平板打开 App → 点【可被发现】（允许 300 秒）。
2. 点【连接电脑】→ 列表里点【开始扫描附近的电脑】。
3. 电脑端：打开蓝牙，保持"蓝牙设置"窗口可见（macOS 只有在这个窗口打开时才对外可被发现）。
4. 列表里会出现带 **· 电脑** 标记的设备，点它 → 电脑上点「允许/配对」。
5. 配对成功会自动连接，界面变成「已连接：xxx」。之后每次只需点【连接电脑】→ 点那台电脑。

列表只显示**有名字**的设备，并按「已配对 → 电脑 → 其他」排序；没有名称的一律隐藏（勾选「显示未命名的设备」才会出现），避免看到一堆 MAC 地址。


## v1.1.0 按键音 + 屏幕视觉反馈（第 8 条需求）

本机没有振动马达（见「已知问题」），所以把「按下去有感觉」改成两条不依赖马达的反馈，共用一个开关：

| 项 | 说明 |
|---|---|
| 开关 | 顶栏 `主题` 之后的 **`反馈 开/关`**，默认**开启**，状态存 SharedPreferences 的 `feedback`（关闭后可再点开关关掉） |
| 按键音 | `SoundPool` 播放 `res/raw/key_tap.wav`（18ms 自合成点击声：噪声脉冲 + 1.45kHz 成分 + 指数衰减），`USAGE_ASSISTANCE_SONIFICATION`，音量 0.42，最多 4 条并发流 |
| 视觉反馈 | 被按下的键在 150ms 内轻微内缩（最多 3.2dp）并叠一层白光（alpha 95→0）后回弹，`postInvalidateOnAnimation()` 驱动重绘 |
| 与震动的区别 | 震动开关走系统 haptic（本机无效）；反馈开关是 App 自己出声 + 自绘动画，任何设备都能用 |

## v1.1.0 顶栏重构（第 7 条需求）

布局改成「顶上一行控制 + 左右两个大件」：

| 位置 | 内容 |
|---|---|
| 顶栏一行 | 电量 `100% ⚡`（App 名 `PadKeys` 只在「说明 / 关于」里显示，不再占顶栏）、**震动 / RGB / 键盘位置 / 特效 / 主题 / 反馈** 六个开关、连接状态（`已连接：Kent's Mac mini4`，点一下=重新选电脑）、`布局：macOS`、`可被发现`、`说明 / 关于` | 右侧原来那颗重复的「已连接」pill 已删除（连接状态只在左侧状态块显示，点它仍然可以换电脑）
| 左 25% | 一个放大到 267dp 的铝合金大旋钮（旋转=滚动、按中心=回车），垂直居中；「滚动 · 回车」说明字直接写在旋钮盘面下半部，盘面因此可以占满整块 267dp |
| 中 50% | 触控板 + 下方【左键】【右键】 |
| 右 25% | 一个 267dp 的「语音输入」大按钮（按住说话、松开结束），垂直居中 |

- 第二个旋钮已删除，原位置改为语音输入按钮。
- 顶栏开关文案压成 `震动 开/关`、`键盘 上/下`、`主题 深/浅` 这种短标签，才放得进一行。
- 语音按钮是自绘 View（`VoiceButton.java`）：径向渐变的磨砂盘面 + 顶部高光弧 + 底部暗影 + 细亮边，忽略态是深色磨砂、按住时整盘变亮青并向外扩散呼吸圈，配现代麦克风描边图标与「按住说话 / 语音输入」两行字。按下会调系统 `SpeechRecognizer`，识别结果按字符逐个用 HID 键盘打到电脑（英文、数字、半角符号已实现；中文受 HID 只能发键码的限制，暂不上屏，待定方案）。

截图：`screenshot-v11-topbar.png`（深色）、`screenshot-v11-voice.png`（按住态）、`screenshot-v11-light-voice.png`（浅色）。

## 实测记录

- **键位自检（自动化）**：标准 HID usage 表逐项比对，`124/124 通过`（字母、数字、Enter/Esc/BS/Tab/Space、`- = [ ] \ ; ' \` , . /`、F1~F12，mac 与 windows 两套）。
- **端到端打字（Mac / TextEdit）**：H/E/L/L/O → 文稿出现 `Hello`；Shift 锁存 + A → `A`；⌘+V 粘贴、⌘+Tab 切换应用均通过。
- **多媒体键**：fn+F12 音量 +6%、fn+F11 音量 -6%、fn+F10 静音（`output muted:true`）。
- **触控板（v1.1）**：平板滑动一次，Mac 光标位置从 `(1969, 951)` 移到 `(2560, 1263)` —— 真机实测 HID 鼠标生效。
- **电量（v1.1）**：界面 `100%`，`dumpsys battery level=100 scale=100` 一致。
- **十项 UI（v1.1）**：横屏深色 / 横屏浅色 / RGB 全键彩虹 / 烟花特效 / 键盘位置切换 / 竖屏 2560×3840 全部截图核对通过。
- **按键音 + 视觉反馈（v1.1，第 8 条）**：把动画时长临时拉到 1200ms 抓帧，确认按下键确实内缩并发白（正常 150ms 太快，`screencap` 抓不到）；`dumpsys audio` 里能看到本 App 的 `type=android.media.SoundPool usage=USAGE_ASSISTANCE_SONIFICATION` 播放器已注册，无 AudioTrack 报错；切主题、切键盘位置重建界面后开关状态都能保持。
- **顶栏重构（v1.1，第 7 条）**：真机截图核对——顶栏五个开关与连接状态同排显示正常、左旋钮 267dp 居中、右语音按钮按住态变亮青并显示「松开结束」，`uiautomator` 也确认点击区域正常。
- **语音识别（v1.1）**：按住按钮时系统识别服务（`LeRecognitionService`）确实被拉起（`logcat` 有 `onStartListening/onStopListening`），松手后有兜底提示；识别文本→HID 逐字符发送的代码路径已用日志确认会连续 `send id=1`，但中文上屏还没有可行方案。

## 已知问题 / 处理办法

- **本机（TB711FU）没有振动马达**：`dumpsys vibrator_manager` 里 `vibratorIds = []`、`Vibrators:` 段为空，`cmd vibrator` 直接报 `Can't find service: vibrator`，系统会把所有震动请求判为 `ignored_unsupported`（连系统的按键触感也一样）。所以 App 启动时会自动检测，没马达就把顶栏开关显示成灰色的「震动 无马达」并不允许点击，不会让人误以为功能坏了。
- 若「界面显示已连接但电脑收不到按键」：多为 Android 蓝牙栈 HID 会话卡住。平板下拉开关一次蓝牙，或 App 内【已连接】→「断开当前连接」重连（App 已有 7 秒自动重连，最多 3 次）。
- macOS「蓝牙设置」窗口关闭后电脑不再可被发现，重新配对时需重新打开。
- 这台平板的系统自动旋转在接键盘保护套时会被锁定；想用竖屏请确认系统「自动旋转」已开启（本 App 本身 `fullSensor`，横竖屏都会自动重排）。
- 浅色主题下 fn 副标、shift 上标等小字对比度偏低（可接受，后续可调）。

## 后续可做

- 语音输入的中文上屏方案（拼音表 / 电脑端接收器，待定）
- 旋钮增加更多动作（缩放 / 亮度 / 滚动步长切换等）
- 换皮肤（键帽材质）、更多特效主题
- Windows / Linux / Android 主机的连接与键位回归测试
