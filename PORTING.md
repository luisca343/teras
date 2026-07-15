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

- `Teras.java` — `@Mod` entry, registers items + creative tab.
- `init/ItemInit.java` — `smartrotom` item + `teras` creative tab.
- `items/SmartRotom.java` — right-click opens the browser (dex-scan path deferred, see below).
- `client/TerasClient.java` — client setup: load config, register JS bridge with MCEF.
- `mcef/TerasMCEF.java` — browser lifecycle + JS bridge registration + Java→JS.
- `mcef/TerasQueryRouter.java` — `window.mcefQuery(...)` → Java dispatch.
- `mcef/JsQueryCallback.java` — montoyo-shaped callback backed by `CefQueryCallback`.
- `client/gui/PantallaSmartRotom.java` — full-screen browser Screen (render + input).
- `util/QueryHelper.java` — query dispatch (structure preserved 1:1; `getPlayers`, `chatMessage`,
  `getUserData` wired).
- `util/TerasConfig.java` — reads `config/teras/config.json` (`home`, `API_URL`, …).
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

- **Single shared browser.** Uses `TerasMCEF.getBrowser()` (not the 1.16.5 per-item `PadID` pad).
  If `null`, the black fallback quad is drawn. The shared browser is sized to the window by the
  full-screen path; in-hand we ignore its pixel size and just map UVs `0..1` onto the screen quad,
  so resolution never matters here.
- **Buffer flush before the browser quad.** NeoForge's hand-render pass hands us a *buffered*
  `MultiBufferSource.BufferSource`; the arm/item go through it while the browser quad is an
  immediate-mode draw. We `endBatch()` after the arm/item so the model flushes first and the browser
  lands on top (with depth test disabled). 1.16.5's montoyo draw was immediate too, but the flush is
  made explicit here to guarantee ordering under the buffered pass.
- **Fallback shader.** The black fallback uses `getPositionColorShader` (`POSITION_COLOR`) rather than
  reusing the textured shader with texture 0 bound (as 1.16.5 did) — a solid quad is robust and
  avoids sampling an unbound texture unit. Also, unlike the 1.16.5 quirk where the fallback captured
  its matrix *before* a (no-op) `translate(0.063, 0.28, 0.001)`, the offset there was dead code; the
  real-browser branch applies that offset. Cosmetic only, and only visible when no browser exists.
- **Item model asset still pending.** The screen-quad coords `(27.65/32, 14/32)` are tuned for the
  1.16.5 3D Rotom-pad item model. The item currently resolves a flat sprite model (see below), so the
  browser quad is positioned relative to that, not a real 3D screen face. Porting the pad model JSON
  + textures (a resource-pack asset, separate from the unused `SmartRotomModel.java` Blockbench
  export) is the remaining piece for pixel-correct placement.

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
2. Set `config/teras/config.json` → `home` to your SmartRotom URL (must contain `smartrotom` to
   pass the whitelist), then right-click the SmartRotom item: the page should render and be
   clickable/scrollable/typeable.
3. From the page, call `mcefQuery({request: JSON.stringify({query:"getPlayers"}), onSuccess:…,
   onFailure:…})` — `onSuccess` should receive the online player list. This proves JS→Java→JS.
   - `{query:"getUserData"}` → `onSuccess` receives your uuid/name/op via a **server round-trip**
     (client→server→client), proving the async `McefResponsePayload` → `pendingCallback` path.
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
- **Multi-pad lifecycle**: the 1.16.5 `ClientProxy` created a browser per SmartRotom item (NBT
  `PadID`) and tracked hotbar presence per tick. This port uses a single shared browser; restore
  the per-item pad model if you need multiple simultaneous browsers.
- **In-hand renderer DONE** (`client/renders/SmartRotomRenderer.java` + `client/ClientEvents.java`,
  see the section above). Remaining for pixel-correct placement: add the **3D Rotom-pad item model
  JSON + textures** (resource-pack asset) so the item renders as the pad instead of a flat sprite —
  the browser-quad coords assume the pad geometry. The 1.16.5 `SmartRotomModel.java` (a Blockbench
  `EntityModel` export) is **not** used by the renderer and was intentionally not ported.

## Notes

- Verified compiling/packaging with `net.neoforged.moddev` 2.0.78, NeoForge 21.1.77, Gradle 8.14.2,
  Temurin JDK 21, MCEF 2.1.6-1.21.1. Bump any of these to a newer patch if you hit a resolution
  error later.
- In-game behaviour (browser render/input, JS round-trips, chat broadcast) still needs a real
  client run on a machine with a display — see the verification checklist above.
