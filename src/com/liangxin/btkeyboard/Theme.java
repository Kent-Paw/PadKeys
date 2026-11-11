package com.liangxin.btkeyboard;

/** 两套配色：深色（默认）/ 浅色 */
public class Theme {

    public int rootBg;
    public int topBarBg;
    public int zoneBg;
    public int keyCap;
    public int keyMod;
    public int keyPressed;
    public int textMain;
    public int textSub;
    public int textTitle;
    public int bodyTop, bodyMid, bodyBottom;   // 键盘机身渐变
    public int strokeColor;
    public int padBg;

    public static Theme dark() {
        Theme t = new Theme();
        t.rootBg = 0xFF181B21;
        t.topBarBg = 0xFF12141A;
        t.zoneBg = 0xFF15181D;
        t.keyCap = 0xFF26282D;
        t.keyMod = 0xFF1E2024;
        t.keyPressed = 0xFF565A61;
        t.textMain = 0xFFE8EAED;
        t.textSub = 0xFF7C828B;
        t.textTitle = 0xFF9AA0A6;
        t.bodyTop = 0xFF20242B;
        t.bodyMid = 0xFF191D23;
        t.bodyBottom = 0xFF14171C;
        t.strokeColor = 0x22000000;
        t.padBg = 0xFF191C22;
        return t;
    }

    public static Theme light() {
        Theme t = new Theme();
        t.rootBg = 0xFFF1F3F6;
        t.topBarBg = 0xFFFFFFFF;
        t.zoneBg = 0xFFE9ECF1;
        t.keyCap = 0xFFFFFFFF;
        t.keyMod = 0xFFE8EBEF;
        t.keyPressed = 0xFFBFC6D1;
        t.textMain = 0xFF1B1E23;
        t.textSub = 0xFF6E747C;
        t.textTitle = 0xFF41474E;
        t.bodyTop = 0xFFFDFDFE;
        t.bodyMid = 0xFFEDF0F4;
        t.bodyBottom = 0xFFDCE0E6;
        t.strokeColor = 0x1A000000;
        t.padBg = 0xFFFBFCFD;
        return t;
    }

    public static Theme get(boolean light) {
        return light ? light() : dark();
    }
}
