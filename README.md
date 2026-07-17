# DynamicDistance

A **server-side** Fabric mod that gives every connected player their **own** view /
chunk-send / simulation distance, independently, based on the render distance their
client already asks for. **No client mod required** — vanilla clients work as-is.

Two separate builds share one pure-logic core:

| Build | Minecraft | Mappings | Java |
|-------|-----------|----------|------|
| `fabric-1.21.x` | 1.21.1 – 1.21.11 (one jar) | Yarn | 21 |
| `fabric-26.x`   | 26.1 – 26.x (one jar) | Mojang official | 25 |

They are kept as separate Gradle projects on purpose: the mappings and toolchains are
incompatible, so one jar can **not** cover both. The distance math and config live in
`common/` and are compiled into both.

---

## What it actually does (and the honest limits)

Modern Minecraft **already** tracks and sends chunks per-player: the server clamps each
client's requested render distance to `[2, server-view-distance]` and sends only that
player's chunks, diffing old vs new so nothing is needlessly resent. DynamicDistance hooks
the exact method that produces that per-player number (`ChunkMap.getPlayerViewDistance` /
`ServerChunkLoadingManager.getViewDistance`) and replaces it with a configurable value:

- honours the client's own render distance,
- never exceeds `server.properties` **or** `maximumViewDistance`,
- never drops below `minimumViewDistance`,
- can be pinned per-player via command,
- re-applies instantly when a player changes their video settings (no reconnect),
- is fully independent between players.

Because it feeds into vanilla's existing pipeline, this automatically reduces chunk
**tracking, loading, generation-while-exploring, and bandwidth** for players who ask for
less — with no extra resend work.

### Simulation distance — real, with a ceiling

Clients never send a simulation distance, so it's **derived** from the effective view
distance (see the formula in the config). DynamicDistance then registers each player's
simulation contribution at their own level instead of one global level.

