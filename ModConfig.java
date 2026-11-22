package com.goldautosell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent settings, stored in config/goldautosell.json */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("goldautosell.json");

    public static ModConfig INSTANCE = new ModConfig();

    /** Registry id of the selected item, e.g. "minecraft:gold_ingot". Empty = none. */
    public String item = "";
    /** Price passed to /ah sell. Supports 1000, 1.5k, 2m, 1b ... */
    public String price = "1000";
    /** Items per listing. 0 = whole stack. 1 = single item. */
    public int amount = 1;
    public boolean autoSell = false;
    /** Minimum delay between two listings (duplicate protection). */
    public int delayMs = 2000;

    public static final int MIN_DELAY = 500;

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                ModConfig c = GSON.fromJson(Files.readString(PATH), ModConfig.class);
                if (c != null) INSTANCE = c;
            }
        } catch (Exception e) {
            GoldAutoSellClient.LOGGER.warn("Could not read config, using defaults", e);
        }
        INSTANCE.sanitize();
    }

    public static void save() {
        try {
            INSTANCE.sanitize();
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            GoldAutoSellClient.LOGGER.warn("Could not save config", e);
        }
    }

    public void sanitize() {
        if (item == null) item = "";
        if (price == null) price = "";
        amount = Math.max(0, Math.min(64, amount));
        delayMs = Math.max(MIN_DELAY, Math.min(60000, delayMs));
    }

    public static boolean validPrice(String s) {
        if (s == null || !s.matches("^\\d+(\\.\\d{1,2})?[kKmMbB]?$")) return false;
        return s.chars().anyMatch(ch -> ch >= '1' && ch <= '9');
    }

    /** Converts "1k", "2.5m", "1,000" ... to a number. Returns null if it cannot be parsed. */
    public static BigDecimal parsePrice(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace(",", "").replace(" ", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)([kKmMbB]?)$").matcher(s);
        if (!m.matches()) return null;
        BigDecimal v = new BigDecimal(m.group(1));
        switch (m.group(2).toLowerCase()) {
            case "k": v = v.multiply(BigDecimal.valueOf(1_000L)); break;
            case "m": v = v.multiply(BigDecimal.valueOf(1_000_000L)); break;
            case "b": v = v.multiply(BigDecimal.valueOf(1_000_000_000L)); break;
            default: break;
        }
        return v;
    }
}
