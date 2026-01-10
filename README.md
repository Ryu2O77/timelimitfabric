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

## Commands

Player:
- `/timelimit remaining` — Show your remaining time.
- `/timelimit hud bossbar|actionbar|none` — Set your own HUD.
- `/timelimit nextreset` — Show next reset timestamp and countdown.

Admin (requires `timelimit.admin` or operator):
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
