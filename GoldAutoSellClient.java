package com.goldautosell;

import com.goldautosell.gui.GoldScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoldAutoSellClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("goldautosell");

    private static KeyBinding openKey;
    private static boolean rightShiftPrev = false;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.goldautosell.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of("goldautosell", "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(GoldAutoSellClient::tick);

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) SellEngine.onServerMessage(message);
        });
    }

    private static void tick(MinecraftClient mc) {
        boolean rs = mc.getWindow() != null
                && GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        // Right Shift also closes the GUI (unless the user is typing in a text field)
        if (mc.currentScreen instanceof GoldScreen gs && rs && !rightShiftPrev && !gs.isTyping()) {
            mc.setScreen(null);
        }

        while (openKey.wasPressed()) {
            if (mc.currentScreen == null && mc.player != null) {
                mc.setScreen(new GoldScreen());
            }
        }
        rightShiftPrev = rs;

        SellEngine.tick(mc);
    }
}
