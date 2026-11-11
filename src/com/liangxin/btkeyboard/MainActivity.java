package com.liangxin.btkeyboard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements HidBridge.Listener, KeyboardView.Callback {

    private static final int REQ_PERMS = 100;
    private static final int REQ_DISCOVERABLE = 101;

    private HidBridge hid;
    private KeyboardView keyboard;
    private TextView statusBig;
    private TextView hintText;
    private TextView macButton;
    private TextView connectButton;

    private boolean macMode = true;

    // ---- 主题 / 开关状态 ----
    private static final String APP_VERSION = "1.1.0";
    private static final String APP_AUTHOR = "老梁&AI";
    private static final String BUILD_DATE = "2026-11-11";
    private Theme theme = Theme.dark();
    private boolean lightTheme = false;
    private int forcePortrait = -1;   // 调试用：-1 跟随系统，1 强制竖屏，0 强制横屏
    private boolean vibOn = true;
    private boolean rgbOn = false;
    private boolean kbBottom = false;   // 默认键盘在上
    private boolean fxOn = false;

    private LinearLayout root;
    private FrameLayout outer;
    private TouchpadView touchpad;
    private FireworksView fxView;
    private TextView batteryText;
    private TextView vibBtn, rgbBtn, posBtn, fxBtn, themeBtn, feedbackBtn;
    private boolean feedbackOn;   // 按键音 + 屏幕视觉反馈（默认关）
    private String lastStatus = "正在初始化蓝牙 HID 服务…";
    private String lastHint = "";
    private int batteryPct = -1;
    private boolean batteryCharging = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int level = intent.getIntExtra("level", -1);
            int scale = intent.getIntExtra("scale", 100);
            if (level >= 0) {
                batteryPct = (int) Math.round(level * 100.0 / Math.max(1, scale));
            }
            int status = intent.getIntExtra("status", 0);
            batteryCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            updateBatteryText();
        }
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int mouseHeld = 0;   // 当前按住的鼠标键位（1=左,2=右）
    private boolean hapticAvailable = true;   // 本机是否有振动马达
    private VoiceButton voiceButton;
    private android.speech.SpeechRecognizer speech;
    private boolean voiceListening;
    private String typeBuf = "";
    private int typeIdx;

    private boolean linked = false;
    private BluetoothDevice pendingHost;
    private int connectRetry = 0;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable connectWatchdog = new Runnable() {
        @Override
        public void run() {
            if (linked || pendingHost == null) return;
            if (connectRetry >= 2) {
                setStatus("连接失败，请重试");
                setHint("再点一次【连接电脑】重新连接；若反复失败，可在系统设置里关掉再打开蓝牙");
                return;
            }
            connectRetry++;
            setStatus("正在连接（第 " + (connectRetry + 1) + " 次）…");
            attemptConnect();
        }
    };
    private final ArrayList<BluetoothDevice> devicePool = new ArrayList<>();
    private boolean showUnnamed = false;
    private final ArrayList<BluetoothDevice> foundDevices = new ArrayList<>();
    private final ArrayList<String> foundNames = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;
    private AlertDialog hostDialog;
    private boolean receiverRegistered = false;
    private String lastRegisterMsg = "";

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
        setStatus(lastStatus);
        setHint(lastHint);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Throwable ignored) {
        }
        unregisterReceiverSafe();
        if (hid != null) hid.stop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        macMode = getPreferences(MODE_PRIVATE).getBoolean("mac_mode", true);
        lightTheme = getPreferences(MODE_PRIVATE).getBoolean("light", false);
        vibOn = getPreferences(MODE_PRIVATE).getBoolean("vib", true);
        rgbOn = getPreferences(MODE_PRIVATE).getBoolean("rgb", false);
        kbBottom = getPreferences(MODE_PRIVATE).getBoolean("kb_bottom", false);   // 默认键盘在上
        fxOn = getPreferences(MODE_PRIVATE).getBoolean("fx", false);
        feedbackOn = getPreferences(MODE_PRIVATE).getBoolean("feedback", true);   // 默认开
        theme = Theme.get(lightTheme);
        hapticAvailable = detectHaptics();
        if (getIntent() != null && getIntent().hasExtra("portrait")) {
            forcePortrait = getIntent().getIntExtra("portrait", -1);
            if (forcePortrait == 1) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else if (forcePortrait == 0) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        buildUi();
        hideSystemUi();

        hid = new HidBridge(this);
        hid.setListener(this);

        if (hasBluetoothPermissions()) {
            hid.start();
        } else {
            requestPermissions();
        }
    }

    // ---------------- UI ----------------

    private void buildUi() {
        boolean portrait = false;   // 运行时强制横屏，不考虑平板姿态

        statusBig = null;
        hintText = null;
        batteryText = null;
        createSwitches();

        FrameLayout outer = new FrameLayout(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.rootBg);

        // ---------- 顶部条：左上角电量 + 右侧连接相关 ----------
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        topBar.setBackgroundColor(theme.topBarBg);

        batteryText = new TextView(this);
        batteryText.setTextSize(16);
        batteryText.setTextColor(theme.textMain);
        batteryText.setGravity(Gravity.CENTER_VERTICAL);
        batteryText.setPadding(dp(4), dp(3), dp(12), dp(3));
        batteryText.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        topBar.addView(batteryText);
        updateBatteryText();

        // App 名字只在「说明 / 关于」里显示，顶栏不再重复

        // ---- 所有功能开关收进顶栏一行
        TextView[] headSwitches = {vibBtn, rgbBtn, posBtn, fxBtn, themeBtn, feedbackBtn};
        for (TextView sw : headSwitches) {
            LinearLayout.LayoutParams swlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            swlp.rightMargin = dp(6);
            topBar.addView(sw, swlp);
        }

        // ---- 连接状态（点一下重新选电脑）
        LinearLayout statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        statusBox.setGravity(Gravity.CENTER_VERTICAL);
        statusBox.setPadding(dp(10), 0, dp(6), 0);

        statusBig = new TextView(this);
        statusBig.setText(lastStatus);
        statusBig.setTextColor(theme.textMain);
        statusBig.setTextSize(13);
        statusBig.setSingleLine(true);
        statusBig.setEllipsize(android.text.TextUtils.TruncateAt.END);
        statusBox.addView(statusBig, new LinearLayout.LayoutParams(
                dp(330), ViewGroup.LayoutParams.WRAP_CONTENT));

        hintText = new TextView(this);
        hintText.setText(lastHint);
        hintText.setTextColor(theme.textSub);
        hintText.setTextSize(10);
        hintText.setSingleLine(true);
        hintText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        statusBox.addView(hintText, new LinearLayout.LayoutParams(
                dp(330), ViewGroup.LayoutParams.WRAP_CONTENT));

        statusBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHostDialog();
            }
        });
        topBar.addView(statusBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        macButton = pill(macMode ? "布局：macOS" : "布局：Windows");
        macButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                macMode = !macMode;
                getPreferences(MODE_PRIVATE).edit().putBoolean("mac_mode", macMode).apply();
                keyboard.setMacMode(macMode);
                if (touchpad != null) touchpad.setPlatformMac(macMode);
                macButton.setText(macMode ? "布局：macOS" : "布局：Windows");
            }
        });
        topBar.addView(macButton);

        // 「已连接」尾注已并入左侧状态块，这里不再重复显示

        TextView discoverButton = pill("可被发现");
        discoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeDiscoverable();
            }
        });
        topBar.addView(discoverButton);

        TextView helpButton = pill("说明 / 关于");
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAboutDialog();
            }
        });
        topBar.addView(helpButton);

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---------- 控制区 + 触控板 ----------

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        content.setPadding(dp(8), dp(6), dp(8), dp(6));
        content.setBackgroundColor(theme.zoneBg);

        // 左 30%：大旋钮 + 开关（震动 / RGB / 键盘位置）+ 状态
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        left.addView(buildKnob(0), new LinearLayout.LayoutParams(dp(267), dp(267)));

        // 中间 40%：触控板居中 + 下方左右键
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(8), 0, dp(8), 0);
        touchpad = buildTouchpad();
        center.addView(touchpad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        center.addView(buildClickRow(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        // 右 30%：第二个大旋钮 + 开关（特效 / 主题）
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(6), 0, 0, 0);
        right.setGravity(Gravity.CENTER);
        voiceButton = new VoiceButton(this);
        voiceButton.setTheme(theme);
        voiceButton.setListener(new VoiceButton.Listener() {
            @Override
            public void onHoldStart() {
                startVoiceInput();
            }

            @Override
            public void onHoldEnd() {
                stopVoiceInput();
            }
        });
        right.addView(voiceButton, new LinearLayout.LayoutParams(dp(267), dp(267)));

        content.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.25f));
        content.addView(center, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.50f));
        content.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.25f));

        // ---------- 键盘 ----------
        keyboard = new KeyboardView(this);
        keyboard.setCallback(this);
        keyboard.setMacMode(macMode);
        keyboard.setTheme(theme);
        keyboard.setMaxHeightFraction(portrait ? 0.50f : 0.54f);
        keyboard.setHapticEnabled(vibOn);
        keyboard.setRgbEnabled(rgbOn && !fxOn);

        LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams zoneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);

        if (kbBottom) {
            root.addView(content, zoneLp);
            root.addView(keyboard, kbLp);
        } else {
            root.addView(keyboard, kbLp);
            root.addView(content, zoneLp);
        }

        outer.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------- 全屏金鱼特效 ----------
        fxView = new FireworksView(this);
        outer.addView(fxView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fxView.setRunning(fxOn);

        setContentView(outer);
        applyToggleStyles();
        hideSystemUi();
    }


    // ---------------- 使用说明 / 关于 ----------------

    private void showAboutDialog() {
        String text =
                "PadKeys 平板蓝牙键盘\n" +
                "版本 v" + APP_VERSION + "（" + BUILD_DATE + " 构建）\n" +
                "作者：" + APP_AUTHOR + "\n" +
                "包名：" + getPackageName() + "\n\n" +
                "【连接电脑】\n" +
                "1. 点右上角【可被发现】（300 秒）\n" +
                "2. 点【连接电脑】→ 列表里点【开始扫描附近的电脑】\n" +
                "3. 电脑上打开“蓝牙设置”窗口（保持可见才会被扫到）\n" +
                "4. 列表里带“· 电脑”标记的那台就是你的电脑，点它配对\n" +
                "5. 配对成功后自动连接，之后再连只需点【连接电脑】→ 点那台电脑\n\n" +
                "【键盘用法】\n" +
                "· 轻点 Shift / ⌘ / Alt / fn 会“锁存”到下一个键，再点一下取消——平板上不用按住也能打组合键\n" +
                "· fn + F1~F12 = 亮度 / 播放 / 音量 / 锁屏等（副标就是功能）\n" +
                "· 右上【布局】切换 macOS（cmd/Option）与 Windows（Win/Alt）\n\n" +
                "【触控板】\n" +
                "· 单指滑动 = 移动光标；轻点 = 左键；双指轻点 = 右键\n" +
                "· 按住【左键】按钮再滑动 = 拖动 / 选中文字（触控板单指按住拖同样有效）\n" +
                "· 双指上下滑 = 滚轮\n" +
                "· 三指上/下/左/右滑 = 系统手势：\n" +
                "    macOS → Ctrl+↑ Mission Control、Ctrl+↓ App Exposé、Ctrl+←/→ 切换桌面\n" +
                "    Windows → Win+Tab 任务视图、Win+D 桌面、Win+Ctrl+←/→ 虚拟桌面\n\n" +
                "【两个旋钮】\n" +
                "· 左旋钮：顺时针 = 上滑，逆时针 = 下滑；按中心 = 回车\n" +
                "· 右旋钮：功能暂未分配\n\n" +
                "【开关】\n" +
                "· 按键震动：按键盘时振动反馈（可按需关闭）\n" +
                "· RGB 灯效：键帽彩虹流光，可随时开关\n" +
                "· 键盘位置：开 = 键盘在屏幕下方（触控板在上）；关 = 键盘在上，触控板在下\n" +
                "· 特效：开启后键盘灯效关闭，屏幕随机位置不断炸开烟花；按键高亮与指示灯仍正常\n" +
                "· 主题：深色 / 浅色两套配色\n\n" +
                "【常见问题】\n" +
                "· 界面显示“已连接”但电脑收不到按键：多半是蓝牙 HID 会话卡住。下拉开关一次平板蓝牙，或点【已连接】→【断开当前连接】再重连。\n" +
                "· 扫描不到电脑：确认电脑蓝牙已开，并且“蓝牙设置”窗口处于打开状态。\n" +
                "· 竖屏/横屏都会自动重排界面。";

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFFE8EAED);
        tv.setLineSpacing(dp(3), 1f);
        tv.setPadding(dp(18), dp(14), dp(18), dp(6));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF14171C);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("使用说明 / 关于")
                .setView(sv)
                .setPositiveButton("知道了", null)
                .show();
    }

    // ---------------- 旋钮 ----------------

    private KnobView buildKnob(int idx) {
        KnobView k = new KnobView(this);
        k.setTheme(theme);
        if (idx == 0) {
            k.setCaption("滚动 · 回车");
            k.setCenterIcon(true);
            k.setListener(new KnobView.Listener() {
                @Override
                public void onRotate(int direction) {
                    if (hid != null) hid.sendMouse(0, 0, 0, direction);
                }

                @Override
                public void onPress() {
                    pressEnter();
                }
            });
        } else {
            k.setCaption("备用");
            k.setListener(new KnobView.Listener() {
                @Override
                public void onRotate(int direction) {
                    // 第二个旋钮功能暂空
                }

                @Override
                public void onPress() {
                    setHint("第二个旋钮还空着，功能待定");
                }
            });
        }
        return k;
    }

    /** 触控板下方的鼠标左右键 */
    private LinearLayout buildClickRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView leftBtn = mouseButton("左键", 1);
        row.addView(leftBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView rightBtn = mouseButton("右键", 2);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rlp.leftMargin = dp(10);
        row.addView(rightBtn, rlp);
        return row;
    }

    private TextView mouseButton(String label, final int buttons) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(theme.textMain);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.keyCap);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), theme.strokeColor);
        t.setBackground(bg);
        t.setClickable(true);
        // 按住不放 = 左键一直按着（配合触控板滑动即可拖选文字），抬起才松开
        t.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mouseHeld |= buttons;
                        v.setAlpha(0.75f);
                        if (hid != null) hid.sendMouse(mouseHeld, 0, 0, 0);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mouseHeld &= ~buttons;
                        v.setAlpha(1f);
                        if (hid != null) hid.sendMouse(mouseHeld, 0, 0, 0);
                        return true;
                }
                return false;
            }
        });
        return t;
    }

    private LinearLayout buildKnobsRow(boolean portrait) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(10));
        int knobH = portrait ? dp(200) : dp(178);

        KnobView k1 = new KnobView(this);
        k1.setTheme(theme);
        k1.setCaption("滚动 · 回车");
        k1.setCenterIcon(true);
        k1.setListener(new KnobView.Listener() {
            @Override
            public void onRotate(int direction) {
                // 顺时针 = 上滑，逆时针 = 下滑
                if (hid != null) hid.sendMouse(0, 0, 0, direction);
            }

            @Override
            public void onPress() {
                pressEnter();
            }
        });
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(knobH, knobH);
        p1.rightMargin = dp(26);
        row.addView(k1, p1);

        KnobView k2 = new KnobView(this);
        k2.setTheme(theme);
        k2.setCaption("备用");
        k2.setListener(new KnobView.Listener() {
            @Override
            public void onRotate(int direction) {
                // 第二个旋钮功能暂空
            }

            @Override
            public void onPress() {
                setHint("第二个旋钮还空着，功能待定");
            }
        });
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(knobH, knobH);
        row.addView(k2, p2);
        return row;
    }

    private void pressEnter() {
        if (hid == null) return;
        hid.sendKeyboard(0, new int[]{0x28});
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (hid != null) hid.sendKeyboard(0, new int[0]);
            }
        }, 90);
    }

    // ---------------- 开关 ----------------

    private void createSwitches() {
        vibBtn = makeSwitch();
        vibBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibOn = !vibOn;
                getPreferences(MODE_PRIVATE).edit().putBoolean("vib", vibOn).apply();
                applyToggleStyles();
            }
        });

        rgbBtn = makeSwitch();
        rgbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rgbOn = !rgbOn;
                getPreferences(MODE_PRIVATE).edit().putBoolean("rgb", rgbOn).apply();
                applyToggleStyles();
            }
        });

        posBtn = makeSwitch();
        posBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kbBottom = !kbBottom;
                getPreferences(MODE_PRIVATE).edit().putBoolean("kb_bottom", kbBottom).apply();
                buildUi();
            }
        });

        feedbackBtn = makeSwitch();
        feedbackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                feedbackOn = !feedbackOn;
                getPreferences(MODE_PRIVATE).edit().putBoolean("feedback", feedbackOn).apply();
                applyToggleStyles();
            }
        });

        themeBtn = makeSwitch();
        themeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lightTheme = !lightTheme;
                theme = Theme.get(lightTheme);
                getPreferences(MODE_PRIVATE).edit().putBoolean("light", lightTheme).apply();
                buildUi();
            }
        });

        fxBtn = makeSwitch();
        fxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fxOn = !fxOn;
                getPreferences(MODE_PRIVATE).edit().putBoolean("fx", fxOn).apply();
                applyToggleStyles();
            }
        });
    }

    private TextView makeSwitch() {
        TextView t = new TextView(this);
        t.setTextSize(11.5f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9), dp(3), dp(9), dp(3));
        return t;
    }

    private LinearLayout buildSwitchColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView[] views = {vibBtn, rgbBtn, posBtn, fxBtn, themeBtn};
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(5);
            col.addView(views[i], lp);
        }
        return col;
    }

    private LinearLayout buildSwitchGrid(TextView... views) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int rows = (views.length + 1) / 2;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                int idx = r * 2 + c;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(45), 1f);
                lp.rightMargin = dp(c == 0 ? 4 : 0);
                lp.leftMargin = dp(c == 1 ? 4 : 0);
                lp.bottomMargin = dp(5);
                if (idx >= views.length) {
                    View spacer = new View(this);
                    row.addView(spacer, lp);
                    continue;
                }
                TextView t = views[idx];
                if (idx == views.length - 1 && views.length % 2 == 1) {
                    lp = new LinearLayout.LayoutParams(0, dp(45), 2f);
                    lp.rightMargin = 0;
                    lp.leftMargin = 0;
                    lp.bottomMargin = dp(5);
                    row.addView(t, lp);
                    break;
                }
                row.addView(t, lp);
            }
            col.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }
        return col;
    }

    /** 本机是否真的有振动马达（有的平板完全没有） */
    private boolean detectHaptics() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                android.os.VibratorManager vm =
                        (android.os.VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    return vm.getVibratorIds().length > 0;
                }
            }
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            return v != null && v.hasVibrator();
        } catch (Throwable t) {
            return false;
        }
    }

    private void applyToggleStyles() {
        if (vibBtn != null) {
            if (!hapticAvailable) {
                styleSwitch(vibBtn, "震动 无马达", false);
                vibBtn.setEnabled(false);
                vibBtn.setAlpha(0.45f);
            } else {
                vibBtn.setEnabled(true);
                vibBtn.setAlpha(1f);
                styleSwitch(vibBtn, "震动 " + (vibOn ? "开" : "关"), vibOn);
            }
        }
        if (rgbBtn != null) styleSwitch(rgbBtn, "RGB " + (rgbOn ? "开" : "关"), rgbOn);
        if (posBtn != null) styleSwitch(posBtn, "键盘 " + (kbBottom ? "下" : "上"), kbBottom);
        if (fxBtn != null) styleSwitch(fxBtn, "特效 " + (fxOn ? "开" : "关"), fxOn);
        if (themeBtn != null) styleSwitch(themeBtn, "主题 " + (lightTheme ? "浅" : "深"), !lightTheme);
        if (feedbackBtn != null) styleSwitch(feedbackBtn, "反馈 " + (feedbackOn ? "开" : "关"), feedbackOn);

        if (keyboard != null) {
            keyboard.setHapticEnabled(vibOn);
            keyboard.setFeedbackEnabled(feedbackOn, feedbackOn);
            keyboard.setRgbEnabled(rgbOn && !fxOn);   // 特效开启时关闭键盘灯效
        }
        if (fxView != null) fxView.setRunning(fxOn);
    }

    private void styleSwitch(TextView t, String text, boolean on) {
        t.setText(text);
        GradientDrawable bg = new GradientDrawable();
        if (lightTheme) {
            bg.setColor(on ? 0xFFDDF3E6 : 0xFFE7EAEF);
            bg.setStroke((int) dp(1), on ? 0xFF2FA45F : 0x1A000000);
            t.setTextColor(on ? 0xFF1B6E42 : 0xFF3A4048);
        } else {
            bg.setColor(on ? 0xFF1D3A2C : 0xFF23262B);
            bg.setStroke((int) dp(1), on ? 0xFF3DDC84 : 0x22FFFFFF);
            t.setTextColor(on ? 0xFFBDF3D0 : 0xFFB9BEC7);
        }
        bg.setCornerRadius(dp(9));
        t.setBackground(bg);
    }

    // ---------------- 触控板 ----------------

    private void handleGesture(int gesture) {
        // macOS：Ctrl+方向（Mission Control / App Exposé / 切换桌面）
        // Windows：Win+Tab / Win+D / Win+Ctrl+方向（虚拟桌面）
        int mods, usage;
        if (macMode) {
            mods = 0x01;                                   // 左 Ctrl
            usage = gesture == TouchpadView.GESTURE_UP ? 0x52
                    : gesture == TouchpadView.GESTURE_DOWN ? 0x51
                    : gesture == TouchpadView.GESTURE_LEFT ? 0x50 : 0x4F;
        } else {
            if (gesture == TouchpadView.GESTURE_UP) {
                mods = 0x08; usage = 0x2B;                 // Win + Tab
            } else if (gesture == TouchpadView.GESTURE_DOWN) {
                mods = 0x08; usage = 0x07;                 // Win + D
            } else if (gesture == TouchpadView.GESTURE_LEFT) {
                mods = 0x09; usage = 0x50;                 // Win + Ctrl + ←
            } else {
                mods = 0x09; usage = 0x4F;                 // Win + Ctrl + →
            }
        }
        if (hid == null) return;
        hid.sendKeyboard(mods, new int[]{usage});
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (hid != null) hid.sendKeyboard(0, new int[0]);
            }
        }, 90);
    }

    // ---------------- 语音输入 ----------------

    private void startVoiceInput() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 1001);
            setHint("请允许麦克风权限后，再按住说话");
            return;
        }
        try {
            if (speech == null) {
                speech = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
                speech.setRecognitionListener(new VoiceListener());
            } else {
                try {
                    speech.cancel();      // 清掉上一轮残留的会话，避免 ERROR_CLIENT
                } catch (Exception ignored) {
                }
            }
            android.content.Intent it = new android.content.Intent(
                    android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            it.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            it.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            it.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            it.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            speech.startListening(it);
            voiceListening = true;
            setHint("正在听…说完松开");
        } catch (Exception e) {
            setHint("语音识别启动失败：" + e.getMessage());
        }
    }

    private void stopVoiceInput() {
        if (speech != null && voiceListening) {
            try {
                speech.stopListening();
            } catch (Exception ignored) {
            }
            setHint("识别中…");
            // 有些系统识别服务松手后不回调，给个兜底提示，避免一直停在"正在听"
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (voiceListening) {
                        voiceListening = false;
                        setHint("没听到内容，按住按钮说话再松开");
                    }
                }
            }, 2500);
        }
    }

    private class VoiceListener implements android.speech.RecognitionListener {
        public void onReadyForSpeech(android.os.Bundle p) {
            setHint("正在听…");
        }

        public void onBeginningOfSpeech() {
            setHint("听到了，继续说…");
        }

        public void onRmsChanged(float v) {
        }

        public void onBufferReceived(byte[] b) {
        }

        public void onEndOfSpeech() {
            setHint("识别中…");
        }

        public void onError(int code) {
            voiceListening = false;
            setHint("没听清（错误 " + code + "），再按一次试试");
        }

        public void onResults(android.os.Bundle results) {
            voiceListening = false;
            java.util.ArrayList<String> list =
                    results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty() && list.get(0) != null) {
                String text = list.get(0).trim();
                setHint("识别：" + text);
                typeTextToHost(text);
            }
        }

        public void onPartialResults(android.os.Bundle p) {
        }

        public void onEvent(int type, android.os.Bundle p) {
        }
    }

    /** 把识别到的文字逐字符用 HID 键盘打到电脑上 */
    private void typeTextToHost(String text) {
        StringBuilder sb = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (ch >= 32 && ch < 127) {
                sb.append(ch);
            } else if (ch == '，' || ch == '、') {
                sb.append(',');
            } else if (ch == '。') {
                sb.append('.');
            } else if (ch == '？') {
                sb.append('?');
            } else if (ch == '！') {
                sb.append('!');
            } else if (ch == '：') {
                sb.append(':');
            } else if (ch == '；') {
                sb.append(';');
            } else if (ch == '（') {
                sb.append('(');
            } else if (ch == '）') {
                sb.append(')');
            } else if (ch == '“' || ch == '”') {
                sb.append('"');
            }
            // 中文暂无法直接上屏（HID 键盘只能发键码）
        }
        typeBuf = sb.toString();
        typeIdx = 0;
        if (typeBuf.isEmpty()) {
            setHint("识别到中文，但 HID 键盘只能上屏英文/数字，请用另一种方式");
            return;
        }
        handler.post(typeStep);
    }

    private final Runnable typeStep = new Runnable() {
        @Override
        public void run() {
            if (typeIdx >= typeBuf.length()) return;
            char c = typeBuf.charAt(typeIdx++);
            int[] km = keyOf(c);
            if (km != null && hid != null) {
                hid.sendKeyboard(km[0], new int[]{km[1]});
                handler.postDelayed(typeRelease, 26);
            } else {
                handler.postDelayed(this, 5);
            }
        }
    };

    private final Runnable typeRelease = new Runnable() {
        @Override
        public void run() {
            if (hid != null) hid.sendKeyboard(0, new int[0]);
            handler.postDelayed(typeStep, 18);
        }
    };

    /** 字符 → {modifiers, usage} */
    private int[] keyOf(char c) {
        if (c >= 'a' && c <= 'z') return new int[]{0, 0x04 + (c - 'a')};
        if (c >= 'A' && c <= 'Z') return new int[]{0x02, 0x04 + (c - 'A')};
        if (c >= '1' && c <= '9') return new int[]{0, 0x1E + (c - '1')};
        if (c == '0') return new int[]{0, 0x27};
        switch (c) {
            case ' ': return new int[]{0, 0x2C};
            case '\n': return new int[]{0, 0x28};
            case ',': return new int[]{0, 0x36};
            case '.': return new int[]{0, 0x37};
            case '?': return new int[]{0x02, 0x38};
            case '!': return new int[]{0x02, 0x1E};
            case ':': return new int[]{0x02, 0x33};
            case ';': return new int[]{0, 0x33};
            case '\'': return new int[]{0, 0x34};
            case '"': return new int[]{0x02, 0x34};
            case '-': return new int[]{0, 0x2D};
            case '_': return new int[]{0x02, 0x2D};
            case '=': return new int[]{0, 0x2E};
            case '+': return new int[]{0x02, 0x2E};
            case '/': return new int[]{0, 0x38};
            case '(': return new int[]{0x02, 0x26};
            case ')': return new int[]{0x02, 0x27};
            case '@': return new int[]{0x02, 0x1F};
            case '#': return new int[]{0x02, 0x20};
            case '%': return new int[]{0x02, 0x22};
            case '&': return new int[]{0x02, 0x23};
            case '*': return new int[]{0x02, 0x25};
            case '[': return new int[]{0, 0x2F};
            case ']': return new int[]{0, 0x30};
            case '{': return new int[]{0x02, 0x2F};
            case '}': return new int[]{0x02, 0x30};
        }
        return null;
    }

    private TouchpadView buildTouchpad() {
        TouchpadView tp = new TouchpadView(this);
        tp.setTheme(theme);
        tp.setPlatformMac(macMode);
        tp.setListener(new TouchpadView.Listener() {
            @Override
            public void onMouseMove(int dx, int dy) {
                if (hid != null) hid.sendMouse(mouseHeld, dx, dy, 0);
            }

            @Override
            public void onMouseButton(int buttons) {
                mouseHeld = buttons;
                if (hid != null) hid.sendMouse(mouseHeld, 0, 0, 0);
            }

            @Override
            public void onWheel(int amount) {
                if (hid != null) hid.sendMouse(mouseHeld, 0, 0, amount);
            }

            @Override
            public void onGesture(int gesture) {
                handleGesture(gesture);
            }
        });
        return tp;
    }

    // ---------------- 状态块 ----------------

    private LinearLayout buildStatusPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(6), dp(8), dp(6), dp(6));

        statusBig = new TextView(this);
        statusBig.setText(lastStatus);
        statusBig.setTextColor(theme.textMain);
        statusBig.setTextSize(15);
        p.addView(statusBig, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        hintText = new TextView(this);
        hintText.setText(lastHint);
        hintText.setTextColor(theme.textSub);
        hintText.setTextSize(11);
        hintText.setPadding(0, dp(5), 0, 0);
        hintText.setSingleLine(true);
        hintText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        p.addView(hintText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        p.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHostDialog();
            }
        });
        return p;
    }

    // ---------------- 电量 ----------------

    private void updateBatteryText() {
        int pct = batteryPct;
        if (pct < 0) {
            try {
                android.os.BatteryManager bm = (android.os.BatteryManager)
                        getSystemService(BATTERY_SERVICE);
                if (bm != null) pct = bm.getIntProperty(
                        android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            } catch (Throwable ignored) {
            }
        }
        if (pct < 0) pct = 0;
        String s = pct + "%";
        if (batteryCharging) s = s + " ⚡";
        if (batteryText != null) batteryText.setText(s);
    }

    private TextView pill(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(theme.textMain);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(lightTheme ? 0xFFE3E7ED : 0xFF34373E);
        bg.setCornerRadius(dp(9));
        bg.setStroke((int) dp(1), lightTheme ? 0x1A000000 : 0x22FFFFFF);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void hideSystemUi() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getDecorView().getWindowInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------------- 权限 ----------------

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (need.isEmpty()) {
            hid.start();
            return;
        }
        requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERMS) return;
        if (hasBluetoothPermissions()) {
            hid.start();
        } else {
            setStatus("缺少蓝牙权限，无法作为键盘使用");
        }
    }

    // ---------------- HID 回调 ----------------

    @Override
    public void onHidRegistered(boolean ok, String msg) {
        lastRegisterMsg = msg;
        if (ok) {
            if (!hid.isConnected()) {
                setStatus("HID 就绪");
                setHint("点【连接电脑】选择电脑并配对；也可以在电脑端搜索本平板后配对");
            }
        } else {
            setStatus(msg);
            setHint("");
        }
    }

    @Override
    public void onHidConnection(int state, BluetoothDevice device, String name) {
        linked = (state == BluetoothProfile.STATE_CONNECTED);
        if (linked) {
            uiHandler.removeCallbacks(connectWatchdog);
            setStatus("已连接：" + (name == null || name.isEmpty() ? "电脑" : name));
            setHint("现在可以直接打字的，试试 Shift + 字母、cmd + 空格");
            if (connectButton != null) connectButton.setText("已连接");
        } else {
            uiHandler.removeCallbacks(connectWatchdog);
            setStatus("未连接");
            setHint("在电脑上打开蓝牙，点顶栏的连接状态选择你的电脑（Mac / Windows / Linux 都支持）");
            if (connectButton != null) connectButton.setText("连接电脑");
        }
    }

    @Override
    public void onCapsLock(boolean on) {
        keyboard.setCapsOn(on);
    }

    private void setStatus(String s) {
        lastStatus = s;
        if (statusBig != null) statusBig.setText(s);
    }

    private void setHint(String s) {
        lastHint = s;
        if (hintText != null) hintText.setText(s);
    }

    // ---------------- 键位上报 ----------------

    @Override
    public void onKeyboardReport(int modifiers, int[] keyCodes) {
        hid.sendKeyboard(modifiers, keyCodes);
    }

    @Override
    public void onConsumer(int usage, boolean down) {
        hid.sendConsumer(usage, down);
    }

    @Override
    public void onCombo(final int modifiers, final int usage) {
        hid.sendKeyboard(modifiers, new int[]{usage});
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hid.sendKeyboard(0, new int[0]);
            }
        }, 45);
    }

    // ---------------- 连接 / 配对 ----------------

    private void showHostDialog() {
        if (!hasBluetoothPermissions()) {
            requestPermissions();
            return;
        }
        BluetoothAdapter adapter = hid.getAdapter();
        if (adapter == null) {
            toast("本机没有蓝牙适配器");
            return;
        }
        if (!adapter.isEnabled()) {
            toast("请先打开系统蓝牙");
            return;
        }

        devicePool.clear();
        foundDevices.clear();
        foundNames.clear();
        try {
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                addDevice(d, true);
            }
        } catch (SecurityException ignored) {
        }
        rebuildDeviceList();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));

        TextView tip = new TextView(this);
        tip.setText("① 电脑端打开蓝牙并保持可见；\n② 点下面列表里的电脑即可配对/连接；\n③ 也可以先在电脑的系统设置里搜到本平板并配对，再回来点连接。");
        tip.setTextSize(12);
        tip.setTextColor(0xFF6B7078);
        box.addView(tip);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, foundNames);
        ListView lv = new ListView(this);
        lv.setAdapter(listAdapter);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= foundDevices.size()) return;
                chooseHost(foundDevices.get(position));
            }
        });
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(260));
        box.addView(lv, llp);

        android.widget.CheckBox unnamedBox = new android.widget.CheckBox(this);
        unnamedBox.setText("显示未命名的设备（大多是耳机/手环，一般不用管）");
        unnamedBox.setTextSize(12);
        unnamedBox.setTextColor(0xFF6B7078);
        unnamedBox.setChecked(showUnnamed);
        unnamedBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                showUnnamed = isChecked;
                rebuildDeviceList();
            }
        });
        box.addView(unnamedBox);

        if (linked) {
            Button offBtn = new Button(this);
            offBtn.setText("断开当前连接");
            offBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hid.disconnect();
                    linked = false;
                    setStatus("未连接");
                    setHint("已断开，可以重新选择电脑连接");
                    if (hostDialog != null) hostDialog.dismiss();
                }
            });
            box.addView(offBtn);
        }

        Button scanBtn = new Button(this);
        scanBtn.setText("开始扫描附近的电脑");
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDiscovery();
            }
        });
        box.addView(scanBtn);

        hostDialog = new AlertDialog.Builder(this)
                .setTitle("选择要连接的电脑")
                .setView(box)
                .setNegativeButton("关闭", null)
                .create();
        hostDialog.show();
    }

    private void addDevice(BluetoothDevice d, boolean bondedFirst) {
        for (BluetoothDevice exist : devicePool) {
            if (exist.getAddress().equals(d.getAddress())) return;
        }
        devicePool.add(d);
        rebuildDeviceList();
    }

    /** 判断设备名称是否其实只是个 MAC 地址（很多设备不上报名） */
    private static boolean hasRealName(BluetoothDevice d) {
        String n = HidBridge.safeName(d);
        return n != null && !n.isEmpty() && !n.equalsIgnoreCase(d.getAddress());
    }

    /** 是否是电脑类设备（蓝牙 Class of Device 或名称特征） */
    private static boolean looksLikeComputer(BluetoothDevice d) {
        try {
            if (d.getBluetoothClass() != null
                    && d.getBluetoothClass().getMajorDeviceClass()
                    == android.bluetooth.BluetoothClass.Device.Major.COMPUTER) {
                return true;
            }
        } catch (Exception ignored) {
        }
        String n = HidBridge.safeName(d).toLowerCase();
        String[] keys = {"mac", "imac", "macbook", "mini", "windows", "laptop", "notebook",
                "pc", "linux", "chromebook", "thinkpad", "surface", "电脑", "工作站"};
        for (String k : keys) if (n.contains(k)) return true;
        return false;
    }

    private String labelOf(BluetoothDevice d) {
        int state;
        try {
            state = d.getBondState();
        } catch (SecurityException e) {
            state = BluetoothDevice.BOND_NONE;
        }
        String name = hasRealName(d) ? HidBridge.safeName(d) : "未命名设备";
        StringBuilder sb = new StringBuilder(name);
        if (looksLikeComputer(d)) sb.append("  ·  电脑");
        if (state == BluetoothDevice.BOND_BONDED) sb.append("  ·  已配对");
        else if (state == BluetoothDevice.BOND_BONDING) sb.append("  ·  正在配对…");
        return sb.toString();
    }

    private void rebuildDeviceList() {
        ArrayList<BluetoothDevice> bonded = new ArrayList<>();
        ArrayList<BluetoothDevice> computers = new ArrayList<>();
        ArrayList<BluetoothDevice> others = new ArrayList<>();
        for (BluetoothDevice d : devicePool) {
            boolean nameless = !hasRealName(d);
            if (nameless && !showUnnamed) continue;
            int state;
            try {
                state = d.getBondState();
            } catch (SecurityException e) {
                state = BluetoothDevice.BOND_NONE;
            }
            if (state == BluetoothDevice.BOND_BONDED) bonded.add(d);
            else if (looksLikeComputer(d)) computers.add(d);
            else others.add(d);
        }
        foundDevices.clear();
        foundNames.clear();
        for (BluetoothDevice d : bonded) {
            foundDevices.add(d);
            foundNames.add(labelOf(d));
        }
        for (BluetoothDevice d : computers) {
            foundDevices.add(d);
            foundNames.add(labelOf(d));
        }
        for (BluetoothDevice d : others) {
            foundDevices.add(d);
            foundNames.add(labelOf(d));
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
            if (foundNames.isEmpty() && !showUnnamed) {
                foundNames.add("（暂无已命名的设备，可勾选“显示未命名设备”）");
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void attemptConnect() {
        if (pendingHost == null) return;
        boolean ok = hid.connect(pendingHost);
        if (!ok) {
            setHint("连接请求被系统拒绝，正在重试…");
        }
        uiHandler.removeCallbacks(connectWatchdog);
        uiHandler.postDelayed(connectWatchdog, 7000);
    }

    private void chooseHost(final BluetoothDevice device) {
        int state;
        try {
            state = device.getBondState();
        } catch (SecurityException e) {
            toast("缺少蓝牙权限");
            return;
        }
        if (state == BluetoothDevice.BOND_BONDED) {
            if (hid.isRegistered()) {
                pendingHost = device;
                connectRetry = 0;
                if (hostDialog != null) hostDialog.dismiss();
                setStatus("正在连接 " + HidBridge.safeName(device) + "…");
                setHint("如果电脑上弹出配对/连接确认，请点允许");
                attemptConnect();
            } else {
                toast("HID 服务尚未就绪，请稍等");
            }
        } else {
            try {
                device.createBond();
                toast("正在配对，请在电脑上确认配对请求");
            } catch (SecurityException e) {
                toast("配对失败：缺少权限");
            }
        }
    }

    private void makeDiscoverable() {
        try {
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(i, REQ_DISCOVERABLE);
        } catch (Exception e) {
            toast("无法进入可被发现状态：" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DISCOVERABLE && resultCode > 0) {
            toast("5 分钟内可被电脑发现，请到电脑蓝牙里搜索本平板");
        }
    }

    private void startDiscovery() {
        if (!hasScanPermission()) {
            requestPermissions();
            return;
        }
        BluetoothAdapter adapter = hid.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            toast("请先打开系统蓝牙");
            return;
        }
        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            boolean ok = adapter.startDiscovery();
            if (!ok) {
                // 部分机型/系统不允许应用发起经典蓝牙发现，改走电脑端配对
                toast("本机不允许应用发起搜索，请在电脑上搜索本平板并配对");
                setHint("电脑端搜索本平板（名称：" + adapter.getName() + "）并配对，再回来点【连接电脑】");
                if (hostDialog != null) hostDialog.dismiss();
            } else {
                toast("正在扫描…");
            }
        } catch (SecurityException e) {
            toast("缺少扫描权限");
        }
    }

    // ---------------- 蓝牙广播 ----------------

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null) addDevice(d, false);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (d != null) {
                    rebuildDeviceList();
                    if (state == BluetoothDevice.BOND_BONDED) {
                        toast("配对成功：" + HidBridge.safeName(d));
                        if (hid.isRegistered()) hid.connect(d);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (foundDevices.isEmpty()) toast("没有扫描到设备，可先在电脑端搜索本平板");
            }
        }
    };

    private void registerReceiver() {
        if (receiverRegistered) return;
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(btReceiver, f);
        receiverRegistered = true;
    }

    private void unregisterReceiverSafe() {
        if (!receiverRegistered) return;
        try {
            unregisterReceiver(btReceiver);
        } catch (Exception ignored) {
        }
        receiverRegistered = false;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        registerReceiver();
        if (hid != null && !hid.isRegistered() && hasBluetoothPermissions()) {
            hid.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        BluetoothAdapter adapter = hid == null ? null : hid.getAdapter();
        try {
            if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (Exception ignored) {
        }
        unregisterReceiverSafe();
    }

}
