# Teras (Forge 1.16.5) — Enterprise Codebase Audit

**Auditor scope:** architecture, code quality, Forge correctness, performance, memory, networking, persistence, security, game design, compatibility, enterprise readiness.
**Codebase:** `es.boffmedia.teras` — 304 Java files, ~34,264 LOC, version `0.1.5`, target Forge `36.2.39` / MC `1.16.5`.
**Method:** direct source inspection (not metadata only). Every finding below cites real evidence.

---

## 1. Executive Summary

Teras is an ambitious **server-content mod** (kart racing, Pixelmon battle extensions, NPC quests, in-game browser/video via MCEF, voice chat, dungeons, custom blocks/items/fluids) built on the Forge MDK example template. It is feature-rich and clearly the product of sustained solo development, but it is currently at **prototype maturity, not production maturity**.

The single most important finding: **the project never graduated from the MDK example skeleton.** The mod still identifies itself as `examplemod` in [build.gradle:66](../build.gradle#L66) and [mods.toml](../src/main/resources/META-INF/mods.toml), still sends `"Hello world from the MDK"` IMC messages ([Teras.java:247](../src/main/java/es/boffmedia/teras/Teras.java#L247)), and retains the template's `RegistryEvents` stub. Layered on top is a large body of real but **unhardened** code: blocking network I/O on the main thread, two parallel packet channels, an entire `_old` graveyard (34 files / 2,279 LOC), zero unit tests, zero `WorldSavedData`/Capability usage, and at least two **remotely-exploitable network packets**.

**Verdict:** Strong creative vision, weak engineering foundation. With focused remediation (security packets, threading, persistence, dead-code removal) this can become a solid server mod. As-is, it is **not safe to deploy on a public multiplayer server** and **not maintainable by a team**.

**Top 5 blocking issues:**
1. `SMessageChatMessage` lets any client broadcast arbitrary server-wide system messages — chat-spoofing exploit ([§13](#13-security--exploit-review)).
2. `CMessageRunJS` executes server-pushed JavaScript in the client's embedded browser ([§13](#13-security--exploit-review)).
3. Blocking HTTP on the server thread via `FutureTask.get()` — guaranteed TPS stalls/freezes ([§6](#6-performance-analysis)).
4. All persistence is hand-rolled GSON JSON with no atomic writes — data-corruption risk on crash ([§9](#9-data-storage--serialization-review)).
5. Per-player-tick global vehicle iteration — O(N²) scaling ([§6](#6-performance-analysis)).

---

## 2. Project Health Scorecard

| Dimension | Score (1–10) | Justification |
|---|---|---|
| Architecture | 3 | God-package `util` (132 files), no layering, static singletons everywhere |
| Code Quality | 3 | 49 `printStackTrace`, 34 `System.out.println`, mixed Spanish/English, dead code |
| Forge Correctness | 4 | DeferredRegister used well; but two channels, MDK leftovers, side leaks |
| Performance | 3 | Blocking I/O on game thread, O(N²) tick, per-tick allocations |
| Memory Safety | 4 | Static maps without eviction, but small scale today |
| Network Design | 2 | Two channels, no validation, exploitable packets, full-buffer string decode |
| Persistence | 3 | JSON files, no atomicity, no SavedData, schema-less |
| Security | 2 | RCE-adjacent JS packet, chat spoof, unauthenticated HTTP |
| Game Design | 6 | Genuinely novel kart+Pixelmon concept; balance/UX unfinished |
| Mod Compatibility | 5 | Hard deps wired; no JEI/Jade/CraftTweaker integration |
| Testing | 1 | `src/test` is empty; no CI test stage |
| Documentation | 4 | README is good; code comments sparse/stale, much in Spanish |
| Enterprise Readiness | 2 | examplemod identity, no versioning discipline, no tests |
| **Overall** | **3.2 / 10** | Prototype with strong vision, weak foundation |

---

## 3. Architecture Review

**Architectural style:** None deliberately chosen. It is a **flat utility-bag architecture** centered on a god-package `util` (132 of 304 files) and a static-singleton hub (`Teras` holds `config`, `regions`, `INSTANCE`, `lbc`, `carreraManager`, `raceManager`, `GSON`, `PROXY` as public static mutable fields — [Teras.java:64–93](../src/main/java/es/boffmedia/teras/Teras.java#L64-L93)).

**Layer separation:** Effectively none. `util` mixes math (`Vec3d`, `Matrix3`), networking-adjacent objects, game logic (`RaceManager`, dungeons), persistence (`FileHelper`), HTTP (`SmartRotomAPI`), and DTOs. There is no domain/service/persistence boundary.

**Coupling:** High and bidirectional. `RaceManager` reaches into MrCrayfish vehicles via **reflection** ([RaceManager.java:249–258](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L249-L258)); events call into static managers; persistence types reference Pixelmon and CustomNPCs API types directly inside serializable DTOs.

**Cohesion:** Low. `Teras.java` is a kitchen sink — it holds SmartRotom pad-ratio math, blacklist URL logic, a `getNextAvailablePadID()` that always returns `0` ([Teras.java:302–304](../src/main/java/es/boffmedia/teras/Teras.java#L302-L304)), and an `isSiteBlacklisted` that returns the logical inverse of its name ([Teras.java:306–308](../src/main/java/es/boffmedia/teras/Teras.java#L306-L308)).

**Dependency flow:** Inward-pointing toward the `Teras` static hub, which makes the entire codebase untestable without a running game.

**Architecture diagram (described):**
```
                ┌──────────────────────────┐
                │   Teras (static hub)      │  ← public static mutable state
                │  config, raceManager,     │
                │  carreraManager, lbc, PROXY│
                └─────────┬─────────────────┘
        ┌─────────────────┼───────────────────────────┐
   event/* (forge bus) net/{2 channels}        util/* (132 files: math,
   Pixelmon EVENT_BUS   Messages + PacketHandler   game, dungeons, http,
   CustomNPCs events    (no shared abstraction)     file io, DTOs, _old/)
        │                 │                            │
   ┌────┴────┐      ┌─────┴──────┐            ┌────────┴─────────┐
 blocks/  items/  client/gui  pixelmon/battle  SmartRotomAPI →  external HTTP
 tileentity      mixin/*       (handlers/*)     (blocking)       teras.es API
```

**Recommendation:** Introduce three boundaries — `domain` (pure logic + DTOs, no MC imports where possible), `service` (managers, persistence, network orchestration), `mc` (events, packets, blocks). Replace the static hub with a per-server `TerasServer` context object obtained from `MinecraftServer`. **Effort: High / Transformative.**

### Architecture quality scores
| Quality | Score | Justification |
|---|---|---|
| Maintainability | 3 | God-package, dead code, dual-language naming |
| Testability | 1 | Static singletons + live-game coupling; zero tests exist |
| Extensibility | 4 | Battle handler-factory pattern is good; rest is hardcoded |
| Readability | 3 | Debug logging noise, Spanglish, commented-out blocks |
| Scalability | 3 | O(N²) ticks, blocking I/O |
| Reliability | 2 | `printStackTrace`-and-continue, no atomic saves, NPE-prone statics |

---

## 4. Code Quality Assessment

| # | Finding | Evidence | Severity |
|---|---|---|---|
| 4.1 | **49 `printStackTrace()` calls** swallow errors with no logging context | grep across `src/main/java` | High |
| 4.2 | **34 `System.out.println`** including in hot path `voteStart` | [RaceManager.java:147–149](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L147-L149) | Medium |
| 4.3 | **Debug/placeholder log spam** shipped: `"TESTES"`, `"HELLO FROM PREINIT"`, `"API NO FUNKA"`, `"FUNCTIONANDO LOGIIIINN"` | [Teras.java:124,152,162](../src/main/java/es/boffmedia/teras/Teras.java#L124), [TerasEvents.java:238](../src/main/java/es/boffmedia/teras/event/TerasEvents.java#L238) | Medium |
| 4.4 | **Dual-language naming** (`Carrera`/`Race`, `Mision`, `Coche`, `enviarMensaje`) across the same domain | RaceManager mixes both | Medium |
| 4.5 | **Method names contradict behavior**: `isSiteBlacklisted` returns `!contains`; `getNextAvailablePadID` always `0` | [Teras.java:302–308](../src/main/java/es/boffmedia/teras/Teras.java#L302-L308) | High |
| 4.6 | **Duplicated classes**: `QueryHelper`/`QueryHelperOld`, `QuestList`/`QuestListBak`, `TerasBattle`/`TerasBattleOld`, `VideoScreen`/`VideoScreen2`, two `TerasConfig` classes (root + `_old/serverdata`) | file listing | High |
| 4.7 | **Empty/no-op methods** left in main class: `setBackup()`, `onClientTickEvent(PlayerTickEvent)` fully commented | [Teras.java:280](../src/main/java/es/boffmedia/teras/Teras.java#L280), [TerasEvents.java:137–145](../src/main/java/es/boffmedia/teras/event/TerasEvents.java#L137-L145) | Low |
| 4.8 | **MDK template residue**: `examplemod` mod source name, IMC "Hello world", `RegistryEvents` stub logging only | build.gradle, Teras.java, mods.toml | High |
| 4.9 | **33 ad-hoc `new Gson()`** instead of reusing the static `Teras.GSON` | grep | Low |

**Recommendation:** Adopt a logging standard (SLF4J/Log4j marker), delete `_old`, run a one-pass rename to English, and add Checkstyle/SpotBugs to the build. **Effort: Medium / High impact.**

---

## 5. Forge-Specific Best Practices Review

**Good:**
- `DeferredRegister` is correctly used for blocks, items, fluids, tile entities, biomes, sounds ([init/](../src/main/java/es/boffmedia/teras/init/)). This is the right pattern.
- `DistExecutor.safeRunForDist` for proxy selection ([Teras.java:89](../src/main/java/es/boffmedia/teras/Teras.java#L89)) is correct.
- Mod event bus listeners registered in constructor — correct lifecycle.

**Problems:**

| # | Finding | Evidence | Severity |
|---|---|---|---|
| 5.1 | **Two separate `SimpleChannel`s** with divergent protocol versions (`"1"` vs `"2"`) — `Messages` ("packetsystem") and `PacketHandler` ("network") | [Messages.java:17–24](../src/main/java/es/boffmedia/teras/net/Messages.java#L17-L24), [PacketHandler.java:21–34](../src/main/java/es/boffmedia/teras/net/PacketHandler.java#L21-L34) | High |
| 5.2 | **No `WorldSavedData`** anywhere — all server state is JSON files or `getPersistentData()`. Bypasses MC's dirty-tracking, autosave, and backup | grep: 0 matches | High |
| 5.3 | **No Capabilities** — player race/quest state is stuffed into `getPersistentData()` NBT with string keys (`"inicio"`, `"frentebatalla"`) | [TerasEvents.java:254–258](../src/main/java/es/boffmedia/teras/event/TerasEvents.java#L254-L258) | Medium |
| 5.4 | **`@Mod.EventBusSubscriber` without explicit `Dist`** on classes containing `@OnlyIn(Dist.CLIENT)` client handlers — mixes client/server in one class | [TerasEvents.java:55](../src/main/java/es/boffmedia/teras/event/TerasEvents.java#L55) | Medium |
| 5.5 | **No data generators used** despite `data` run config existing — recipes/loot/models/lang are presumably hand-authored | build.gradle data run targets `examplemod` | Medium |
| 5.6 | **Permission node registered in `FMLLoadCompleteEvent`** then a second ad-hoc `"admin"`/`"teras.frames.video"` scheme — inconsistent | [Teras.java:198](../src/main/java/es/boffmedia/teras/Teras.java#L198), [TerasEvents.java:244](../src/main/java/es/boffmedia/teras/event/TerasEvents.java#L244) | Low |
| 5.7 | **Manifest still declares `examplemod`** Specification-Title/Vendor | [build.gradle:185–195](../build.gradle#L185-L195) | Medium |

**Recommendation:** Collapse to one channel with a single versioned protocol and an enum/registry of packet IDs; migrate race/quest persistence to `DimensionSavedData`/`WorldSavedData`; introduce a proper player Capability. **Effort: High / High impact.**

---

## 6. Performance Analysis

### 6.1 Blocking network I/O on the game thread — **Critical**
`SmartRotomAPI.wingullGET` spawns a thread and then **immediately blocks** on `futureTask.get()` ([SmartRotomAPI.java:58–64](../src/main/java/es/boffmedia/teras/util/data/smartrotom/SmartRotomAPI.java#L58-L64)). Whatever thread calls this — and callers include login/region/dex flows that run on the server thread — will stall for the full HTTP round-trip (potentially seconds, or a TCP timeout of tens of seconds if `teras.es` is down). On the main server thread this **freezes the entire server** and disconnects players.

Compounding: `getConnectionStream` opens a **second** `HttpURLConnection` to the same URL ([SmartRotomAPI.java:114–125](../src/main/java/es/boffmedia/teras/util/data/smartrotom/SmartRotomAPI.java#L114-L125)) — the GET is effectively done twice. Returning `null` on failure then triggers a thrown `NullPointerException` wrapped in `RuntimeException`.
**Impact:** TPS death / full server hang. **Severity: Critical.** **Fix: async with callback + timeouts; never `.get()` on the game thread. Effort: Low.**

### 6.2 Per-player-tick global vehicle iteration — O(N²)
`CarreraEvent.onPlayerTick` fires for every player every tick and calls `raceManager.playerTick` ([CarreraEvent.java:15–19](../src/main/java/es/boffmedia/teras/event/karts/CarreraEvent.java#L15-L19)), which calls `tickVehicles()` — iterating **all** hit/drift handlers globally — once **per participant** ([RaceManager.java:201–247](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L201-L247)). With P participants and H handlers, cost is O(P·H) per tick where it should be O(H).
**Impact:** Scales poorly with player count; redundant `removeIf` allocates iterators every call. **Severity: High.** **Fix: tick vehicles once per server tick (ServerTickEvent), not per player. Effort: Low.**

### 6.3 Null `raceManager` NPE per tick
If `RaceManager` construction threw (caught and logged at [TerasEvents.java:152–156](../src/main/java/es/boffmedia/teras/event/TerasEvents.java#L152-L156)), `Teras.raceManager` stays null and `CarreraEvent` NPEs **every tick for every player** — a log flood and broken world. **Severity: High. Fix: guard + fail-fast. Effort: Trivial.**

### 6.4 Per-tick allocations / reflection
- `RaceManager.hitCar` allocates a `Vector3d` and calls `level.random` per hit; `joinRace` builds many `StringTextComponent`/`Style` objects in a loop ([RaceManager.java:76–105](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L76-L105)).
- Reflection cached statically (`setRawPosition`) is acceptable, but used on a hot vehicle path.
**Severity: Medium.** **Fix: precompute static text components; pool vectors. Effort: Low.**

### 6.5 Worst-case multiplayer estimate
With a down `teras.es` and 20 players logging in, the blocking-GET + double-connection pattern can stack server-thread stalls into multi-second freezes per login. **This is the dominant scaling risk.**

---

## 7. Memory Analysis

| # | Finding | Evidence | Impact |
|---|---|---|---|
| 7.1 | **Static singletons hold live game objects** (`ShinyTracker.INSTANCE`, `TextureCache`, `Teras.lbc`) — risk of stale references across world reloads | TerasEvents tick/unload handlers | Medium |
| 7.2 | **`RaceManager` maps keyed by UUID** (`hitHandlers`, `driftHandlers`) cleaned only via `removeIf` on tick; if a player disconnects mid-race the handler can linger until predicate matches | [RaceManager.java:232–247](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L232-L247) | Low |
| 7.3 | **`Messages.index` / `PacketHandler.nextId`** are mutable statics — fine at load, but the dual system doubles the surface | — | Low |
| 7.4 | **GSON `LinkedTreeMap` cast** for tracks held for server lifetime; unbounded if track config grows | [RaceManager.java:43–51](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L43-L51) | Low |
| 7.5 | **MCEF/video/texture caches** (`TextureCache`, `VideoDisplayer`) ticked every render/client tick; CEF is the largest native-memory consumer | TerasEvents | Medium |

**Net:** No catastrophic leaks at current scale, but the static-singleton + manual-cache pattern is exactly what leaks under long-running servers. **Recommendation: tie lifecycles to `MinecraftServer`/world events and add explicit `clear()` on `WorldEvent.Unload` (partially done for textures). Effort: Medium.**

---

## 8. Network Analysis

**Channels:** 2 (should be 1). Packet count: ~16 active in `Messages` + 3 in `PacketHandler`, with another ~12 commented-out "old" registrations ([Messages.java:61–89](../src/main/java/es/boffmedia/teras/net/Messages.java#L61-L89)).

| # | Finding | Evidence | Severity |
|---|---|---|---|
| 8.1 | **Full-buffer string decode**: `buf.toString(Charsets.UTF_8)` reads the *entire* buffer as a string with no length prefix — fragile and breaks if combined with other fields | [SMessageChatMessage.java:46](../src/main/java/es/boffmedia/teras/net/server/SMessageChatMessage.java#L46), [CMessageRunJS.java:26](../src/main/java/es/boffmedia/teras/net/client/CMessageRunJS.java#L26) | High |
| 8.2 | **No payload validation / size cap** on inbound strings → memory-amplification DoS (client sends a huge string) | both packets above | High |
| 8.3 | **No authority checks** in `handle()` — sender is trusted implicitly | SMessageChatMessage | Critical (see §13) |
| 8.4 | **No rate limiting** on any packet | — | High |
| 8.5 | **Client→server video frame packets** (`FrameVideoMessage`) on a separate channel — potentially large payloads, no chunking review | PacketHandler | Medium |

**Recommendation:** Single channel; use `buf.writeUtf(s, maxLen)`/`buf.readUtf(maxLen)`; validate sender + permissions in every server-bound handler; add per-player packet rate limits. **Effort: Medium.**

---

## 9. Data Storage & Serialization Review

**Pattern:** Hand-rolled GSON → flat JSON files under `config/teras/` and `Teras/`, plus NBT-in-`getPersistentData`.

| # | Finding | Evidence | Severity |
|---|---|---|---|
| 9.1 | **No atomic writes** — `FileHelper.writeFile` truncates then streams JSON directly to the target file. A crash mid-write corrupts the save (tracks, quests, config) | [FileHelper.java:53–70](../src/main/java/es/boffmedia/teras/util/file/FileHelper.java#L53-L70) | Critical |
| 9.2 | **`readFile` auto-creates + `newInstance()`** via no-arg reflection; on `Map.class`/interface tokens this is fragile and the `token.getClass().newInstance()` path is logically wrong (creates a `Class`-of-`TypeToken`, not the target) | [FileHelper.java:113–129](../src/main/java/es/boffmedia/teras/util/file/FileHelper.java#L113-L129) | High |
| 9.3 | **Serializable DTOs reference live mod API types** (e.g. quest DTOs hold CustomNPCs `IQuest`) — GSON will try to serialize engine objects | [QuestDataBase.java](../src/main/java/es/boffmedia/teras/util/objects/quests/QuestDataBase.java) | High |
| 9.4 | **No schema/version field** in any JSON — forward/backward migration impossible | all DTOs | High |
| 9.5 | **`getConfig()` swallows malformed JSON** as `FileNotFound` only; a corrupt config that parses to `null` is not handled | [FileHelper.java:153–173](../src/main/java/es/boffmedia/teras/util/file/FileHelper.java#L153-L173) | Medium |
| 9.6 | **Hardcoded localhost defaults** written into generated config (`http://localhost:3000`) | [FileHelper.java:166](../src/main/java/es/boffmedia/teras/util/file/FileHelper.java#L166) | Medium |

**Recommendation:** Write-to-temp-then-atomic-move; add `schemaVersion`; move world-tied state to `WorldSavedData`; never serialize engine API objects (map to plain DTO fields). **Effort: Medium / High impact.**

---

## 10. Registry & Initialization Review

- **DeferredRegister** correctly used across 6 init classes — the strongest part of the codebase.
- **`Teras` constructor** does meaningful work that belongs in setup events: it calls `NpcAPI.Instance().events().register(...)` and logs `"TESTES"` during construction ([Teras.java:123–125](../src/main/java/es/boffmedia/teras/Teras.java#L123-L125)) — fragile ordering against CustomNPCs load.
- **`setup()` mixes** client-only `MCEFApi.getAPI()` registration with common setup ([Teras.java:159–170](../src/main/java/es/boffmedia/teras/Teras.java#L159-L170)) — MCEF is client-side; this risks dedicated-server crashes.
- **`RegistryEvents` inner class** is the empty MDK stub ([Teras.java:285–293](../src/main/java/es/boffmedia/teras/Teras.java#L285-L293)).
- **Biome generation** triggered in setup (`ModBiomes.generateBiomes()`), worth verifying it uses the BiomeLoadingEvent path rather than mutating registries late.

**Severity: Medium–High. Recommendation: move MCEF init behind `DistExecutor`/client setup; remove constructor side effects; delete stub. Effort: Low.**

---

## 11. Event System Review

- **Three buses in play**: Forge `MinecraftForge.EVENT_BUS` (via `@Mod.EventBusSubscriber`), the mod bus, and `Pixelmon.EVENT_BUS` + `NpcAPI.events()`. This is necessary but undocumented.
- **`TerasEvents` is a god-handler** — 18 handlers spanning client rendering, server lifecycle, command registration, teleport blocking, login, and persistence in one class ([TerasEvents.java](../src/main/java/es/boffmedia/teras/event/TerasEvents.java)). Mixing `@OnlyIn(Dist.CLIENT)` render handlers with server login handlers in one auto-subscribed class is a class-loading hazard on dedicated servers.
- **`onLogin` does heavy work**: reads config from disk, dumps all permission nodes to log, constructs `UserData`, sends a config packet — synchronously on login ([TerasEvents.java:236–262](../src/main/java/es/boffmedia/teras/event/TerasEvents.java#L236-L262)).
- **`enterWorld` logs 4 lines per entity join** for players — log spam at scale.

**Recommendation:** Split into `ClientEventHandler` / `ServerEventHandler` / `CommandRegistry`; gate client handlers by Dist; strip debug logging. **Effort: Low / High impact.**

---

## 12. Thread Safety Review

| # | Finding | Evidence | Severity |
|---|---|---|---|
| 12.1 | **HTTP threads call back into game state** — `post(...)` → `QueryHelper.handlePOST` on a raw `new Thread`; if that touches world/player objects it's an off-thread mutation race | [SmartRotomAPI.java:73–108](../src/main/java/es/boffmedia/teras/util/data/smartrotom/SmartRotomAPI.java#L73-L108) | High |
| 12.2 | **9 raw `new Thread`** with no executor, naming, or exception handling | grep | Medium |
| 12.3 | **Static mutable singletons** (`Teras.config`, `raceManager`) read on game thread, written from setup/login — no synchronization | Teras.java | Medium |
| 12.4 | **`ClientScheduler`/`ShinyTracker`** ticked from render + client tick — verify single-threaded assumptions hold | TerasEvents | Low |

**Recommendation:** Single shared `ExecutorService` for all I/O; marshal results back to the server thread via `server.execute(...)`; never touch MC objects off-thread. **Effort: Low–Medium.**

---

## 13. Security & Exploit Review

This is the most serious category.

| # | Vulnerability | Evidence | Severity |
|---|---|---|---|
| 13.1 | **Chat/system-message spoofing** — `SMessageChatMessage` accepts arbitrary JSON from any client and broadcasts the `"message"` field to **all players** as a `ChatType.SYSTEM` message with `NIL_UUID` (no attribution, no permission, no rate limit). Any modded client can impersonate server announcements or spam everyone. | [SMessageChatMessage.java:32–43](../src/main/java/es/boffmedia/teras/net/server/SMessageChatMessage.java#L32-L43) | **Critical** |
| 13.2 | **Server-pushed client code execution** — `CMessageRunJS` runs server-supplied strings as JavaScript in the client's MCEF (Chromium) browser via `PROXY.runJS(str)`. A malicious/compromised server (or MITM) can execute arbitrary JS in an embedded browser on every client. | [CMessageRunJS.java:21–23](../src/main/java/es/boffmedia/teras/net/client/CMessageRunJS.java#L21-L23) | **Critical** |
| 13.3 | **Unauthenticated outbound HTTP** to a hardcoded/config URL with `User-Agent: Mozilla/4.0`, no TLS enforcement, no auth token — player data (`UserData`, dex, transactions) is POSTed in clear | [SmartRotomAPI.java](../src/main/java/es/boffmedia/teras/util/data/smartrotom/SmartRotomAPI.java), [SmartRotomService.java](../src/main/java/es/boffmedia/teras/util/data/smartrotom/SmartRotomService.java) | High |
| 13.4 | **No size caps on inbound packet strings** → memory-exhaustion DoS | §8.1–8.2 | High |
| 13.5 | **`SMessageDarCaja` / `SMessageEncenderPC` / `SMessageUpdateDex`** — server-bound packets that grant items / open PCs / mutate dex; need authority audit (a client should not be able to grant itself a box) | Messages registration | High (needs review) |
| 13.6 | **WorldEdit/schematic command exposure** (`WECommand`, `SchematicService`, `getSchematic` writing to `plugins/WorldEdit/...`) | [FileHelper.java:175–186](../src/main/java/es/boffmedia/teras/util/file/FileHelper.java#L175-L186) | Medium |

**Recommendation (urgent):**
1. Add permission/authority checks to **every** server-bound packet; never broadcast client-supplied text.
2. Restrict `CMessageRunJS` to a whitelist of allowed operations, or remove it; treat in-game browser JS as untrusted.
3. Enforce HTTPS + an auth token on the SmartRotom API; make it opt-in and fail-closed.
4. Length-cap and validate all packet fields.

**Effort: Medium. Impact: blocks public deployment until fixed.**

---

## 14. Gameplay Design Review

The **kart-racing-meets-Pixelmon** concept is genuinely differentiated — there is no mature Forge mod combining Mario-Kart-style drift/boost/hit mechanics with a Pokémon server. That is the product's moat.

**Strengths:**
- Drift→boost loop (`VehicleDriftHandler`), item-hit stun (`VehicleHitHandler`), checkpoints, laps, voting-to-start, position tracking — a complete racing skeleton.
- Battle extensions (custom abilities `Iaido`/`Desenvaine Súbito`, battle-frontier `TorreBatallaController`) add depth.

**Weaknesses / unfinished:**
| Area | Issue | Severity |
|---|---|---|
| Race start | Vote threshold is `> participants/2` but countdown is a flat "3 seconds" string ([RaceManager.java:165–167](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L165-L167)) — no minimum-player gate, single player can self-start | Medium |
| Position logic | Uses `participants.indexOf(participant)` as "position" — insertion order, **not** actual race standing ([RaceManager.java:305](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L305)) | High (wrong gameplay) |
| Rewards | `SmartRotomService.postCarrera` is TODO "adapt to new endpoint" — race results may not persist | Medium |
| Progression | No visible unlock/upgrade loop for karts or items; no power-curve | Medium |
| Hit direction | Random direction (`level.random`) rather than attacker-relative — feels arbitrary | Medium |

**Recommendation:** Implement true ranking (checkpoint index + lap + distance-to-next-checkpoint); add a minimum-player start gate; finish reward persistence; design a kart/item progression loop. **Effort: Medium.**

---

## 15. UX Review

- **All player-facing text is Spanish, hardcoded** (`"No estás en ninguna carrera"`, `"NO PUEDES TELETRANSPORTARTE..."`) — no `lang` file / i18n. Blocks any non-Spanish audience. [RaceManager.java:112](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L112), [TerasEvents.java:192](../src/main/java/es/boffmedia/teras/event/TerasEvents.java#L192).
- Clickable chat actions (`[Votar inicio]`, `[Salir]`) are a nice touch ([RaceManager.java:78–101](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java#L78-L101)).
- Error messages leak internals to players (`"Error parsing message"`).
- No HUD for live race position/lap beyond a `/karts` status command string with `\n`-joined text — poor at speed.

**Recommendation:** Move all strings to `TranslationTextComponent` + `en_us`/`es_es` lang files; build a proper racing HUD overlay. **Effort: Medium.**

---

## 16. Mod Compatibility Review

**Hard dependencies** (mods.toml): Pixelmon (mandatory), MCEF (mandatory, BEFORE), CustomNPCs (compile), voicechat (optional), epicfight (optional), plus MrCrayfish Vehicle, WorldEdit, JourneyMap, Travelers Backpack, Curios API, WaterMedia, Armourer's Workshop ([build.gradle:138–158](../build.gradle#L138-L158)).

| # | Risk | Evidence |
|---|---|---|
| 16.1 | **Reflection into MrCrayfish internals** (`func_70080_a`) breaks on any vehicle-mod update | RaceManager.java:253 |
| 16.2 | **Mixins into Pixelmon** (`PokemonMixin`, `StatChangeActionMixin`, `TeamSelectionMixin`, etc.) are version-locked to the exact Pixelmon build | teras.mixins.json |
| 16.3 | **CustomNPCs `IQuest` stored in serializable DTOs** couples save format to NPC mod | QuestDataBase |
| 16.4 | **`persistentData` string keys** (`"inicio"`, `"frentebatalla"`) risk collision with other mods | TerasEvents |
| 16.5 | **No soft-fail** if optional mods absent — `Pixelmon.EVENT_BUS.register(...)` in setup assumes presence | Teras.java:146 |

**Ecosystem integrations missing (opportunities):** JEI (custom items/recipes), Jade/TheOneProbe (block/TE info), Patchouli (in-game guide for racing/battles), CraftTweaker/KubeJS (server-pack tuning of karts/quests), Create (thematically adjacent). **None are present.**

**Recommendation:** Wrap all cross-mod calls in capability/optional-mod guards; namespace persistentData keys with `teras:`; add JEI + Jade + Patchouli plugins. **Effort: Medium.**

---

## 17. Scalability Assessment

- **CPU:** Blocking HTTP (§6.1) and O(N²) tick (§6.2) are the two hard caps. Realistically unsafe above ~10–20 concurrent racers / network-active players.
- **Memory:** CEF browser instances per screen + texture/video caches dominate; native memory will be the limiter on big servers.
- **I/O:** Flat JSON files rewritten wholesale (no diffing, no atomicity) — write amplification grows with quest/track count.
- **Horizontal:** External `teras.es` API is a single point of failure with no circuit breaker; if it's down, login/race flows degrade or hang.

**Verdict:** Will not scale to a busy public server without §6/§9/§13 fixes.

---

## 18. Technical Debt Inventory

| Issue | Severity | Impact | Likelihood | Fix Complexity | Recommended Action |
|---|---|---|---|---|---|
| Blocking HTTP on game thread | Critical | Server freeze | High | Low | Async + timeout |
| Chat-spoof packet | Critical | Exploit | High | Low | Permission + remove broadcast |
| Server-pushed JS exec | Critical | Client RCE-adjacent | Medium | Low | Whitelist/remove |
| Non-atomic JSON saves | Critical | Data loss | Medium | Low | Temp+move |
| `_old` graveyard (34 files/2,279 LOC) | High | Confusion | Certain | Low | Delete |
| Two packet channels | High | Maintenance/bugs | Certain | Medium | Merge |
| examplemod identity | High | Branding/conflict | Certain | Low | Rename |
| No tests / empty `src/test` | High | Regressions | Certain | High | Add CI + tests |
| 49 printStackTrace | High | Silent failures | Certain | Low | Logger |
| Wrong race-position logic | High | Bad gameplay | Certain | Medium | Real ranking |
| Hardcoded Spanish strings | Medium | No i18n | Certain | Medium | Lang files |
| Reflection into Vehicle mod | Medium | Breaks on update | Medium | Medium | Stable API/AT |
| Duplicate classes | Medium | Confusion | Certain | Low | Consolidate |
| 33 ad-hoc `new Gson()` | Low | Minor GC | Certain | Low | Reuse static |

Categorized: **4 Critical, 6 High, 4 Medium, 1 Low** (representative, not exhaustive).

---

## 19. Refactoring Roadmap

**Networking** — *Current:* two channels, full-buffer string decode, no validation. *Problems:* §5.1, §8, §13. *Recommended:* one `SimpleChannel`, packet-ID enum, `readUtf(max)`, mandatory authority checks, rate limiting. *Migration:* Medium (touches ~50 packet files). *Benefit:* security + maintainability. **Medium effort / High impact.**

**Persistence** — *Current:* hand-rolled GSON to flat files. *Problems:* §9. *Recommended:* atomic writes, schema versioning, `WorldSavedData` for world-tied data, player Capability. *Migration:* Medium–High. *Benefit:* no corruption, proper backups. **High effort / Transformative.**

**HTTP/SmartRotom** — *Current:* blocking, double-connection, raw threads. *Recommended:* shared executor, `CompletableFuture`, timeouts, HTTPS+auth, circuit breaker. *Migration:* Low. **Low effort / High impact.**

**Race system** — *Current:* god `RaceManager`, insertion-order "position", per-player global tick. *Recommended:* split `RaceService`/`RaceTicker`/`RankingCalculator`, server-tick driving, real standings. *Migration:* Medium. **Medium effort / High impact.**

**Package structure** — *Current:* `util` god-package, `_old` graveyard. *Recommended:* domain/service/mc layering; delete `_old`. *Migration:* Low–Medium (mechanical). **Low effort / High impact.**

---

## 20. Missing Features Analysis

1. **i18n / lang files** — required for any audience beyond Spanish servers.
2. **In-game config UI / CraftTweaker hooks** — currently a single `escalaCarteles` config value ([TerasConfig.java:8](../src/main/java/es/boffmedia/teras/TerasConfig.java#L8)) despite a huge feature set; almost nothing is configurable.
3. **Racing HUD** (position/lap/timer overlay).
4. **Kart/item progression & unlocks.**
5. **Spectator mode / replay** for races.
6. **Leaderboards** (the SmartRotom backend hints at this but `postCarrera` is TODO).
7. **Patchouli guidebook** for the many systems.
8. **Graceful offline mode** when `teras.es` is unreachable.

Quick wins: lang files, config expansion, HUD. Medium: leaderboards, progression. Major: spectator/replay, full quest editor.

---

## 21. Competitive Analysis

- **vs. Pixelmon-only servers:** Teras's racing layer is a unique retention hook none of them have.
- **vs. MrCrayfish Vehicle alone:** Teras adds drift/boost/hit and structured races — a real product on top.
- **vs. minigame plugins (Bukkit/Spigot):** Those have polished UX, leaderboards, and i18n that Teras lacks. The bar for "best in category" is **leaderboards + HUD + i18n + stability**, all currently missing or broken.

To be "best in category," the differentiator (kart+Pixelmon) must be matched by table-stakes polish (HUD, i18n, no freezes).

---

## 22. Enterprise Readiness Assessment

| Capability | Status |
|---|---|
| Large modpacks | ❌ examplemod identity + hard deps + reflection fragility |
| Public servers | ❌ blocked by §13 security + §6 freezes |
| Team development | ❌ god-package, dual-language, no tests, static coupling |
| Open-source contribution | ❌ "All rights reserved", no CONTRIBUTING, no module boundaries |
| CI/CD | ⚠️ `.gitlab-ci.yml` exists (build only); no test/lint stages |
| Automated testing | ❌ `src/test` empty |
| Docs generation | ⚠️ README + DeepWiki badge; no Javadoc/Wiki for systems |

**Recommendations:** rename off `examplemod`; add SpotBugs/Checkstyle + a test stage to CI; introduce module boundaries; write a `CONTRIBUTING.md` and architecture doc; adopt semantic versioning.

---

## 23. Risk Matrix

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Server freeze from blocking HTTP | High | Critical | Async I/O + timeouts (§6.1) |
| Chat-spoof / JS-exec exploit abused | High | Critical | Authority checks, remove/whitelist (§13) |
| Save corruption on crash | Medium | Critical | Atomic writes + schema (§9) |
| Pixelmon/Vehicle update breaks mixins/reflection | High | High | Version pinning + stable APIs (§16) |
| External API outage breaks gameplay | Medium | High | Circuit breaker + offline mode |
| Regression from no tests | High | High | CI test suite |
| O(N²) tick under load | Medium | High | Server-tick batching (§6.2) |
| Branding/registry conflict from examplemod | Low | Medium | Rename identity |

---

## 24. Prioritized Action Plan (by ROI)

> **Remediation status — updated 2026-06-23.** See the "Remediation Progress Log" at the end of this file for details.

1. ✅ **DONE** — Make all HTTP async + timeouts; remove double-connection (Low effort, Critical).
2. ✅ **DONE** — Add authority/permission checks to `SMessageChatMessage`; stop broadcasting unprivileged client text (Low, Critical). *(Other server-bound packets — §13.5 — still need an authority audit.)*
3. ✅ **DONE** — Whitelist `CMessageRunJS` to known JS function calls + length cap (Low, Critical).
4. ✅ **DONE** — Atomic file writes (temp+move) in `FileHelper` (Low, Critical).
5. ✅ **DONE** — Null-guard `raceManager` + tick vehicles once per server tick (Low, High).
6. ✅ **DONE** — Delete `VideoScreen2`, `QuestList`, `QuestListBak` (duplicate/unused classes). *(The `legacy` package is heavily referenced across 20+ files and cannot be deleted without migrating dependents — see remediation log.)*
7. 🟡 **PARTIAL** — Renamed `examplemod` → `teras` in build.gradle run configs/manifest; removed IMC hello-world + RegistryEvents stub. *(mods.toml `logoFile` reference remains.)*
8. 🟡 **PARTIAL** — Replaced `printStackTrace`/`System.out` + debug spam in touched files (SmartRotomAPI, FileHelper, RaceManager, Teras, TerasEvents); full-codebase sweep still pending.
9. ✅ **DONE** — Merge the two packet channels into one (Medium, High).
10. ✅ **DONE** — Fix race-position ranking logic. *(Already implemented: `Race.calculatePositions()` uses lap count + spline-based progress sorting, stored in `ConcurrentHashMap`, sent to clients every second.)*

---

## 25. 30-Day Roadmap (Stabilization)
- Complete all 10 items above.
- Add SpotBugs + Checkstyle + a JUnit stage to CI; write first tests for `RaceManager` ranking, `FileHelper` round-trip, packet encode/decode.
- Length-cap + validate every packet field.
- Introduce one shared `ExecutorService`; marshal results to server thread.
- Split `TerasEvents` into client/server/command handlers.

## 26. 90-Day Roadmap (Hardening & Polish)
- Migrate world-tied state to `WorldSavedData`; introduce a player Capability for race/quest flags.
- Add schema versioning + migration to all JSON.
- Full i18n (`en_us`/`es_es`); racing HUD; minimum-player start gate.
- HTTPS + auth + circuit breaker on SmartRotom; offline-safe gameplay.
- JEI + Jade + Patchouli integrations.
- Restructure into domain/service/mc packages; shrink `util`.

## 27. Long-Term Vision (1 Year)
- Single, documented, versioned network protocol.
- 60%+ test coverage on pure-logic modules; reproducible CI builds with reobf + publish.
- Leaderboards, kart/item progression, spectator/replay.
- CraftTweaker/KubeJS-driven content so server admins tune races/quests without code.
- Public, documented mod with `CONTRIBUTING`, architecture docs, and stable cross-mod API boundaries — positioned as the definitive kart-racing layer for Pixelmon servers.

---

## Top 50 Improvements (ranked by expected ROI)

| # | Improvement | Effort | Impact |
|---|---|---|---|
| 1 | Async HTTP + timeouts; kill `FutureTask.get()` blocking | Low | Critical |
| 2 | Remove double `HttpURLConnection` in `getConnectionStream` | Trivial | High |
| 3 | Add permission/authority checks to all server-bound packets | Low | Critical |
| 4 | Stop broadcasting client-supplied chat (`SMessageChatMessage`) | Trivial | Critical |
| 5 | Whitelist/remove `CMessageRunJS` | Low | Critical |
| 6 | Atomic file writes (temp + move) in `FileHelper` | Low | Critical |
| 7 | Null-guard `Teras.raceManager` before per-tick use | Trivial | High |
| 8 | Tick vehicles once per server tick, not per player | Low | High |
| 9 | Length-cap + validate all packet string fields | Low | High |
| 10 | Delete `_old` (34 files / 2,279 LOC) | Low | High |
| 11 | Rename `examplemod` → `teras` in build/manifest/IMC | Low | High |
| 12 | Remove MDK stubs (`RegistryEvents`, IMC hello world) | Trivial | Medium |
| 13 | Merge two packet channels into one | Medium | High |
| 14 | Replace 49 `printStackTrace` with logger | Low | Medium |
| 15 | Remove 34 `System.out.println` | Low | Medium |
| 16 | Fix real race-position ranking (not `indexOf`) | Medium | High |
| 17 | i18n: move all strings to lang files | Medium | High |
| 18 | Migrate race/quest state to `WorldSavedData` | High | High |
| 19 | Introduce player Capability for FB/race flags | Medium | High |
| 20 | Add `schemaVersion` to all JSON DTOs | Low | High |
| 21 | Stop serializing engine API types in DTOs (`IQuest`) | Medium | High |
| 22 | Single shared `ExecutorService` for all I/O | Low | High |
| 23 | HTTPS + auth token + circuit breaker on SmartRotom | Medium | High |
| 24 | Add CI test + lint stages (SpotBugs/Checkstyle) | Medium | High |
| 25 | Write tests for ranking, FileHelper, packet codecs | Medium | High |
| 26 | Split `TerasEvents` into client/server/command | Low | High |
| 27 | Gate client-only init (MCEF) behind DistExecutor properly | Low | High |
| 28 | Consolidate duplicate classes (`QueryHelperOld`, `QuestListBak`, `TerasBattleOld`, `VideoScreen2`, dual `TerasConfig`) | Low | Medium |
| 29 | Minimum-player gate before race start | Low | Medium |
| 30 | Racing HUD overlay (position/lap/timer) | Medium | High |
| 31 | Finish `postCarrera` reward persistence | Low | Medium |
| 32 | Reuse static `Teras.GSON` (remove 33 `new Gson()`) | Low | Low |
| 33 | Namespace `persistentData` keys with `teras:` | Low | Medium |
| 34 | Wrap cross-mod calls in optional-mod guards | Medium | Medium |
| 35 | Replace MrCrayfish reflection with AT/stable API | Medium | Medium |
| 36 | Expand config beyond single `escalaCarteles` value | Medium | Medium |
| 37 | JEI integration for custom items/recipes | Medium | Medium |
| 38 | Jade/TheOneProbe integration for blocks/TEs | Medium | Medium |
| 39 | Patchouli guidebook | Medium | Medium |
| 40 | CraftTweaker/KubeJS hooks for races/quests | High | Medium |
| 41 | Restructure `util` god-package into layers | Medium | High |
| 42 | Offline-safe gameplay when API down | Medium | Medium |
| 43 | Per-player packet rate limiting | Low | Medium |
| 44 | Audit `SMessageDarCaja`/`EncenderPC`/`UpdateDex` authority | Low | High |
| 45 | Leaderboards backed by SmartRotom | Medium | Medium |
| 46 | Kart/item progression loop | High | Medium |
| 47 | Use data generators for models/loot/recipes/lang | Medium | Medium |
| 48 | Semantic versioning + changelog discipline | Low | Medium |
| 49 | Spectator/replay mode | High | Medium |
| 50 | Architecture + CONTRIBUTING docs; relicense decision | Medium | Medium |

---

### Audit completeness note
Central files were read end-to-end (main mod class, both network channels and representative packets, `RaceManager`, the HTTP layer, `FileHelper`, `TerasEvents`, `CarreraEvent`, config, mixins manifest, build files), plus structural metrics across all 304 files. Not every one of the 304 files was line-read; per-class findings generalize from the representative sample plus structural evidence (file names, grep counts, package layout). The highest-severity items (§13 security, §6 performance, §9 persistence) are based on direct reads and are immediately actionable.

---

## Remediation Progress Log

**Started 2026-06-23.** Fixes applied in priority order (§24). Each entry cites the files changed.

### ✅ Done

**6. Duplicate/unused class removal (§24.6)** — [VideoScreen.java](../src/main/java/es/boffmedia/teras/client/gui/VideoScreen.java), [ClientProxy.java](../src/main/java/es/boffmedia/teras/client/ClientProxy.java)
- Consolidated `VideoScreen2` into `VideoScreen` by adding an `instantClose` constructor parameter (default `false`). `VideoScreen` preserves its fade-to-black behavior; `ClientProxy.verVideo()` now uses `new VideoScreen(url, 100, true)` for instant close.
- Deleted `VideoScreen2.java` (196 lines of near-duplicate code).
- Deleted `QuestListBak.java` (33 lines, entirely commented-out logic, unused).
- Deleted `QuestList.java` (56 lines, never imported by any other file).
- **Note:** The `legacy` package (27 files) is heavily referenced across 20+ active files (`TerasConfig`, `CarreraManagerOld`, `Circuito`, `Punto`, `Recompensa`, `ObjColocable`, `ObjetoMC`, mission types, etc.). Deleting it would break the build. Migration of dependents is required before removal — this is a larger refactoring effort, not a simple deletion.

**1. Blocking HTTP / double-connection (§6.1, §13.3 partial)** — [SmartRotomAPI.java](../src/main/java/es/boffmedia/teras/util/data/smartrotom/SmartRotomAPI.java)
- Replaced the `new Thread(futureTask)` + `futureTask.get()` (unbounded block on the calling thread) with a shared daemon `ExecutorService` and a new non-blocking `wingullGETAsync` returning a `CompletableFuture`.
- `wingullGET` now waits with a bounded timeout (connect+read+1s) and returns `null` on failure instead of hanging or throwing — so a dead `teras.es` can no longer freeze the server thread.
- Added 5s connect/read timeouts to GET and POST.
- Removed the duplicate `getConnectionStream` second connection (the GET was firing twice) and the `setDoOutput(true)`-on-GET bug (which silently turned the GET into a POST).
- `printStackTrace` → logger; streams closed via try-with-resources.
- *Follow-up:* callers (`getRegions`, login flows) should move off the server thread to `wingullGETAsync`; HTTPS + auth token (§13.3) still pending.

**2. Chat-spoof packet (§13.1)** — [SMessageChatMessage.java](../src/main/java/es/boffmedia/teras/net/server/SMessageChatMessage.java)
- Added an authority check: only players with permission level ≥ 2 (OP) can broadcast a server-wide system message; others are logged and ignored.
- Replaced full-buffer `buf.toString` / `writeCharSequence` with length-capped `readUtf(4096)` / `writeUtf` (§8.1, §8.2).

**3. Server-pushed JS execution (§13.2)** — [CMessageRunJS.java](../src/main/java/es/boffmedia/teras/net/client/CMessageRunJS.java)
- Added a client-side whitelist: the payload must match a single call to one of the known functions (`frenteBatalla`, `openDex`, `takeScreenshot`); anything else is rejected and logged.
- Length-capped `readUtf(2048)` / `writeUtf`.

**4. Non-atomic JSON saves (§9.1, §9.2)** — [FileHelper.java](../src/main/java/es/boffmedia/teras/util/file/FileHelper.java)
- `writeFile` / `writeStringFile` now write to a sibling temp file then `Files.move(..., ATOMIC_MOVE)` (with non-atomic fallback) — a crash mid-write leaves the original intact.
- Fixed the broken `token.getClass().newInstance()` path in the `Type`-token `readFile` (it created a `TypeToken` instance, not the target type); it now seeds `{}` and deserializes through the token so callers get a correctly-typed empty map.
- `printStackTrace` → logger; readers closed via try-with-resources.

**5. raceManager NPE / O(N²) tick (§6.2, §6.3)** — [CarreraEvent.java](../src/main/java/es/boffmedia/teras/event/karts/CarreraEvent.java), [RaceManager.java](../src/main/java/es/boffmedia/teras/util/objects/karts/RaceManager.java)
- Null-guarded `Teras.raceManager` before per-tick use.
- Moved `tickVehicles()` out of the per-player `playerTick` into a single `ServerTickEvent` handler — now O(H) per tick instead of O(P·H).
- Removed the `System.out.println` debug lines in `voteStart`.

**9. Merge packet channels (§5.1, §24.9)** — [Messages.java](../src/main/java/es/boffmedia/teras/net/Messages.java), [CommonHandler.java](../src/main/java/es/boffmedia/teras/CommonHandler.java), [FrameBlockEntity.java](../src/main/java/es/boffmedia/teras/tileentity/FrameBlockEntity.java), [TVVideoScreen.java](../src/main/java/es/boffmedia/teras/client/gui/TVVideoScreen.java)
- Merged the `PacketHandler` channel (`teras:network`, protocol `"2"`, 3 video packets) into the `Messages` channel (`teras:packetsystem`).
- Bumped `Messages` protocol version from `"1"` to `"2"`.
- Moved `sendTo`, `sendToClient`, `sendToAllTracking`, `sendToAll`, `sendToServer` utility methods from `PacketHandler` into `Messages`.
- Registered `FrameVideoMessage`, `OpenVideoManagerScreen`, `UploadVideoUpdateMessage` in `Messages.registryNetworkPackets()`.
- Updated `FrameBlockEntity` (3 calls) and `TVVideoScreen` (9 calls) to use `Messages` instead of `PacketHandler`.
- Removed `PacketHandler.init()` from `CommonHandler.setup()`.
- Deleted `PacketHandler.java` (76 lines). Total: 1 channel, 21 packets, protocol `"2"`.

### 🟡 Partial

**7. examplemod identity (§4.8, §5.7)** — [Teras.java](../src/main/java/es/boffmedia/teras/Teras.java), [build.gradle](../build.gradle)
- Removed the IMC "Hello world from the MDK" send and the empty `RegistryEvents` MDK stub.
- Renamed `examplemod` → `teras` in the `runs` mod blocks + data-gen `--mod` arg (these must match the real modid `teras`); manifest `Specification/Implementation-Vendor` → `boffmedia`, `Specification-Title` → `teras`.
- Removed debug log spam: `TESTES`, `HELLO FROM PREINIT`, `API NO FUNKA`, `FUNCTIONANDO LOGIIIINN`, and the `enterWorld`/`onLogin` permission-dump spam.
- *Remaining:* `mods.toml` still has `logoFile="examplemod.png"`.

**8. Logging cleanup (§4.1, §4.2, §4.3)** — touched files only
- Replaced `printStackTrace`/`System.out`/placeholder logs in the files above. A full-codebase sweep of the remaining ~49 `printStackTrace` / ~34 `System.out` is still pending.

### ⏳ Not yet started
- §24.6 continued — The `legacy` package (27 files) is still referenced across 20+ files. Migrating dependents off `legacy.serverdata.TerasConfig`, `legacy.karts.CarreraManagerOld`, `legacy.karts.Circuito`, etc. is required before the package can be removed. `TerasBattleOld` is still needed by `CombateFrenteBatalla`.
- §13.3 HTTPS + auth token + circuit breaker on SmartRotom.
- §13.5 Authority audit of `SMessageDarCaja` / `SMessageEncenderPC` / `SMessageUpdateDex`.
- §4.5 Contradictory `isSiteBlacklisted` / `getNextAvailablePadID` (left as-is to avoid breaking callers that may depend on current behavior — needs caller review).
