# Steel Rain

A Minecraft mod about drones, missiles and artillery for **NeoForge 1.21.1**.

Fly kamikaze FPV drones through their own camera, plan cruise and ballistic missile strikes on a
live map, shell targets with rocket and tube artillery, and defend your base with air defence and
electronic warfare.

The idea was inspired by the *Iron Drone* mod. All code, models and textures here are original.

## What is in it

| System | Type | How it is used |
| --- | --- | --- |
| **FPV Kamikaze Drone** | Piloted drone | Flown by hand through its camera, explodes on impact |
| **Shahed-136** | One-way attack drone | Launch Rail + laptop, range 4000 |
| **MQ-9 Reaper** | Reusable strike drone | Ground Station + laptop. Fires up to 4 Hellfires, flies home and lands |
| **Tomahawk** | Cruise missile | Launcher + laptop, hugs the terrain, range 6000 |
| **Iskander-M** | Ballistic missile | Launcher + laptop, biggest warhead, range 8000 |
| **M142 HIMARS** | Rocket artillery | 6 GMLRS rockets, salvo fire, range 1200 |
| **M109 Howitzer** | Tube artillery | 155 mm shells, cheap, less accurate, range 600 |
| **Pantsir-S1** | Air defence | Shoots down hostile drones and missiles on its own |
| **EW Jammer** | Electronic warfare | Knocks hostile drones off course while it has fuel |

Ranges are in blocks and can be scaled in the config.

## How to play

### Launchers and the Field Laptop
1. Place a launcher and right-click it to open its inventory.
2. Load **ammo** and **fuel** (see below).
3. Right-click the launcher while holding a **Field Laptop** to link it (up to 8 launchers per laptop).
4. Right-click the air with the laptop to open **Fire Control**:
   - left-click the map to pick a target, or type X / Z coordinates
   - right-drag pans the map, the scroll wheel zooms
   - the orange ring is maximum range, the red ring is minimum range
   - choose the number of rounds for HIMARS / M109 salvos, then press **LAUNCH**
5. You must stay within 128 blocks of the launcher to command it.

Missiles keep the chunks along their path loaded, so strikes work thousands of blocks away.

### FPV drone
1. Place the **FPV Kamikaze Drone** on the ground.
2. Right-click it with the **FPV Goggles & Controller** to pair.
3. Right-click the air with the controller to take off.

| Control | Action |
| --- | --- |
| Mouse | Look / steer |
| W, S | Fly forward / back along where you look |
| A, D | Strafe |
| Jump / Sneak | Up / down |
| Attack (left click) | Detonate now |
| X (rebindable) | Leave the drone camera |
| F5 | Chase view |

The drone arms itself 1.5 seconds after take-off and explodes when it hits something at speed.
It has a limited battery (150 s) and control range (200 blocks); if either runs out it falls.
Sneak + right-click a parked drone to pick it back up.

### Fuel
Launchers hold 8000 mB of fuel and burn some for every launch; the jammer burns it continuously.

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

### Operator command
`/steelrain launch <weapon> <from> <target>` fires a munition without a launcher (permission level 2).

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
| `airDefenceRange` / `airDefenceHitChance` | 96 / 0.8 | Pantsir tuning |
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
- Models are simple blocky shapes and launchers are single blocks with fixed barrels.
- There are no custom sounds yet (vanilla sounds are used).
- Create: Diesel Generators support is tag based and has not been tested with that mod installed.
