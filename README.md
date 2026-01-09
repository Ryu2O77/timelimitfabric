# TimeLimit Mod

Controla el tiempo de juego diario por jugador. Incluye HUD en BossBar/ActionBar, comandos para administrar tiempo, reset diario con procesamiento incremental y opciones de bypass. Diseñado para servidores Fabric.

## Tabla de contenidos
- [Características](#características)
- [Requisitos](#requisitos)
- [Instalación](#instalación)
- [Configuración](#configuración)
  - [Parámetros clave](#parámetros-clave)
  - [Permisos y bypass](#permisos-y-bypass)
  - [HUD](#hud)
- [Comandos](#comandos)
  - [Comandos públicos](#comandos-públicos)
  - [Comandos de administración](#comandos-de-administración)
- [Autocompletado](#autocompletado)
- [Preguntas frecuentes](#preguntas-frecuentes)
- [Solución de problemas](#solución-de-problemas)
- [Compilación y desarrollo](#compilación-y-desarrollo)
- [Compatibilidad](#compatibilidad)
- [Licencia](#licencia)

---

## Características
- Tiempo diario por jugador.
- HUD configurable:
  - BossBar (barra superior)
  - ActionBar (texto sobre la hotbar)
  - None (oculto)
- Comandos para sumar, establecer y descontar tiempo.
- Reset diario con procesamiento incremental en lotes para evitar tirones.
- Lista de bypass para jugadores selectos.
- Autocompletado:
  - `reset offline`: sugiere nombres/UUIDs de jugadores offline conocidos.
  - `bypass add/remove`: sugiere jugadores conocidos y entradas actuales de bypass.
- “remaining” público solo muestra tu propio tiempo; consultar el de otros requiere permisos.

## Requisitos
- Servidor Fabric (Loader y Fabric API).
- Java 17+.
- Versión de Minecraft compatible con el build del mod usado.

## Instalación
1. Copia el `.jar` del mod a `mods/` de tu servidor Fabric.
2. Inicia el servidor una vez para generar archivos de datos (si aplica).
3. Ajusta `config/timelimitmod/config.json` según tus necesidades.
4. Reinicia el servidor.

## Configuración
El archivo se encuentra en `config/timelimitmod/config.json`.

### Parámetros clave
- `defaultTime` (segundos): tiempo por defecto asignado al jugador tras reset.
  - Ejemplo: 21600 = 6 horas; 86400 = 24 horas.
- `profiles[].defaultTime` (segundos): tiempo por defecto alternativo si el jugador tiene el permiso indicado por `profiles[].node`.

Parámetros del reset incremental:
- `incrementalResetEnabled` (boolean):
  - `true`: activa el reset diario en lotes para evitar tirones.
  - `false`: procesa todo de golpe (puede causar lag en servidores con muchos jugadores).
- `resetBatchSize` (entero):
  - Cantidad de jugadores por lote durante el reset.
  - Más grande = termina antes; más chico = más suave en rendimiento.
- `resetBatchIntervalTicks` (entero):
  - Pausa entre lotes, en ticks del servidor (20 ticks = 1 segundo).
  - Aumenta si notas micro‑lag durante el reset.
- `resetSaveEveryBatches` (entero):
  - Cada cuántos lotes se guarda `players.json` a disco.
  - Menor = más escrituras (más seguro ante crash); mayor = menos IO (más rápido).

Recomendaciones prácticas:
- Servidor pequeño (≤500 entradas): `batch 100–200`, `interval 1`.
- Servidor mediano (500–5,000): `batch 200–300`, `interval 1–2`.
- Servidor grande (>5,000): `batch 300–500`, `interval 2–4`, `saveEvery 10–20`.

Estos ajustes solo afectan el reset diario; no cambian el decremento por segundo ni los comandos manuales.

### Permisos y bypass
- `permissionAdminNode`: nodo para administración (por defecto `timelimit.admin`). Requerido para consultar tiempos de otros y ejecutar comandos admin.
- `permissionBypassNode`: nodo de bypass (por defecto `timelimit.bypass`).
- `opIsImmune`: si `true`, los OP actúan como en bypass.
- Lista de `immunePlayers` (alias “bypass” en comandos): nombres o UUIDs que se consideran en bypass.

### HUD
Modos disponibles:
- `bossbar`: barra superior con el progreso de tiempo restante.
- `actionbar`: mensaje sobre la hotbar con cuenta regresiva.
- `none`: oculta el HUD.

Ejemplo de mensaje ActionBar configurable usando placeholders:
- `%hours%`, `%minutes%`, `%seconds%` para tiempo restante formateado.

## Comandos

### Comandos públicos
```text
/timelimit help
/timelimit remaining
/timelimit hud bossbar
/timelimit hud actionbar
/timelimit hud none
/timelimit nextreset
```
- `remaining`: muestra solo tu tiempo.
- `hud`: cambia tu propio HUD.
- `nextreset`: muestra cuándo será el próximo reset y cuánto falta.

### Comandos de administración
```text
/timelimit remaining <targets>
/timelimit get <targets>
/timelimit setremaining <targets> <duración>
/timelimit add <targets> <duración>
/timelimit decrease <targets> <duración>
/timelimit pause <targets> <on|off>
/timelimit reset <targets>
/timelimit reset offline <name|uuid>
/timelimit resetall
/timelimit bypass list
/timelimit bypass add <name|uuid>
/timelimit bypass remove <name|uuid>
/timelimit purge <days>      (stub de diagnóstico)
/timelimit reload
```

Notas:
- Formatos de `<duración>` admitidos: `30s`, `10m`, `1h30m`, etc.
- `reset offline` usa autocompletado con jugadores conocidos que estén desconectados.
- El grupo “bypass” reemplaza a “immune” (se mantiene alias `immune` por compatibilidad).

## Autocompletado
- Visible solo si el usuario tiene permiso para el comando correspondiente.
- `reset offline <spec>`: sugiere nombres y UUIDs de jugadores offline conocidos (presentes en `players.json`).
- `bypass add <spec>`: sugiere jugadores conocidos (online y offline).
- `bypass remove <spec>`: sugiere entradas existentes en la lista de bypass.

## Preguntas frecuentes
- ¿“defaultTime” está en segundos?
  - Sí. Usa segundos para `defaultTime` y los perfiles. Los comandos permiten formatos amigables (`10m`, `1h`, etc.).
- ¿Por qué los no‑OP solo ven “help”?
  - Asegúrate de usar la versión del mod donde los subcomandos públicos declaran visibilidad y que tu sistema de permisos permita el literal `/timelimit`.
- ¿Se puede evitar que entren sin tiempo?
  - Sí, bloqueando en la fase de login (antes de crear el jugador). Si necesitas esta opción, confirma tu versión de MC+Mappings para recibir el parche específico.

## Solución de problemas
- Cierre lento del servidor:
  - Ejecuta `save-all flush` antes de `stop`.
  - Ajusta el reset incremental (ver configuración).
  - Revisa hilos activos con `jstack`/`jcmd` si persiste.
- Autocompletado no aparece:
  - Verifica permisos del usuario.
  - Asegura que `players.json` tenga entradas con nombre/UUID (se generan cuando se conectan al menos una vez).

## Compilación y desarrollo
```bash
./gradlew clean build
```
- Copia el `.jar` generado en `mods/`.
- Si modificas comandos/config, reinicia el servidor para aplicar cambios.

## Compatibilidad
- Scoreboard eliminado para evitar confusiones. HUD soportado: BossBar y ActionBar.
- Fabric API requerida. Revisa la versión exacta de Minecraft y mappings usadas por tu build.

## Licencia
Incluye aquí la licencia de tu proyecto (por ejemplo, MIT). Si no tienes licencia aún, considera añadir una para clarificar uso y distribución.
