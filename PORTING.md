# Teras 1.16.5 (Forge) → 1.21.1 (NeoForge) port

Branch: **`1.21.1-neoforge`**. The original 1.16.5 project is preserved untouched in
[`legacy-1.16/`](legacy-1.16/) for reference during the port (and still lives on branches
`teras-1.0` / `1.16.5`, which keep the live server running).

## First feature ported: SmartRotom (MCEF browser)

The 1.16.5 mod used the **montoyo** MCEF API (`net.montoyo.mcef.api.*`). This port targets
**CinemaMod MCEF** for 1.21.1 (`com.cinemamod:mcef:2.1.6-1.21.1`), which exposes raw JCEF instead
of montoyo's high-level abstraction. Everything is rebuilt on top of MCEF's **public** API — MCEF
source is **not** modified.

### API mapping (montoyo → CinemaMod)

| montoyo (1.16) | 1.21.1 replacement | Where |
|---|---|---|
| `API.createBrowser(url)` | `MCEF.createBrowser(url, transparent)` | `mcef/TerasMCEF.java` |
| `IBrowser.runJS(js, frame)` | `CefBrowser.executeJavaScript(js, url, 0)` | `TerasMCEF.runJS` |
| `IBrowser.inject*` (mouse/key) | `MCEFBrowser.sendMouse*/sendKey*` | `client/gui/PantallaSmartRotom.java` |
| `IBrowser.draw(...)` | textured quad from `getRenderer().getTextureID()` | `PantallaSmartRotom.render` |
| `registerJSQueryHandler` + `mcefQuery` JS | JCEF `CefMessageRouter` on MCEF's `CefClient` | `mcef/TerasQueryRouter.java`, `TerasMCEF.registerRouter` |
| `IJSQueryCallback` | wrapper over `CefQueryCallback` | `mcef/JsQueryCallback.java` |
| `api.registerScheme("teras", …)` | not ported — the web app is remote-hosted | n/a |

### Files added

- `Teras.java` — `@Mod` entry, registers items + creative tab + data components, loads config (common).
- `init/ItemInit.java` — `smartrotom` item + `teras` creative tab.
- `init/ComponentInit.java` — the per-item `smartrotom_id` UUID data component (see per-item browsers).
- `items/SmartRotom.java` — right-click opens the browser (dex-scan path deferred, see below).
- `client/TerasClient.java` — client setup: load config, register JS bridge with MCEF.
- `mcef/TerasMCEF.java` — browser lifecycle + JS bridge registration + Java→JS.
- `mcef/TerasQueryRouter.java` — `window.mcefQuery(...)` → Java dispatch.
- `mcef/JsQueryCallback.java` — montoyo-shaped callback backed by `CefQueryCallback`.
- `client/gui/PantallaSmartRotom.java` — full-screen browser Screen (render + input).
- `util/QueryHelper.java` — query dispatch (structure preserved 1:1; `getPlayers`, `chatMessage`,
  `getUserData` wired).
- `util/TerasConfig.java` — reads `config/teras/config.json` (`home`, `API_URL`, `apiToken`,
  `requireHttps`); **auto-creates it with defaults if missing** (ports 1.16.5 `FileHelper.getConfig`).
  Default `home` is the real SmartRotom site `http://teras.es/smartrotom` (not a generic page), so the
  browser loads the actual app out of the box; admins override it in the JSON. See the config note below.
- `net/TerasNet.java` — NeoForge payload networking (replaces the 1.16.5 `SimpleChannel`).
- `net/ChatMessagePayload.java`, `net/UserDataRequestPayload.java` — client→server payloads.
- `net/McefResponsePayload.java` — server→client async reply (replaces `CMessageMCEFResponse`).
- `client/ClientNetHandler.java` — client-only handler resolving the pending `mcefQuery` callback.

## Second feature ported: in-hand (first-person) SmartRotom renderer

When the player holds the SmartRotom, the live browser texture is drawn on the "screen" of the item
model in first person, mirroring 1.16.5's `SmartRotomRenderer`.

### Files added

- `client/renders/IItemRenderer.java` — tiny first-person renderer contract (`PoseStack` +
  `MultiBufferSource`), ported 1:1.
- `client/renders/SmartRotomRenderer.java` — the port of the 1.16.5 renderer: on a hand render it
  cancels vanilla hand rendering, manually renders the arm, renders the item model, then draws the
  browser texture as a quad on the model screen area at model coords `(0,0) → (27.65/32, 14/32)`,
  with a black-quad fallback when no browser exists. Early-returns while `PantallaSmartRotom` is open.
