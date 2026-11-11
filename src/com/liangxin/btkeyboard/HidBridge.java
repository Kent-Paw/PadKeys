package com.liangxin.btkeyboard;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 把 Android 端注册成一个蓝牙 HID 复合设备（键盘 + 多媒体控制 + 鼠标），
 * 这样 macOS / Windows / Linux / Android / iOS 都能把它当普通蓝牙键盘使用。
 */
public class HidBridge {

    private static final String TAG = "HidBridge";

    public static final int REPORT_KEYBOARD = 1;
    public static final int REPORT_CONSUMER = 2;
    public static final int REPORT_MOUSE = 3;

    public interface Listener {
        void onHidRegistered(boolean ok, String msg);

        void onHidConnection(int state, BluetoothDevice device, String name);

        void onCapsLock(boolean on);
    }

    /**
     * HID 报告描述符：
     * Report ID 1 = 标准 8 字节键盘报告（modifier + 保留 + 6 键）
     * Report ID 2 = 16 位 Consumer 报告（音量/亮度/播放）
     * Report ID 3 = 鼠标报告（5 键 + X/Y/Wheel，为后续触控板预留）
     */
    private static final String DESCRIPTOR_HEX =
            "05 01 09 06 A1 01 85 01" +
            " 05 07 19 E0 29 E7 15 00 25 01 75 01 95 08 81 02" +
            " 95 01 75 08 81 01" +
            " 95 05 75 01 05 08 19 01 29 05 91 02" +
            " 95 01 75 03 91 01" +
            " 95 06 75 08 15 00 25 65 05 07 19 00 29 65 81 00" +
            " C0" +
            " 05 0C 09 01 A1 01 85 02 15 00 26 FF 03 19 00 2A FF 03 75 10 95 01 81 00 C0" +
            " 05 01 09 02 A1 01 85 03 09 01 A1 00 05 09 19 01 29 05 15 00 25 01" +
            " 95 05 75 01 81 02 95 01 75 03 81 01" +
            " 05 01 09 30 09 31 09 38 15 81 25 7F 75 08 95 03 81 06 C0 C0";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;

    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private boolean registered;
    private boolean starting;
    private byte[] lastKeyboardReport = new byte[8];

    public HidBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public BluetoothAdapter getAdapter() {
        return adapter;
    }

