package com.goldautosell.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Reads all text from a confirmation screen ("Are you sure you want to sell this?") and finds the
 * Yes / No buttons. Works for server dialogs (button widgets) and for chest-style menus (items).
 * The widget tree is walked by type, not by field name, so it does not depend on mappings.
 */
public final class ScreenScanner {
    public static final class Result {
        public final List<String> texts = new ArrayList<>();
        public Runnable yes, no;
    }

    private static final String[] SKIP = {"MinecraftClient", "TextRenderer", "Font", "Registry", "World", "Entity",
            "NetworkHandler", "Texture", "Sprite", "Resource", "Window", "Mouse", "Keyboard", "GameOptions"};

    private ScreenScanner() {}

    public static Result scan(MinecraftClient mc, ClientPlayerEntity p, Screen screen) {
        Result r = new Result();

        if (screen instanceof HandledScreen<?> hs) {
            ScreenHandler h = hs.getScreenHandler();
            for (Slot sl : h.slots) {
                if (sl.inventory == p.getInventory()) continue;
                ItemStack st = sl.getStack();
                if (st.isEmpty()) continue;
                String name = st.getName().getString();
                r.texts.add(name);
                LoreComponent lore = st.get(DataComponentTypes.LORE);
                if (lore != null) for (Text t : lore.lines()) r.texts.add(t.getString());
                String l = name.toLowerCase(Locale.ROOT).trim();
                Runnable click = () -> mc.interactionManager.clickSlot(h.syncId, sl.id, 0, SlotActionType.PICKUP, p);
                if (r.yes == null && isYes(l)) r.yes = click;
                else if (r.no == null && isNo(l)) r.no = click;
            }
            return r;
        }

        List<ClickableWidget> widgets = new ArrayList<>();
        walk(screen, 0, new IdentityHashMap<>(), r, widgets);
        for (ClickableWidget w : widgets) {
            if (!w.visible || !w.active) continue;
            String l = w.getMessage().getString().toLowerCase(Locale.ROOT).trim();
            if (r.yes == null && isYes(l)) r.yes = () -> click(screen, w);
            else if (r.no == null && isNo(l)) r.no = () -> click(screen, w);
        }
        return r;
    }

    private static boolean isYes(String l) { return l.equals("yes") || l.equals("confirm") || l.equals("accept"); }
    private static boolean isNo(String l) { return l.equals("no") || l.equals("cancel") || l.equals("decline"); }

    /** Simulates a real left click in the centre of the widget. */
    private static void click(Screen screen, ClickableWidget w) {
        double x = w.getX() + w.getWidth() / 2.0;
        double y = w.getY() + w.getHeight() / 2.0;
        Click c = new Click(x, y, new MouseInput(0, 0));
        screen.mouseClicked(c, false);
        screen.mouseReleased(c);
    }

    private static void walk(Object o, int depth, IdentityHashMap<Object, Boolean> seen, Result r, List<ClickableWidget> widgets) {
        if (o == null || depth > 6 || seen.size() > 4000) return;
        if (o instanceof Text t) { r.texts.add(t.getString()); return; }
        Class<?> c = o.getClass();
        if (c.isPrimitive() || o instanceof Number || o instanceof Boolean || o instanceof CharSequence
                || o instanceof Enum || o instanceof Character) return;
        String n = c.getName();
        if (c.isSynthetic() || n.contains("$$Lambda")) return;
        boolean container = o instanceof Iterable || o instanceof Map || c.isArray();
        if (!container && !n.startsWith("net.minecraft")) return;
        for (String s : SKIP) if (n.contains(s)) return;
        if (seen.put(o, Boolean.TRUE) != null) return;

        if (o instanceof ClickableWidget w && !widgets.contains(w)) widgets.add(w);

        if (o instanceof Iterable<?> it) { for (Object e : it) walk(e, depth + 1, seen, r, widgets); return; }
        if (o instanceof Map<?, ?> m) { for (Object e : m.values()) walk(e, depth + 1, seen, r, widgets); return; }
        if (c.isArray()) {
            if (!c.getComponentType().isPrimitive()) for (Object e : (Object[]) o) walk(e, depth + 1, seen, r, widgets);
            return;
        }
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    walk(f.get(o), depth + 1, seen, r, widgets);
                } catch (Throwable ignored) {}
            }
        }
    }
}
