package com.liangxin.btkeyboard;

/**
 * 一个按键的定义。布局数据在 Layouts.java 中组装。
 */
public class KeyDef {

    // 按键类型
    public static final int T_KEY = 0;       // 普通 HID 键
    public static final int T_MOD = 1;       // 修饰键（Shift/Ctrl/Alt/Gui）
    public static final int T_CONSUMER = 2;  // 多媒体键（音量/亮度/播放）
    public static final int T_FN = 3;        // fn 键（切换多媒体层）
    public static final int T_COMBO = 4;     // 组合键（如 Cmd+Space）
    public static final int T_CAPS = 5;      // Caps Lock

    public String label = "";
    public String shiftLabel = null;   // 键左上角的小字（shift 符号）
    public String sub = null;          // 键下方的提示文字（如 Android/Windows/iOS）
    public int subColor = 0xFF9AA0A6;
    public float units = 1.0f;         // 宽度（以标准字母键为 1.0）

    public int type = T_KEY;
    public int usage = 0;              // HID keyboard usage 或 consumer usage
    public int modBit = 0;             // 修饰键对应的 modifier bit
    public int comboMods = 0;          // 组合键的 modifier
    public int comboUsage = 0;         // 组合键的 usage

    // fn 层：按住 fn 时使用（0 表示没有特殊行为）
    public int fnConsumer = 0;
    public int fnComboMods = 0;
    public int fnComboUsage = 0;

    // 半高键（用于 PgUp/PgDn 那种上下叠放）
    public boolean halfTop = false;
    public boolean halfBottom = false;
    // 与上一个键共用同一列（上下叠放的 PgUp/PgDn）
    public boolean sameColumn = false;

    // 文字缩放（例如 "BackSpace" 需要小一点）
    public float textScale = 1.0f;
    // 两行显示（label 上下拆开）
    public boolean twoLine = false;
    public String secondLine = null;

    public static KeyDef key(String label, int usage) {
        KeyDef k = new KeyDef();
        k.label = label;
        k.usage = usage;
        return k;
    }

    public static KeyDef symKey(String label, String shift, int usage, float units) {
        KeyDef k = key(label, usage);
        k.shiftLabel = shift;
        k.units = units;
        return k;
    }

    public static KeyDef mod(String label, String sub, int bit, float units) {
        KeyDef k = new KeyDef();
        k.label = label;
        k.sub = sub;
        k.subColor = 0xFFB9BEC7;
        k.modBit = bit;
        k.units = units;
        k.type = T_MOD;
        return k;
    }
}
