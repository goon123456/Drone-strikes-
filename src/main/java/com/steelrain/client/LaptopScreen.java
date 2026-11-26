package com.steelrain.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Field Laptop fire control, laid out like a rugged military tablet: linked units down the left with their state at a
 * glance, a live map with a grid, range rings and the line of fire in the middle, the fire mission on the right.
 */
public class LaptopScreen extends Screen {
    private static final ResourceLocation MAP_TEXTURE = ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "laptop_map");
    private static final int[] SCALES = {1, 2, 4, 8, 16, 32, 64};
    /** Printed field-manual palette: warm off-white ink, olive drab rules, one amber accent, muted alarms. */
    private static final int P_BG = 0xFF030805, P_PLATE = 0xFF06100A, P_RULE = 0xFF1F4A2B, P_TEXT = 0xFF8DF29A,
            P_DIM = 0xFF4F9A60, P_FAINT = 0xFF2A5A36, P_AMBER = 0xFFF5C451, P_GO = 0xFF8DF29A, P_RED = 0xFFFF5F52,
            P_CYAN = 0xFF6FE0F0, P_BEZEL = 0xFF101412, P_BEZEL_EDGE = 0xFF252B27;

    /** A plain plate: thin rule round a flat fill. No highlights, no corner marks. */
    private void plate(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, P_RULE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, P_PLATE);
    }

    /** A section title in caps with a rule under it and an optional count on the right. */
    private void header(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, Component title, Component right) {
        g.drawString(font, Ui.upper(title), x + 4, y + 2, P_TEXT, false);
        if (right != null) g.drawString(font, right, x + w - 4 - font.width(right), y + 2, P_DIM, false);
        g.fill(x + 2, y + 12, x + w - 2, y + 13, P_RULE);
    }

    /** A readout: label, dotted leader, value. */
    private void field(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, Component label, Component value, int colour) {
        String l = Ui.upper(label).getString();
        g.drawString(font, l, x, y, P_DIM, false);
        int vx = x + w - font.width(value);
        for (int dx = font.width(l) + 4; dx < vx - 4; dx += 4) g.fill(x + dx, y + 6, x + dx + 1, y + 7, P_FAINT);
        g.drawString(font, value, vx, y, colour, false);
    }

    /** A status word as plain coloured text with a small square, no box. */
    private int chip(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, Component text, int colour) {
        g.fill(x, y + 2, x + 4, y + 6, colour);
        g.drawString(font, Ui.upper(text), x + 7, y, colour, false);
        return 7 + font.width(Ui.upper(text));
    }

    private static final int UNKNOWN = 0xFF181410;
    private static final int LIST_W = 120, SIDE_W = 132, GAP = 5, CARD_H = 28, TOP_BAR = 14, FOOT = 27;

    private List<Payloads.Entry> entries;
    private int selected, listScroll;
    private Component message = Component.empty();
    private int messageTicks;

    private double centreX, centreZ;
    private int scaleIndex = 1;
    private boolean hasTarget;
    private int targetX, targetZ;
    private int count = 1;

    /** Height chart instead of the ordinary map: tinted by elevation, with contour lines and shading. */
    private static boolean heightMode;
    private int[] heights = new int[0];
    private int heightMin, heightMax;
    /** Recent events, kept while the game runs so the strip along the bottom still has them on reopening. */
    private static final Deque<String> LOG = new ArrayDeque<>();
    private Ui.TabletButton layerButton;

    private DynamicTexture mapTexture;
    private int mapRes;
    private boolean mapDirty = true;
    private int age;
    private boolean panning;
    private boolean settingBoxes;
    private long openedAt = -1;

    private EditBox xBox, zBox;
    private Ui.TabletButton launchButton, cameraButton, recallButton, minusButton, plusButton;
    private int left, top, w, h, map, mapLeft, mapTop, sideLeft;

    public LaptopScreen(Payloads.LaptopData data) {
        super(Component.translatable("item.steelrain.field_laptop"));
        this.entries = data.entries();
    }

    public void update(Payloads.LaptopData data) {
        this.entries = data.entries();
        if (selected >= entries.size()) selected = Math.max(0, entries.size() - 1);
        data.message().ifPresent(m -> {
            message = m;
            messageTicks = 400;
            log(m.getString());
        });
    }

    /** The bezel keys step through the linked units; the map and mission follow. */
    private void stepUnit(int by) {
        if (entries.isEmpty()) return;
        selected = Math.floorMod(selected + by, entries.size());
        if (selected < listScroll) listScroll = selected;
        if (selected >= listScroll + visibleCards()) listScroll = selected - visibleCards() + 1;
        mapDirty = true;
    }

    /** Status lights in the bezel: link, ammunition, a fire solution, and aircraft in the air. */
    private void drawLights(GuiGraphics g) {
        boolean link = false, ammo = false, air = false;
        for (Payloads.Entry e : entries) {
            if (!known(e)) continue;
            if (e.reachable()) link = true;
            if (e.ammo() > 0 || e.manual()) ammo = true;
            if (e.aircraft() >= 0) air = true;
        }
        String[] names = {"LINK", "RDY", "FIX", "AIR", "PWR"};
        boolean[] on = {link, ammo, hasTarget, air, true};
        int[] cols = {P_GO, P_GO, P_AMBER, P_CYAN, P_GO};
        int x = left + w - 4, y = top + h + 15;
        for (int i = names.length - 1; i >= 0; i--) {
            x -= font.width(names[i]) + 14;
            g.drawString(font, names[i], x + 9, y - 1, P_DIM, false);
            g.fill(x, y, x + 5, y + 5, on[i] ? cols[i] : P_FAINT);
            if (on[i]) g.fill(x - 1, y - 1, x + 6, y, (cols[i] & 0x00FFFFFF) | 0x50000000);
        }
    }

    /** A hardware key in the bezel: a dark square with a bevel and a green legend, lit when pressed. */
    private static final class BezelKey extends AbstractButton {
        private final Runnable press;

        BezelKey(int x, int y, int w, int h, Component text, Runnable press) {
            super(x, y, w, h, text);
            this.press = press;
        }

        @Override public void onPress() { press.run(); }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            boolean hot = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
            g.fill(getX(), getY(), getX() + width, getY() + height - 1, hot ? 0xFF3A5A40 : 0xFF2A3430);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, hot ? 0xFF1A2A1E : 0xFF141A16);
            int c = hot ? P_TEXT : P_DIM;
            g.drawString(font, getMessage(), getX() + (width - font.width(getMessage())) / 2, getY() + (height - 8) / 2, c, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    private int scale() { return SCALES[scaleIndex]; }

    private void log(String text) {
        String clock = "--:--";
        if (minecraft != null && minecraft.level != null) {
            long t = (minecraft.level.getDayTime() + 6000) % 24000;
            clock = String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
        }
        if (!LOG.isEmpty() && LOG.peekLast().endsWith(text)) return;
        LOG.addLast(clock + "  " + text);
        while (LOG.size() > 30) LOG.removeFirst();
    }

    /** What a unit fires, for flight times and blast sizes; null for things that do not fire at a point. */
    private static MissileProfile profileFor(Payloads.Entry e) {
        String n = e.name();
        if (n.contains("himars")) return MissileProfile.GMLRS;
        if (n.contains("m109")) return MissileProfile.SHELL;
        if (n.contains("mortar")) return MissileProfile.MORTAR_SHELL;
        if (n.contains("tomahawk")) return MissileProfile.TOMAHAWK;
        if (n.contains("iskander")) return MissileProfile.ISKANDER;
        if (n.contains("catapult") || n.contains("shahed")) return MissileProfile.SHAHED;
        return null;
    }

    private static Component t(String key, Object... args) { return Component.translatable("gui.steelrain." + key, args); }

    private void toggleLayer() {
        heightMode = !heightMode;
        mapDirty = true;
    }

    private Payloads.Entry current() {
        return selected >= 0 && selected < entries.size() ? entries.get(selected) : null;
    }

    /** False for a linked launcher that is out of reach or gone. */
    private static boolean known(Payloads.Entry e) { return !e.name().isEmpty(); }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        if (openedAt < 0) openedAt = Util.getMillis();
        w = Math.min(width - 12, 470);
        h = Math.min(height - 46, 316);
        left = (width - w) / 2;
        top = (height - h) / 2;
        map = Math.max(96, Math.min(w - LIST_W - SIDE_W - GAP * 4, h - TOP_BAR - FOOT - GAP * 2 - 2));
        mapLeft = left + GAP + LIST_W + GAP + (w - LIST_W - SIDE_W - GAP * 4 - map) / 2;
        mapTop = top + TOP_BAR + GAP + 1;
        sideLeft = left + w - GAP - SIDE_W;
        if (mapTexture == null || mapRes != map) {
            if (mapTexture != null) minecraft.getTextureManager().release(MAP_TEXTURE);
            mapRes = map;
            mapTexture = new DynamicTexture(map, map, false);
            minecraft.getTextureManager().register(MAP_TEXTURE, mapTexture);
            mapDirty = true;
            if (minecraft.player != null && centreX == 0 && centreZ == 0) {
                centreX = minecraft.player.getX();
                centreZ = minecraft.player.getZ();
            }
        }
        int sx = sideLeft + 6, sy = mapTop + 70;
        xBox = new EditBox(font, sx + 12, sy + 4, 46, 10, Component.literal("X"));
        zBox = new EditBox(font, sx + 74, sy + 4, 46, 10, Component.literal("Z"));
        for (EditBox box : new EditBox[]{xBox, zBox}) {
            box.setBordered(false);
            box.setTextColor(P_TEXT);
            box.setMaxLength(9);
            box.setFilter(s -> s.matches("-?\\d*"));
            box.setResponder(s -> boxesChanged());
            addRenderableWidget(box);
        }
        if (hasTarget) setBoxes();
        int kx = left + 4, ky = top + h + 12;
        addRenderableWidget(new BezelKey(kx, ky, 58, 12, Component.literal("<< UNIT"), () -> stepUnit(-1)));
        addRenderableWidget(new BezelKey(kx + 62, ky, 58, 12, Component.literal("UNIT >>"), () -> stepUnit(1)));
        int by = top + h - FOOT - GAP - 1;
        launchButton = addRenderableWidget(new Ui.TabletButton(sx, by - 22, SIDE_W - 12, 20, Component.translatable("gui.steelrain.launch"), true, this::launch));
        cameraButton = addRenderableWidget(new Ui.TabletButton(sx, by - 40, (SIDE_W - 14) / 2, 14, Component.translatable("gui.steelrain.camera"), false,
                () -> order(Payloads.AircraftOrder.CAMERA)));
        recallButton = addRenderableWidget(new Ui.TabletButton(sx + (SIDE_W - 14) / 2 + 2, by - 40, (SIDE_W - 14) / 2, 14, Component.translatable("gui.steelrain.recall"), false,
                () -> order(Payloads.AircraftOrder.RECALL)));
        minusButton = addRenderableWidget(new Ui.TabletButton(sx + 52, by - 40, 14, 14, Component.literal("-"), false, () -> count = Math.max(1, count - 1)));
        plusButton = addRenderableWidget(new Ui.TabletButton(sx + SIDE_W - 26, by - 40, 14, 14, Component.literal("+"), false, () -> count = Math.min(32, count + 1)));
        layerButton = addRenderableWidget(new Ui.TabletButton(left + w / 2 - 44, top + 1, 88, 12, t("layer_topo"), false, this::toggleLayer));
        int zx = mapLeft + map - 15, zy = mapTop + map - 15;
        addRenderableWidget(new Ui.TabletButton(zx, zy - 32, 13, 13, Component.literal("+"), false, () -> zoom(-1)));
        addRenderableWidget(new Ui.TabletButton(zx, zy - 17, 13, 13, Component.literal("-"), false, () -> zoom(1)));
        addRenderableWidget(new Ui.TabletButton(zx, zy, 13, 13, Component.literal("◎"), false, () -> {
            if (minecraft.player != null) {
                centreX = minecraft.player.getX();
                centreZ = minecraft.player.getZ();
                mapDirty = true;
            }
        }));
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
        PacketDistributor.sendToServer(new Payloads.Launch(e.pos(), e.vehicle(), targetX, targetZ, count));
        log(t("log_fire", e.manual() || e.station() ? 1 : count, Component.translatable(e.name()).getString(), targetX, targetZ).getString());
    }

    private void order(int action) {
        Payloads.Entry e = current();
        if (e != null) PacketDistributor.sendToServer(new Payloads.AircraftOrder(e.pos(), e.vehicle(), action));
    }

    @Override
    public void tick() {
        age++;
        if (messageTicks > 0) messageTicks--;
        if (age % 20 == 0) PacketDistributor.sendToServer(new Payloads.LaptopRefresh());
        if (age % 40 == 0) mapDirty = true;
        Payloads.Entry e = current();
        boolean ok = e != null && known(e);
        layerButton.setMessage(Component.literal(Ui.upper(t(heightMode ? "layer_height" : "layer_topo")).getString() + "  1:" + scale()));
        launchButton.active = ok && hasTarget && e.reachable();
        boolean airborne = e != null && e.aircraft() >= 0 && e.reachable();
        launchButton.setMessage(Component.translatable(airborne ? "gui.steelrain.retask" : ok && e.manual() ? "gui.steelrain.solution" : "gui.steelrain.launch"));
        cameraButton.visible = recallButton.visible = ok && e.station();
        minusButton.visible = plusButton.visible = ok && !e.station() && !e.manual();
        cameraButton.active = recallButton.active = airborne;
    }

    // ------------------------------------------------------------------ map

    private static final int NO_HEIGHT = Integer.MIN_VALUE;

    private void rebuildMap() {
        mapDirty = false;
        ClientLevel level = minecraft.level;
        NativeImage img = mapTexture.getPixels();
        if (level == null || img == null) return;
        int s = scale();
        int x0 = Mth.floor(centreX) - map / 2 * s, z0 = Mth.floor(centreZ) - map / 2 * s;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // heights first: the shading and contour lines need each cell's neighbours
        if (heights.length != map * map) heights = new int[map * map];
        heightMin = Integer.MAX_VALUE;
        heightMax = Integer.MIN_VALUE;
        for (int pz = 0; pz < map; pz++) {
            for (int px = 0; px < map; px++) {
                int x = x0 + px * s, z = z0 + pz * s;
                int y = NO_HEIGHT;
                if (level.hasChunk(x >> 4, z >> 4)) {
                    y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    heightMin = Math.min(heightMin, y);
                    heightMax = Math.max(heightMax, y);
                }
                heights[pz * map + px] = y;
            }
        }
        int sea = level.getSeaLevel();
        for (int pz = 0; pz < map; pz++) {
            for (int px = 0; px < map; px++) {
                int x = x0 + px * s, z = z0 + pz * s;
                int y = heights[pz * map + px];
                int colour = ReconClient.colour(x, z); // what recon drones have seen
                if (colour == 0) colour = UNKNOWN;
                if (y != NO_HEIGHT) {
                    int north = pz > 0 && heights[(pz - 1) * map + px] != NO_HEIGHT ? heights[(pz - 1) * map + px] : y;
                    if (heightMode) {
                        colour = heightColour(level, pos, x, z, y, north, px > 0 && heights[pz * map + px - 1] != NO_HEIGHT ? heights[pz * map + px - 1] : y, sea);
                    } else {
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        MapColor mc = state.getMapColor(level, pos);
                        if (mc != MapColor.NONE) {
                            MapColor.Brightness b = y > north ? MapColor.Brightness.HIGH
                                    : y < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                            colour = mc.calculateRGBColor(b);
                        }
                    }
                }
                // a touch darker and cooler, so the overlays read clearly on top
                int r = colour & 0xFF, gg = (colour >> 8) & 0xFF, bb = (colour >> 16) & 0xFF;
                r = r * 78 / 100; gg = gg * 82 / 100; bb = Math.min(255, bb * 88 / 100 + 6);
                img.setPixelRGBA(px, pz, 0xFF000000 | bb << 16 | gg << 8 | r);
            }
        }
        mapTexture.upload();
    }

    /** Elevation ramp, as on a topographic chart: sea in blues by depth, then greens, yellows, browns, and white on the peaks. */
    private static final int[][] RAMP = {{0, 70, 130, 70}, {16, 110, 160, 80}, {40, 190, 190, 100}, {70, 175, 140, 80}, {100, 140, 100, 70}, {140, 200, 200, 200}, {200, 255, 255, 255}};

    private int heightColour(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y, int north, int west, int sea) {
        int r, g, b;
        boolean water = !level.getFluidState(pos.set(x, y, z)).isEmpty();
        if (water) {
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
            double depth = Mth.clamp((sea - floor) / 30.0, 0, 1);
            r = (int) Mth.lerp(depth, 90, 18); g = (int) Mth.lerp(depth, 160, 50); b = (int) Mth.lerp(depth, 215, 120);
        } else {
            double above = Math.max(0, y - sea);
            int i = 0;
            while (i < RAMP.length - 2 && above >= RAMP[i + 1][0]) i++;
            double f = Mth.clamp((above - RAMP[i][0]) / (RAMP[i + 1][0] - RAMP[i][0]), 0, 1);
            r = (int) Mth.lerp(f, RAMP[i][1], RAMP[i + 1][1]); g = (int) Mth.lerp(f, RAMP[i][2], RAMP[i + 1][2]); b = (int) Mth.lerp(f, RAMP[i][3], RAMP[i + 1][3]);
            // hillshade: light from the north west, so slopes facing it brighten and the others darken
            double shade = Mth.clamp(1 + ((north - y) + (west - y)) * 0.07 * Math.min(2, 1.0 + scale() / 8.0), 0.55, 1.4);
            r = (int) Math.min(255, r * shade); g = (int) Math.min(255, g * shade); b = (int) Math.min(255, b * shade);
            // contour lines: faint every 8 blocks of height, dark every 32
            int minor = Math.floorDiv(y, 8), minorN = Math.floorDiv(north, 8), minorW = Math.floorDiv(west, 8);
            if (minor != minorN || minor != minorW) {
                boolean major = Math.floorDiv(y, 32) != Math.floorDiv(Math.min(north, west), 32) || Math.floorDiv(y, 32) != Math.floorDiv(Math.max(north, west), 32);
                int k = major ? 55 : 80;
                r = r * k / 100; g = g * k / 100; b = b * k / 100;
            }
        }
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    private boolean overMap(double mx, double my) {
        return mx >= mapLeft && mx < mapLeft + map && my >= mapTop && my < mapTop + map;
    }

    /** Screen position of a world point on the map, or null when it is off the map. */
    private int[] onMap(double wx, double wz, int margin) {
        int px = (int) Math.floor((wx - centreX) / scale()) + map / 2;
        int pz = (int) Math.floor((wz - centreZ) / scale()) + map / 2;
        if (px < margin || pz < margin || px >= map - margin || pz >= map - margin) return null;
        return new int[]{mapLeft + px, mapTop + pz};
    }

    private void dot(GuiGraphics g, double wx, double wz, int colour, int size) {
        int[] p = onMap(wx, wz, size);
        if (p == null) return;
        g.fill(p[0] - size, p[1] - size, p[0] + size + 1, p[1] + size + 1, 0xFF000000);
        g.fill(p[0] - size + 1, p[1] - size + 1, p[0] + size, p[1] + size, colour);
    }

    private void unitIcon(GuiGraphics g, Payloads.Entry e, double wx, double wz, int colour) {
        int[] p = onMap(wx, wz, 4);
        if (p == null) return;
        g.fill(p[0] - 4, p[1] - 4, p[0] + 5, p[1] + 5, 0xC0000000);
        Ui.icon(g, p[0] - 3, p[1] - 3, iconFor(e), colour);
    }

    private void ring(GuiGraphics g, double wx, double wz, double radius, int colour, boolean dashed) {
        double r = radius / scale();
        if (r < 3 || r > 2000) return;
        int steps = (int) Mth.clamp(r * 3, 24, 720);
        for (int i = 0; i < steps; i++) {
            if (dashed && (i / 3) % 2 == 1) continue;
            double a = i * Math.PI * 2 / steps;
            int px = (int) Math.floor((wx - centreX) / scale() + Math.cos(a) * r) + map / 2;
            int pz = (int) Math.floor((wz - centreZ) / scale() + Math.sin(a) * r) + map / 2;
            if (px < 0 || pz < 0 || px >= map || pz >= map) continue;
            g.fill(mapLeft + px, mapTop + pz, mapLeft + px + 1, mapTop + pz + 1, colour);
        }
    }

    private void line(GuiGraphics g, double ax, double az, double bx, double bz, int colour) {
        double x0 = (ax - centreX) / scale() + map / 2.0, z0 = (az - centreZ) / scale() + map / 2.0;
        double x1 = (bx - centreX) / scale() + map / 2.0, z1 = (bz - centreZ) / scale() + map / 2.0;
        int steps = (int) Math.max(1, Math.hypot(x1 - x0, z1 - z0));
        for (int i = 0; i <= steps; i++) {
            if ((i / 3) % 2 == 1) continue;
            int px = (int) Math.floor(Mth.lerp((double) i / steps, x0, x1)), pz = (int) Math.floor(Mth.lerp((double) i / steps, z0, z1));
            if (px < 0 || pz < 0 || px >= map || pz >= map) continue;
            g.fill(mapLeft + px, mapTop + pz, mapLeft + px + 1, mapTop + pz + 1, colour);
        }
    }

    /** Grid lines every round number of blocks, labelled along the top and left edges. */
    private void grid(GuiGraphics g) {
        int span = map * scale();
        int step = 16;
        while (span / step > 6) step *= 2;
        double x0 = centreX - map / 2.0 * scale(), z0 = centreZ - map / 2.0 * scale();
        for (int gx = (int) Math.ceil(x0 / step) * step; gx < x0 + span; gx += step) {
            int px = mapLeft + (int) ((gx - x0) / scale());
            g.fill(px, mapTop, px + 1, mapTop + map, 0x30FFFFFF);
            g.drawString(font, Integer.toString(gx), px + 2, mapTop + 2, 0xA0DCE6EA, false);
        }
        for (int gz = (int) Math.ceil(z0 / step) * step; gz < z0 + span; gz += step) {
            int pz = mapTop + (int) ((gz - z0) / scale());
            g.fill(mapLeft, pz, mapLeft + map, pz + 1, 0x30FFFFFF);
            if (pz > mapTop + 10) g.drawString(font, Integer.toString(gz), mapLeft + 2, pz + 2, 0xA0DCE6EA, false);
        }
        // scale bar, bottom left
        int bar = step / scale(), bx = mapLeft + 5, by = mapTop + map - 7;
        g.fill(bx - 2, by - 10, bx + bar + 2 + font.width(step + " m") + 4, by + 4, 0xA0000000);
        g.fill(bx, by, bx + bar, by + 2, P_TEXT);
        g.fill(bx, by - 3, bx + 1, by + 2, P_TEXT); g.fill(bx + bar - 1, by - 3, bx + bar, by + 2, P_TEXT);
        g.drawString(font, step + " m", bx + bar + 4, by - 6, P_TEXT, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overMap(mx, my)) {
            if (button == 0) {
                targetX = Mth.floor(centreX + (mx - mapLeft - map / 2.0) * scale());
                targetZ = Mth.floor(centreZ + (my - mapTop - map / 2.0) * scale());
                hasTarget = true;
                setBoxes();
            } else {
                panning = true;
            }
            return true;
        }
        int lx = left + GAP, ly = mapTop + 14;
        if (mx >= lx && mx < lx + LIST_W && my >= ly) {
            int row = (int) ((my - ly) / CARD_H) + listScroll;
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
        if (mx < left + GAP + LIST_W) {
            listScroll = Mth.clamp(listScroll - (int) Math.signum(sy), 0, Math.max(0, entries.size() - visibleCards()));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private int visibleCards() { return Math.max(1, (map - 14) / CARD_H); }

    // ------------------------------------------------------------------ drawing

    static String[] iconFor(Payloads.Entry e) {
        String n = e.name();
        if (e.station() || n.contains("ground_station")) return Ui.ICON_PLANE;
        if (n.contains("catapult") || n.contains("shahed")) return Ui.ICON_DRONE;
        if (n.contains("himars")) return Ui.ICON_ROCKET;
        if (n.contains("m109") || n.contains("mortar")) return Ui.ICON_SHELL;
        return Ui.ICON_MISSILE;
    }

    private static Component status(Payloads.Entry e) {
        if (!known(e)) return Component.translatable("gui.steelrain.offline");
        if (!e.reachable()) return Component.translatable("gui.steelrain.no_signal");
        if (e.aircraft() >= 0) return Component.translatable("gui.steelrain.airborne");
        if (e.busy() > 0) return Component.translatable("gui.steelrain.busy", (e.busy() + 19) / 20);
        if (e.ammo() <= 0 && !e.manual()) return Component.translatable("gui.steelrain.empty");
        return Component.translatable("gui.steelrain.ready");
    }

    private static int statusColour(Payloads.Entry e) {
        if (!known(e) || !e.reachable()) return P_RED;
        if (e.aircraft() >= 0) return P_CYAN;
        if (e.busy() > 0 || (e.ammo() <= 0 && !e.manual())) return P_AMBER;
        return P_GO;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (mapDirty) rebuildMap();
        // opening: the display zooms up as the laptop comes to the eye
        float open = Mth.clamp((Util.getMillis() - openedAt) / 260f, 0f, 1f);
        float ease = 1 - (1 - open) * (1 - open) * (1 - open);
        boolean anim = open < 1;
        if (anim) {
            g.pose().pushPose();
            g.pose().translate(width / 2f, height / 2f, 0);
            g.pose().scale(0.82f + 0.18f * ease, 0.82f + 0.18f * ease, 1);
            g.pose().translate(-width / 2f, -height / 2f, 0);
        }
        renderBackground(g, mouseX, mouseY, partialTick);
        // tablet body: thick rubber bezel and the screen inside it
        g.fill(left - 8, top - 8, left + w + 8, top + h + 30, P_BEZEL_EDGE);
        g.fill(left - 7, top - 7, left + w + 7, top + h + 29, P_BEZEL);
        g.fill(left - 2, top - 2, left + w + 2, top + h + 2, 0xFF000000);
        g.fill(left, top, left + w, top + h, Ui.BG);
        drawTopBar(g);
        drawUnits(g);
        drawMap(g, mouseX, mouseY);
        drawMission(g);
        drawFooter(g);
        drawLights(g);
        for (var r : renderables) r.render(g, mouseX, mouseY, partialTick);
        if (anim) {
            g.fill(left, top, left + w, top + h, ((int) ((1 - ease) * 200) << 24) | 0x0A0E10);
            g.pose().popPose();
        }
    }

    private void drawTopBar(GuiGraphics g) {
        g.fill(left, top, left + w, top + TOP_BAR, P_BG);
        g.fill(left, top + TOP_BAR - 1, left + w, top + TOP_BAR, P_RULE);
        g.drawString(font, Ui.upper(Component.translatable("gui.steelrain.laptop_title")), left + 6, top + 3, P_TEXT, false);
        g.drawString(font, "STEEL RAIN C2", left + 6 + font.width(Ui.upper(Component.translatable("gui.steelrain.laptop_title"))) + 8, top + 3, P_FAINT, false);
        if (minecraft.player != null && minecraft.level != null) {
            long t = (minecraft.level.getDayTime() + 6000) % 24000;
            String clock = String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
            String own = "GRID " + Mth.floor(minecraft.player.getX()) + " " + Mth.floor(minecraft.player.getZ());
            g.drawString(font, clock, left + w - 6 - font.width(clock), top + 3, P_AMBER, false);
            g.drawString(font, own, left + w - 14 - font.width(clock) - font.width(own), top + 3, P_DIM, false);
            String ct = "CT " + contactCount;
            g.drawString(font, ct, left + w - 14 - font.width(clock) - font.width(own) - 12 - font.width(ct), top + 3, contactCount > 0 ? P_AMBER : P_DIM, false);
        }
    }

    private void drawUnits(GuiGraphics g) {
        int x = left + GAP, y = mapTop;
        plate(g, x, y - 1, LIST_W, map + 2);
        header(g, font, x + 1, y, LIST_W - 2, Component.translatable("gui.steelrain.units"), Component.literal(Integer.toString(entries.size())));
        int ly = y + 14;
        if (entries.isEmpty()) {
            g.drawString(font, Ui.upper(Component.translatable("gui.steelrain.no_links")).getString(), x + 6, ly + 6, P_DIM, false);
            return;
        }
        int shown = visibleCards();
        for (int i = listScroll; i < entries.size() && i < listScroll + shown; i++) {
            Payloads.Entry e = entries.get(i);
            int cy = ly + (i - listScroll) * CARD_H;
            boolean sel = i == selected;
            g.fill(x + 3, cy + 1, x + LIST_W - 3, cy + CARD_H - 1, sel ? 0xFF0F2415 : P_PLATE);
            if (sel) g.fill(x + 3, cy + 1, x + 5, cy + CARD_H - 1, P_AMBER);
            int colour = statusColour(e);
            Ui.icon(g, x + 8, cy + 4, iconFor(e), sel ? P_AMBER : P_TEXT);
            Component name = known(e) ? Component.translatable(e.name()) : Component.translatable("gui.steelrain.offline");
            g.drawString(font, font.plainSubstrByWidth(name.getString(), LIST_W - 28), x + 18, cy + 4, sel ? P_TEXT : 0xFFC8D2D8, false);
            // second line: status dot + text, then rounds and energy gauges
            g.fill(x + 8, cy + 16, x + 12, cy + 20, colour);
            String st = status(e).getString();
            g.drawString(font, font.plainSubstrByWidth(st, 50), x + 15, cy + 14, colour, false);
            if (known(e)) {
                String rounds = e.ammo() + (e.second() ? "+" + e.ammo2() : "");
                g.drawString(font, rounds, x + LIST_W - 8 - font.width(rounds), cy + 14, P_DIM, false);
                double energy = e.fuel() <= 0 ? 0 : Math.min(1, e.fuel() / 1000.0);
                Ui.gauge(g, x + 8, cy + CARD_H - 5, LIST_W - 19, 2, energy, P_CYAN, 20);
            }
        }
        if (entries.size() > shown) {
            int track = map - 16, knob = Math.max(8, track * shown / entries.size());
            int ky = ly + (track - knob) * listScroll / Math.max(1, entries.size() - shown);
            g.fill(x + LIST_W - 3, ly, x + LIST_W - 2, ly + track, 0xFF2A353C);
            g.fill(x + LIST_W - 3, ky, x + LIST_W - 2, ky + knob, P_AMBER);
        }
    }

    private int contactCount;

    private void drawMap(GuiGraphics g, int mouseX, int mouseY) {
        int mx = mapLeft, my = mapTop;
        Ui.well(g, mx, my, map, map);
        // the terrain is printed in muted tones, so the coloured markers stand out from it
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.40f, 0.95f, 0.52f, 1f);
        g.blit(MAP_TEXTURE, mx, my, 0, 0f, 0f, map, map, map, map);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.enableScissor(mx, my, mx + map, my + map);
        grid(g);
        Payloads.Entry cur = current();
        boolean curType = cur != null && known(cur);
        if (curType) {
            double cx = cur.pos().getX() + 0.5, cz = cur.pos().getZ() + 0.5;
            ring(g, cx, cz, cur.maxRange(), 0xD0F2B33D, false);
            ring(g, cx, cz, cur.minRange(), 0xC0E5534B, true);
            if (hasTarget) line(g, cx, cz, targetX + 0.5, targetZ + 0.5, 0xE0F2B33D);
        }
        // radar sweep: a fading wedge turning once every four seconds about the selected unit, or about you
        double sx = 0, sz = 0, sr = 0;
        if (curType) { sx = cur.pos().getX() + 0.5; sz = cur.pos().getZ() + 0.5; sr = Math.max(1, cur.maxRange()); }
        else if (minecraft.player != null) { sx = minecraft.player.getX(); sz = minecraft.player.getZ(); sr = 96; }
        if (sr > 0) {
            double turn = (Util.getMillis() % 4000) / 4000.0 * 360;
            for (int k = 0; k < 28; k++) {
                double a = Math.toRadians(turn - k * 1.25);
                int alpha = (int) (0x60 * (1 - k / 28.0));
                line(g, sx, sz, sx + Math.sin(a) * sr, sz + Math.cos(a) * sr, (alpha << 24) | 0x7CF59A);
            }
            for (int d = 0; d < 360; d += 30) {
                double a = Math.toRadians(d);
                dot(g, sx + Math.sin(a) * sr, sz + Math.cos(a) * sr, d % 90 == 0 ? P_TEXT : P_FAINT, 1);
            }
        }
        // recon: what the drone can see and its contacts
        int contacts = 0;
        for (ReconClient.Report report : ReconClient.fresh()) {
            Payloads.Markers mk = report.markers();
            ring(g, mk.x() + 0.5, mk.z() + 0.5, mk.radius(), 0xA04FC3F7, true);
            int[] d = mk.data();
            contacts += d.length / 3;
            for (int i = 0; i + 2 < d.length; i += 3) {
                int colour = switch (d[i + 2]) {
                    case 0 -> 0xFFFF4040;   // hostile mob
                    case 1 -> 0xFFB0B0B0;   // animal / neutral
                    case 2 -> 0xFFFF40FF;   // enemy player
                    case 3 -> 0xFF4080FF;   // friendly player
                    case 5 -> 0xFFFFF040;   // enemy jammer
                    default -> 0xFFFF9020;  // enemy weapon system
                };
                dot(g, d[i] + 0.5, d[i + 1] + 0.5, colour, d[i + 2] >= 4 ? 2 : 1);
            }
            dot(g, mk.x() + 0.5, mk.z() + 0.5, P_CYAN, 1);
        }
        for (int i = 0; i < entries.size(); i++) {
            Payloads.Entry e = entries.get(i);
            if (!known(e)) continue;
            unitIcon(g, e, e.pos().getX() + 0.5, e.pos().getZ() + 0.5, i == selected ? P_AMBER : e.reachable() ? P_GO : P_RED);
            if (e.aircraft() >= 0) unitIcon(g, e, e.ax() + 0.5, e.az() + 0.5, P_CYAN);
        }
        if (minecraft.player != null) {
            int[] p = onMap(minecraft.player.getX(), minecraft.player.getZ(), 3);
            if (p != null) {
                float yaw = minecraft.player.getYRot() * Mth.DEG_TO_RAD;
                g.fill(p[0] - 2, p[1] - 2, p[0] + 3, p[1] + 3, 0xFF000000);
                g.fill(p[0] - 1, p[1] - 1, p[0] + 2, p[1] + 2, 0xFFFFFFFF);
                int hx = p[0] + Math.round(-Mth.sin(yaw) * 6), hz = p[1] + Math.round(Mth.cos(yaw) * 6);
                g.fill(hx - 1, hz - 1, hx + 1, hz + 1, 0xFFFFFFFF);
            }
        }
        if (hasTarget) {
            int[] p = onMap(targetX + 0.5, targetZ + 0.5, 0);
            if (p != null) {
                boolean blink = Ui.blink();
                Ui.brackets(g, p[0] - 6, p[1] - 6, 13, 13, 4, P_RED);
                g.fill(p[0] - 1, p[1], p[0] + 2, p[1] + 1, blink ? P_RED : 0xFFFFFFFF);
                g.fill(p[0], p[1] - 1, p[0] + 1, p[1] + 2, blink ? P_RED : 0xFFFFFFFF);
            }
        }
        if (overMap(mouseX, mouseY)) {
            int hx = Mth.floor(centreX + (mouseX - mx - map / 2.0) * scale());
            int hz = Mth.floor(centreZ + (mouseY - my - map / 2.0) * scale());
            g.fill(mouseX, my, mouseX + 1, my + map, 0x40FFFFFF);
            g.fill(mx, mouseY, mx + map, mouseY + 1, 0x40FFFFFF);
            int hy = heights.length == map * map ? heights[(mouseY - my) * map + (mouseX - mx)] : NO_HEIGHT;
            String c = hx + " " + hz + (hy != NO_HEIGHT ? "  Y " + hy : "");
            int tx = Math.min(mouseX + 6, mx + map - font.width(c) - 4), ty = Math.max(mouseY - 12, my + 2);
            g.fill(tx - 2, ty - 2, tx + font.width(c) + 2, ty + 9, 0xD0000000);
            g.drawString(font, c, tx, ty, P_TEXT, false);
        }
        g.disableScissor();
        // the scope's readout, top left
        contactCount = contacts;
        g.fill(mx - 1, my - 1, mx + map + 1, my, P_RULE);
        g.fill(mx - 1, my + map, mx + map + 1, my + map + 1, P_RULE);
        if (heightMode && heightMin <= heightMax && map >= 150) heightLegend(g, mx + map - 74, my + map - 16);
        String zoom = "1:" + scale();
        g.drawString(font, zoom, mx + map + 3, mapTop + map - 60, P_DIM, false);
    }

    /** A colour ramp with the lowest and highest ground in view. */
    private void heightLegend(GuiGraphics g, int x, int y) {
        g.fill(x - 2, y - 2, x + 72, y + 13, 0xB0000000);
        int sea = minecraft.level.getSeaLevel();
        int lo = heightMin, hi = Math.max(heightMax, heightMin + 1);
        for (int i = 0; i < 40; i++) {
            int y2 = (int) Mth.lerp(i / 39.0, lo, hi);
            int c = heightColour(minecraft.level, new BlockPos.MutableBlockPos(), Integer.MIN_VALUE / 2, 0, y2, y2, y2, sea);
            int cr = c & 0xFF, cg = (c >> 8) & 0xFF, cb = (c >> 16) & 0xFF;
            g.fill(x + i, y, x + i + 1, y + 5, 0xFF000000 | cr << 16 | cg << 8 | cb);
        }
        g.drawString(font, Integer.toString(lo), x, y + 5, P_DIM, false);
        String top = Integer.toString(hi);
        g.drawString(font, top, x + 40 - font.width(top), y + 5, P_DIM, false);
        g.drawString(font, "Y", x + 46, y + 1, P_FAINT, false);
        g.drawString(font, "8/32", x + 54, y + 5, P_FAINT, false);
    }

    private void drawMission(GuiGraphics g) {
        int x = sideLeft, y = mapTop;
        plate(g, x, y - 1, SIDE_W, h - TOP_BAR - FOOT - GAP * 2);
        Payloads.Entry cur = current();
        boolean ok = cur != null && known(cur);
        header(g, font, x + 1, y, SIDE_W - 2, Component.translatable("gui.steelrain.mission"), null);
        int ix = x + 6, iw = SIDE_W - 12;
        if (!ok) {
            g.drawString(font, Ui.upper(Component.translatable("gui.steelrain.select_unit")).getString(), ix, y + 18, P_DIM, false);
            return;
        }
        // the unit: name, state, what it carries and where it is
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(cur.name()).getString(), iw), ix, y + 16, P_TEXT, false);
        chip(g, font, ix, y + 27, status(cur), statusColour(cur));
        field(g, font, ix, y + 41, iw, t("f_range"), Component.literal(cur.minRange() + "-" + cur.maxRange() + " m"), P_TEXT);
        String rounds = cur.ammo() + (cur.second() ? " + " + cur.ammo2() : "");
        field(g, font, ix, y + 52, iw, Component.translatable("gui.steelrain.f_rounds"), Component.literal(rounds),
                cur.ammo() > 0 || cur.manual() ? P_TEXT : P_RED);
        int ux = cur.aircraft() >= 0 ? cur.ax() : cur.pos().getX(), uz = cur.aircraft() >= 0 ? cur.az() : cur.pos().getZ();
        int uy = groundAt(ux, uz);
        String from = minecraft.player == null ? "" : " \u00B7 " + (int) Math.sqrt(minecraft.player.distanceToSqr(ux + 0.5, minecraft.player.getY(), uz + 0.5)) + " m";
        field(g, font, ix, y + 63, iw, t("f_grid"), Component.literal(ux + ", " + uz), P_TEXT);
        field(g, font, ix, y + 73, iw, t("f_height"), Component.literal((uy == NO_HEIGHT ? "--" : "Y " + uy) + from), P_TEXT);
        field(g, font, ix, y + 84, iw, t("f_energy"), Component.literal(cur.fuel() + " k"), cur.fuel() > 200 ? P_TEXT : P_AMBER);
        Ui.gauge(g, ix, y + 95, iw, 3, Math.min(1, cur.fuel() / 1000.0), cur.fuel() > 200 ? P_CYAN : P_AMBER, 24);
        if (cur.busy() > 0) field(g, font, ix, y + 103, iw, t("f_reload"), Component.literal(String.format(Locale.ROOT, "%.1f s", cur.busy() / 20.0)), P_AMBER);
        else field(g, font, ix, y + 103, iw, t("f_reload"), t("f_ready"), P_GO);

        // engagement envelope: the band the unit can hit, with the target marked on it when one is set
        int dist = -1;
        if (hasTarget) {
            double ddx = targetX + 0.5 - (cur.pos().getX() + 0.5), ddz = targetZ + 0.5 - (cur.pos().getZ() + 0.5);
            dist = (int) Math.sqrt(ddx * ddx + ddz * ddz);
        }
        g.drawString(font, Ui.upper(t("f_envelope")), ix, y + 116, P_DIM, false);
        envelope(g, ix, y + 127, iw, cur, dist);

        // target entry
        int ty = y + 146;
        g.drawString(font, "X", ix, ty + 5, P_DIM, false);
        Ui.well(g, ix + 10, ty + 2, 50, 12);
        g.drawString(font, "Z", ix + 64, ty + 5, P_DIM, false);
        Ui.well(g, ix + 72, ty + 2, 50, 12);
        int floorY = top + h - FOOT - GAP - 1 - 44;   // the buttons start here
        if (hasTarget) {
            double dx = targetX + 0.5 - (cur.pos().getX() + 0.5), dz = targetZ + 0.5 - (cur.pos().getZ() + 0.5);
            int bearing = (int) Math.round((Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360);
            boolean inRange = dist >= cur.minRange() && dist <= cur.maxRange();
            MissileProfile prof = profileFor(cur);
            int row = ty + 18;
            field(g, font, ix, row, iw, t("f_distance"), Component.literal(dist + " m"), inRange ? P_TEXT : P_RED); row += 10;
            field(g, font, ix, row, iw, t("f_bearing"), Component.literal(String.format("%03d\u00B0 %s", bearing, compassPoint(bearing))), P_TEXT); row += 10;
            if (prof != null) {
                int ticks = Math.max(20, (int) (dist / prof.speed()));
                field(g, font, ix, row, iw, t("f_flight"), Component.literal(String.format(Locale.ROOT, "%d:%02d", ticks / 1200, ticks / 20 % 60)), P_TEXT); row += 10;
            }
            int groundY = groundAt(targetX, targetZ), ownY = groundAt(cur.pos().getX(), cur.pos().getZ());
            if (groundY != NO_HEIGHT) {
                String d = ownY != NO_HEIGHT ? String.format(" (%+d)", groundY - ownY) : "";
                field(g, font, ix, row, iw, t("f_elev"), Component.literal("Y " + groundY + d), P_TEXT); row += 10;
            } else {
                field(g, font, ix, row, iw, t("f_elev"), Component.literal("--"), P_FAINT); row += 10;
            }
            if (prof != null && prof.blastPower() > 0) {
                field(g, font, ix, row, iw, t("f_blast"), Component.literal((int) prof.damageRadius() + " m"), P_TEXT); row += 10;
            }
            if (prof != null && prof.spread() > 0 && row + 10 < floorY - 30) {
                field(g, font, ix, row, iw, t("f_error"), Component.literal("\u00B1" + String.format(Locale.ROOT, "%.1f m", prof.spread())), P_TEXT); row += 10;
            }
            chip(g, font, ix, row + 1, Component.translatable(inRange ? "gui.steelrain.in_range" : dist < cur.minRange() ? "gui.steelrain.too_close_short" : "gui.steelrain.out_of_range"),
                    inRange ? P_GO : P_RED);
            row += 14;
            if (floorY - row >= 26) profile(g, ix, row, iw, floorY - row - 2, cur, prof, dist);
        } else {
            g.drawString(font, Ui.upper(Component.translatable("gui.steelrain.pick_target")).getString(), ix, ty + 20, P_DIM, false);
        }
        if (!cur.station() && !cur.manual()) {
            int by = top + h - FOOT - GAP - 1 - 40;
            g.drawString(font, Ui.upper(Component.translatable("gui.steelrain.rounds")), ix, by + 3, P_DIM, false);
            String n = Integer.toString(count);
            int cx = (ix + 52 + 14 + ix + iw - 26) / 2;
            g.drawString(font, n, cx - font.width(n) / 2, by + 3, P_AMBER, false);
        }
    }

    /** The band from the unit's minimum to maximum range, with the target's distance marked on it. */
    private void envelope(GuiGraphics g, int x, int y, int w, Payloads.Entry cur, int dist) {
        int max = Math.max(1, cur.maxRange());
        Ui.well(g, x, y, w, 6);
        int x1 = x + (int) Math.round(w * Math.min(1.0, cur.minRange() / (double) max));
        g.fill(x1, y, x + w, y + 6, Ui.fade(P_CYAN, 0.45f));
        g.fill(x1, y - 2, x1 + 1, y + 8, P_AMBER);
        if (dist >= 0) {
            int mx = x + (int) Math.round(w * Math.min(dist, max) / (double) max);
            boolean in = dist >= cur.minRange() && dist <= cur.maxRange();
            g.fill(mx - 1, y - 3, mx + 2, y + 9, in ? P_GO : P_RED);
        }
        g.drawString(font, "0", x, y + 9, P_FAINT, false);
        String top = max + " m";
        g.drawString(font, top, x + w - font.width(top), y + 9, P_FAINT, false);
    }

    private static String compassPoint(int deg) {
        return new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[Math.floorMod(Math.round(deg / 45f), 8)];
    }

    /** Ground level at a point, from what the client has loaded, or {@link #NO_HEIGHT}. */
    private int groundAt(int x, int z) {
        ClientLevel level = minecraft.level;
        if (level == null || !level.hasChunk(x >> 4, z >> 4)) return NO_HEIGHT;
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    /**
     * Side view of the shot: the ground between launcher and target, and the path the munition takes over it. Cruise
     * missiles hug the terrain, ballistic ones climb and drop, direct ones fly straight. Drawn to scale for the ground,
     * schematic for the arc.
     */
    private void profile(GuiGraphics g, int x, int y, int w, int hgt, Payloads.Entry cur, MissileProfile prof, int dist) {
        Ui.well(g, x, y, w, hgt);
        g.enableScissor(x, y, x + w, y + hgt);
        double ax = cur.pos().getX() + 0.5, az = cur.pos().getZ() + 0.5;
        int n = w;
        int[] ground = new int[n];
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            double f = i / (double) (n - 1);
            int gy = groundAt(Mth.floor(Mth.lerp(f, ax, targetX + 0.5)), Mth.floor(Mth.lerp(f, az, targetZ + 0.5)));
            ground[i] = gy;
            if (gy != NO_HEIGHT) { lo = Math.min(lo, gy); hi = Math.max(hi, gy); }
        }
        if (lo > hi) { lo = 60; hi = 70; }
        double apex = prof == null ? 0 : switch (prof.mode()) {
            case BALLISTIC -> Mth.clamp(dist * 0.35, 12, 220);
            case CRUISE -> prof.cruiseAltitude();
            default -> 0;
        };
        int top = (int) (hi + apex * (prof != null && prof.mode() == MissileProfile.FlightMode.CRUISE ? 1 : 1) + 6), bottom = lo - 4;
        if (top - bottom < 20) top = bottom + 20;
        double sy = (hgt - 4) / (double) (top - bottom);
        int prevPy = -1;
        for (int i = 0; i < n; i++) {
            if (ground[i] == NO_HEIGHT) continue;
            int py = y + hgt - 2 - (int) ((ground[i] - bottom) * sy);
            g.fill(x + i, py, x + i + 1, y + hgt, i % 2 == 0 ? 0xFF3C4A32 : 0xFF343F2C);
            g.fill(x + i, py, x + i + 1, py + 1, 0xFF8FA06A);
            prevPy = py;
        }
        // path
        if (prof != null) {
            int startG = ground[0] == NO_HEIGHT ? lo : ground[0], endG = ground[n - 1] == NO_HEIGHT ? lo : ground[n - 1];
            for (int i = 0; i < n; i++) {
                double f = i / (double) (n - 1);
                double alt;
                switch (prof.mode()) {
                    case BALLISTIC -> alt = Mth.lerp(f, startG, endG) + 4 * apex * f * (1 - f);
                    case CRUISE -> {
                        int gy = ground[i] == NO_HEIGHT ? (int) Mth.lerp(f, startG, endG) : ground[i];
                        alt = gy + Math.min(apex, i < 10 ? i * apex / 10 : apex) ;
                    }
                    default -> alt = Mth.lerp(f, startG + 2, endG);
                }
                if (i % 3 == 2) continue;
                int py = y + hgt - 2 - (int) ((alt - bottom) * sy);
                g.fill(x + i, py, x + i + 1, py + 1, P_AMBER);
            }
        }
        // launcher and target markers
        g.fill(x, y + 1, x + 1, y + hgt - 1, P_GO);
        g.fill(x + w - 1, y + 1, x + w, y + hgt - 1, P_RED);
        g.disableScissor();
        g.drawString(font, Ui.upper(t("profile")), x + 4, y + 2, P_FAINT, false);
    }

    private void drawFooter(GuiGraphics g) {
        int y = top + h - FOOT;
        g.fill(left, y, left + w, top + h, P_BG);
        g.fill(left, y, left + w, y + 1, P_RULE);
        // status chips, right to left
        int rx = left + w - 5;
        int reachable = 0, known = 0, rounds = 0;
        for (Payloads.Entry e : entries) if (known(e)) { known++; if (e.reachable()) reachable++; if (!e.station()) rounds += e.ammo() + (e.second() ? e.ammo2() : 0); }
        String wx = "CLEAR";
        boolean day = true;
        if (minecraft.level != null) {
            wx = minecraft.level.isThundering() ? "STORM" : minecraft.level.isRaining() ? "RAIN" : "CLEAR";
            day = minecraft.level.isDay();
        }
        int ping = 0;
        if (minecraft.getConnection() != null && minecraft.player != null) {
            var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
            if (info != null) ping = info.getLatency();
        }
        String[] labels = {"LINK", "RDS", "WX", day ? "DAY" : "NIGHT", "PING"};
        String[] values = {reachable + "/" + entries.size(), Integer.toString(rounds), wx, "", ping + " ms"};
        int[] cols = {entries.isEmpty() || reachable == entries.size() ? P_GO : reachable == 0 ? P_RED : P_AMBER,
                rounds > 0 ? P_TEXT : P_RED, wx.equals("CLEAR") ? P_TEXT : P_AMBER, day ? P_AMBER : P_CYAN, ping < 100 ? P_TEXT : ping < 250 ? P_AMBER : P_RED};
        for (int i = labels.length - 1; i >= 0; i--) {
            String text = values[i].isEmpty() ? labels[i] : labels[i] + " " + values[i];
            String value = values[i];
            if (value.isEmpty()) {
                rx -= font.width(labels[i]) + 10;
                g.drawString(font, labels[i], rx, y + 4, cols[i], false);
            } else {
                rx -= font.width(labels[i]) + 3 + font.width(value) + 10;
                g.drawString(font, labels[i], rx, y + 4, P_DIM, false);
                g.drawString(font, value, rx + font.width(labels[i]) + 3, y + 4, cols[i], false);
            }
            g.fill(rx - 5, y + 4, rx - 4, y + 12, P_RULE);
        }
        // event log, two lines at the left; a fresh message shows brighter
        int lx = left + 6, lw = rx - lx - 8;
        String[] last = LOG.toArray(new String[0]);
        for (int i = 0; i < 2; i++) {
            int idx = last.length - 1 - i;
            int ly = y + 15 - i * 10 + (i == 0 ? 0 : -3);
            if (idx < 0) break;
            boolean fresh = i == 0 && messageTicks > 0;
            if (fresh) Ui.icon(g, lx, ly, Ui.ICON_WARN, P_AMBER);
            g.drawString(font, font.plainSubstrByWidth(last[idx], lw - (fresh ? 10 : 0)), lx + (fresh ? 10 : 0), ly, fresh ? P_AMBER : i == 0 ? P_DIM : P_FAINT, false);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean typing = (xBox != null && xBox.isFocused()) || (zBox != null && zBox.isFocused());
        if (!typing && key == org.lwjgl.glfw.GLFW.GLFW_KEY_H) { toggleLayer(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        if (mapTexture != null) {
            minecraft.getTextureManager().release(MAP_TEXTURE);
            mapTexture = null;
        }
        LaptopHold.closed();
    }
}