    public BluetoothDevice getHost() {
        return host;
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean isConnected() {
        if (hid == null) return false;
        try {
            List<BluetoothDevice> devices = hid.getConnectedDevices();
            if (devices != null && !devices.isEmpty()) {
                host = devices.get(0);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public void start() {
        if (starting || registered) return;
        starting = true;
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            starting = false;
            notifyRegister(false, "本机没有蓝牙适配器");
            return;
        }
        if (!adapter.isEnabled()) {
            starting = false;
            notifyRegister(false, "蓝牙未开启，请先打开系统蓝牙");
            return;
        }
        try {
            boolean ok = adapter.getProfileProxy(context, proxyListener, BluetoothProfile.HID_DEVICE);
            if (!ok) {
                starting = false;
                notifyRegister(false, "无法连接 HID Device 服务");
            }
        } catch (SecurityException e) {
            starting = false;
            notifyRegister(false, "缺少蓝牙权限");
        }
    }

    private final BluetoothProfile.ServiceListener proxyListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile != BluetoothProfile.HID_DEVICE) return;
            hid = (BluetoothHidDevice) proxy;
            registerApp();
            try {
                List<BluetoothDevice> connected = hid.getConnectedDevices();
                if (connected != null && !connected.isEmpty()) {
                    host = connected.get(0);
                    notifyConnection(BluetoothProfile.STATE_CONNECTED, host, safeName(host));
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            hid = null;
            registered = false;
        }
    };

    @SuppressWarnings("MissingPermission")
    private void registerApp() {
        if (hid == null) return;
        byte[] descriptor = hexToBytes(DESCRIPTOR_HEX);
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "PadKeys Tablet",
                "Tablet as Bluetooth keyboard",
                "LiangXin",
                (byte) 0xC0, // 键盘 + 指点设备组合
                descriptor);
        try {
            boolean ok = hid.registerApp(sdp, null, null, executor, appCallback);
            if (!ok) {
                starting = false;
                notifyRegister(false, "HID 注册失败（可能被系统限制）");
            }
        } catch (SecurityException e) {
            starting = false;
            notifyRegister(false, "缺少蓝牙权限");
        } catch (IllegalArgumentException e) {
            starting = false;
            notifyRegister(false, "HID 描述符被拒绝：" + e.getMessage());
        } catch (Exception e) {
            starting = false;
            notifyRegister(false, "HID 注册异常：" + e.getMessage());
        }
    }

    private void notifyRegister(final boolean ok, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onHidRegistered(ok, msg);
            }
        });
    }

    private void notifyConnection(final int state, final BluetoothDevice device, final String name) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onHidConnection(state, device, name);
            }
        });
    }

    private void notifyCaps(final boolean on) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onCapsLock(on);
            }
        });
    }

    private final BluetoothHidDevice.Callback appCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean isRegistered) {
            registered = isRegistered;
            starting = false;
            if (isRegistered && pluggedDevice != null) {
                // 系统已有配对的宿主，直接连
                connect(pluggedDevice);
            }
            notifyRegister(isRegistered, isRegistered ? "HID 服务已就绪" : "HID 服务已断开");
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            host = (state == BluetoothProfile.STATE_CONNECTED) ? device : null;
            notifyConnection(state, device, safeName(device));
        }

        @Override
        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            byte[] data = new byte[Math.max(bufferSize, 1)];
            if (id == REPORT_KEYBOARD && bufferSize >= lastKeyboardReport.length) {
                System.arraycopy(lastKeyboardReport, 0, data, 0, lastKeyboardReport.length);
            }
            try {
                hid.replyReport(device, type, id, data);
            } catch (Exception e) {
                Log.w(TAG, "replyReport failed: " + e);
            }
        }

        @Override
        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            if (id == REPORT_KEYBOARD && data != null && data.length > 0) {
                // LED bit1 = Caps Lock
                notifyCaps((data[0] & 0x02) != 0);
            }
            try {
                hid.replyReport(device, type, id, new byte[]{0});
            } catch (Exception e) {
                Log.w(TAG, "replyReport failed: " + e);
            }
        }

        @Override
        public void onSetProtocol(BluetoothDevice device, byte protocol) {
            Log.d(TAG, "setProtocol " + protocol);
        }

        @Override
        public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
            Log.d(TAG, "interrupt data id=" + reportId);
        }

        @Override
        public void onVirtualCableUnplug(BluetoothDevice device) {
            Log.d(TAG, "virtual cable unplug " + safeName(device));
        }
    };

    @SuppressWarnings("MissingPermission")
    public boolean connect(BluetoothDevice device) {
        if (hid == null || device == null) return false;
        try {
            if (!isRegistered()) return false;
            // 之前残留的连接先断开，否则系统会返回“连接已在进行中”
            if (host != null) {
                try { hid.disconnect(host); } catch (Exception ignored) {}
                host = null;
            }
            boolean ok = hid.connect(device);
            Log.d(TAG, "connect " + safeName(device) + " -> " + ok);
            return ok;
        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            Log.w(TAG, "connect failed: " + e);
            return false;
        }
    }

    @SuppressWarnings("MissingPermission")
    public boolean disconnect() {
        if (hid == null || host == null) return false;
        try {
            return hid.disconnect(host);
        } catch (Exception e) {
            return false;
        }
    }

    /** 发送 8 字节键盘报告 */
    public void sendKeyboard(int modifiers, int[] keyCodes) {
        byte[] data = new byte[8];
        data[0] = (byte) (modifiers & 0xFF);
        int n = keyCodes == null ? 0 : Math.min(keyCodes.length, 6);
        for (int i = 0; i < n; i++) data[2 + i] = (byte) (keyCodes[i] & 0xFF);
        lastKeyboardReport = data;
        send(REPORT_KEYBOARD, data);
    }

    /** 发送多媒体报告 */
    public void sendConsumer(int usage, boolean down) {
        byte[] data = new byte[2];
        if (down) {
            data[0] = (byte) (usage & 0xFF);
            data[1] = (byte) ((usage >> 8) & 0xFF);
        }
        send(REPORT_CONSUMER, data);
    }

    /** 鼠标报告（为后续触控板预留） */
    public void sendMouse(int buttons, int dx, int dy, int wheel) {
        byte[] data = new byte[4];
        data[0] = (byte) (buttons & 0x1F);
        data[1] = (byte) dx;
        data[2] = (byte) dy;
        data[3] = (byte) wheel;
        send(REPORT_MOUSE, data);
    }

    private void send(int id, byte[] data) {
        if (hid == null || host == null) {
            Log.w(TAG, "send skipped id=" + id + " hid=" + (hid != null) + " host=" + host);
            return;
        }
        Log.d(TAG, "send id=" + id + " data=" + android.util.Base64.encodeToString(data, 2));
        try {
            hid.sendReport(host, id, data);
        } catch (SecurityException e) {
            Log.w(TAG, "sendReport permission denied");
        } catch (Exception e) {
            Log.w(TAG, "sendReport failed: " + e);
        }
    }

    public void stop() {
        try {
            if (hid != null && registered) hid.unregisterApp();
        } catch (Exception ignored) {
        }
        try {
            if (adapter != null && hid != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        } catch (Exception ignored) {
        }
        registered = false;
        starting = false;
        hid = null;
    }

    @SuppressWarnings("MissingPermission")
    public static String safeName(BluetoothDevice device) {
        if (device == null) return "";
        try {
            String n = device.getName();
            return n == null ? device.getAddress() : n;
        } catch (SecurityException e) {
            return device.getAddress();
        }
    }

    private static byte[] hexToBytes(String hex) {
        String[] parts = hex.trim().split(" ");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }
}
