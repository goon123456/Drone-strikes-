# Steel Rain

A standalone mod for **NeoForge 1.21.1**: drones, cruise and ballistic missiles, rocket and tube artillery, air
defence and electronic warfare, on its own drivable vehicles.

Fly kamikaze FPV drones through their own camera, plan missile strikes on a live map, shell targets with rocket
and tube artillery, and defend your base with air defence and jammers.

**Requires:** nothing but NeoForge. **Works with** [Superb Warfare](https://modrinth.com/mod/superb-warfare)
0.8.9+ and Curios if they are installed (see below).
**Licence:** GPL-3.0-only.

The idea was inspired by the *Iron Drone* mod. All code, models and textures here are original.

## Vehicles
All twelve vehicles are Steel Rain's own: HIMARS, M109, 120 mm mortar, Tomahawk and Iskander launchers, ZU-23-2,
Gepard, Pantsir, Patriot, EW Jammer, Trench Jammer and Krasukha-4. The drone and aircraft launch sites (UAV
Ground Station, Orlan Catapult, Shahed Launch Rail) are placed full-size emplacements.

- **Getting one**: craft it (or take it from the creative tab) and right-click the ground to put it down,
  facing the way you look. The owner sneak-hits it to pack it back into the item; the item keeps its damage,
  fuel, charge, loaded rockets and any dome, and the load in its storage is dropped on the ground.
- **Seats**: right-click to get in; you take the first free seat (driver first). Press **Z** to move to the next
  free seat (driver, gunner, passenger).
- **Driving**: from the driver's seat. W/S throttle and reverse, A/D steer,
  Space brakes, Shift gets out. Real steering geometry, wheels that turn and steer, the hull leaning on its springs,
  climbs one-block steps and slopes. The camera pulls back in third person (F5). Deep water drowns the engine.
- **Fuel**: the engine runs on diesel. Fill a **Jerrycan** (craft) with coal or charcoal to get a
  **Jerrycan of Diesel** and right-click the vehicle with it: 20 L a can, 100 L tank, about 60 blocks a litre.
- **Electricity**: the electronics and launcher run on FE from the vehicle's battery (500k FE). The engine's
  alternator charges it while you drive, and any FE cable or charger can feed it.
- **Status**: getting in as the driver shows fuel and battery. Sneak + right-click opens its storage.

### Air defence vehicles (Steel Rain's own)
- **Getting one**: craft it and place it from the item; the owner sneak-hits it to pack it up again (it keeps its
  state). Sneak + right-click opens its storage for belts and missiles.
- **Gepard** (tracked) and **Pantsir** (8x8) are driven from seat 0; seat 1 is the gunner in the turret. The
  **ZU-23-2** has one exposed gunner. The **Patriot** has no crew position. **Z** changes seat.
- **Gunner**: look where to shoot and **hold left click** to fire; the guns follow your eye at their own pace (a
  white cross shows where they point). The radar (Gepard, Pantsir) tracks the threat nearest your sight: a box
  marks it and a green diamond shows where to aim so the shells meet it. **Middle click** locks on: the turret then
  lays itself on the lead and your sight follows the guns; middle click again to let go. **Right click** fires a
  Pantsir missile at the tracked target. **B** switches between HE and AP belts. The ZU-23-2 has no radar: you judge
  the lead yourself.
- **Automatic mode** (**I** while aboard; right-click the Patriot): with nobody at the guns it searches for hostile
  aircraft, drones and missiles in range, aims at where they will be and fires its guns or missiles. The owner
  switches it.
- **Targets**: Steel Rain drones and missiles (and, with Superb Warfare installed, its crewed aircraft and
  helicopters, recon drones and guided weapons), unless they belong to you or your scoreboard team. The Patriot only takes on missiles and aircraft.
- **Ammo**: one belt is 50 rounds (40 for the Gepard's 35 mm); the loader puts the next belt on in 2 seconds.
  HE rounds have a proximity fuse and a small blast; AP rounds hit 1.8x harder but must strike. Every second round
  is a tracer. 57E6 missiles for the Pantsir, PAC-2 for the Patriot.
- **Energy**: the radar draws 250 FE a second while searching (500 while tracking), a Pantsir missile 5000 FE, a
  Patriot missile 25000 FE. Diesel engines charge the battery while driving; the Patriot has a diesel power unit.
- Measured in automatic mode against a Shahed crossing overhead: Gepard and Pantsir guns down nearly all of them,
  the ZU-23-2 about one in ten.

### Launchers and the Field Laptop
1. Load ammunition into the vehicle's storage (sneak + right-click) and make sure it is charged.
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
- **Manning the guns**: see "Air defence vehicles" above. The shells also hurt mobs and players.

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

- **Crew**: the **120 mm Mortar** (Steel Rain's own, static) has one crewman kneeling beside it; craft it, place
  it from the item and sneak-hit it to carry it away again. No fuel or battery. The **M109** (Steel Rain's own vehicle, tracked)
  has a driver, a gunner up in the turret and a passenger; press **Z** to change seat.
- **Gunner (M109 and mortar)**: look where the gun should point; the turret (2.5 degrees a tick) and barrel (1.5) follow at
  their own pace, and a marker on the sight shows where the barrel is pointing until it catches up (it turns green
  when laid). **Left click** fires, the **scroll wheel** picks the charge, **B** steps through the shell types.
  The crew loads the next round of the chosen type from the storage (sneak + right-click) while anyone is aboard,
  6 seconds for the M109 and 3 for the mortar;
  the sight shows what is in the breech.
- **Gun sight**: while seated you see the barrel's compass **bearing** (0 = north, 90 = east) and **elevation**,
  and a **range table** giving the elevation each charge needs for a distance on level ground.
- **Charges**: Charge 1, 2 or 3 (scroll wheel). More charge means more range and a flatter, faster shot.
  M109 reaches about 180 / 350 / 600 blocks, the mortar about 80 / 180 / 400.
- The mortar only fires high (45 to 85 degrees). The M109 uses a flat trajectory where it can (-3 to 70 degrees).
- **Shells** come from the vehicle's inventory: mortar bombs for the mortar; HE,
  cluster, smoke or incendiary 155 mm shells for the M109 (B).
- **Field Laptop**: link the gun, pick a target, press **SOLUTION**. The laptop cannot fire these guns; it gives
  the bearing, the range, and for every charge that can reach, the elevation and time of flight, corrected for
  the height difference between gun and target. Dial it in and fire. A recon drone over the target lets you see
  where the rounds land and correct.

### Laptop-fired launchers: HIMARS, Tomahawk, Iskander
The **HIMARS** and **Iskander-M launcher** (wheeled) can be driven; the **Tomahawk launcher** is static.

- Put ammunition in the vehicle's inventory (sneak + right-click).
- Right-click the vehicle with a **Field Laptop** to link it, then use Fire Control. Links follow the vehicle.
- On a fire order the launcher lays itself first (HIMARS pod swings onto the target, Tomahawk container and
  Iskander missile raise), then fires.
- **HIMARS pod**: it fires from a pod of 6 GMLRS rockets or 1 ATACMS. When the pod is empty it takes 30 seconds
  to load the next one from the inventory; the laptop shows the countdown. The first pod also has to be loaded.
- Energy per round: HIMARS 25k, Tomahawk 125k, Iskander 250k FE. The Tomahawk trailer and the Iskander carry a
  diesel power unit that charges the battery by itself while there is fuel in the tank (refuel with a Jerrycan of
  Diesel); any FE cable or charger works too.
- The Tomahawk and Iskander refuse to fire while moving: stop first. The Iskander opens its roof doors and stands
  the missile up; once fired it is gone from the cradle until another is loaded.

### Jammers
- **EW Jammer** and **Trench Jammer** (static) and **Krasukha-4** (drivable 8x8): place from the item; sneak +
  right-click with an empty hand switches them on or off (owner only). The owner's and their team's drones are
  left alone. They draw 500 FE a second (Krasukha 2500).
- The EW Jammer and the Krasukha have a diesel power unit that charges the battery while there is fuel (Jerrycan
  of Diesel). The Trench Jammer runs on its battery: right-click it with a **Drone Battery** for 40k FE. Any FE
  cable or charger works on all three. Right-click a static one to see its fuel and battery.
- **Jammer Dome**: right-click **any Steel Rain vehicle** (or Superb Warfare vehicle, if installed) with it to
  give it a small jamming bubble that draws a trickle of that vehicle's energy.

### Drone and aircraft launch sites (placed emplacements)
The **UAV Ground Station**, **Orlan Catapult** and **Shahed Launch Rail** are placed from a single item and
occupy their full footprint; breaking any part removes the whole thing and gives the item back.

- Right-click to open the inventory, or right-click holding the aircraft, drone or missiles to load by hand.
  What is loaded is shown: the aircraft parked at the start of the runway, the Orlan or Shahed on its rail.
- Right-click with a **Field Laptop** to link, then launch from Fire Control.
- The Ground Station needs energy (Reaper 250k, Bayraktar 375k, Global Hawk 1M FE): connect an FE cable or
  charger to any block of it. The catapult and rail are worked by hand and need none.

### Ammunition
Everything a vehicle fires has to be crafted and loaded. A slot holds one kind at a time; empty it to switch.

| Weapon | Ammunition |
| --- | --- |
| 120 mm Mortar | **Mortar bomb** |
| M109 | **HE shell** (crater), **Cluster shell** (bursts overhead into 12 bomblets), **Smoke shell** (a minute-long screen that hides whatever is under it from recon drones), **Incendiary shell** (sets the area alight) |
| HIMARS | 6 **GMLRS** rockets (range 1200), or 1 **ATACMS** (range 3000, big warhead, cannot be jammed, worth a Patriot) |
| Gepard, Pantsir cannons, ZU-23-2 | **HE** belts (each shell bursts and hurts things within 2.5 blocks) or **AP** belts (no burst, 80% more damage on a direct hit) |
| Pantsir / Patriot | 57E6 / PAC-2 interceptor missiles (in the vehicle's storage) |
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
- `/steelrain fire <x> <z> [count]` orders the nearest launcher (within 64 blocks) to fire, like the Field Laptop

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

## Sounds
- Explosions, gunfire, missile launches, reloads, engines and the missile warning will be Steel Rain's own
  recordings (the list is in `docs/SOUNDS_NEEDED.md`). Until they are in, Superb Warfare's recordings are used
  when it is installed, and the nearest vanilla sounds otherwise. Blasts and launches switch to a duller
  distant version the further away you are, and big warheads can be heard from about a kilometre.
- FPV and Mavic rotors blend between low, medium and high-speed loops as the drone speeds up, with a spin-up
  when you take control. The Shahed, Reaper, Bayraktar, Orlan and Global Hawk each have their own engine. The
  jammer hums, a jammed video link hisses louder the worse it gets, and the fibre makes one of three snaps.

## Superb Warfare compatibility (optional)
Steel Rain does not need Superb Warfare. If it is installed as well: Steel Rain air defence engages its crewed
aircraft and helicopters, recon drones and guided weapons; Jammer Domes fit on its vehicles; its vehicles show
on recon drones; and its recordings stand in for sounds Steel Rain does not have yet. All of this lives in
`com.steelrain.compat`, which is only loaded when Superb Warfare is present. Run the development game with it
using `./gradlew runClient -PwithSbw`.

## Known limits
- Aircraft and drones (Reaper, TB2, Global Hawk, Orlan-10, Shahed-136, both FPV drones and the Mavic) are
  textured meshes built with the same kit as the ground vehicles, in `tools/gen_aircraft.py`: smooth fuselage
  skins through a row of cross-sections, aerofoil-section wings, tails and propeller blades, turned sensor
  balls, real wheels on folding gear legs, weapons on pylons, markings. They are drawn by Steel Rain's own
  renderer from `models/aircraft/*.json`. Propellers and rotors spin, landing gear retracts and aircraft bank.
- Missiles, rockets, shells and grenades in flight are textured meshes too, in `tools/gen_munitions.py`: bodies of
  revolution with true ogive noses, aerofoil fins, nozzles, paint bands and stencilled designations.
- The three launch sites are textured meshes as well, in `tools/gen_sites.py`: the Ground Station is a 6 m
  equipment shelter on jacks with a satellite dish, a data-link mast, air conditioning and a cable drum; the
  Orlan Catapult is an inclined folding rail on a base and A-frame with a carriage and launch cylinder; the
  Shahed Launch Rail is a skid frame carrying twin inclined rails with a blast plate and firing box.
- A fitted Jammer Dome is drawn on the vehicle's roof (the top middle of its hit box): a mushroom radome on a
  mast over a ring of stub aerials, with a control box and lead. The server tells each player which vehicles
  carry one.
- The two backpacks worn in the Curios back slot are textured meshes with a full harness (shoulder straps over
  the shoulders and down the chest, chest strap, waist belt): the fibre pack carries a big cable reel with a
  guide arm, the jammer pack an electronics case with a control panel, battery and five aerials.
  Tracer rounds are the only thing still drawn as a simple flat-coloured shape, which suits a streak of light.
- Ground vehicles are true meshes, not boxes: turned wheels with dished
  rims, round barrels, masts and dishes, and bevelled plate edges. Shapes are in `tools/gen_vehicles.py`; the kit
  of parts, the mesh builder and the texture painter are in `tools/vehicle_kit.py`.
- Textures are 64 pixels per block (up to 2048 px per vehicle). Every surface has its own patch, painted from its
  real position: soft-edged camouflage that flows across parts, faded paint, plate seams and bolt rows, rain
  streaks, grime, chips, road dust, tyre tread and track links.
  `python3 tools/gen_vehicles.py <dir> [vehicle]` renders textured previews without the game.
- Moving parts: tracks are made of separate links that travel round the sprocket, idler and road wheels as
  the vehicle drives (the Gepard and M109), with the sprockets, idlers and return rollers turning too. Front
  axles steer. The Gepard's and Pantsir's search radars turn and the Krasukha's dishes sweep. Track paths are
  worked out by the generator from the wheel positions and stored with each vehicle's model.
- Hulls, turrets, cabs, trailers, shelters and cases are built from their real outlines (side profiles carried
  across the vehicle, plan outlines rising to a smaller roof) rather than from blocks, on all twelve vehicles:
  sloped glacis plates, faceted turret fronts, raked cab noses, gooseneck trailers, chamfered shelters.
- Every vehicle carries small fittings from a shared library in the kit: lifting eyes, hand rails, ladders,
  screw jacks, jerrycans, fire extinguishers, pioneer tools, sprung aerials, hatches, vision blocks, lamps in
  bezels, beacons, bedrolls, spare wheels, shackles, wheel chocks, hoses, cable runs and diagonal struts.
  Real artist-made models can still replace generated ones where a freely licensed one is available.
- Turret, gun and launcher angles are synced to every player, so everyone sees them turn and elevate.
- Anti-aircraft HE shells have proximity fuses: one that passes close to a hostile drone or missile may burst
  there and do light fragment damage (eight bursts down a Shahed); a direct hit does full damage. Against a
  Shahed crossing overhead in automatic mode the Gepard and the Pantsir's guns down nearly all, the ZU-23-2
  about one in ten.
- Shooting things down: a single heavy hit (10 damage or more: an interceptor missile, a direct cannon shell)
  blows a drone, aircraft or missile apart in the air without setting off its warhead. Being worn down by
  lighter hits such as proximity bursts leaves it in one piece: it tumbles to the ground trailing smoke and
  flame and goes off where it lands at about half strength (missiles and attack drones), or as a fuel fire
  (unarmed drones and aircraft). Defences stop shooting at a falling wreck; another heavy hit finishes it in
  the air. Interceptor missiles always just burst.
- Animation, driven by Steel Rain's vehicle renderer:
  the hull sits on its own bone above the running gear and squats when pulling away, dips under braking and
  rolls in turns; gun barrels slide back on firing and run out again; search radars turn and the Krasukha's
  dishes sweep; stabiliser jacks wind up to drive and back down to park, and wheel chocks are stowed; hatch lids
  open when the vehicle stands with a crew aboard; aerials whip as it moves; the Iskander's roof doors swing open
  as the missile rises; on wheeled vehicles each wheel rides up and down over uneven ground on its own.
- Animated model parts are named `move_...`, wheels `wheelL/R<n>` (`...Turn` steer), track links `trackL/R<n>`.
- The drone, engine, jammer, static and cable sounds are the files in `tools/sounds/`. Replace one there (mono
  `.ogg`, same name) and rerun `tools/gen_assets.py` to change it. They are synthesised, not field recordings.
- Low-flying drones (Orlan, Shahed) can fly into steep mountains.
