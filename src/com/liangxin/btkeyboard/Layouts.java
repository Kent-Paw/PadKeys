package com.liangxin.btkeyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 键盘布局定义（参考联想 Y900 原装键盘保护套的 65% 布局）。
 * 所有 usage 均为 HID Keyboard/Keypad 页（0x07）或 Consumer 页（0x0C）的标准值。
 */
public class Layouts {

    // ---- modifier bits ----
    public static final int M_LCTRL = 0x01;
    public static final int M_LSHIFT = 0x02;
    public static final int M_LALT = 0x04;
    public static final int M_LGUI = 0x08;
    public static final int M_RCTRL = 0x10;
    public static final int M_RSHIFT = 0x20;
    public static final int M_RALT = 0x40;
    public static final int M_RGUI = 0x80;

    // ---- keyboard usages ----
    static final int K_ESC = 0x29;
    static final int K_F1 = 0x3A;
    static final int K_DELETE = 0x4C;
    static final int K_GRAVE = 0x35;
    static final int K_1 = 0x1E;
    static final int K_0 = 0x27;
    static final int K_MINUS = 0x2D;
    static final int K_EQUAL = 0x2E;
    static final int K_BACKSPACE = 0x2A;
    static final int K_TAB = 0x2B;
    static final int K_Q = 0x14, K_W = 0x1A, K_E = 0x08, K_R = 0x15, K_T = 0x17, K_Y = 0x1C,
            K_U = 0x18, K_I = 0x0C, K_O = 0x12, K_P = 0x13;
    static final int K_LBRACKET = 0x2F, K_RBRACKET = 0x30, K_BACKSLASH = 0x31;
    static final int K_CAPS = 0x39;
    static final int K_A = 0x04, K_S = 0x16, K_D = 0x07, K_F = 0x09, K_G = 0x0A, K_H = 0x0B,
            K_J = 0x0D, K_K = 0x0E, K_L = 0x0F;
    static final int K_SEMI = 0x33, K_APOS = 0x34, K_ENTER = 0x28;
    static final int K_Z = 0x1D, K_X = 0x1B, K_C = 0x06, K_V = 0x19, K_B = 0x05, K_N = 0x11, K_M = 0x10;
    static final int K_COMMA = 0x36, K_PERIOD = 0x37, K_SLASH = 0x38;
    static final int K_SPACE = 0x2C;
    static final int K_UP = 0x52, K_DOWN = 0x51, K_LEFT = 0x50, K_RIGHT = 0x4F;
    static final int K_PGUP = 0x4B, K_PGDN = 0x4E, K_HOME = 0x4A, K_END = 0x4D;

    // ---- consumer usages ----
    static final int C_BRIGHT_DEC = 0x70, C_BRIGHT_INC = 0x6F;
    static final int C_PLAY = 0xCD, C_NEXT = 0xB5, C_PREV = 0xB6;
    static final int C_MUTE = 0xE2, C_VOL_INC = 0xE9, C_VOL_DEC = 0xEA;

    public static List<List<KeyDef>> build(boolean mac) {
        List<List<KeyDef>> rows = new ArrayList<>();
        rows.add(rowFn(mac));
        rows.add(rowNumber());
        rows.add(rowQrow());
        rows.add(rowArow());
        rows.add(rowZrow());
        rows.add(rowBottom(mac));
        return rows;
    }

