package com.goldautosell.gui;

import com.goldautosell.ModConfig;
import com.goldautosell.SellEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public class GoldScreen extends Screen {
    private static final int W = 320, H = 310;

    private int px, py;
    private TextFieldWidget priceField, amountField, delayField;
    private GoldButton autoBtn;

    public GoldScreen() {
        super(Text.literal("Gold AutoSell"));
    }

    public boolean isTyping() {
        return (priceField != null && priceField.isFocused())
                || (amountField != null && amountField.isFocused())
                || (delayField != null && delayField.isFocused());
    }

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.INSTANCE;
        px = (width - W) / 2;
        py = (height - H) / 2;

        // ---- inventory picker (3 rows of main inventory + hotbar)
        int gx = px + (W - 180) / 2, gy = py + 46;
        for (int i = 0; i < 27; i++) addDrawableChild(slot(gx + (i % 9) * 20, gy + (i / 9) * 20, 9 + i));
        for (int i = 0; i < 9; i++) addDrawableChild(slot(gx + i * 20, gy + 64, i));

        // ---- close + clear
        addDrawableChild(new GoldButton(px + W - 22, py + 8, 14, 14, Text.literal("x"), b -> close()));
        addDrawableChild(new GoldButton(px + W - 64, py + 134, 50, 16, Text.literal("Clear"), b -> cfg.item = ""));

        // ---- price
        priceField = new TextFieldWidget(textRenderer, px + 14, py + 166, 138, 18, Text.literal("Price"));
        priceField.setMaxLength(16);
        priceField.setTextPredicate(s -> s.matches("[0-9.kKmMbB]*"));
        priceField.setText(cfg.price);
        priceField.setChangedListener(s -> cfg.price = s);
        addDrawableChild(priceField);

        // ---- amount
        amountField = new TextFieldWidget(textRenderer, px + 168, py + 166, 138, 18, Text.literal("Amount"));
        amountField.setMaxLength(2);
        amountField.setTextPredicate(s -> s.matches("\\d{0,2}"));
        amountField.setText(String.valueOf(cfg.amount));
        amountField.setChangedListener(s -> {
            if (!s.isEmpty()) cfg.amount = Math.min(64, Integer.parseInt(s));
        });
        addDrawableChild(amountField);

        // ---- amount presets
        String[] labels = {"1", "16", "32", "64", "ALL"};
        int[] values = {1, 16, 32, 64, 0};
        for (int i = 0; i < 5; i++) {
            final int v = values[i];
            addDrawableChild(new GoldButton(px + 14 + i * 60, py + 190, 52, 16, Text.literal(labels[i]), b -> {
                cfg.amount = v;
                amountField.setText(String.valueOf(v));
            }));
        }

        // ---- delay
        addDrawableChild(new GoldButton(px + 100, py + 212, 20, 18, Text.literal("-"), b -> changeDelay(-250)));
        delayField = new TextFieldWidget(textRenderer, px + 124, py + 212, 70, 18, Text.literal("Delay"));
        delayField.setMaxLength(5);
        delayField.setTextPredicate(s -> s.matches("\\d{0,5}"));
        delayField.setText(String.valueOf(cfg.delayMs));
        delayField.setChangedListener(s -> {
            if (!s.isEmpty()) cfg.delayMs = Integer.parseInt(s);
        });
        addDrawableChild(delayField);
        addDrawableChild(new GoldButton(px + 198, py + 212, 20, 18, Text.literal("+"), b -> changeDelay(250)));

        // ---- main actions
        autoBtn = new GoldButton(px + 14, py + 238, 140, 22, Text.empty(), b -> {
            cfg.autoSell = !cfg.autoSell;
            ModConfig.save();
            refreshAutoButton();
        });
        addDrawableChild(autoBtn);
        refreshAutoButton();

        GoldButton sell = new GoldButton(px + 166, py + 238, 140, 22, Text.literal("SELL NOW"), b -> SellEngine.manualSell());
        sell.accent = true;
        addDrawableChild(sell);
    }

    private SlotButton slot(int x, int y, int invIdx) {
        return new SlotButton(x, y, invIdx, b -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            ItemStack s = mc.player.getInventory().getStack(invIdx);
            if (!s.isEmpty()) {
                ModConfig.INSTANCE.item = Registries.ITEM.getId(s.getItem()).toString();
                ModConfig.save();
            }
        });
    }

    private void changeDelay(int d) {
        ModConfig cfg = ModConfig.INSTANCE;
        cfg.delayMs = Math.max(ModConfig.MIN_DELAY, Math.min(60000, cfg.delayMs + d));
        delayField.setText(String.valueOf(cfg.delayMs));
    }

    private void refreshAutoButton() {
        boolean on = ModConfig.INSTANCE.autoSell;
        autoBtn.accent = on;
        autoBtn.setMessage(Text.literal("AUTOSELL: " + (on ? "ON" : "OFF")));
    }

    @Override
    public boolean shouldPause() { return false; }

    /** Background is drawn manually in render(); this stops vanilla blur/dirt from being drawn. */
    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        ModConfig cfg = ModConfig.INSTANCE;
        // keep the toggle in sync (server error can pause AutoSell)
        if (autoBtn != null && autoBtn.accent != cfg.autoSell) refreshAutoButton();

        // dim + panel
        c.fill(0, 0, width, height, 0xB0000000);
        Theme.rect(c, px, py, W, H, Theme.BG);
        Theme.outline(c, px, py, W, H, Theme.GOLD);
        Theme.outline(c, px + 2, py + 2, W - 4, H - 4, Theme.GOLD_DARK);
        Theme.rect(c, px + 3, py + 3, W - 6, 28, 0xFF111118);
        Theme.rect(c, px + 3, py + 31, W - 6, 1, Theme.GOLD);

        super.render(c, mouseX, mouseY, delta); // widgets

        // ---- header
        c.drawText(textRenderer, "GOLD AUTOSELL", px + 14, py + 8, Theme.GOLD_LIGHT, true);
        c.drawText(textRenderer, "DonutSMP  |  /ah sell", px + 14, py + 19, Theme.MUTED, false);
        c.drawText(textRenderer, "SELECT ITEM", px + 14, py + 36, Theme.GOLD, false);

        // gold outline around text fields
        Theme.outline(c, priceField.getX() - 1, priceField.getY() - 1, priceField.getWidth() + 2, priceField.getHeight() + 2,
                priceField.isFocused() ? Theme.GOLD_LIGHT : Theme.GOLD_DARK);
        Theme.outline(c, amountField.getX() - 1, amountField.getY() - 1, amountField.getWidth() + 2, amountField.getHeight() + 2,
                amountField.isFocused() ? Theme.GOLD_LIGHT : Theme.GOLD_DARK);
        Theme.outline(c, delayField.getX() - 1, delayField.getY() - 1, delayField.getWidth() + 2, delayField.getHeight() + 2,
                delayField.isFocused() ? Theme.GOLD_LIGHT : Theme.GOLD_DARK);

        // ---- selected item row
        Item sel = SellEngine.resolveItem(cfg.item);
        if (sel != null) {
            c.drawItem(new ItemStack(sel), px + 14, py + 134);
            c.drawText(textRenderer, textRenderer.trimToWidth(sel.getName().getString(), 190), px + 36, py + 138, Theme.WHITE, true);
        } else {
            c.drawText(textRenderer, "No item selected - click a slot", px + 14, py + 138, Theme.MUTED, false);
        }

        // ---- labels
        c.drawText(textRenderer, "PRICE (1000, 2.5k, 1m)", px + 14, py + 156, Theme.GOLD, false);
        c.drawText(textRenderer, "AMOUNT (0 = ALL)", px + 168, py + 156, Theme.GOLD, false);
        c.drawText(textRenderer, "DELAY (ms)", px + 14, py + 217, Theme.GOLD, false);
        c.drawText(textRenderer, "min " + ModConfig.MIN_DELAY, px + 226, py + 217, Theme.MUTED, false);

        // ---- status
        Theme.rect(c, px + 14, py + 266, W - 28, 1, Theme.GOLD_DARK);
        c.drawText(textRenderer, textRenderer.trimToWidth("Status: " + SellEngine.status, W - 28),
                px + 14, py + 272, SellEngine.statusColor, false);
        long left = SellEngine.cooldownLeft();
        String info = "Listings sent: " + SellEngine.listedCount
                + (left > 0 ? "   |   Cooldown: " + String.format("%.1fs", left / 1000.0) : "   |   Ready")
                + (SellEngine.isBusy() ? "   |   Working..." : "");
        c.drawText(textRenderer, info, px + 14, py + 284, Theme.WHITE, false);
        c.drawText(textRenderer, "Amount 1 = single item (stack is split via empty hotbar slot)", px + 14, py + 296, Theme.MUTED, false);
    }

    @Override
    public void removed() {
        ModConfig cfg = ModConfig.INSTANCE;
        cfg.sanitize();
        ModConfig.save();
    }
}
