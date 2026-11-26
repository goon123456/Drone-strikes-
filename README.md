# Steel Rain

An addon for **[Superb Warfare](https://modrinth.com/mod/superb-warfare)** (SBW) on **NeoForge 1.21.1**:
drones, cruise and ballistic missiles, rocket artillery, air defence and electronic warfare.

Fly kamikaze FPV drones through their own camera, plan missile strikes on a live map, shell targets with rocket
and tube artillery, and defend your base with air defence and jammers.

**Requires:** Superb Warfare 0.8.9 or newer and Kotlin for Forge (which SBW itself needs).
**Licence:** GPL-3.0-only, the same as Superb Warfare.

The idea was inspired by the *Iron Drone* mod. All code, models and textures here are original.

## Built on Superb Warfare
Steel Rain's ground vehicles (air defence, artillery, missile launchers, jammers) are real Superb Warfare
vehicles. The drone and aircraft launch sites (UAV Ground Station, Orlan Catapult, Shahed Launch Rail) are placed
full-size emplacements, and the drones and aircraft themselves are Steel Rain's own.

- **Getting one**: each comes packed in an SBW **container** (craftable; also in the Steel Rain and SBW vehicle
  creative tabs). Place it and open it with an SBW **crowbar**. Sneak + crowbar packs the vehicle up again.
- **Energy**: everything runs on SBW energy (FE). Park next to an SBW Charging Station or use any FE source.
  There is no liquid fuel.
- **Inventory**: sneak + right-click opens the vehicle's SBW inventory for ammunition, missiles and aircraft.
- **Driving**: the Gepard and M109 (tracked) and the Pantsir, HIMARS, Iskander launcher and Krasukha (wheeled)
  can be driven. The rest are static.
- **Damage and repair** follow SBW's rules.

### SBW air defence vehicles
- They come packed in a Superb Warfare **container** (craftable, or from the Steel Rain and SBW vehicle tabs).
  Place it and open it with an SBW **crowbar**. Sneak + crowbar packs a vehicle up again.
- **Gepard** (tracked) and **Pantsir** (8x8) can be driven; the **ZU-23-2** and **Patriot** are static.
- Get in to fire the cannons yourself with SBW's HUD. The radar-laid Gepard and Pantsir show a lead point; the
  hand-laid ZU-23-2 deliberately does not.
- **Automatic mode**: sneak + right-click (first player becomes the owner) toggles it. With nobody inside, the
  vehicle then searches for hostile aircraft, drones and missiles anywhere in range, aims at where they will be,
  and fires. This is Steel Rain's own area-defence logic: SBW's built-in turrets only defend themselves.
- **Targets**: Steel Rain drones and missiles, crewed SBW aircraft and helicopters, SBW recon drones and SBW
  guided weapons, unless they belong to you or your scoreboard team.
- **Ammo** goes in the vehicle's SBW inventory (sneak + right-click): 23 / 35 / 30 mm HE or AP belts for the
  guns, 57E6 missiles for the Pantsir, PAC-2 missiles for the Patriot. The cannons fire SBW's own shell
  projectile, which has a flight time.
- **Energy**: the radar draws 250 FE a second while searching (500 while tracking), a Pantsir missile 5000 FE,
  a Patriot missile 25000 FE. Driving uses energy the SBW way.
- Steel Rain's flying things can also be locked and shot by Superb Warfare's own weapons.

## What is in it

Everything is built at real-world scale (1 block = 1 metre). Turrets, pods and barrels physically swing onto
the target before firing.

| System | Size (W x H x L) | Type | How it is used |
| --- | --- | --- | --- |
| **FPV Kamikaze Drone** | 0.5 m | Piloted drone | Flown by hand through its camera, explodes on impact |
| **Fiber-Optic FPV Drone** | 0.5 m | Piloted drone | Same, but wired: cannot be jammed, limited by its cable instead of radio range |
| **DJI Mavic** | 0.45 m | Piloted recon drone | Reusable, hovers, zoom camera, carries one droppable grenade |
| **Orlan-10** | 3 m wingspan | Recon drone | Orlan Catapult + laptop. Circles the target for 4 minutes, returns by parachute |
| **Bayraktar TB2** | 12 m wingspan | Recon drone | Ground Station + runway (30 blocks). Circles for 8 minutes |
| **RQ-4 Global Hawk** | 40 m wingspan | High-altitude recon | Ground Station + runway (60 blocks). Very wide sensor, flies above the Pantsir |
| **MQ-9 Reaper** | 20 m wingspan | Strike drone | Ground Station + runway (40 blocks). Fires up to 4 Hellfires, then lands |
| **Shahed-136** | 2.5 m wingspan | One-way attack drone | Launch Rail (3 x 2 x 5) + laptop, range 4000 |
| **Tomahawk** | 5.6 m | Cruise missile | Trailer launcher (3 x 3 x 9), container erects to fire, range 6000 |
| **Iskander-M** | 7.3 m | Ballistic missile | TEL (3 x 3 x 13), missile raises to vertical, range 8000 |
| **M142 HIMARS** | 3 x 3 x 7 | Rocket artillery | 6 GMLRS rockets, salvo fire, range 1200 |
| **M109 Howitzer** | 3 x 3 x 7 | Tube artillery | 155 mm shells, cheap, less accurate, range 600 |
| **Pantsir-S1** | 3 x 4 x 10 | Short-range air defence | Missiles for far or fast targets, twin 30 mm cannons for close ones; reaches 100 blocks above itself |
| **Flakpanzer Gepard** | 3 x 4 x 8 | Gun air defence | Radar-aimed twin 35 mm guns, 96 block range, cheap ammo |
| **ZU-23-2** | 3 x 2 x 3 | Gun air defence | Twin 23 mm. Fires on its own, or sit on it and aim by hand |
| **MIM-104 Patriot** | 3 x 4 x 9 | Long-range air defence | 400 block reach, any altitude; only fires at aircraft, cruise and ballistic missiles |
| **EW Jammer** | 3 x 3 x 3 + mast | Jammer | 64 block bubble |
| **Krasukha-4** | 3 x 4 x 11 | Long-range jammer | 200 block bubble, burns fuel twenty times faster |
| **Trench Jammer** | 1 x 2 x 1 | Jammer | 32 block bubble, cheap base protection |

Ranges are in blocks and can be scaled in the config.

## How to play

### Launchers and the Field Laptop
1. Load ammunition into the vehicle's SBW inventory and make sure it is charged.
2. Right-click it while holding a **Field Laptop** to link it (up to 8 per laptop).
3. Right-click the air with the laptop to open **Fire Control**:
   - left-click the map to pick a target, or type X / Z coordinates
   - right-drag pans the map, the scroll wheel zooms
   - the orange ring is maximum range, the red ring is minimum range
   - choose the number of rounds for HIMARS / M109 salvos, then press **LAUNCH**
4. You must stay within 128 blocks of the launcher to command it.

Missiles keep the chunks along their path loaded, so strikes work thousands of blocks away.

### Runways and aircraft
The Reaper, Bayraktar and Global Hawk fly from a **UAV Ground Station**. In front of the station there must be a
straight, flat strip of any solid blocks: 5 wide, with nothing in the way across the aircraft's wingspan and
4 blocks up. It starts 3 blocks beyond the front of the station. The aircraft waits at the start of the strip,
rolls down it to take off, and lands back on it. If the strip is too short or blocked the laptop tells you where.

While an aircraft is up, its station's laptop entry shows **AIRBORNE** and offers:
- **RETASK** - pick a new target and send it there
- **RTB** - call it home
- **CAM** - watch through its camera

The **Orlan-10** needs no runway: load it on an **Orlan Catapult**. It comes back on its own and parachutes down.

### Recon
Every aircraft and the Mavic are sensors. For you and your scoreboard team they:
- **reveal the laptop map** wherever they fly (remembered until you log off)
- **mark targets** inside the cyan sensor ring: hostile mobs red, animals grey, enemy players magenta, friendly
  players blue, enemy weapon systems orange. Anything under a roof, underground or beneath trees stays hidden
- **spot for artillery**: rounds landing inside the ring are five times more accurate

Sensor radius: Mavic 32, Orlan and Reaper 48, Bayraktar 80, Global Hawk 128.

### Remote camera
**CAM** puts you inside the aircraft's camera at any distance: mouse to look around, scroll to zoom, the exit key
(X) or sneak to leave. Minecraft has to move you there to show you that part of the world, so while you watch,
a motionless copy of you (same name, skin and armour) stands where you were. If anything hits it, you are
pulled straight back into your body and take the damage. You are also returned if the aircraft is lost or you
log out. Your render distance needs to be at least 8 chunks to see the ground from cruise height.

### Air defence: nothing is hitscan
Every shot is a real object with a flight time.

- **Guns** fire individual shells (about 110-130 blocks a second, with a little drop). The fire-control computer
  works out where the target will be when the shells arrive and points the barrels there; the shells go where the
  barrels actually point, and only do damage if their path passes through the target. Drones and missiles have
  hit points, so it usually takes several hits.
- **Missiles** steer towards the predicted intercept point with a limited turn rate and carry a proximity fuse:
  they only go off if they truly pass within a few blocks. There is no hit-chance roll anywhere.
- **Manning the ZU-23-2**: right-click it with an empty hand to sit down (sneak + right-click opens its ammo
  box instead). The guns follow where you look, hold attack to fire, sneak to get off. There is no lead marker:
  judging the lead is up to you. The shells also hurt mobs and players.

Ammo: 30 mm belts go in the Pantsir's second slot, 35 mm belts in the Gepard, 23 mm boxes in the ZU-23-2.

### Jamming
Jammers affect anything that depends on a radio or satellite signal and belongs to someone who is not you or on
your scoreboard team. The effect fades in from the edge of the bubble and is total at 40% of its radius.

| Thing | Effect inside a hostile bubble |
| --- | --- |
| Radio FPV drone, Mavic | Video static, sluggish weak controls, then the link is lost |
| Shahed, Orlan, GMLRS (satellite guided) | Aim point drifts for as long as they are inside |
| Reaper, Bayraktar, Global Hawk, Orlan | Sensors go blind, camera feed fills with static, they turn for home after 5 seconds |
| Field Laptop | Cannot command launchers or aircraft |
| Fiber-optic FPV, Tomahawk, Iskander, shells | Unaffected |

| Jammer | Radius | Power |
| --- | --- | --- |
| **Jammer Backpack** | 24 | 10 minute battery. Right-click to switch on or off. Works from the Curios back slot or the inventory |
| **Anti-Drone Gun** | 80, narrow cone | Hold use while aiming at a drone. 2 minute battery |
| **Jammer Dome** | 14 | Right-click one of your vehicles with it to fit it. Needs no power |
| **Trench Jammer** | 32 | Fuel or Drone Batteries |
| **EW Jammer** | 64 | Fuel or Drone Batteries |
| **Krasukha-4** | 200 | Fuel, about 10 mB a second: a full tank lasts 13 minutes |

Block jammers have a power button in their screen. Recharge the backpack and the gun by crafting them together
with a Drone Battery. A running jammer is a loud radio source: any enemy recon drone close enough marks it on
their laptop map (yellow), even while that drone itself is being blinded.

### Hand-flown drones
1. Place the drone on the ground.
2. Right-click it with the **FPV Goggles & Controller** to pair.
3. Right-click the air with the controller to take off (you must be within 48 blocks of the drone).

While you fly you are inside the drone, and a motionless copy of you stays where you stood, exactly as with the
remote camera. If the copy is hit you are thrown out of the drone and take the damage, so fly from cover.

| Control | Action |
| --- | --- |
| Mouse | Look / steer |
| W, S | Forward / back (FPV: along where you look. Mavic: level) |
| A, D | Strafe |
| Jump / Sneak | Up / down |
| Attack (left click) | FPV: detonate now. Mavic: drop the grenade |
| Scroll wheel | Zoom |
| X (rebindable) | Leave the drone |
| F5 | Chase view |

- The **FPV drone** arms itself 1.5 seconds after take-off and explodes when it hits something at speed.
  Radio range is 500 blocks from where you stand; battery 150 s.
- The **Fiber-Optic FPV Drone** needs a **Fiber Spool Backpack** (Curios back slot if you have Curios, otherwise
  anywhere in your inventory). The pack holds up to 1000 blocks of cable; click **Fiber Spools** onto it in the
  inventory to add 250 each. The cable is drawn in the world, wraps round corners as you fly behind things, and
  the drone stops dead when the spool runs out. Cable you paid out is used up. The cable breaks if an explosion
  goes off next to it or it gets wrapped round too many corners.
- The **Mavic** is reusable: right-click a parked one with a **Drone Grenade** to arm it, sneak + right-click to
  pick it up (it keeps its charge), and craft it together with a Drone Battery to recharge. If you let go of it in
  the air it settles to the ground by itself. Battery 360 s.

### Hand-laid artillery: M109 and 120 mm mortar
These two are aimed and fired by a gunner, Arma Reforger style. Nothing guides the shell: it leaves the barrel
at the charge's muzzle velocity and falls under gravity.

- **Crew**: the **120 mm Mortar** (static, new) has one seat. The **M109** has a driver's seat and a gunner's
  seat; the gunner controls the turret.
- **Gun sight**: while seated you see the barrel's compass **bearing** (0 = north, 90 = east) and **elevation**,
  and a **range table** giving the elevation each charge needs for a distance on level ground.
- **Charges**: switch weapon to pick Charge 1, 2 or 3. More charge means more range and a flatter, faster shot.
  M109 reaches about 180 / 350 / 600 blocks, the mortar about 80 / 180 / 400.
- The mortar only fires high (45 to 85 degrees). The M109 uses a flat trajectory where it can (-3 to 70 degrees).
- **Shells** come from the vehicle's inventory: mortar bombs for the mortar; HE, cluster, smoke or incendiary
  155 mm shells for the M109 (switch ammo type the SBW way).
- **Field Laptop**: link the gun, pick a target, press **SOLUTION**. The laptop cannot fire these guns; it gives
  the bearing, the range, and for every charge that can reach, the elevation and time of flight, corrected for
  the height difference between gun and target. Dial it in and fire. A recon drone over the target lets you see
  where the rounds land and correct.

### Laptop-fired launchers: HIMARS, Tomahawk, Iskander
The **HIMARS** and **Iskander-M launcher** (wheeled) can be driven; the **Tomahawk launcher** is static.

- Put ammunition in the vehicle's SBW inventory (sneak + right-click).
- Right-click the vehicle with a **Field Laptop** to link it, then use Fire Control. Links follow the vehicle.
- On a fire order the launcher lays itself first (HIMARS pod swings onto the target, Tomahawk container and
  Iskander missile raise), then fires.
- **HIMARS pod**: it fires from a pod of 6 GMLRS rockets or 1 ATACMS. When the pod is empty it takes 30 seconds
  to load the next one from the inventory; the laptop shows the countdown. The first pod also has to be loaded.
- Energy per round: HIMARS 25k, Tomahawk 125k, Iskander 250k FE.

### Jammers (SBW vehicles)
- **EW Jammer**, **Trench Jammer** (static) and **Krasukha-4** (drivable): sneak + right-click with an empty hand
  switches them on or off; whoever does so becomes the owner, and the owner's and their team's drones are left
  alone. They draw 500 FE a second (Krasukha 2500).
- **Jammer Dome**: right-click **any Superb Warfare vehicle** with it to give it a small jamming bubble that
  draws a trickle of that vehicle's energy.

### Drone and aircraft launch sites (placed emplacements)
The **UAV Ground Station**, **Orlan Catapult** and **Shahed Launch Rail** are placed from a single item and
occupy their full footprint; breaking any part removes the whole thing and gives the item back.

- Right-click to open the inventory, or right-click holding the aircraft, drone or missiles to load by hand.
  What is loaded is shown: the aircraft parked at the start of the runway, the Orlan or Shahed on its rail.
- Right-click with a **Field Laptop** to link, then launch from Fire Control.
- The Ground Station needs energy (Reaper 250k, Bayraktar 375k, Global Hawk 1M FE): put an SBW Charging Station
  next to any block of it or connect an FE cable. The catapult and rail are worked by hand and need none.

### Ammunition
Everything a vehicle fires has to be crafted and loaded. A slot holds one kind at a time; empty it to switch.

| Weapon | Ammunition |
| --- | --- |
| 120 mm Mortar | **Mortar bomb** |
| M109 | **HE shell** (crater), **Cluster shell** (bursts overhead into 12 bomblets), **Smoke shell** (a minute-long screen that hides whatever is under it from recon drones), **Incendiary shell** (sets the area alight) |
| HIMARS | 6 **GMLRS** rockets (range 1200), or 1 **ATACMS** (range 3000, big warhead, cannot be jammed, worth a Patriot) |
| Gepard, Pantsir cannons, ZU-23-2 | **HE** belts (each shell bursts and hurts things within 2.5 blocks) or **AP** belts (no burst, 80% more damage on a direct hit) |
| Pantsir / Patriot | 57E6 / PAC-2 interceptor missiles (in the vehicle's SBW inventory) |
| Tomahawk, Iskander, Shahed launchers | Their missile or drone |
| Ground Station | The aircraft, plus Hellfires for the Reaper |

### Teams
Every weapon remembers who placed or launched it. Air defence and jammers ignore things owned by
you and by players on your scoreboard team (`/team`), and engage everything else.

### Crafting
Everything is craftable in survival. Intermediate parts: Circuit Board, Electric Motor, Drone Battery,
Propeller, Rocket Engine, Warhead, Guidance Unit. A recipe viewer such as JEI or EMI shows all recipes.

### Operator commands
- `/steelrain launch <weapon> <from> <target>` fires a munition without a launcher

It needs permission level 2. Vehicles can be spawned with `/summon steelrain:<name>`.

## Config
`config/steelrain-common.toml`

| Option | Default | Meaning |
| --- | --- | --- |
| `blockDamage` | true | Set to false and explosions hurt entities but never break blocks |
| `debris` | true | Craters throw out falling blocks |
| `explosionScale` | 1.0 | Crater and blast size |
| `damageScale` | 1.0 | Damage to entities |
| `rangeMultiplier` | 1.0 | Maximum range of all launchers |
| `chunkLoading` | true | Flying weapons load chunks along their path |
| `laptopLinkRange` | 128 | Laptop to launcher distance |
| `radioRange` / `fpvBatterySeconds` | 500 / 150 | Radio control range, FPV flight time |
| `fiberMaxLength` | 1000 | Most cable a spool backpack holds |
| `mavicBatterySeconds` | 360 | Mavic flight time |
| `airDefenceRange` / `airDefenceCeiling` | 128 / 100 | Pantsir missile reach |
| `patriotRange` | 400 | Patriot reach |
| `trenchJammerRange` / `strategicJammerRange` / `backpackJammerRange` / `domeJammerRange` | 32 / 200 / 24 / 14 | Jammer bubbles |
| `reconMarksPlayers` | true | Turn off so recon drones never show enemy players |
| `spottedSpreadMultiplier` | 0.2 | Accuracy bonus under a friendly recon drone |
| `jammerRange` | 64 | EW Jammer bubble |
| `requireEnergy` | true | Turn energy use by emplacements off entirely |

Every impact is written to the server log with its owner and coordinates.

## Building
Requires Java 21.

```
./gradlew build          # jar ends up in build/libs/
./gradlew runClient      # start a development client
./gradlew runServer      # start a development server
python3 tools/gen_assets.py   # regenerate textures, models, recipes and language file
```

## Known limits
- Vehicle models are simple generated shapes in SBW's Blockbench format, each with one plain hit box. A model
  rework is planned.
- A loaded Iskander is not drawn on its launcher.
- The Pantsir's missiles only fire in automatic mode.
- A fitted Jammer Dome is not drawn.
- There are no custom sounds yet.
- Low-flying drones (Orlan, Shahed) can fly into steep mountains.