- `client/ClientEvents.java` — game-bus (`Bus.GAME`, `Dist.CLIENT`) subscriber for
  `RenderHandEvent`; replaces 1.16.5 `ClientProxy.onRenderPlayerHand`. Holds the single renderer
  instance, delegates for the SmartRotom item, then `ev.setCanceled(true)`.

### montoyo/1.16.5 → 1.21.1 API changes handled

| 1.16.5 | 1.21.1 | Note |
|---|---|---|
| `MatrixStack` | `PoseStack` | `stack.clear()` was a no-op (assertion helper) — dropped |
| `Vector3f.YP/XP/ZP.rotationDegrees` | `com.mojang.math.Axis.YP/XP/ZP.rotationDegrees` | |
| `IRenderTypeBuffer` | `MultiBufferSource` | |
| `ItemCameraTransforms.TransformType.FIRST_PERSON_*` | `ItemDisplayContext.FIRST_PERSON_*_HAND` | |
| `mc.getItemInHandRenderer()` | `mc.getEntityRenderDispatcher().getItemInHandRenderer()` | accessor moved onto the dispatcher |
| `PlayerRenderer.renderRightHand(stack, buffer, light, player)` | **same signature** | takes `AbstractClientPlayer`; `LocalPlayer` qualifies |
| `mc.getTextureManager().bind(player skin)` before arm | **dropped** | `renderHand` binds `player.getSkin().texture()` internally now |
| montoyo `IBrowser.draw(...)` | hand-rolled textured quad from `browser.getRenderer().getTextureID()` | via `poseStack.last().pose()` + `BufferBuilder.addVertex(Matrix4f,…)`, UV Y-flipped |

### Design decisions / deviations from legacy (flag list)

- **Per-item browser** (see the dedicated section below). The in-hand renderer looks up the browser
  for *this* item's `smartrotom_id` via `TerasMCEF.getBrowser(uuid)`. Each browser is sized to the
  window by the full-screen path; in-hand we ignore its pixel size and just map UVs `0..1` onto the
  screen quad, so resolution never matters here.
- **Nothing drawn when off.** When there is no browser (SmartRotom off) the screen area draws
  nothing — the model's own screen face shows through. (1.16.5 drew a black placeholder quad here,
  which under this model's transforms rendered misplaced, so it was dropped.)
- **Buffer flush before the browser quad.** NeoForge's hand-render pass hands us a *buffered*
  `MultiBufferSource.BufferSource`; the arm/item go through it while the browser quad is an
  immediate-mode draw. We `endBatch()` after the arm/item so the model flushes first and the browser
  lands on top (with depth test disabled). 1.16.5's montoyo draw was immediate too, but the flush is
  made explicit here to guarantee ordering under the buffered pass.
- **3D SmartRotom item model — DONE.** The Blockbench model was ported verbatim from 1.16.5:
  `assets/teras/models/item/smartrotom.json` (was a flat `item/generated` sprite) plus the
  `custom_model_data:1` override variant `smartrotom_sprigatito.json` + its texture. The base
  texture (`textures/item/smartrotom.png`) was already byte-identical in the project. On 1.21.1 the
  classic `models/item/<id>.json` + `overrides` system still applies (the data-driven `assets/<ns>/
  items/` model system only arrived in 1.21.4), so the model auto-binds to `teras:smartrotom` with no
  code change. The screen-quad coords `(27.65/32, 14/32)` and the renderer's transform math are tuned
  for this model + its `firstperson_*_hand` display transforms, so the browser now lands on the
  SmartRotom's screen face. Blockbench-only fields (`format_version`, `credit`, `groups`) are ignored
  by the Java model loader. The unused `SmartRotomModel.java` `EntityModel` export was still not ported.

## SmartRotom home configuration

`config/teras/config.json`:

```json
{
  "id": "aB3xK9pQ",
  "home": "http://teras.es/smartrotom",
  "API_URL": "http://api.boffmedia.es/smartrotom",
  "apiToken": "",
  "requireHttps": false
}
```

- **Ported behaviour:** `TerasConfig.load()` reads the file and, if it does not exist, **writes this
  default** — the 1.16.5 `FileHelper.getConfig()` did the same. The old port defaulted `home` to
  `https://www.google.com` and never wrote a template, so a fresh install opened a generic page
  ("default browser"); it now loads the **real SmartRotom** unless overridden.
