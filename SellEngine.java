package com.goldautosell;

import com.goldautosell.gui.GoldScreen;
import com.goldautosell.gui.ScreenScanner;
import com.goldautosell.gui.Theme;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Handles listing items with /ah sell. Work is split into small steps that run one per client tick,
 * so the server never receives a burst of packets. A busy flag + cooldown prevents duplicate listings.
 */
public final class SellEngine {
    @FunctionalInterface
    private interface Step { void run(MinecraftClient mc, ClientPlayerEntity p) throws Fail; }

    private static final class Fail extends Exception {
        Fail(String m) { super(m); }
    }

    private static final Deque<Step> steps = new ArrayDeque<>();
    private static boolean busy = false;
    private static long nextAllowedAt = 0;
    private static long responseUntil = 0;
    private static int cleanupSlot = -1;
    private static final int CLICKS_PER_TICK = 4;

    // --- confirmation dialog handling ("Are you sure you want to sell this?")
    private static long awaitConfirmUntil = 0;
    private static String pendingPrice = "";
    private static Screen confirmScreen = null;
    private static int confirmTicks = 0;
    private static final Pattern PRICE_RX = Pattern.compile("\\$\\s*(\\d[\\d.,]*\\s*[kKmMbB]?)");

    public static String status = "Idle";
    public static int statusColor = Theme.MUTED;
    public static int listedCount = 0;

    private SellEngine() {}

    public static boolean isBusy() { return busy; }
    public static long cooldownLeft() { return Math.max(0, nextAllowedAt - System.currentTimeMillis()); }

    public static Item resolveItem(String id) {
        if (id == null || id.isEmpty()) return null;
        Identifier ident = Identifier.tryParse(id);
        if (ident == null || !Registries.ITEM.containsId(ident)) return null;
        Item it = Registries.ITEM.get(ident);
        return it == net.minecraft.item.Items.AIR ? null : it;
    }

    private static int delay() { return Math.max(ModConfig.MIN_DELAY, ModConfig.INSTANCE.delayMs); }

    private static void setStatus(String s, int color) { status = s; statusColor = color; }
    private static void error(String s) { setStatus(s, Theme.ERR); GoldAutoSellClient.LOGGER.info("[AutoSell] {}", s); }

    private static boolean canAct(MinecraftClient mc) {
        return mc.currentScreen == null || mc.currentScreen instanceof GoldScreen;
    }

    private static int slotId(int invIdx) { return invIdx < 9 ? 36 + invIdx : invIdx; }

