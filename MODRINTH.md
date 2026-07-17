# Modrinth listing — copy/paste sheet

Create at <https://modrinth.com/new> → Mod. Untracked scratch file; delete when done.

---

## 1. Project settings

| Field | Value |
|---|---|
| **Name** | `DynamicDistance` |
| **Slug** | `dynamicdistance`  (verified free) |
| **Project type** | Mod |
| **Categories** | `Optimization`, `Utility` |
| **Client side** | **Unsupported** |
| **Server side** | **Required** |
| **License** | `MIT` |
| **Source code** | `https://github.com/Codetaker56/dynamicdistance` |
| **Issues** | `https://github.com/Codetaker56/dynamicdistance/issues` |

**Summary** (paste verbatim):

```
Per-player view, chunk-send and simulation distance, driven by each player's own render distance. Server-side only — players install nothing.
```

---

## 2. Description (paste into the body editor)

Everything between the lines below.

---

Every player gets their **own** view, chunk-send and simulation distance, driven by the render distance their client already asks for and clamped to a window you configure.

**Players install nothing.** Vanilla clients work as-is. This is a dedicated-server mod.

## What it does

- Player on render distance **6** gets 6 chunks. One on **12** gets 12. One on **32** is capped at your server maximum.
- Fully independent per player — one person changing their setting never affects anyone else.
- **No reconnect needed.** Change it in Video Settings and it applies live (debounced, so spamming the slider can't thrash chunks).
- Less chunk tracking, loading, generation-while-exploring and bandwidth for players who ask for less.
- Configurable floor and cap, derived per-player simulation distance, and manual per-player overrides.

## Commands

All require permission level 2.

| Command | Effect |
|---|---|
| `/dynamicdistance status [player]` | Show requested vs effective view / send / simulation distance |
| `/dynamicdistance set <player> <2-32>` | Pin a per-player view distance |
| `/dynamicdistance simulation <player> <2-32>` | Pin a per-player simulation distance |
| `/dynamicdistance reset <player>` | Back to client-driven |
| `/dynamicdistance reload` | Reload config, reapply to everyone |

## Configuration

`config/dynamicdistance.properties` — `enabled`, `minimumViewDistance`, `maximumViewDistance`, `minimumSimulationDistance`, `maximumSimulationDistance`, `simulationDistanceOffset`, `updateCooldownTicks`, `updateOnClientSettingsChange`, `updateOnJoin`, `allowPerPlayerOverrides`, `debugLogging`.

Invalid values are corrected on load and logged; a minimum is never allowed above its maximum. Set `maximumViewDistance` to your `server.properties` `view-distance` or lower.

## Honest notes

Modern Minecraft **already** sends chunks per-player. This mod hooks the single method that decides that number and adds the configurable window, per-player overrides and instant re-apply — reusing vanilla's own delta-tracking, so it never needlessly resends chunks. If you were hoping for magic on top of vanilla, the win here is control, not a rewrite.

**Simulation distance has a ceiling.** A chunk ticks if *any* nearby player needs it — ticking is per-chunk, not per-player. Two players standing in the *same chunk* with different simulation distances collapse to the higher one. In the normal case (players spread out) each ticks at their own radius.

Every injection point was verified against real decompiled server bytecode. 26.1, 26.2 and 26.3-snapshot are identical, which is why one jar covers the 26.x line.

Requires [Fabric API](https://modrinth.com/mod/fabric-api).

---

## 3. Versions (upload TWO, same project)

### Version A — 1.21.x

| Field | Value |
|---|---|
| **Name** | `DynamicDistance 1.0.0 (MC 1.21.x)` |
| **Version number** | `1.0.0+1.21.x` |
| **Channel** | Release |
| **Loader** | `Fabric` |
| **Game versions** | `1.21.1` `1.21.2` `1.21.3` `1.21.4` `1.21.5` `1.21.6` `1.21.7` `1.21.8` `1.21.9` `1.21.10` `1.21.11` |
| **File** | `fabric-1.21.x/build/libs/dynamicdistance-1.0.0.jar` |
| **Dependency** | Fabric API — **required** |

> Do **not** tick plain `1.21` — the range starts at 1.21.1.

### Version B — 26.x

| Field | Value |
|---|---|
| **Name** | `DynamicDistance 1.0.0 (MC 26.x)` |
| **Version number** | `1.0.0+26.x` |
| **Channel** | Release |
| **Loader** | `Fabric` |
| **Game versions** | `26.1` `26.1.1` `26.1.2` `26.2` |
| **File** | `fabric-26.x/build/libs/dynamicdistance-1.0.0.jar` |
| **Dependency** | Fabric API — **required** |

> Both projects build a jar with the **same filename**. Upload them as separate
> versions (above) so they never collide. If you prefer distinct names, the
> GitHub release has them pre-renamed:
> <https://github.com/Codetaker56/dynamicdistance/releases/tag/v1.0.0>

---

## 4. After submitting

New Modrinth projects go into a **moderation review queue** — it won't be public instantly.

Modrinth requires an icon before publishing. There isn't one yet.