- **Loaded on both sides** in common setup (`Teras#onCommonSetup` → `TerasConfig.load()`). The server
  needs `id`; the client needs `home`. (Was client-only before.)
- **`id` — the server/world identifier.** This is what the SmartRotom web uses to confirm the player
  is on the right server. The **server** injects its own `id` into the `getUserData` response as the
  `world` field (`TerasNet.handleUserDataRequest`), so a multiplayer client's local `id` is
  irrelevant — only the server's config `id` counts (exactly the 1.16.5
  `SMessageDatosServer`→`CMessageDatosServer` flow, where the server sent `config.getId()` and the
  client returned it to the page as `world`). A random 8-char id is generated + persisted on first
  run (matching `RandomStringUtils.random(8,true,true)`); real deployments set it to the value
  registered with the SmartRotom backend. An existing config without `id` gets one minted and written
  back (unknown fields preserved).
- `home` is the browser URL. It must contain `smartrotom` to satisfy `isSiteAllowed(...)`;
  `load()` logs a warning if it doesn't. Switch it to `https://…` in the JSON once the endpoint
  serves TLS.
- `apiToken` is a **server-side secret** for the (deferred) outbound HTTP API — leave it empty on
  clients. When the server-side API integration lands, keep the token server-side only (the 1.16.5
  `copyForClient()` stripped it before sending config to clients).
- **Navigation lock (deliberately NOT enabled):** 1.16.5 had an `onAddressChange` guard that would
  redirect back to `home` on any non-`smartrotom` URL — but it was **commented out**
  (`//api.registerDisplayHandler(this)`), so it never actually ran. CinemaMod MCEF supports it
  cleanly (`MCEF.getClient().addDisplayHandler(...)` appends, doesn't clobber MCEF's own handler), so
  it can be added later as an opt-in. It's left off here to preserve parity and avoid breaking the
  real app's own redirects, which can't be verified without a live run.

## Per-item browser instances

Like 1.16.5, **each individual SmartRotom item has its own browser** (its own page + navigation
state) — two SmartRotoms in an inventory show two independent pages. The old model was rebuilt, not
copied: 1.16.5 keyed browsers on `PadID`, a client-side `padList.size()+1` counter written into item
NBT (non-unique, non-persistent, and clobbered on server resync). This port uses a **persistent,
server-assigned `UUID`**.

- **`init/ComponentInit.java`** — a custom `smartrotom_id` data component
  (`DataComponentType<UUID>`, `UUIDUtil.CODEC` + `UUIDUtil.STREAM_CODEC`), registered on the mod bus.