**Known ceiling (by design of vanilla's ticking model):** a chunk ticks if it is within the
simulation radius of *any* nearby player — ticking is per-chunk, not per-player. So:

- Two players standing in the **same chunk** with different simulation distances collapse to
  the **higher** of the two for that chunk. This is correct (the chunk has to tick for the
  player who needs it) but it means simulation isn't perfectly isolated the way view distance is.
- Changing a player's simulation distance while another player occupies the *exact same
  chunk* can leave one stale simulation contribution in that chunk until it unloads. Rare,
  bounded, self-clearing, and it never affects view distance.

In the common case (players spread across different chunks), each player genuinely ticks at
their own radius. If you need perfectly isolated per-player simulation, that requires
managing a private per-(player, chunk) ticket instead of reusing vanilla's — noted as the
upgrade path in the mixin.

> "Effective chunk send distance" and "effective view distance" are the **same number** in
> vanilla: one value drives both chunk tracking and chunk sending. The status command shows
> them separately only for clarity.

---

## Installation

1. Build (below) or drop the jar into your **dedicated server's** `mods/` folder.
2. Requires [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api)
   for the matching Minecraft version.
3. Start the server once — it writes `config/dynamicdistance.properties`.
4. Set `maximumViewDistance` to your `server.properties` `view-distance` (or lower) and restart / `/dynamicdistance reload`.

Client-side: nothing. Players just set their render distance normally.

---

## Commands

All require permission level 2 (`op` / gamemaster tier).

| Command | Effect |
|---|---|
| `/dynamicdistance reload` | Reload the config, correct invalid values, reapply to everyone online. |
| `/dynamicdistance status [player]` | Show that player's distances (see below). Defaults to yourself. |
| `/dynamicdistance set <player> <2-32>` | Pin a per-player **view** distance override. |
| `/dynamicdistance simulation <player> <2-32>` | Pin a per-player **simulation** distance override. |
| `/dynamicdistance reset <player>` | Clear that player's overrides (back to client-driven). |

`status` reports: client-requested render distance, effective view distance, effective chunk
send distance, effective simulation distance, whether a manual override is set, and the
server's configured maximum/minimum.

### Verifying different players get different distances

Set `debugLogging=true` (or run it live) and watch the console — every apply logs
`<player> requested=R -> view=V sim=S (serverMax=M)`. Have two players pick different render
distances and compare their `/dynamicdistance status` output.

---

## Configuration

Written to `config/dynamicdistance.properties`. Full annotated example in
[`config-example/dynamicdistance.properties`](config-example/dynamicdistance.properties).

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch; `false` = pure vanilla behaviour. |
| `minimumViewDistance` | `3` | Floor for effective view distance. |
| `maximumViewDistance` | `32` | Cap for effective view distance (also hard-capped by `server.properties`). |
| `minimumSimulationDistance` | `3` | Floor for effective simulation distance. |
| `maximumSimulationDistance` | `12` | Cap for effective simulation distance. |
| `simulationDistanceOffset` | `0` | Added to view distance before the simulation cap. |
| `updateCooldownTicks` | `20` | Debounce window (ticks) so rapid slider changes can't thrash chunks. |
| `updateOnClientSettingsChange` | `true` | Re-apply when a player changes video settings. |
| `updateOnJoin` | `true` | Apply on join. |
| `allowPerPlayerOverrides` | `true` | Allow the `set` / `simulation` override commands. |
| `debugLogging` | `false` | Log every applied change. |

Validation runs on load and on `reload`: every value is clamped to `[2, 32]`, a minimum is
lowered if it exceeds its maximum, and a negative cooldown becomes `0`. Corrections are
logged; the mod never crashes on a bad config.

---

## Building

Prerequisites: JDK 21 (for 1.21.x) and JDK 25 (for 26.1). No global Gradle needed — each
project ships a wrapper.

```bash
# 1.21.x  (Gradle runs on JDK 21)
cd fabric-1.21.x
./gradlew build          # Windows: gradlew.bat build

# 26.x  (its wrapper is Gradle 9.5, which must run on JDK 25)
cd ../fabric-26.x
JAVA_HOME=/path/to/jdk-25 ./gradlew build
```

Jars land in `build/libs/dynamicdistance-1.0.0.jar` for each project. `build` also runs the
unit tests.

### Tests

Pure distance math and config validation are covered by JUnit 5 in `common/src/test`
(`DistanceCalculatorTest`, `DistanceConfigTest`) and run as part of `gradlew build` in both
projects — they need no Minecraft.

---

## Layout

```
dynamicdistance/
  common/                     pure logic + tests, shared by both builds (no Minecraft imports)
    DistanceCalculator.java   effectiveView / effectiveSimulation
    DistanceConfig.java       load / save / validate (java.util.Properties)
  fabric-1.21.x/              Yarn, Java 21, one jar for 1.21.1–1.21.11
    mixin/ ServerChunkLoadingManagerMixin, ServerChunkManagerAccessor,
           ChunkTicketManagerMixin, ServerPlayerEntityMixin
  fabric-26.x/                Mojang mappings, Java 25, one jar for the 26.x line
    mixin/ ChunkMapMixin, DistanceManagerMixin, ServerPlayerMixin
  config-example/             annotated default config
```

## Version coverage

Each build is compiled against the **lowest** version in its range, so nothing newer can
sneak into the bytecode:

- **`fabric-1.21.x`** builds against 1.21.1, declares `>=1.21.1 <=1.21.11`. Yarn is remapped
  through Fabric intermediary, which is stable across the range.
- **`fabric-26.x`** builds against 26.1, declares `>=26.1 <27`. The 26.x line ships
  **unobfuscated**, so the mod binds directly to real Mojang names — there is no
  intermediary layer, which means the jar works on any 26.x whose internals kept the same
  names and signatures.

Every injection target was checked against the **actual server bytecode** of 26.1, 26.2 and
26.3-snapshot-4, and all are identical:

| Target | 26.1 | 26.2 | 26.3-snap |
|---|---|---|---|
| `ChunkMap.getPlayerViewDistance(ServerPlayer)` | ✅ | ✅ | ✅ |
| `ChunkMap.serverViewDistance` / `updateChunkTracking` | ✅ | ✅ | ✅ |
| `DistanceManager.addPlayer/removePlayer` → calls `getPlayerTicketLevel()` | ✅ | ✅ | ✅ |
| `ServerPlayer.requestedViewDistance()` / `updateOptions(ClientInformation)` | ✅ | ✅ | ✅ |

The range stops at `<27` on purpose: 27.x does not exist yet, so claiming it would be a
guess. If a future 26.x moves one of these, the mixin **fails loudly at load** with the
exact target named, rather than silently doing nothing — bump the range once verified.

## Status

- Both projects **compile** (`gradlew build`, Java 21 / Java 25) and their unit tests pass
  (17 each: distance math + config validation).
- Every mixin target and access path was verified against real decompiled Minecraft
  bytecode, not guessed from memory.
- Live multi-client behaviour (different render distances producing different effective
  distances) is best verified with `debugLogging` + `/dynamicdistance status` on your server.