    /** 功能键行：Esc + F1~F12 + Delete */
    private static List<KeyDef> rowFn(boolean mac) {
        List<KeyDef> r = new ArrayList<>();
        KeyDef esc = KeyDef.key("Esc", K_ESC);
        esc.units = 1.15f;
        r.add(esc);

        for (int i = 0; i < 12; i++) {
            KeyDef k = KeyDef.key("F" + (i + 1), K_F1 + i);
            k.units = 1.0f;
            switch (i + 1) {
                case 1: k.fnConsumer = C_BRIGHT_DEC; k.sub = "亮度-"; break;
                case 2: k.fnConsumer = C_BRIGHT_INC; k.sub = "亮度+"; break;
                case 3:
                    if (mac) { k.fnComboMods = M_LCTRL; k.fnComboUsage = K_UP; k.sub = "调度中心"; }
                    else { k.fnComboMods = M_LGUI; k.fnComboUsage = K_TAB; k.sub = "任务视图"; }
                    break;
                case 4:
                    if (mac) { k.fnComboMods = 0; k.fnComboUsage = 0x3D; k.sub = "启动台"; }
                    else { k.fnComboMods = M_LGUI; k.fnComboUsage = K_E; k.sub = "资源管理器"; }
                    break;
                case 5:
                    if (mac) { k.fnComboMods = M_LGUI; k.fnComboUsage = K_SPACE; k.sub = "聚焦搜索"; }
                    else { k.fnComboMods = M_LGUI; k.fnComboUsage = K_S; k.sub = "搜索"; }
                    break;
                case 6:
                    if (mac) { k.fnComboMods = M_LCTRL | M_LGUI; k.fnComboUsage = K_Q; k.sub = "锁屏"; }
                    else { k.fnComboMods = M_LGUI; k.fnComboUsage = K_L; k.sub = "锁屏"; }
                    break;
                case 7: k.fnConsumer = C_PREV; k.sub = "上一曲"; break;
                case 8: k.fnConsumer = C_PLAY; k.sub = "播放"; break;
                case 9: k.fnConsumer = C_NEXT; k.sub = "下一曲"; break;
                case 10: k.fnConsumer = C_MUTE; k.sub = "静音"; break;
                case 11: k.fnConsumer = C_VOL_DEC; k.sub = "音量-"; break;
                case 12: k.fnConsumer = C_VOL_INC; k.sub = "音量+"; break;
            }
            k.subColor = 0xFFAEB4BD;
            r.add(k);
        }

        KeyDef del = KeyDef.key("Delete", K_DELETE);
        del.units = 1.15f;
        del.textScale = 0.9f;
        r.add(del);
        return r;
    }

    /** 数字行 */
    private static List<KeyDef> rowNumber() {
        List<KeyDef> r = new ArrayList<>();
        r.add(sym("`", "~", K_GRAVE, 1.0f));
        String shifted = "!@#$%^&*()";
        for (int i = 0; i < 10; i++) {
            KeyDef k = sym(String.valueOf(i == 9 ? 0 : i + 1), String.valueOf(shifted.charAt(i)), K_1 + i, 1.0f);
            r.add(k);
        }
        r.add(sym("-", "_", K_MINUS, 1.0f));
        r.add(sym("=", "+", K_EQUAL, 1.0f));
        KeyDef bs = KeyDef.key("Backspace", K_BACKSPACE);
        bs.units = 1.4f;
        bs.textScale = 0.78f;
        r.add(bs);
        return r;
    }

    /** Tab + QWERTY 行 */
    private static List<KeyDef> rowQrow() {
        List<KeyDef> r = new ArrayList<>();
        KeyDef tab = KeyDef.key("Tab", K_TAB);
        tab.units = 1.2f;
        tab.textScale = 0.9f;
        r.add(tab);

        KeyDef q = KeyDef.key("Q", K_Q);
        q.sub = "Android";
        q.subColor = 0xFF3DDC84;
        r.add(q);

        KeyDef w = KeyDef.key("W", K_W);
        w.sub = "Windows";
        w.subColor = 0xFF57A9FF;
        r.add(w);

        KeyDef e = KeyDef.key("E", K_E);
        e.sub = "iOS";
        e.subColor = 0xFFCFD8DC;
        r.add(e);

        r.add(KeyDef.key("R", K_R));
        r.add(KeyDef.key("T", K_T));
        r.add(KeyDef.key("Y", K_Y));
        r.add(KeyDef.key("U", K_U));
        r.add(KeyDef.key("I", K_I));
        r.add(KeyDef.key("O", K_O));
        r.add(KeyDef.key("P", K_P));
        r.add(sym("[", "{", K_LBRACKET, 1.0f));
        r.add(sym("]", "}", K_RBRACKET, 1.0f));
        r.add(sym("\\", "|", K_BACKSLASH, 1.2f));
        return r;
    }

