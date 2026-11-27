# Sounds needed to replace Superb Warfare's

Format: mono `.ogg` or `.wav`, 44.1 kHz. Loops must repeat without a click (or send a long take and I will cut
the loop). Licence must allow putting them in the mod: CC0, CC-BY (tell me the author) or your own recordings.
Name the files as below; a few takes of the same sound (`_1`, `_2`, `_3`) are welcome and get picked at random.

Already done (your drone pack): FPV and Mavic rotors, Shahed / Reaper / TB2 / Orlan engines, Global Hawk jet,
jammer hum, video static, fibre snap.

## Small arms (synthesised for now by `tools/gen_gun_sounds.py`; recordings welcome)
`gun_m107_fire`, `gun_rpg7_fire`, `gun_rocket_in`, `gun_ak74m_fire`, `gun_m4a1_fire`, `gun_svd_fire`, `gun_pkm_fire`, `gun_m17_fire`, `gun_m590_fire` (one shot, close),
`gun_rifle_far`, `gun_heavy_far`, `gun_pistol_far`, `gun_suppressed`, `gun_pistol_suppressed`, `gun_mag_out`,
`gun_mag_in`, `gun_bolt`, `gun_bolt_release`, `gun_dry`, `gun_selector`, `gun_pump`, `gun_shell_in`, `gun_round_in`,
`gun_belt`, `gun_cover`, `gun_casing`, `bullet_crack`, `bullet_whizz`, `bullet_ricochet`.

## Explosions
Each heard close up, far off and very far off (the game switches by distance).

| File | What it is |
|---|---|
| `explosion_small_close` / `_far` / `_very_far` | grenade, mortar bomb, 30 mm shell, FPV charge |
| `explosion_medium_close` / `_far` / `_very_far` | 155 mm shell, GMLRS rocket, Shahed, Hellfire |
| `explosion_large_close` / `_far` / `_very_far` | Tomahawk, Iskander, ATACMS, vehicle blowing up |
| `explosion_air` | proximity burst / missile blowing up in mid-air (sharp crack, no ground thud) |
| `impact_metal_1..3` | small shells and fragments hitting a vehicle's armour |

## Guns firing
Close (`_close`) and distant (`_far`) for each; for the automatic guns a single shot that I repeat at the rate of fire.

| File | What it is |
|---|---|
| `zu23_fire_close` / `_far` | ZU-23-2, 23 mm, about 2,000 rounds a minute from two barrels |
| `gepard_fire_close` / `_far` | Gepard, 35 mm, slower and heavier |
| `pantsir_gun_fire_close` / `_far` | Pantsir, 30 mm, very fast rip |
| `howitzer_fire_close` / `_far` / `_very_far` | M109, 155 mm |
| `mortar_fire_close` / `_far` | 120 mm mortar, the hollow "thoonk" |
| `shell_casing_1..3` | spent cases clinking on the deck (optional, for the automatic guns) |

## Missiles and rockets leaving
| File | What it is |
|---|---|
| `rocket_launch_close` / `_far` | GMLRS rocket leaving the HIMARS pod (whoosh and roar) |
| `missile_launch_large_close` / `_far` | Tomahawk booster, Iskander, ATACMS, Patriot (deep roar) |
| `missile_launch_small` | Pantsir missile, Hellfire (short sharp hiss-bang) |
| `rocket_motor_loop` | a missile's motor heard as it flies past (loop) |
| `shell_incoming` | artillery shell whistle coming in |
| `catapult_launch` | Orlan catapult firing (pneumatic bang and rattle of the carriage) |
| `booster_launch` | Shahed leaving its rail on its booster rocket |

## Loading and handling
| File | What it is |
|---|---|
| `reload_cannon` | loading a 155 mm shell and closing the breech |
| `reload_mortar` | dropping a bomb into the mortar tube |
| `reload_belt` | feeding a new ammunition belt into an anti-aircraft gun |
| `reload_rocket_pod` | swapping a HIMARS pod (crane, clunks, latches) |
| `reload_missile` | loading a large missile onto a launcher |
| `load_drone` | fitting a drone onto a launcher or catapult |
| `grenade_release` | Mavic dropping its grenade (click and release) |

## Vehicles (new, for our own driving)
| File | What it is |
|---|---|
| `truck_engine_idle` / `truck_engine_drive` | HIMARS, Pantsir, Iskander, Krasukha; loops, the drive loop gets pitched up with speed |
| `tracked_engine_idle` / `tracked_engine_drive` | Gepard, M109; loops |
| `engine_start` / `engine_stop` | starting and stopping a diesel |
| `tracks_loop` | track links clattering, loop, louder with speed |
| `tyres_gravel_loop` | big tyres on rough ground, loop |
| `brakes_air` | air-brake hiss when a truck stops |
| `turret_traverse_loop` | electric turret motor whine while a turret turns, loop |
| `hydraulic_loop` / `hydraulic_stop` | HIMARS pod and Iskander erector raising, Patriot launcher elevating, jacks |
| `hatch_open` / `hatch_close` | heavy steel hatch |
| `vehicle_door` | truck cab door |
| `refuel` | pouring diesel from a can |
| `radar_lock` | short tone when an air-defence radar locks a target |

## Warnings
| File | What it is |
|---|---|
| `missile_warning` | tone in a drone pilot's ears when an interceptor is chasing their drone |
