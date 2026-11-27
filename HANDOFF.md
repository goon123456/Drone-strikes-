# Handoff: small arms work (branch `claude/youthful-hamilton-vv974c`)

For whoever picks this up next. Everything described here is committed and pushed; the working tree was clean at
the time of writing. Current version: **0.50.0** (`dist/steelrain-1.21.1-0.50.0.jar`). No pull request is open.

## What was added in this stretch of work
All of it is the small-arms system (README section "Small arms" is the user-facing description):

- 16 guns: AK-74M, M4A1, SVD, PKM, M17, M590A1, Barrett M107, RPG-7, M18, M9, HK416, G36, SCAR-H, M249, M240, M110.
- Magazines as items that remember their rounds; loose rounds in ball, tracer and training kinds; two RPG rockets.
- Server-flown bullets (drag, drop, zeroing, penetration, ricochet), rockets with burst and backblast.
- Fittings in five slots (sight, muzzle, under, magnifier, side): red dot, holo, 4x, PSO-1, 10x, 1-6x, night,
  thermal, suppressors, foregrip, angled grip, bipod, flip magnifier, weapon light, laser.
- First-person rendering with arms, aim-down-sights, recoil, sway, step-by-step reloads, HUD, fittings screen.

## Where things live
| What | Where |
| --- | --- |
| Gun stats, calibres, attachments | `src/main/java/com/steelrain/gun/GunType.java`, `Caliber.java`, `Attachment.java` |
| Trigger, reload state machine, fittings, weapon light | `gun/GunLogic.java` |
| Bullet and rocket flight, hits, penetration | `gun/Bullets.java` |
| Items | `gun/GunItem.java`, `MagazineItem.java`, `AttachmentItem.java`; registered in `ModRegistry.java` |
| Packets | `net/Payloads.java` (GunInput, GunAction, GunShot, GunEvent, GunHit) |
| Input, recoil, sway, zoom, reload timing | `client/GunClient.java` |
| First-person gun, arms, reload motions | `client/GunHand.java` |
| Model assembly (bones, attachments) | `client/GunModels.java`; items/third person in `client/GunItemRenderer.java` |
| Tracers, casings, flash, lasers, shot sounds | `client/GunFx.java` |
| HUD and scope pictures | `client/GunHud.java`; fittings screen `client/GunScreen.java` |
| Gun models (Python, generates JSON + PNG) | `tools/gen_guns.py`, built on `tools/vehicle_kit.py` |
| Gun sounds (synthesised) | `tools/gen_gun_sounds.py` -> `tools/sounds/`, installed by `tools/gen_sounds.py` |
| Item files, recipes, language | `tools/gen_assets.py` (`guns()`, `GUN_LANG`) |

## How to work on it
- **Models**: edit `tools/gen_guns.py`. Gun space is bore along +z, trigger at z = 0, +x the firer's left, metres.
  Bones the game animates by name: `mag`, `bolt`, `trigger`, `selector`, `charge`, `cover`, `belt`, `bipod`,
  `irons`, `muzzle`, `frag`. Named points (`muzzle`, `sight`, `front`, `optic`, `under`, `grip`, `hand`, `mag`,
  `charge`, `cover`, `belt`) are read by the renderer. Use `body()`, `slab()`, `sweep()` for rounded shapes.
- **Regenerate only the gun assets** (do not run the whole of `gen_assets.py`: it rebakes every vehicle):
  `PYTHONPATH=tools python3 -c "import sys; sys.argv=['x']; import gen_assets as g; g.guns(); g.lang()"`
  After `tools/gen_gun_sounds.py`, run `git checkout -- tools/sounds src/main/resources/assets/steelrain/sounds`
  before `gen_sounds.main()` if you only added sounds: the encoder is not byte-stable and rewrites every .ogg.
- **Adding a gun**: append to `GunType` (keep order: ordinals go over the network and attachments are saved by
  ordinal), add the model to `MODELS` in `gen_guns.py`, a sound in `gen_gun_sounds.py` + `gen_sounds.py` +
  `ModRegistry.GUN_SOUNDS`, and names/recipes in `gen_assets.py`. Behaviour flags are methods on `GunType`
  (`beltFed()`, `pistol()`, `handRacked()`, `holdsOpen()`, `accepts()`), not per-gun `if`s elsewhere.
- **Releasing**, as every commit so far has done: bump `mod_version` in `gradle.properties`, `./gradlew build`,
  replace the jar in `dist/`, update `README.md`, commit, push to this branch.

## Looking at it in the game without a screen
The client runs under Xvfb with software GL (slow but usable; no audio device, so sounds were never heard):
`Xvfb :99 -screen 0 1600x900x24 &` then `STEELRAIN_SHOTS=1 DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runClient`.
`tools/dev/DevShots.java.txt` is the test driver used throughout: copy it to
`src/main/java/com/steelrain/dev/DevShots.java`. It creates a flat creative world and then runs any script dropped
into `run/dev_script.txt` (one command per line: `wait N`, `cmd <command>`, `shot <name>`, `use down|up`,
`attack down|up`, `reload`, `look yaw pitch`, `slot N`, `camera N`, `screen fit|inventory|none`, `quit`), saving
screenshots to `run/screenshots/`. **Delete that file again before building the jar for `dist/`.**
Give a loaded gun with e.g. `/give @s steelrain:m4a1[steelrain:rounds=30,steelrain:chamber=true,steelrain:fire_mode=1,steelrain:parts=[7,13],steelrain:active=true]`
(`parts` are `Attachment` ordinals; `load` is the round kind: 0 ball, 1 tracer, 2 training).

## Not verified, and known gaps
- Sounds are synthesised and have never been listened to.
- Animations were judged from still screenshots only, never in motion.
- Not exercised in game: M240 reload, the magnifier / light / laser keys and the scroll-wheel zoom (states were
  set directly), angled grip and clip-on bipod effects, reduced-motion setting, backblast against a wall, guns
  against vehicles, the OG-7V rocket, multiplayer of any kind.
- The old PKM belt always shows six rounds if any are left.
- The PKM stock is rounded limbs, not the solid thumbhole board of the real gun.
- The weapon light is a moving invisible light block; a server crash could leave one behind.
- Rockets are simulated like bullets, not entities, so air defence does not see them.
- Thermal sight is an overlay (warm shapes drawn over a dulled picture), not a real heat image.
- The jar is about 49 MB, mostly gun textures and meshes; texture resolution per model is the number in `MODELS`.
- From the user's reference vault these were left out: dual-optic setups (piggyback, offset), the digital-HUD
  sight, and cosmetic attachments (stocks, slings, rail covers). Its invented stats and abstract reloads were
  set aside at the user's choice in favour of realistic stats and step-by-step reloads.

## Working agreement with the user
- Ask before any major fork (they asked for this explicitly); small defaults can just be chosen and mentioned.
- They want TaCZ / Arma levels of detail: real steps in reloads, real ballistics, rounded mesh models, no gaps.
- Show a picture of a model change when the look is in question.
