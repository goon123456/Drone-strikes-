# Steel Rain

A Minecraft mod about drones, missiles and artillery for **NeoForge 1.21.1**.

Fly kamikaze FPV drones through their own camera, plan cruise and ballistic missile strikes on a
live map, shell targets with rocket and tube artillery, and defend your base with air defence and
electronic warfare.

The idea was inspired by the *Iron Drone* mod. All code, models and textures here are original.

## What is in it

Everything is built at real-world scale (1 block = 1 metre). Vehicles are placed from a single item and
occupy their full footprint; turrets, pods and barrels physically swing onto the target before firing.

| System | Size (W x H x L) | Type | How it is used |
| --- | --- | --- | --- |
| **FPV Kamikaze Drone** | 0.5 m | Piloted drone | Flown by hand through its camera, explodes on impact |
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
| **Pantsir-S1** | 3 x 4 x 10 | Short-range air defence | Shoots down hostile drones and missiles up to 100 blocks above it |
| **MIM-104 Patriot** | 3 x 4 x 9 | Long-range air defence | 400 block reach, any altitude; only fires at aircraft, cruise and ballistic missiles |
| **EW Jammer** | 3 x 3 x 3 + mast | Electronic warfare | Knocks hostile drones off course while it has fuel |

Ranges are in blocks and can be scaled in the config.

## How to play

### Placing vehicles
Each vehicle is one item. Use it on the ground and the whole thing appears, extending away from you, with you
standing at its rear. It needs its full footprint clear (the tooltip gives the size). Breaking any part removes
the whole vehicle and gives the item back; one destroyed by an explosion is gone for good.

### Launchers and the Field Laptop
1. Right-click any part of a launcher to open its inventory and load **ammo** and **fuel**.
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

### FPV drone and Mavic
1. Place the drone on the ground.
2. Right-click it with the **FPV Goggles & Controller** to pair.
3. Right-click the air with the controller to take off.

| Control | Action |
| --- | --- |
| Mouse | Look / steer |
| W, S | Forward / back (FPV: along where you look. Mavic: level) |
| A, D | Strafe |
| Jump / Sneak | Up / down |
| Attack (left click) | FPV: detonate now. Mavic: drop the grenade |
| Scroll wheel | Zoom |
| X (rebindable) | Leave the drone camera |
| F5 | Chase view |

The **FPV drone** arms itself 1.5 seconds after take-off and explodes when it hits something at speed.
The **Mavic** is reusable: right-click a parked one with a **Drone Grenade** to arm it, sneak + right-click to
pick it up (it keeps its charge), and craft it together with a Drone Battery to recharge. If you let go of it in
the air it settles to the ground by itself.
Both have a limited battery (FPV 150 s, Mavic 360 s) and a 200 block control range.

### Fuel
Launchers hold 8000 mB of fuel and burn some for every launch (aircraft take the most: 1000 for a Reaper,
4000 for a Global Hawk); the jammer burns it continuously.

- **Fuel Canister** (crafted from blaze powder and coal): put it in the fuel slot, 1000 mB each.
- **Diesel and friends from other mods**: buckets in the fuel slot, or pump it in with pipes.
  Any fluid in the `steelrain:launcher_fuel` tag works. Out of the box that covers diesel, gasoline and
  biodiesel from **Create: Diesel Generators**, plus the equivalents from TFMG, PneumaticCraft and
  Immersive Engineering. Add more with a datapack.

### Teams
Every weapon remembers who placed or launched it. Air defence and jammers ignore things owned by
you and by players on your scoreboard team (`/team`), and engage everything else.

### Crafting
Everything is craftable in survival. Intermediate parts: Circuit Board, Electric Motor, Drone Battery,
Propeller, Rocket Engine, Warhead, Guidance Unit. A recipe viewer such as JEI or EMI shows all recipes.

### Operator commands
- `/steelrain launch <weapon> <from> <target>` fires a munition without a launcher
- `/steelrain place <system> <pos> <north|south|east|west>` puts down a complete vehicle

Both need permission level 2.

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
| `fpvRange` / `fpvBatterySeconds` | 200 / 150 | FPV drone limits |
| `mavicBatterySeconds` | 360 | Mavic flight time |
| `airDefenceRange` / `airDefenceCeiling` / `airDefenceHitChance` | 128 / 100 / 0.8 | Pantsir tuning |
| `patriotRange` / `patriotHitChance` | 400 / 0.9 | Patriot tuning |
| `reconMarksPlayers` | true | Turn off so recon drones never show enemy players |
| `spottedSpreadMultiplier` | 0.2 | Accuracy bonus under a friendly recon drone |
| `jammerRange` | 48 | Jammer radius |
| `requireFuel` | true | Turn fuel use off entirely |

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
- Models are simple blocky shapes built in code; there are no wheels turning or recoil animations.
- Vehicles are static emplacements: they cannot be driven.
- A vehicle's collision is one solid box the size of its footprint.
- There are no custom sounds yet (vanilla sounds are used).
- Create: Diesel Generators support is tag based and has not been tested with that mod installed.
- Low-flying drones (Orlan, Shahed) can fly into steep mountains.