- **`items/SmartRotom.java`** — `inventoryTick` assigns the id **server-side** (`UUID.randomUUID()`)
  the first time an item without one ticks in an inventory, so it persists in the save and syncs
  authoritatively to the client (a client can't spoof which browser is which). `SmartRotom.getId(stack)`
  reads it.
- **`mcef/TerasMCEF.java`** — now holds `Map<UUID, MCEFBrowser>` instead of one browser:
  `getBrowser(id)`, `getOrCreateBrowser(id, url)`, `closeBrowser(id)`, `closeAll()`, `runJS(id, js)`.
- **`client/ClientEvents.java`** — ports the 1.16.5 `onTick` GC. Every 10 ticks it scans the hotbar
  (slots 0–8) + offhand: it **creates** the browser for the item actually in a hand (identity match,
  so the in-hand renderer shows it live), **keeps alive** any SmartRotom present in the hotbar/offhand,
  and **closes** browsers whose item has left — so a full inventory of SmartRotoms never spawns a live
  Chromium per item. `closeAll()` fires on `ClientPlayerNetworkEvent.LoggingOut`.
- **`client/gui/PantallaSmartRotom.java`** takes the specific item's `MCEFBrowser`; `SmartRotom.use()`
  → `TerasClient.openSmartRotom(stack)` resolves the id, get-or-creates that browser, and opens it.

Known limitations (flagged, matching or exceeding 1.16.5): the JS async-reply path
(`QueryHelper.pendingCallback`, used by `getUserData`) is still a **single static callback** — as in
1.16.5 (`callbackMCEF`) — so two browsers issuing a server round-trip at the exact same moment could
race. A per-query id would fix it; deferred. Creative middle-click *copies* the component, so a
duplicated item would share the original's browser (edge case, accepted).

## Build & run

Requires JDK 21. A user-local Temurin 21 is installed at `~/jdks/jdk-21.0.11+10` and exported in
`~/.bashrc` (`JAVA_HOME`). `./gradlew build` and `./gradlew compileJava` are **verified passing**
in this environment (the jar assembles and the `neoforge.mods.toml` template expands correctly).

```bash
./gradlew build          # compile + package   (verified ✓)
./gradlew runClient      # launch the dev client (needs a display; run on your machine)
```

MCEF is pulled from `https://mcef-download.cinemamod.com`. It downloads the Chromium runtime on
first launch. In a dev environment MCEF also gives you an F10 demo browser to sanity-check MCEF
itself independently of Teras.

## ✅ Verification checklist (do these in-game)

1. **JS bridge function name.** Verified from the shipped `mcef-1.1.0.jar`: montoyo registered the
   query function as `mcefQuery` and the cancel function as JCEF's default `cefQueryCancel`. These
   are reproduced exactly in `TerasMCEF.JS_QUERY_FN` / `JS_CANCEL_FN`, so `window.mcefQuery(...)`
   from your site resolves as before. (Only revisit if you later changed the site's JS.)
2. First launch with no config: confirm `config/teras/config.json` is **auto-created** and that
   right-clicking the SmartRotom loads the real site (`home` defaults to `http://teras.es/smartrotom`).
   To point elsewhere, edit `home` (must contain `smartrotom` to pass the whitelist) and reopen — the
   page should render and be clickable/scrollable/typeable.
3. From the page, call `mcefQuery({request: JSON.stringify({query:"getPlayers"}), onSuccess:…,
   onFailure:…})` — `onSuccess` should receive the online player list. This proves JS→Java→JS.
   - `{query:"getUserData"}` → `onSuccess` receives `{uuid, username, world, x, y, z, op}` via a
     **server round-trip** (client→server→client), proving the async `McefResponsePayload` →
     `pendingCallback` path. **`world` = the server's config `id`** — verify it matches what you set
     in `config/teras/config.json` on the server; that's the "right server" check the web relies on.
   - `{query:"chatMessage","message":"hi"}` sent by an OP → broadcast to all players; a non-OP is
     silently rejected server-side (permission level 2).
4. Confirm mouse mapping is pixel-accurate at non-100% GUI scale (the port scales by
   `getGuiScale()`; the 1.16.5 code scaled by physical window size — behaviour should match, but
   verify clicks land where expected).

## ⏭️ Deferred (next phases, in dependency order)

- **Add the Pixelmon 1.21.1 dependency** (fill in `curse.maven:pixelmon-389487:<fileId>` in
  `build.gradle`), then port: the item's raytrace → `openDex(...)` scan, dex registration
  networking, and `RotomListenerMixin`. (Needs the mixin setup re-enabled too.)
- **Networking**: the NeoForge payload scaffold is in place (`net/TerasNet.java`) with `chatMessage`
  and `getUserData` wired end-to-end, plus the reusable async `McefResponsePayload` →
  `pendingCallback` path. Remaining `QueryHelper` handlers (`openPC`, `darCaja`, `setCall`/
  `leaveCall`, `getMisiones`, `getSpawns`) still need their server-side logic ported — most are
  coupled to Pixelmon (PC storage), voicechat (calls), or the mod's data model.
- **JourneyMap 1.21.1**: re-enable `getWaypoints` / `addWaypoint`.
- **ScreenshotHandler** and **CameraZoomHandler**: port and re-enable those handlers + the
  shift-click screenshot item path.
- **Multi-instance (per-item) lifecycle — DONE.** See the "Per-item browser instances" section above.
- **In-hand renderer DONE** (`client/renders/SmartRotomRenderer.java` + `client/ClientEvents.java`)
  **and the 3D SmartRotom item model DONE** (`models/item/smartrotom.json` + `smartrotom_sprigatito.json`
  override + texture) — see the sections above. The 1.16.5 `SmartRotomModel.java` (a Blockbench
  `EntityModel` export) is **not** used by the renderer and was intentionally not ported.

## Notes

- Verified compiling/packaging with `net.neoforged.moddev` 2.0.78, NeoForge 21.1.77, Gradle 8.14.2,
  Temurin JDK 21, MCEF 2.1.6-1.21.1. Bump any of these to a newer patch if you hit a resolution
  error later.
- In-game behaviour (browser render/input, JS round-trips, chat broadcast) still needs a real
  client run on a machine with a display — see the verification checklist above.
