package com.steelrain.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
import com.steelrain.weapon.WeaponType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Fire control screen: linked launchers on the left, a live top-down map in the middle, launch controls on the right. */
public class LaptopScreen extends Screen {
    private static final int W = 320, H = 200, MAP = 128;
    private static final ResourceLocation MAP_TEXTURE = ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "laptop_map");
    private static final int[] SCALES = {1, 2, 4, 8, 16, 32, 64};
    private static final int UNKNOWN = 0xFF181410;

    private List<Payloads.Entry> entries;
    private int selected;
    private Component message = Component.empty();
    private int messageTicks;

    private double centreX, centreZ;
    private int scaleIndex = 1;
    private boolean hasTarget;
    private int targetX, targetZ;
    private int count = 1;

    private DynamicTexture mapTexture;
    private boolean mapDirty = true;
    private int age;
    private boolean panning;
    private boolean settingBoxes;

    private EditBox xBox, zBox;
    private Button launchButton, cameraButton, recallButton, minusButton, plusButton;
    private int left, top;

    public LaptopScreen(Payloads.LaptopData data) {
        super(Component.translatable("item.steelrain.field_laptop"));
        this.entries = data.entries();
    }

    public void update(Payloads.LaptopData data) {
        this.entries = data.entries();
        if (selected >= entries.size()) selected = Math.max(0, entries.size() - 1);
        data.message().ifPresent(m -> {
            message = m;
            messageTicks = 100;
        });
    }

    private int scale() { return SCALES[scaleIndex]; }

    private Payloads.Entry current() {
        return selected >= 0 && selected < entries.size() ? entries.get(selected) : null;
    }

    private static WeaponType typeOf(Payloads.Entry e) {
        return e.weapon() >= 0 && e.weapon() < WeaponType.values().length ? WeaponType.values()[e.weapon()] : null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (mapTexture == null) {
            mapTexture = new DynamicTexture(MAP, MAP, false);
            minecraft.getTextureManager().register(MAP_TEXTURE, mapTexture);
            if (minecraft.player != null) {
                centreX = minecraft.player.getX();
                centreZ = minecraft.player.getZ();
            }
        }
        int mx = left + 102, my = top + 18;
        xBox = new EditBox(font, mx + 10, my + MAP + 5, 50, 14, Component.literal("X"));
        zBox = new EditBox(font, mx + 78, my + MAP + 5, 50, 14, Component.literal("Z"));
        for (EditBox box : new EditBox[]{xBox, zBox}) {
            box.setMaxLength(9);
            box.setFilter(s -> s.matches("-?\\d*"));
            box.setResponder(s -> boxesChanged());
            addRenderableWidget(box);
        }
        if (hasTarget) setBoxes();
        addRenderableWidget(Button.builder(Component.literal("-"), b -> zoom(1)).bounds(mx, my + MAP + 24, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoom(-1)).bounds(mx + 18, my + MAP + 24, 16, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.steelrain.centre"), b -> {
            if (minecraft.player != null) {
                centreX = minecraft.player.getX();
                centreZ = minecraft.player.getZ();
                mapDirty = true;
            }
        }).bounds(mx + 82, my + MAP + 24, 46, 16).build());
        int rx = left + 236;
        minusButton = addRenderableWidget(Button.builder(Component.literal("-"), b -> count = Math.max(1, count - 1)).bounds(rx, top + 118, 16, 16).build());
        plusButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> count = Math.min(32, count + 1)).bounds(rx + 62, top + 118, 16, 16).build());
        launchButton = addRenderableWidget(Button.builder(Component.translatable("gui.steelrain.launch"), b -> launch())
                .bounds(rx, top + 138, 78, 20).build());
        cameraButton = addRenderableWidget(Button.builder(Component.translatable("gui.steelrain.camera"), b -> order(Payloads.AircraftOrder.CAMERA))
                .bounds(rx, top + 161, 38, 16).build());
        recallButton = addRenderableWidget(Button.builder(Component.translatable("gui.steelrain.recall"), b -> order(Payloads.AircraftOrder.RECALL))
                .bounds(rx + 40, top + 161, 38, 16).build());
    }

    private void zoom(int by) {
        scaleIndex = Mth.clamp(scaleIndex + by, 0, SCALES.length - 1);
        mapDirty = true;
    }

    private void boxesChanged() {
        if (settingBoxes) return;
        try {
            targetX = Integer.parseInt(xBox.getValue());
            targetZ = Integer.parseInt(zBox.getValue());
            hasTarget = true;
        } catch (NumberFormatException e) {
            hasTarget = false;
        }
    }

    private void setBoxes() {
        settingBoxes = true;
        xBox.setValue(Integer.toString(targetX));
        zBox.setValue(Integer.toString(targetZ));
        settingBoxes = false;
    }

    private void launch() {
        Payloads.Entry e = current();
        if (e == null || !hasTarget) return;
        PacketDistributor.sendToServer(new Payloads.Launch(e.pos(), targetX, targetZ, count));
    }

    private void order(int action) {
        Payloads.Entry e = current();
        if (e != null) PacketDistributor.sendToServer(new Payloads.AircraftOrder(e.pos(), action));
    }

    @Override
    public void tick() {
        age++;
        if (messageTicks > 0) messageTicks--;
        if (age % 20 == 0) PacketDistributor.sendToServer(new Payloads.LaptopRefresh());
        if (age % 40 == 0) mapDirty = true;
        Payloads.Entry e = current();
        WeaponType t = e == null ? null : typeOf(e);
        launchButton.active = e != null && hasTarget && e.reachable() && t != null;
        boolean airborne = e != null && e.aircraft() >= 0 && e.reachable();
        launchButton.setMessage(Component.translatable(airborne ? "gui.steelrain.retask" : "gui.steelrain.launch"));
        cameraButton.visible = recallButton.visible = t != null && t.launchesAircraft();
        minusButton.visible = plusButton.visible = t != null && !t.launchesAircraft();
        cameraButton.active = recallButton.active = airborne;
    }

    // ---- map ----

    private void rebuildMap() {
        mapDirty = false;
        ClientLevel level = minecraft.level;
        NativeImage img = mapTexture.getPixels();
        if (level == null || img == null) return;
        int s = scale();
        int x0 = Mth.floor(centreX) - MAP / 2 * s, z0 = Mth.floor(centreZ) - MAP / 2 * s;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int pz = 0; pz < MAP; pz++) {
            for (int px = 0; px < MAP; px++) {
                int x = x0 + px * s, z = z0 + pz * s;
                int colour = ReconClient.colour(x, z); // what recon drones have seen
                if (colour == 0) colour = UNKNOWN;
                if (level.hasChunk(x >> 4, z >> 4)) {
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    MapColor mc = state.getMapColor(level, pos);
                    if (mc != MapColor.NONE) {
                        int north = level.hasChunk(x >> 4, (z - s) >> 4)
                                ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - s) - 1 : y;
                        MapColor.Brightness b = y > north ? MapColor.Brightness.HIGH
                                : y < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                        colour = mc.calculateRGBColor(b);
                    }
                }
                img.setPixelRGBA(px, pz, colour);
            }
        }
        mapTexture.upload();
    }

    private int mapX() { return left + 102; }
    private int mapY() { return top + 18; }

    private boolean overMap(double mx, double my) {
        return mx >= mapX() && mx < mapX() + MAP && my >= mapY() && my < mapY() + MAP;
    }

    private void marker(GuiGraphics g, double wx, double wz, int colour, int size) {
        int px = (int) Math.floor((wx - centreX) / scale()) + MAP / 2;
        int pz = (int) Math.floor((wz - centreZ) / scale()) + MAP / 2;
        if (px < size || pz < size || px >= MAP - size || pz >= MAP - size) return;
        int x = mapX() + px, y = mapY() + pz;
        g.fill(x - size, y - size, x + size + 1, y + size + 1, 0xFF000000);
        g.fill(x - size + 1, y - size + 1, x + size, y + size, colour);
    }

    private void ring(GuiGraphics g, double wx, double wz, double radius, int colour) {
        double r = radius / scale();
        if (r < 3 || r > 400) return;
        int steps = (int) Mth.clamp(r * 3, 24, 360);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int px = (int) Math.floor((wx - centreX) / scale() + Math.cos(a) * r) + MAP / 2;
            int pz = (int) Math.floor((wz - centreZ) / scale() + Math.sin(a) * r) + MAP / 2;
            if (px < 0 || pz < 0 || px >= MAP || pz >= MAP) continue;
            g.fill(mapX() + px, mapY() + pz, mapX() + px + 1, mapY() + pz + 1, colour);
        }
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overMap(mx, my)) {
            if (button == 0) {
                targetX = Mth.floor(centreX + (mx - mapX() - MAP / 2.0) * scale());
                targetZ = Mth.floor(centreZ + (my - mapY() - MAP / 2.0) * scale());
                hasTarget = true;
                setBoxes();
            } else {
                panning = true;
            }
            return true;
        }
        int lx = left + 6, ly = top + 18;
        if (mx >= lx && mx < lx + 90 && my >= ly) {
            int row = (int) ((my - ly) / 22);
            if (row >= 0 && row < entries.size()) {
                selected = row;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        panning = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            centreX -= dx * scale();
            centreZ -= dy * scale();
            mapDirty = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (overMap(mx, my)) {
            zoom(sy > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ---- drawing ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF0B0E0A);
        g.fill(left, top, left + W, top + H, 0xFF1C2219);
        g.fill(left, top, left + W, top + 13, 0xFF2F3B28);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (mapDirty) rebuildMap();
        super.render(g, mouseX, mouseY, partialTick);
        int green = 0xFF8CE08C, dim = 0xFF7A8A70, white = 0xFFE8F0E0, red = 0xFFFF6060, amber = 0xFFE0B040;
        g.drawString(font, Component.translatable("gui.steelrain.laptop_title"), left + 5, top + 3, green, false);

        // Launcher list.
        int lx = left + 6, ly = top + 18;
        if (entries.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("gui.steelrain.no_links"), lx, ly, 90, dim);
        }
        for (int i = 0; i < entries.size() && i < 8; i++) {
            Payloads.Entry e = entries.get(i);
            WeaponType type = typeOf(e);
            int y = ly + i * 22;
            g.fill(lx, y, lx + 90, y + 20, i == selected ? 0xFF3C4C34 : 0xFF262E22);
            Component name = type == null ? Component.translatable("gui.steelrain.offline")
                    : Component.translatable(type.translationKey());
            g.drawString(font, font.plainSubstrByWidth(name.getString(), 86), lx + 2, y + 2,
                    e.reachable() ? white : red, false);
            String info = type == null ? e.pos().toShortString()
                    : e.ammo() + (type.secondaryAmmo() != null ? "+" + e.ammo2() : "") + " | " + e.fuel() + " mB";
            g.drawString(font, font.plainSubstrByWidth(info, 86), lx + 2, y + 11, dim, false);
        }

        // Map.
        int mx = mapX(), my = mapY();
        g.fill(mx - 1, my - 1, mx + MAP + 1, my + MAP + 1, 0xFF50603F);
        g.blit(MAP_TEXTURE, mx, my, 0, 0f, 0f, MAP, MAP, MAP, MAP);
        Payloads.Entry cur = current();
        WeaponType curType = cur == null ? null : typeOf(cur);
        if (cur != null && curType != null) {
            ring(g, cur.pos().getX() + 0.5, cur.pos().getZ() + 0.5, cur.maxRange(), 0xC0E0B040);
            ring(g, cur.pos().getX() + 0.5, cur.pos().getZ() + 0.5, cur.minRange(), 0xC0FF6060);
        }
        for (int i = 0; i < entries.size(); i++) {
            Payloads.Entry e = entries.get(i);
            marker(g, e.pos().getX() + 0.5, e.pos().getZ() + 0.5, i == selected ? 0xFFFFE040 : 0xFF40C040, 2);
        }
        // Recon: the drone, the edge of what it sees, and its contacts.
        for (ReconClient.Report report : ReconClient.fresh()) {
            Payloads.Markers mk = report.markers();
            ring(g, mk.x() + 0.5, mk.z() + 0.5, mk.radius(), 0xA040E0FF);
            int[] d = mk.data();
            for (int i = 0; i + 2 < d.length; i += 3) {
                int colour = switch (d[i + 2]) {
                    case 0 -> 0xFFFF4040;   // hostile mob
                    case 1 -> 0xFFB0B0B0;   // animal / neutral
                    case 2 -> 0xFFFF40FF;   // enemy player
                    case 3 -> 0xFF4080FF;   // friendly player
                    default -> 0xFFFF9020;  // enemy weapon system
                };
                marker(g, d[i] + 0.5, d[i + 1] + 0.5, colour, d[i + 2] == 4 ? 2 : 1);
            }
            marker(g, mk.x() + 0.5, mk.z() + 0.5, 0xFF40E0FF, 1);
        }
        for (Payloads.Entry e : entries) {
            if (e.aircraft() >= 0) marker(g, e.ax() + 0.5, e.az() + 0.5, 0xFF40E0FF, 2);
        }
        if (minecraft.player != null) marker(g, minecraft.player.getX(), minecraft.player.getZ(), 0xFFFFFFFF, 1);
        if (hasTarget) marker(g, targetX + 0.5, targetZ + 0.5, 0xFFFF3030, 2);
        g.drawString(font, "X", mx, my + MAP + 8, dim, false);
        g.drawString(font, "Z", mx + 68, my + MAP + 8, dim, false);
        g.drawString(font, "1:" + scale(), mx + 40, my + MAP + 28, dim, false);
        if (overMap(mouseX, mouseY)) {
            int hx = Mth.floor(centreX + (mouseX - mx - MAP / 2.0) * scale());
            int hz = Mth.floor(centreZ + (mouseY - my - MAP / 2.0) * scale());
            g.drawString(font, hx + ", " + hz, mx + 2, my + 2, 0xFFFFFFFF, true);
        }

        // Selected launcher.
        int rx = left + 236, ry = top + 18;
        if (cur != null && curType != null) {
            g.drawWordWrap(font, Component.translatable(curType.translationKey()), rx, ry, 80, white);
            g.drawString(font, Component.translatable("gui.steelrain.range", cur.minRange(), cur.maxRange()), rx, ry + 22, dim, false);
            g.drawString(font, Component.translatable("gui.steelrain.ammo_count", cur.ammo()), rx, ry + 33, dim, false);
            g.drawString(font, Component.translatable("gui.steelrain.fuel_count", cur.fuel()), rx, ry + 44, dim, false);
            if (hasTarget) {
                double dx = targetX - cur.pos().getX(), dz = targetZ - cur.pos().getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                boolean ok = dist >= cur.minRange() && dist <= cur.maxRange();
                g.drawString(font, Component.translatable("gui.steelrain.distance", dist), rx, ry + 55, ok ? green : red, false);
            } else {
                g.drawString(font, Component.translatable("gui.steelrain.pick_target"), rx, ry + 55, amber, false);
            }
            Component status = !cur.reachable() ? Component.translatable("gui.steelrain.no_signal")
                    : cur.aircraft() >= 0 ? Component.translatable("gui.steelrain.airborne")
                    : cur.busy() > 0 ? Component.translatable("gui.steelrain.busy", (cur.busy() + 19) / 20)
                    : Component.translatable("gui.steelrain.ready");
            g.drawString(font, status, rx, ry + 66, !cur.reachable() || (cur.busy() > 0 && cur.aircraft() < 0) ? red
                    : cur.aircraft() >= 0 ? 0xFF40E0FF : green, false);
            if (!curType.launchesAircraft()) {
                g.drawString(font, Component.translatable("gui.steelrain.rounds"), rx, top + 107, dim, false);
                String n = Integer.toString(count);
                g.drawString(font, n, rx + 39 - font.width(n) / 2, top + 122, white, false);
            }
        }

        if (messageTicks > 0) {
            g.drawString(font, message, left + 6, top + H - 12, amber, false);
        } else {
            g.drawString(font, Component.translatable("gui.steelrain.map_hint"), left + 6, top + H - 12, dim, false);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        if (mapTexture != null) {
            minecraft.getTextureManager().release(MAP_TEXTURE);
            mapTexture = null;
        }
    }
}
