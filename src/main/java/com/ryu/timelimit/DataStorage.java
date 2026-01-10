package com.ryu.timelimit;

import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DataStorage {
    private static String fileName = "players.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ConcurrentHashMap<UUID, PlayerEntry> PLAYERS = new ConcurrentHashMap<>();

    public enum DisplayMode {
        BOSSBAR, ACTIONBAR, SCOREBOARD, NONE;
        public static DisplayMode parse(String s) {
            if (s == null) return BOSSBAR;
            try { return DisplayMode.valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return BOSSBAR; }
        }
    }

    public static Path dataDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("timelimitmod");
    }
    public static Path dataPath() {
        return dataDir().resolve(fileName);
    }

    public static void init(String name) {
        if (name != null && !name.isBlank()) fileName = name;
        try { Files.createDirectories(dataDir()); } catch (IOException ignored) {}
    }

    public static void loadData() {
        try {
            Path path = dataPath();
            if (!Files.exists(path)) return;
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Type type = new TypeToken<List<PlayerEntry>>(){}.getType();
            List<PlayerEntry> list = GSON.fromJson(json, type);
            PLAYERS.clear();
            if (list != null) {
                for (PlayerEntry e : list) {
                    if (e != null && e.playerId != null) {
                        if (e.name == null) e.name = "";
                        // Compat: si venía hideBossbar=true, cámbialo a NONE
                        if (e.displayMode == null) {
                            e.displayMode = (e.hideBossbar != null && e.hideBossbar) ? DisplayMode.NONE : DisplayMode.BOSSBAR;
                        }
                        PLAYERS.put(e.playerId, e);
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    public static void saveAsync() {
        Completable.run(DataStorage::saveNow);
    }

    public static synchronized void saveNow() {
        try {
            Path path = dataPath();
            List<PlayerEntry> list = new ArrayList<>(PLAYERS.values());
            String json = GSON.toJson(list);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[TimeLimitMod] Error guardando players.json: " + e.getMessage());
        }
    }

    public static void ensureEntry(UUID id, int defaultSeconds, String name) {
        PLAYERS.compute(id, (k, v) -> {
            if (v == null) {
                PlayerEntry e = new PlayerEntry();
                e.playerId = id;
                e.remainingTime = Math.max(0, defaultSeconds);
                e.lastReset = System.currentTimeMillis();
                e.paused = false;
                e.displayMode = DisplayMode.BOSSBAR;
                e.name = name != null ? name : "";
                return e;
            } else {
                if (name != null && !name.isBlank()) v.name = name;
                if (v.displayMode == null) v.displayMode = DisplayMode.BOSSBAR;
                return v;
            }
        });
    }

    public static PlayerEntry get(UUID id) {
        return PLAYERS.get(id);
    }

    public static int getRemaining(UUID id, int defaultSeconds) {
        PlayerEntry e = PLAYERS.get(id);
        return e == null ? defaultSeconds : Math.max(0, e.remainingTime);
    }

    public static void setRemaining(UUID id, int seconds) {
        PlayerEntry e = PLAYERS.get(id);
        if (e != null) e.remainingTime = Math.max(0, seconds);
    }

    public static void addRemaining(UUID id, int seconds) {
        PlayerEntry e = PLAYERS.get(id);
        if (e != null) e.remainingTime = Math.max(0, e.remainingTime + Math.max(0, seconds));
    }

    public static boolean isPaused(UUID id) {
        PlayerEntry e = PLAYERS.get(id);
        return e != null && e.paused;
    }

    public static void setPaused(UUID id, boolean paused) {
        PlayerEntry e = PLAYERS.get(id);
        if (e != null) e.paused = paused;
    }

    public static DisplayMode getDisplayMode(UUID id) {
        PlayerEntry e = PLAYERS.get(id);
        return e != null && e.displayMode != null ? e.displayMode : DisplayMode.BOSSBAR;
    }

    public static void setDisplayMode(UUID id, DisplayMode mode) {
        PlayerEntry e = PLAYERS.get(id);
        if (e != null) e.displayMode = mode != null ? mode : DisplayMode.BOSSBAR;
    }

    public static void resetToDefault(UUID id, int defaultSeconds) {
        PlayerEntry e = PLAYERS.get(id);
        if (e != null) {
            e.remainingTime = Math.max(0, defaultSeconds);
            e.lastReset = System.currentTimeMillis();
        }
    }

    public static void resetAllToDefault(int defaultSeconds) {
        long now = System.currentTimeMillis();
        for (PlayerEntry e : PLAYERS.values()) {
            e.remainingTime = Math.max(0, defaultSeconds);
            e.lastReset = now;
        }
    }

    public static UUID lookupByNameOrUuid(String spec) {
        if (spec == null || spec.isBlank()) return null;
        String s = spec.trim();
        try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        for (PlayerEntry e : PLAYERS.values()) {
            if (e.name != null && e.name.equalsIgnoreCase(s)) return e.playerId;
        }
        return null;
    }

    public static Collection<PlayerEntry> allEntries() {
        return Collections.unmodifiableCollection(PLAYERS.values());
    }

    private static final class Completable {
        static void run(Runnable r) { Thread t = new Thread(r, "TimeLimitMod-Storage"); t.setDaemon(true); t.start(); }
    }

    public static final class PlayerEntry {
        public UUID playerId;
        public int remainingTime;
        public long lastReset;
        public boolean paused;
        @Deprecated public Boolean hideBossbar; // compat
        public DisplayMode displayMode;         // nuevo: preferencia de HUD
        public String name;
    }
}