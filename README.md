# TimeLimit Mod

Server-side Fabric mod that enforces a daily playtime limit per player, with a configurable HUD and an organized admin command suite.

## How it works
- Decrements each player’s remaining daily time in real time.
- Resets everyone’s remaining time daily at a configured local time and timezone.
- Per-player HUD preference:
  - BossBar (progress bar),
  - ActionBar (text),
  - None (fully hidden).
- Optional AFK detection to pause decrement while AFK.
- Supports bypass by list (name/UUID) and by permission.
- Incremental daily reset runs in batches to avoid lag spikes (configurable batch size and interval).

## Commands

Player:
- `/timelimit remaining` — Show your remaining time.
- `/timelimit hud bossbar|actionbar|none` — Set your own HUD.
- `/timelimit nextreset` — Show next reset timestamp and countdown.

Admin (requires operator or `timelimit.admin`):
- `/timelimit admin hud <targets> bossbar|actionbar|none` — Apply HUD mode to targets.
- `/timelimit admin setremaining <targets> <duration>` — Set remaining time.
- `/timelimit admin add <targets> <duration>` — Add time.
- `/timelimit admin decrease <targets> <duration>` — Subtract time.
- `/timelimit admin pause <targets>` — Pause counting.
- `/timelimit admin resume <targets>` — Resume counting.
- `/timelimit admin reset <targets>` — Reset to default (online targets).
- `/timelimit admin reset offline <name|uuid>` — Reset a known offline player.
- `/timelimit admin resetall` — Reset all players (including offline).
- `/timelimit admin bypass list|add|remove <name|uuid>` — Manage bypass entries.
- `/timelimit admin purge <days>` — Informational report of offline entries older than N days.
- `/timelimit admin reload` — Reload config and reinitialize managers.

Durations supported:
- `600` (seconds), `10m`, `15s`, `1h`, `1h30m`, `1h 30m 15s`, `10 min`.

## Configuration (example)

Place your configuration in `config/timelimitmod/config.json`:

```json
{
  "autosaveIntervalSeconds": 60,
  "dataFileName": "players.json",
  "defaultTime": 21600,
  "reset": {
    "time": "07:00",
    "timezone": "America/New_York"
  },
  "warnings": [],
  "messages": {
    "prefix": "&e&lSERVER &8» &7",
    "timeExpired": "Your daily playtime has expired. See you tomorrow!",
    "welcome": "Welcome! You have &e%time% &7of playtime remaining.",
    "timeAdded": "&e%time%&7 has been added to your session!",
    "timeRemoved": "&e%time%&7 has been removed from your session.",
    "timeReset": "Your time has been reset to &e%time%&7.",
    "paused": "Your time counting is currently paused.",
    "broadcastReset": "&eDaily reset executed. Next: &f%next%",
    "reloadApplied": "Configuration reloaded."
  },
  "bossbar": {
    "message": "Remaining time: %hours%h %minutes%m %seconds%s",
    "color": "WHITE"
  },
  "dailyResetEnabled": true,
  "kickOnExpire": true,
  "kickMessage": "Your daily playtime has expired. See you tomorrow!",
  "immunePlayers": [],
  "permissionBypassNode": "timelimit.bypass",
  "permissionAdminNode": "timelimit.admin",
  "sendWelcomeOnJoin": true,
  "incrementalResetEnabled": true,
  "resetBatchSize": 200,
  "resetBatchIntervalTicks": 2,
  "resetSaveEveryBatches": 5,
  "afk": {
    "enabled": false,
    "inactivitySeconds": 300,
    "pauseCounting": true,
    "moveThreshold": 0.1,
    "rotationThreshold": 1.0
  },
  "hud": {
    "actionBarMessage": "Remaining time: %hours%h %minutes%m %seconds%s"
  },
  "profiles": []
}
```

Notes:
- Set `incrementalResetEnabled` to true and tune `resetBatchSize` and `resetBatchIntervalTicks` to avoid lag spikes during daily reset.
