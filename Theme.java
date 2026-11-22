package com.goldautosell.gui;

import net.minecraft.client.gui.DrawContext;

public final class Theme {
    public static final int BG = 0xFF0B0B0F;
    public static final int PANEL = 0xFF13131A;
    public static final int PANEL_HOVER = 0xFF23232C;
    public static final int GOLD = 0xFFD4AF37;
    public static final int GOLD_LIGHT = 0xFFFFD75E;
    public static final int GOLD_DARK = 0xFF8A6D1F;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int MUTED = 0xFFA0A0A8;
    public static final int OK = 0xFF6BE675;
    public static final int ERR = 0xFFFF5C5C;

    private Theme() {}

    public static void rect(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + h, col);
    }

    public static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        rect(c, x, y, w, 1, col);
        rect(c, x, y + h - 1, w, 1, col);
        rect(c, x, y, 1, h, col);
        rect(c, x + w - 1, y, 1, h, col);
    }
}
