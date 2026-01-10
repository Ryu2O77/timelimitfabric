package com.ryu.timelimit;

import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public int autosaveIntervalSeconds = 60;
    public String dataFileName = "players.json";

    public int defaultTime = 21600;
    public Reset reset = new Reset();
    public Warning[] warnings = new Warning[] {};
    public Messages messages = new Messages();
    public Bossbar bossbar = new Bossbar();

    public boolean dailyResetEnabled = true;
    public boolean kickOnExpire = true;
    public String kickMessage = "Tu tiempo de juego de hoy ha terminado. ¡Nos vemos mañana!";

    // Lista de bypass e info de permisos
    public List<String> immunePlayers = new ArrayList<>();
    public String permissionBypassNode = "timelimit.bypass";
    public String permissionAdminNode = "timelimit.admin";

    // Bienvenida
    public boolean sendWelcomeOnJoin = true;

    // Reset incremental para evitar lag spikes
    public boolean incrementalResetEnabled = true;
    public int resetBatchSize = 200;
    public int resetBatchIntervalTicks = 2;
    public int resetSaveEveryBatches = 5;

    // AFK
    public Afk afk = new Afk();

    // HUD (solo ActionBar)
    public Hud hud = new Hud();

    // Perfiles por LuckPerms (override de defaultTime por permiso)
    public List<Profile> profiles = new ArrayList<>();

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("timelimitmod");
    }
    public static Path configPath() {
        return configDir().resolve("config.json");
    }

    public static Config loadFromFabricConfigDir() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(configDir());
            Path path = configPath();
            if (Files.exists(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                Config cfg = gson.fromJson(json, Config.class);
                if (cfg == null) cfg = new Config();
                Files.writeString(path, gson.toJson(cfg), StandardCharsets.UTF_8);
                return cfg;
            } else {
                Config cfg = new Config();
                Files.writeString(path, gson.toJson(cfg), StandardCharsets.UTF_8);
                return cfg;
            }
        } catch (IOException e) {
            return new Config();
        }
    }

    public static void save(Config cfg) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(configDir());
            Files.writeString(configPath(), gson.toJson(cfg), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    public static final class Reset {
        public String time = "07:00";
        public String timezone = "America/Argentina/Buenos_Aires";
    }
    public static final class Warning {
        public int time;
        public String title = "";
        public String message = "";
    }
    public static final class Messages {
        public String prefix = "&e&lSERVER &8» &7";
        public String timeExpired = "Tu tiempo de juego de hoy ha terminado. ¡Nos vemos mañana! \n Reinicio de horas a las 07:00 AM hora Argentina";
        public String welcome = "¡Bienvenido! Tienes &e%time% &7de tiempo de juego restantes.";
        public String timeAdded = "Se han agregado &e%time%&7 a tu sesión!";
        public String timeRemoved = "Se han removido &e%time%&7 de tu sesión.";
        public String timeReset = "Tu tiempo ha sido reiniciado a &e%time%&7.";
        public String paused = "Tu tiempo está actualmente pausado.";
        public String broadcastReset = "&eReset diario ejecutado. Próximo: &f%next%";
        public String reloadApplied = "Configuración recargada.";
    }
    public static final class Bossbar {
        public String message = "Tiempo restante: %hours%h %minutes%m %seconds%s";
        public String color = "WHITE";
    }
    public static final class Afk {
        public boolean enabled = false;
        public int inactivitySeconds = 300;
        public boolean pauseCounting = true;
        public double moveThreshold = 0.1;
        public double rotationThreshold = 1.0;
    }
    public static final class Hud {
        public String actionBarMessage = "Tiempo restante: %hours%h %minutes%m %seconds%s";
    }
    public static final class Profile {
        public String node;
        public int defaultTime;
    }
}