    /** Caps Lock + ASDF 行 */
    private static List<KeyDef> rowArow() {
        List<KeyDef> r = new ArrayList<>();
        KeyDef caps = KeyDef.key("Caps Lock", K_CAPS);
        caps.units = 1.6f;
        caps.type = KeyDef.T_CAPS;
        caps.textScale = 0.72f;
        r.add(caps);

        r.add(KeyDef.key("A", K_A));
        r.add(KeyDef.key("S", K_S));
        r.add(KeyDef.key("D", K_D));
        r.add(KeyDef.key("F", K_F));
        r.add(KeyDef.key("G", K_G));
        r.add(KeyDef.key("H", K_H));
        r.add(KeyDef.key("J", K_J));
        r.add(KeyDef.key("K", K_K));
        r.add(KeyDef.key("L", K_L));
        r.add(sym(";", ":", K_SEMI, 1.0f));
        r.add(sym("'", "\"", K_APOS, 1.0f));

        KeyDef enter = KeyDef.key("Enter", K_ENTER);
        enter.units = 1.8f;
        enter.textScale = 0.85f;
        r.add(enter);
        return r;
    }

    /** Shift + ZXCV 行 */
    private static List<KeyDef> rowZrow() {
        List<KeyDef> r = new ArrayList<>();
        KeyDef ls = KeyDef.mod("Shift", "⇧", M_LSHIFT, 2.0f);
        ls.textScale = 0.85f;
        r.add(ls);

        r.add(KeyDef.key("Z", K_Z));
        r.add(KeyDef.key("X", K_X));
        r.add(KeyDef.key("C", K_C));
        r.add(KeyDef.key("V", K_V));
        r.add(KeyDef.key("B", K_B));
        r.add(KeyDef.key("N", K_N));
        r.add(KeyDef.key("M", K_M));
        r.add(sym(",", "<", K_COMMA, 1.0f));
        r.add(sym(".", ">", K_PERIOD, 1.0f));
        r.add(sym("/", "?", K_SLASH, 1.0f));

        KeyDef rs = KeyDef.mod("Shift", "⇧", M_RSHIFT, 2.4f);
        rs.textScale = 0.85f;
        r.add(rs);
        return r;
    }

    /** 最下面一行 */
    private static List<KeyDef> rowBottom(boolean mac) {
        List<KeyDef> r = new ArrayList<>();

        KeyDef lctrl = KeyDef.mod("Ctrl", "Control", M_LCTRL, 1.1f);
        lctrl.textScale = 0.72f;
        r.add(lctrl);

        KeyDef fn = new KeyDef();
        fn.label = "Fn";
        fn.type = KeyDef.T_FN;
        fn.units = 1.0f;
        r.add(fn);

        KeyDef lalt = KeyDef.mod("Alt", mac ? "Option" : "Alt", M_LALT, 1.0f);
        lalt.textScale = 0.78f;
        r.add(lalt);

        KeyDef lcmd = KeyDef.mod(mac ? "Cmd" : "Win", mac ? "Command" : "Windows", M_LGUI, 1.2f);
        lcmd.textScale = 0.72f;
        r.add(lcmd);

        KeyDef space = KeyDef.key("", K_SPACE);
        space.units = 6.2f;
        space.sub = "Space";
        space.subColor = 0xFF6E737B;
        r.add(space);

        KeyDef rctrl = KeyDef.mod("Ctrl", "Control", M_RCTRL, 1.1f);
        rctrl.textScale = 0.72f;
        r.add(rctrl);

        KeyDef ralt = KeyDef.mod("Alt", mac ? "Option" : "Alt", M_RALT, 1.0f);
        ralt.textScale = 0.78f;
        r.add(ralt);

        KeyDef pgup = KeyDef.key("↑", K_PGUP);
        pgup.secondLine = "PgUp";
        pgup.halfTop = true;
        pgup.units = 1.0f;
        r.add(pgup);

        KeyDef pgdn = KeyDef.key("↓", K_PGDN);
        pgdn.secondLine = "PgDn";
        pgdn.halfBottom = true;
        pgdn.sameColumn = true;
        pgdn.units = 1.0f;
        r.add(pgdn);

        KeyDef home = KeyDef.key("←", K_HOME);
        home.secondLine = "Home";
        home.units = 1.0f;
        r.add(home);

        KeyDef end = KeyDef.key("→", K_END);
        end.secondLine = "End";
        end.units = 1.0f;
        r.add(end);

        return r;
    }

    private static KeyDef sym(String label, String shift, int usage, float units) {
        KeyDef k = KeyDef.key(label, usage);
        k.shiftLabel = shift;
        k.units = units;
        return k;
    }
}
