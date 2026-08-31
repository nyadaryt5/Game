# Galaxy Sandbox

A **native Android sandbox game** that mixes *Solar Smash* (orbital planet destruction) with
*WorldBox* (god-game civilisation sandbox) — written from scratch in **Kotlin**, with a custom
render engine on a `SurfaceView` and a **Jetpack Compose** interface on top.

No WebView, no game framework, no third-party engine: the simulation, the renderer, the particle
system and even the sound effects are all hand-written.

```
GalaxySandbox/
  app/src/main/java/com/nova/galaxysandbox/
    core/        Rng, value noise / fbm / ridged noise, math helpers, name generation
    galaxy/      Star systems, planets, moons, factions, fleets  +  living galaxy simulation
    world/       Tile world: terrain generation, biomes, creatures, kingdoms, wars, disasters
    action/      Weapons (orbit) and Tools (surface) + the systems that apply them
    fx/          Pooled particle system (sparks, embers, smoke, debris, shockwaves, plasma)
    render/      Procedural planet sphere textures, parallax starfield, galaxy & surface renderers
    engine/      Camera, game engine, HUD snapshot, mode transitions
    view/        SurfaceView + render thread + multi-touch input
    audio/       Procedurally synthesised sound effects (no audio assets shipped)
    ui/          Compose: menu, HUD, weapon/tool rails, inspector, settings, help
    MainActivity.kt
```

## Two scales, one continuous simulation

### Galaxy view (Solar Smash side)
* 16–140 procedurally generated star systems on spiral arms, each with orbiting planets, moons and rings.
* Star classes: red dwarf, G-type, blue giant, white dwarf, neutron star, black hole — each rendered differently.
* 12 planet classes from terran to gas giant, with atmospheres, city lights on the night side, ice caps and shields.
* Civilisations actually live there: population growth, tech progress, terraforming, colony fleets,
  interstellar wars, planetary shields, and anger at whoever keeps blowing up their neighbours.
* 15 weapons: laser, railgun, missile salvo, nuke, asteroid, meteor storm, annihilator beam,
  black hole, sun crusher, gravity slam, cryo beam, bio-plague, EMP, alien mothership, terraformer.
* Destroyed planets leave drifting debris fields; a dead star takes its whole system with it.

### Planet view (WorldBox side)
* Double-tap any planet to descend to a 200×120 tile surface generated from that planet's own seed:
  domain-warped fbm terrain, rivers, coastlines, latitude-driven biomes.
* Terrain brushes (raise, dig, forest, grass, desert, snow, mountain, lava) reshape the world live.
* Ten species — humans, sylvan, orcs, dwarves, frostkin, synthetics, wolves, bears, dragons, xenomorphs.
* Creatures wander, hunt, breed, fight, age and die. Civilised ones found kingdoms, claim territory,
  build huts → houses → cities → watchtowers, sign peace treaties and raid each other's borders.
* Disasters: meteor, volcano, nuke (with lasting fallout), lightning, tsunami, tornado, plague,
  ice age, acid rain, earthquake, plus Restore / Smite / Erase / Armageddon.
* Fire spreads through forests, ash regrows into grassland, lava crusts over, radiation decays.
* Whatever you do on the surface is written back to the planet in the galaxy view — and orbital
  strikes leave craters on the surface below.

## UI / presentation
* Animated title screen with a parallax starfield and orbiting planet motif.
* Glassmorphic Compose HUD: live stat bar, animated inspector panel, event feed, weapon/tool rails
  with cooldown fills, brush-size slider, settings sheet, help sheet.
* Custom rendering: radial-gradient star coronas, sweep-gradient accretion discs, shield bubbles,
  parallax nebulae, screen shake, impact flashes, vignette and a zoom cross-fade between scales.
* Procedural audio: every effect is synthesised as PCM at startup.

## Controls
| Action | Galaxy view | Planet view |
|---|---|---|
| Drag | Pan | Paint with the selected tool (pan with the cursor tool) |
| Pinch | Zoom | Zoom |
| Tap | Fire the armed weapon (or select when disarmed) | Apply tool |
| Double tap | Descend to the planet's surface | — |
| ⊕ button | Arm / disarm weapons | — |
| ❚❚ / » | Pause / cycle 0.5× → 4× speed | same |
| Back | Leave orbit → main menu | Leave orbit |

## Building the APK

**Android Studio** — open the `GalaxySandbox/` folder, let Gradle sync, then `Build → Build APK(s)`.

**Command line** (JDK 17 + Android SDK 34):
```bash
cd GalaxySandbox
gradle assembleRelease      # or ./gradlew assembleRelease once a wrapper is generated
# output: app/build/outputs/apk/release/app-release.apk
```

**GitHub Actions** — copy [`ci/build-galaxy-apk.yml`](../ci/build-galaxy-apk.yml) to
`.github/workflows/build-galaxy-apk.yml`, push, and the workflow builds the APK and uploads it as
the `galaxy-sandbox-apk` artifact (it can also be started manually from the Actions tab).

> The release build is signed with the standard debug key so it installs directly on a phone.
> Swap in your own keystore before publishing anywhere.

Requirements: Android 7.0 (API 24) or newer, landscape.
