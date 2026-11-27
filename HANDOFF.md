# Handoff: small arms work (branch `claude/youthful-hamilton-vv974c`)

For whoever picks this up next. Everything described here is committed and pushed; the working tree was clean at
the time of writing. Current version: **0.52.0** (`dist/steelrain-1.21.1-0.52.0.jar`). No pull request is open.

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

## V5 reference vault: ~37 more guns + grenades (in progress, batched)
The user supplied a V5 vault (`...uploads/...V5.zip`, extracted to scratch) asking for ALL ~37 new firearms plus
throwable frag/smoke grenades and the M320 grenade launcher, textures TRIMMED (lower texel density), realistic
stats. Being shipped in batches, each its own version:
- **0.52.0 DONE — 10 pistols**: Glock17/19, P99, HK USP, Five-seveN, M1911A1, Hi-Power, CZ75, P226, P228.
  Two shared builders in gen_guns.py: `_poly_pistol` (striker/polymer) and `_steel_pistol` (hammer/metal, grip
  panels). New calibres added to `Caliber`: P57 (5.7x28), ACP45 (.45). `GunType.pistol()` is now a switch listing
  all of them (used by holdsOpen/boltTravel/HUD); update it when adding more pistols.
- **Still to do**: assault rifles (10: AK12, AKM, AUG, FAMAS, Tavor X95, F2000, Bren2, QBZ-191/95, L85A2 —
  calibres R76239 7.62x39 and R58 5.8x42 already in `Caliber`), battle rifles (6: BM59, FAL, HK417, G3, M14,
  SG542 — 7.62x51), marksman (5: G28, M39, Mk12, PSL, QBU-88), bolt snipers (5: AWM, M24, M40A5, SV98, TRG42 —
  LAPUA338 in `Caliber`, need a bolt-action reload), grenades+M320 (new throw mechanic). Ammo models + ROUND_SPECS
  for R76239/R58/LAPUA338/P57/ACP45 are already in gen_guns.py; ammo items auto-register in ModRegistry.
- Pattern for each batch: build models in gen_guns.py (share builders), add MODELS entries (trim texel density),
  add GunType rows, per-gun fire sound in gen_gun_sounds.py + gen_sounds.py EVENTS + ModRegistry.GUN_SOUNDS,
  names/recipes/GUN_ITEMS/GUN_MAGS in gen_assets.py, then regenerate (gen_sounds.main(); g.guns(); g.lang()),
  test in the dev client, ship. NOTE: `git checkout -- tools/sounds src/main/resources/assets/steelrain/sounds`
  after gen_gun_sounds to drop re-encoded existing .ogg churn (new untracked sounds survive).

## 0.51.0: gun-system improvements (all four verified in game)
- **Caliber vs armour**: `Caliber.vehicleDamage()` gives each round an armour-bite; `Bullets.hitBody` uses it for
  `SrVehicle`/SBW targets and `SrVehicle.hurt` lets a BULLET source through without the generic reduction. Tuned
  so the .50 does ~26/hit to a vehicle (~19 to kill a 500-HP Gepard), 5.56 ~1.3 (bounces). Verified: .50 500->448
  in two hits; M249 spray 500->489.
- **Hit flinch + feedback**: `Payloads.Flinch` punches a shot victim's view (`GunClient.flinch`, fully recovers);
  a hit-marker tick (`GunHud.hit`, note-block hat sound). Flinch only fires for ServerPlayer victims, so it was
  NOT multiplayer-tested — the code path is simple but unproven against a real second player.
- **MG overheat & jams**: belt guns only. `GunLogic.State.heat/jammed`, `HEAT_PER_SHOT`/`HEAT_COOL`; heat adds
  spread above 0.4, cooks off at 1.0 into a stoppage cleared by reload (`EV_JAM`). Client mirrors heat for the
  gauge (`GunClient.heat/jammed`, `GunHud`). Verified: jams after ~114 continuous rounds, gauge green->red.
- **Weapon collision**: `GunLogic.muzzleBlocked` (raycast eye->muzzleReach) blocks firing server-side and
  `GunClient.blocked`/`GunHand` raise the gun to port. Verified: pressed to a wall, held fire, muzzle up.
- **Bug fixed**: heat/jam lived in the per-player `State` and carried across a gun swap (a jammed M249 made the
  next gun unfireable). The gun-change reset now clears `st.heat`/`st.jammed`.

## Verified in game since 0.50.0 (0.50.1 pass)
M240 belt-change reload; magnifier flip, light and laser toggles through their keys; weapon light lights the
ground; reduced motion (same burst lifts the view 1.60 deg normally, 0.70 deg with it on); HEAT rocket does its
full 70 damage to a Gepard at 100 m; OG-7V burst kills animals within 7.5 blocks and spares one 8 m away; wall
backblast hurts the firer (5 damage). The .50 damages vehicles (about 9 per hit, so roughly 55 hits for a
500-HP Gepard; raise `Caliber.B50` damage if that feels weak).
Bugs found and fixed: the side-slot key defaulted to **L** (vanilla Advancements) and is now **U**
(anyone who ran 0.50.0 keeps L in their saved options and must rebind); a quick one-tick trigger tap could be lost
between server ticks (now remembered with a `pulse` flag in `GunLogic`).
Testing notes: scripts run against the *same world* unless you restart the client, so `/give`n guns pile up in the
inventory and slot 0 may be an old, emptied gun; the test world is created on Peaceful, which makes `player.hurt`
return false (use `/difficulty normal` to test anything that hurts the player); animals stand below eye level, so
aim rockets at the ground to hit them.

## Not verified, and known gaps
- Sounds are synthesised and have never been listened to.
- Animations were judged from still screenshots only, never in motion.
- Not exercised in game: the scroll-wheel zoom of the variable scope, angled grip and clip-on bipod effects,
  multiplayer of any kind (other players' gun rendering, laser and light as seen by others).
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
