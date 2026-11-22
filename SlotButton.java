package com.goldautosell.gui;

import com.goldautosell.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

/** One inventory slot in the picker grid. Clicking selects that slot's item type. */
public class SlotButton extends ButtonWidget {
    private final int invIndex;

    public SlotButton(int x, int y, int invIndex, PressAction action) {
        super(x, y, 20, 20, Text.empty(), action, DEFAULT_NARRATION_SUPPLIER);
        this.invIndex = invIndex;
    }

    @Override
    public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getInventory().getStack(invIndex);
        boolean selected = !stack.isEmpty()
                && Registries.ITEM.getId(stack.getItem()).toString().equals(ModConfig.INSTANCE.item);
        boolean hov = this.isHovered();

        Theme.rect(c, getX(), getY(), 20, 20, selected ? 0xFF2A2210 : (hov ? Theme.PANEL_HOVER : Theme.PANEL));
        Theme.outline(c, getX(), getY(), 20, 20, selected ? Theme.GOLD_LIGHT : (hov ? Theme.GOLD : 0xFF2C2C36));
        if (stack.isEmpty()) return;

        c.drawItem(stack, getX() + 2, getY() + 2);
        if (stack.getCount() > 1) {
            String n = String.valueOf(stack.getCount());
            c.drawText(mc.textRenderer, n, getX() + 19 - mc.textRenderer.getWidth(n), getY() + 11, Theme.WHITE, true);
        }
    }
}