    private static void click(MinecraftClient mc, ClientPlayerEntity p, int invIdx, int button, SlotActionType type) {
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slotId(invIdx), button, type, p);
    }

    private static int findEmptyHotbar(PlayerInventory inv) {
        int sel = inv.getSelectedSlot();
        if (inv.getStack(sel).isEmpty()) return sel;
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isEmpty()) return i;
        return -1;
    }

    // ------------------------------------------------------------------ public API

    public static void manualSell() { start(false); }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            if (busy) { steps.clear(); busy = false; }
            return;
        }
        handleConfirm(mc, p);
        if (busy) {
            if (!canAct(mc)) { abort(mc, p, "Interrupted: another menu was opened"); return; }
            Step s = steps.poll();
            if (s == null) { busy = false; return; }
            try {
                s.run(mc, p);
            } catch (Fail f) {
                abort(mc, p, f.getMessage());
            } catch (RuntimeException e) {
                GoldAutoSellClient.LOGGER.error("AutoSell step failed", e);
                abort(mc, p, "Internal error: " + e.getClass().getSimpleName());
            }
            if (steps.isEmpty()) busy = false;
            return;
        }
        if (!ModConfig.INSTANCE.autoSell) return;
        if (!canAct(mc)) return;
        if (System.currentTimeMillis() < nextAllowedAt) return;
        start(true);
    }

    /** Called for every system chat message; used to show the server's reply to /ah sell. */
    public static void onServerMessage(Text t) {
        long now = System.currentTimeMillis();
        if (now > responseUntil) return;
        String msg = t.getString().trim();
        if (msg.isEmpty()) return;
        String l = msg.toLowerCase(Locale.ROOT);
        boolean bad = l.contains("cannot") || l.contains("can't") || l.contains("invalid") || l.contains("error")
                || l.contains("not enough") || l.contains("limit") || l.contains("full") || l.contains("must")
                || l.contains("unknown") || l.contains("failed") || l.contains("permission") || l.contains("wait");
        String shown = msg.length() > 90 ? msg.substring(0, 87) + "..." : msg;
        responseUntil = 0;
        if (bad) {
            if (ModConfig.INSTANCE.autoSell) {
                ModConfig.INSTANCE.autoSell = false;
                ModConfig.save();
                shown += "  (AutoSell paused)";
            }
            error("Server: " + shown);
        } else {
            setStatus("Server: " + shown, Theme.OK);
        }
    }

    // ------------------------------------------------------------------ core

    private static void start(boolean auto) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || p.networkHandler == null) {
            if (!auto) error("Not connected to a world/server");
            return;
        }
        if (!canAct(mc)) { if (!auto) error("Close other menus first"); return; }
        if (busy) { if (!auto) error("Busy - previous listing still in progress"); return; }
        long left = cooldownLeft();
        if (left > 0) {
            if (!auto) error("Cooldown active - wait " + left + " ms (duplicate protection)");
            return;
        }

        ModConfig cfg = ModConfig.INSTANCE;
        final Item item = resolveItem(cfg.item);
        if (item == null) { failStart(auto, "No item selected - pick one in the GUI"); return; }
        if (!ModConfig.validPrice(cfg.price)) { failStart(auto, "Invalid price: '" + cfg.price + "'"); return; }

        final String name = item.getName().getString();
        final String cmd = "ah sell " + cfg.price;
        final int delay = delay();
        final int want = cfg.amount; // 0 = whole stack

        PlayerInventory inv = p.getInventory();
        int best = -1;
        boolean any = false;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !s.isOf(item)) continue;
            any = true;
            int c = s.getCount();
            if (want == 0) {
                if (best == -1 || c > inv.getStack(best).getCount()) best = i;
            } else if (c >= want) {
                // use the smallest stack that still satisfies the amount (clears partial stacks first)
                if (best == -1 || c < inv.getStack(best).getCount()) best = i;
            }
        }
        if (best == -1) {
            if (auto) {
                setStatus("AutoSell waiting for " + (any ? want + "x " : "") + name + " in one stack...", Theme.GOLD);
            } else {
                error(any ? "No single stack with at least " + want + "x " + name : "No " + name + " in inventory");
            }
            return;
        }

        final int src = best;
        final int stackCount = inv.getStack(src).getCount();
        final int expected = (want == 0 || want >= stackCount) ? stackCount : want;
        final boolean whole = expected == stackCount;
        cleanupSlot = src;
        steps.clear();

        final int hot;
        if (whole) {
            if (src < 9) {
                hot = src;
            } else {
                int e = findEmptyHotbar(inv);
                hot = e >= 0 ? e : inv.getSelectedSlot();
                steps.add((m, pl) -> click(m, pl, src, hot, SlotActionType.SWAP));
            }
        } else {
            int e = findEmptyHotbar(inv);
            if (e < 0) { failStart(auto, "No empty hotbar slot - needed to split the stack"); return; }
            hot = e;
            steps.add((m, pl) -> click(m, pl, src, 0, SlotActionType.PICKUP)); // pick up whole stack
            int remaining = expected;
            while (remaining > 0) {
                final int n = Math.min(CLICKS_PER_TICK, remaining);
                remaining -= n;
                steps.add((m, pl) -> {
                    for (int k = 0; k < n; k++) click(m, pl, hot, 1, SlotActionType.PICKUP); // right-click = place 1
                });
            }
            steps.add((m, pl) -> click(m, pl, src, 0, SlotActionType.PICKUP)); // put the remainder back
        }

        steps.add((m, pl) -> pl.getInventory().setSelectedSlot(hot));
        steps.add((m, pl) -> {}); // let the server sync
        steps.add((m, pl) -> {});
        steps.add((m, pl) -> {
            ItemStack h = pl.getMainHandStack();
            if (!h.isOf(item) || h.getCount() != expected) {
                throw new Fail("Held stack mismatch (expected " + expected + "x " + name + ") - aborted");
            }
        });
        steps.add((m, pl) -> {
            pl.networkHandler.sendChatCommand(cmd);
            long now = System.currentTimeMillis();
            nextAllowedAt = now + delay;
            responseUntil = now + 3500;
            pendingPrice = cfg.price;
            awaitConfirmUntil = now + 8000;
            confirmScreen = null;
            listedCount++;
            cleanupSlot = -1;
            setStatus("Sent /" + cmd + "  (" + expected + "x " + name + ")", Theme.OK);
        });

        busy = true;
        nextAllowedAt = System.currentTimeMillis() + delay; // block duplicates while working
        setStatus("Preparing " + expected + "x " + name + "...", Theme.GOLD);
    }

    /**
     * Server asks "Are you sure you want to sell this?". We only press Yes when the price shown in the
     * dialog is exactly the price we sent. Any mismatch or unreadable price -> press No and pause AutoSell.
     */
    private static void handleConfirm(MinecraftClient mc, ClientPlayerEntity p) {
        if (awaitConfirmUntil == 0) return;
        long now = System.currentTimeMillis();
        Screen s = mc.currentScreen;
        if (s == null || s instanceof GoldScreen || s instanceof ChatScreen) {
            confirmScreen = null;
            if (now > awaitConfirmUntil) {
                awaitConfirmUntil = 0;
                if (status.startsWith("Sent /")) setStatus("Command sent (no confirmation dialog appeared)", Theme.GOLD);
            }
            return;
        }
        if (s != confirmScreen) { confirmScreen = s; confirmTicks = 0; }
        confirmTicks++;
        if (confirmTicks < 4) return; // let the screen finish building

        ScreenScanner.Result r = ScreenScanner.scan(mc, p, s);
        BigDecimal shown = null;
        String shownRaw = null;
        Matcher m = PRICE_RX.matcher(String.join(" ", r.texts));
        while (m.find()) {
            int a = Math.max(0, m.start() - 3);
            if (String.join(" ", r.texts).substring(a, m.start()).contains("+")) continue; // skip "(+$ 20 tax)"
            shownRaw = m.group(1).trim();
            shown = ModConfig.parsePrice(shownRaw);
            if (shown != null) break;
        }

        if (r.yes == null || shown == null) {
            if (confirmTicks < 14) return; // maybe still loading
            GoldAutoSellClient.LOGGER.info("[AutoSell] Unrecognised confirmation screen {} texts={}", s.getClass().getName(), r.texts);
            awaitConfirmUntil = 0;
            declineAndPause(mc, r, "Could not read price/buttons in the confirmation - NOT confirmed (see latest.log)");
            return;
        }

        BigDecimal expected = ModConfig.parsePrice(pendingPrice);
        awaitConfirmUntil = 0;
        if (expected != null && expected.compareTo(shown) == 0) {
            r.yes.run();
            responseUntil = now + 3500;
            nextAllowedAt = now + delay();
            setStatus("Confirmed sale for $" + shownRaw + " (matches your price " + pendingPrice + ")", Theme.OK);
        } else {
            declineAndPause(mc, r, "BLOCKED: dialog shows $" + shownRaw + " but your price is " + pendingPrice + " - declined");
        }
    }

    private static void declineAndPause(MinecraftClient mc, ScreenScanner.Result r, String msg) {
        if (r.no != null) r.no.run(); else mc.setScreen(null);
        if (ModConfig.INSTANCE.autoSell) {
            ModConfig.INSTANCE.autoSell = false;
            ModConfig.save();
            msg += " (AutoSell paused)";
        }
        nextAllowedAt = System.currentTimeMillis() + Math.max(1000, delay());
        error(msg);
    }

    private static void failStart(boolean auto, String msg) {
        error(msg);
        if (auto) nextAllowedAt = System.currentTimeMillis() + delay();
    }

    private static void abort(MinecraftClient mc, ClientPlayerEntity p, String msg) {
        steps.clear();
        busy = false;
        try {
            if (cleanupSlot >= 0 && !p.currentScreenHandler.getCursorStack().isEmpty()) {
                click(mc, p, cleanupSlot, 0, SlotActionType.PICKUP); // put the held stack back
            }
        } catch (RuntimeException ignored) {}
        cleanupSlot = -1;
        nextAllowedAt = System.currentTimeMillis() + Math.max(1000, delay());
        error(msg);
    }
}
