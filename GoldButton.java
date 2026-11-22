package com.goldautosell.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Flat black/gold button. When {@code accent} is true it is filled gold with black text. */
public class GoldButton extends ButtonWidget {
    public boolean accent = false;

    public GoldButton(int x, int y, int w, int h, Text msg, PressAction action) {
        super(x, y, w, h, msg, action, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        boolean hov = this.isHovered() && this.active;
        int bg = accent ? (hov ? Theme.GOLD_LIGHT : Theme.GOLD) : (hov ? Theme.PANEL_HOVER : Theme.PANEL);
        int border = accent ? Theme.GOLD_LIGHT : (hov ? Theme.GOLD_LIGHT : Theme.GOLD_DARK);
        int text = accent ? 0xFF0B0B0F : Theme.WHITE;
        Theme.rect(c, getX(), getY(), getWidth(), getHeight(), bg);
        Theme.outline(c, getX(), getY(), getWidth(), getHeight(), border);
        c.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, text);
    }
}
