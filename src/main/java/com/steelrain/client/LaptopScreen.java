package com.steelrain.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
import com.steelrain.weapon.MissileProfile;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
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
    /** Command display palette: charcoal glass, silver text, green readiness and red fire controls. */
    private static final int P_BG = 0xFF050A0B, P_PLATE = 0xFF0B1213, P_RULE = 0xFF30423D, P_TEXT = 0xFFDBE5DF,
            P_DIM = 0xFF8BABA0, P_FAINT = 0xFF405A50, P_AMBER = 0xFFF5C451, P_GO = 0xFF8DF29A, P_RED = 0xFFFF5F52,
            P_CYAN = 0xFF6FE0F0, P_BEZEL = 0xFF101412, P_BEZEL_EDGE = 0xFF252B27;

    /** Thin metal edge and a subtle glass highlight for each instrument panel. */
    private void plate(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, P_RULE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, P_PLATE);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 16, 0xFF17221F, P_PLATE);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF43564C);
    }

    /** A section title in caps with a rule under it and an optional count on the right. */
    private void header(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, Component title, Component right) {
        g.drawString(font, Ui.upper(title), x + 4, y + 2, P_TEXT, false);
        if (right != null) g.drawString(font, right, x + w - 4 - font.width(right), y + 2, P_DIM, false);
        g.fill(x + 2, y + 12, x + w - 2, y + 13, P_RULE);
    }

    /** A readout: label, dotted leader, value. */
    private void field(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, Component label, Component value, int colour) {
        String v = font.plainSubstrByWidth(value.getString(), Math.max(0, w * 2 / 3));
        String l = font.plainSubstrByWidth(Ui.upper(label).getString(), Math.max(0, w - font.width(v) - 8));
        g.drawString(font, l, x, y, P_DIM, false);
        g.drawString(font, v, x + w - font.width(v), y, colour, false);
    }

    /** A status word as plain coloured text with a small square, no box. */
    private int chip(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, Component text, int colour) {
        g.fill(x, y + 2, x + 4, y + 6, colour);
        g.drawString(font, Ui.upper(text), x + 7, y, colour, false);
        return 7 + font.width(Ui.upper(text));
    }

    private static final int UNKNOWN = 0xFF181410;
    private static final int LIST_W = 210, SIDE_W = 228, GAP = 8, CARD_H = 36, TOP_BAR = 74;
    private static final int CANVAS_W = 960, CANVAS_H = 530, MAIN_H = 254, DETAIL_Y = 369, DETAIL_H = 121;
    private float uiScale;
    private int viewportX, viewportY;
    private String detailPage = "overview";

    private List<Payloads.Entry> entries;
    private int selected, listScroll;
    private Component message = Component.empty();
    private int messageTicks;

    private double centreX, centreZ;
    private int scaleIndex;
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
    private int textureWidth, textureHeight;
    private boolean mapDirty = true;
    private int age;
    private boolean panning;
    private boolean settingBoxes;

    private EditBox xBox, zBox;
    private Ui.TabletButton launchButton, cameraButton, recallButton, minusButton, plusButton;
    private int left, top, w, h, mapWidth, mapHeight, mapLeft, mapTop, sideLeft;

    public LaptopScreen(Payloads.LaptopData data) {
        super(Component.translatable("item.steelrain.field_laptop"));
        this.entries = data.entries();
    }

    public void update(Payloads.LaptopData data) {
        this.entries = data.entries();
        if (selected >= entries.size()) selected = Math.max(0, entries.size() - 1);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, entries.size() - visibleCards()));
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
        for (Payloads.Entry e : entries) if (known(e)) {
            link |= e.reachable(); ammo |= e.ammo() > 0 || e.manual(); air |= e.aircraft() >= 0;
        }
        String[] names = {"LINK", "RDY", "FIX", "AIR", "PWR"};
        boolean[] on = {link, ammo, hasTarget, air, true};
        int x = 307;
        for (int i = 0; i < names.length; i++) {
            plate(g, x, 502, 68, 20);
            g.fill(x + 8, 509, x + 16, 514, on[i] ? P_GO : P_FAINT);
            g.drawString(font, names[i], x + 23, 508, P_DIM, false);
            x += 68;
        }
    }

    /** All display controls share the same geometry for rendering, pointer input and keyboard focus. */
    private final class DisplayButton extends Ui.TabletButton {
        private final java.util.function.BooleanSupplier lit;
        private final boolean danger;
        DisplayButton(int x, int y, int w, int h, Component text, boolean danger,
                      java.util.function.BooleanSupplier lit, Runnable press) {
            super(x, y, w, h, text, danger, press);
            this.danger = danger;
            this.lit = lit;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean on = lit.getAsBoolean(), hot = active && isHoveredOrFocused();
            int edge = !active ? P_RULE : danger ? P_RED : on || hot ? P_GO : P_RULE;
            g.fill(x, y, x + w, y + h, edge);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1,
                    !active ? 0xFF141B1C : danger ? 0xFF421417 : on || hot ? 0xFF193725 : 0xFF17201F,
                    danger && active ? 0xFF1E090C : P_BG);
            if (on || hot) g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, edge);
            if (danger && active) {
                for (int i = 3; i < 14; i += 4) {
                    g.fill(x + i, y + 3, x + i + 2, y + h - 3, 0xFF712528);
                    g.fill(x + w - i - 2, y + 3, x + w - i, y + h - 3, 0xFF712528);
                }
            }
            String label = font.plainSubstrByWidth(Ui.upper(getMessage()).getString(), w - 8);
            g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2,
                    !active ? P_FAINT : on ? P_GO : P_TEXT, false);
        }
    }

    private DisplayButton button(int x, int y, int w, int h, Component text, boolean danger,
                                 java.util.function.BooleanSupplier lit, Runnable press) {
        DisplayButton key = addRenderableWidget(new DisplayButton(x, y, w, h, text, danger, lit, press));
        return key;
    }

    private DisplayButton button(int x, int y, int w, int h, String text, Runnable press) {
        return button(x, y, w, h, Component.literal(text), false, () -> false, press);
    }

    private void pageButton(int x, int y, int w, int h, String label, String page) {
        button(x, y, w, h, Component.literal(label), false, () -> detailPage.equals(page), () -> detailPage = page);
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
        // Work in one logical space, independent of Minecraft's GUI scale and window aspect ratio.
        w = CANVAS_W; h = CANVAS_H; left = 0; top = 0;
        uiScale = Math.min((width - 8f) / w, (height - 8f) / h);
        viewportX = Math.round((width - w * uiScale) / 2);
        viewportY = Math.round((height - h * uiScale) / 2);
        mapLeft = GAP + LIST_W + GAP;
        mapTop = TOP_BAR + GAP;
        sideLeft = w - GAP - SIDE_W;
        mapWidth = sideLeft - GAP - 22 - mapLeft;
        mapHeight = MAIN_H;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, entries.size() - visibleCards()));
        if (mapTexture == null || textureWidth != mapWidth || textureHeight != mapHeight) {
            if (mapTexture != null) minecraft.getTextureManager().release(MAP_TEXTURE);
            textureWidth = mapWidth; textureHeight = mapHeight;
            mapTexture = new DynamicTexture(mapWidth, mapHeight, false);
            minecraft.getTextureManager().register(MAP_TEXTURE, mapTexture);
            mapDirty = true;
            if (minecraft.player != null && centreX == 0 && centreZ == 0) {
                centreX = minecraft.player.getX(); centreZ = minecraft.player.getZ();
            }
        }
        int sx = sideLeft + 10, sy = mapTop + 63;
        xBox = new EditBox(font, sx + 16, sy + 5, 78, 12, Component.literal("Target X"));
        zBox = new EditBox(font, sx + 120, sy + 5, 78, 12, Component.literal("Target Z"));
        for (EditBox box : new EditBox[]{xBox, zBox}) {
            box.setBordered(false); box.setTextColor(P_TEXT); box.setMaxLength(9);
            box.setFilter(v -> v.matches("-?\\d*"));
            box.setResponder(v -> boxesChanged());
            addRenderableWidget(box);
        }
        if (hasTarget) setBoxes();
        pageButton(246, 7, 90, 33, "FIRE CONTROL", "overview");
        pageButton(342, 7, 66, 33, "ISR", "recon");
        pageButton(414, 7, 66, 33, "BDA", "bda");
        pageButton(486, 7, 66, 33, "COMMS", "comms");
        pageButton(558, 7, 66, 33, "SYSTEM", "system");
        layerButton = button(234, 50, 94, 20, t("layer_topo"), false, () -> heightMode, this::toggleLayer);
        button(334, 50, 68, 20, Component.literal("GRID"), false, () -> showGrid, () -> showGrid = !showGrid);
        button(408, 50, 76, 20, Component.literal("RANGES"), false, () -> showRanges, () -> showRanges = !showRanges);
        button(490, 50, 90, 20, Component.literal("CONTACTS"), false, () -> showContacts, () -> showContacts = !showContacts);
        int rail = mapLeft + mapWidth + 4;
        button(rail, mapTop + 24, 18, 26, "U", () -> centreOnUnit());
        button(rail, mapTop + 57, 18, 26, "+", () -> zoom(-1));
        button(rail, mapTop + 89, 18, 26, "-", () -> zoom(1));
        button(rail, mapTop + 121, 18, 26, "@", () -> {
            if (minecraft.player != null) { centreX = minecraft.player.getX(); centreZ = minecraft.player.getZ(); mapDirty = true; }
        });
        button(rail, mapTop + 153, 18, 26, "X", () -> { hasTarget = false; xBox.setValue(""); zBox.setValue(""); });
        int by = mapTop + MAIN_H;
        launchButton = button(sx, by - 35, SIDE_W - 20, 26, t("launch"), true, () -> false, this::launch);
        cameraButton = button(sx, by - 58, 100, 18, t("camera"), false, () -> false, () -> order(Payloads.AircraftOrder.CAMERA));
        recallButton = button(sx + 108, by - 58, 100, 18, t("recall"), false, () -> false, () -> order(Payloads.AircraftOrder.RECALL));
        minusButton = button(sx + 105, by - 58, 24, 18, "-", () -> count = Math.max(1, count - 1));
        plusButton = button(sx + 184, by - 58, 24, 18, "+", () -> count = Math.min(32, count + 1));
        String[] tabs = {"OVERVIEW", "TARGETS", "WEAPONS", "ROE", "AIRSPACE", "LOGS"};
        String[] pages = {"overview", "targets", "weapons", "roe", "airspace", "logs"};
        for (int i = 0; i < tabs.length; i++) pageButton(8 + i * 91, 342, 85, 19, tabs[i], pages[i]);
        button(14, 502, 90, 20, "<< UNIT", () -> stepUnit(-1));
        button(112, 502, 90, 20, "UNIT >>", () -> stepUnit(1));
        button(728, 502, 112, 20, Component.literal("CLEAR TARGET"), true, () -> false,
                () -> { hasTarget = false; xBox.setValue(""); zBox.setValue(""); });
        button(850, 502, 96, 20, "SHUTDOWN", this::onClose);
        updateControls();
    }

    private void centreOnUnit() {
        Payloads.Entry e = current();
        if (e != null && known(e)) {
            centreX = e.aircraft() >= 0 ? e.ax() : e.pos().getX();
            centreZ = e.aircraft() >= 0 ? e.az() : e.pos().getZ();
            mapDirty = true;
        }
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
        updateControls();
    }

    private void updateControls() {
        Payloads.Entry e = current();
        boolean ok = e != null && known(e);
        layerButton.setMessage(Component.literal((heightMode ? "HEIGHT" : "MAP") + " 1:" + scale()));
        launchButton.active = ok && hasTarget && e.reachable();
        boolean airborne = ok && e.aircraft() >= 0 && e.reachable();
        launchButton.setMessage(t(airborne ? "retask" : ok && e.manual() ? "solution" : "launch"));
        cameraButton.visible = recallButton.visible = ok && e.station();
        minusButton.visible = plusButton.visible = ok && !e.station() && !e.manual();
        cameraButton.active = recallButton.active = airborne;
        xBox.setEditable(ok); zBox.setEditable(ok);
    }

    // ------------------------------------------------------------------ map

    private static final int NO_HEIGHT = Integer.MIN_VALUE;

    private void rebuildMap() {
        mapDirty = false;
        ClientLevel level = minecraft.level;
        NativeImage img = mapTexture.getPixels();
        if (level == null || img == null) return;
        int s = scale();
        int x0 = Mth.floor(centreX) - mapWidth / 2 * s, z0 = Mth.floor(centreZ) - mapHeight / 2 * s;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // heights first: the shading and contour lines need each cell's neighbours
        if (heights.length != mapWidth * mapHeight) heights = new int[mapWidth * mapHeight];
        heightMin = Integer.MAX_VALUE;
        heightMax = Integer.MIN_VALUE;
        for (int pz = 0; pz < mapHeight; pz++) {
            for (int px = 0; px < mapWidth; px++) {
                int x = x0 + px * s, z = z0 + pz * s;
                int y = NO_HEIGHT;
                if (level.hasChunk(x >> 4, z >> 4)) {
                    y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    heightMin = Math.min(heightMin, y);
                    heightMax = Math.max(heightMax, y);
                }
                heights[pz * mapWidth + px] = y;
            }
        }
        int sea = level.getSeaLevel();
        for (int pz = 0; pz < mapHeight; pz++) {
            for (int px = 0; px < mapWidth; px++) {
                int x = x0 + px * s, z = z0 + pz * s;
                int y = heights[pz * mapWidth + px];
                int colour = ReconClient.colour(x, z); // what recon drones have seen
                if (colour == 0) colour = UNKNOWN;
                if (y != NO_HEIGHT) {
                    int north = pz > 0 && heights[(pz - 1) * mapWidth + px] != NO_HEIGHT ? heights[(pz - 1) * mapWidth + px] : y;
                    if (heightMode) {
                        colour = heightColour(level, pos, x, z, y, north, px > 0 && heights[pz * mapWidth + px - 1] != NO_HEIGHT ? heights[pz * mapWidth + px - 1] : y, sea);
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
        return mx >= mapLeft && mx < mapLeft + mapWidth && my >= mapTop && my < mapTop + mapHeight;
    }

    /** Screen position of a world point on the map, or null when it is off the map. */
    private int[] onMap(double wx, double wz, int margin) {
        int px = (int) Math.floor((wx - centreX) / scale()) + mapWidth / 2;
        int pz = (int) Math.floor((wz - centreZ) / scale()) + mapHeight / 2;
        if (px < margin || pz < margin || px >= mapWidth - margin || pz >= mapHeight - margin) return null;
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
        g.fill(p[0] - 5, p[1] - 5, p[0] + 6, p[1] + 6, 0xE0000000);
        g.fill(p[0] - 5, p[1] - 5, p[0] + 6, p[1] - 4, colour);
        g.fill(p[0] - 5, p[1] + 5, p[0] + 6, p[1] + 6, colour);
        g.fill(p[0] - 5, p[1] - 5, p[0] - 4, p[1] + 6, colour);
        g.fill(p[0] + 5, p[1] - 5, p[0] + 6, p[1] + 6, colour);
        Ui.icon(g, p[0] - 3, p[1] - 3, iconFor(e), colour);
    }

    private void ring(GuiGraphics g, double wx, double wz, double radius, int colour, boolean dashed) {
        double r = radius / scale();
        if (r < 3 || r > 2000) return;
        int steps = (int) Mth.clamp(r * 3, 24, 720);
        for (int i = 0; i < steps; i++) {
            if (dashed && (i / 3) % 2 == 1) continue;
            double a = i * Math.PI * 2 / steps;
            int px = (int) Math.floor((wx - centreX) / scale() + Math.cos(a) * r) + mapWidth / 2;
            int pz = (int) Math.floor((wz - centreZ) / scale() + Math.sin(a) * r) + mapHeight / 2;
            if (px < 0 || pz < 0 || px >= mapWidth || pz >= mapHeight) continue;
            g.fill(mapLeft + px, mapTop + pz, mapLeft + px + 1, mapTop + pz + 1, colour);
        }
    }

    private void line(GuiGraphics g, double ax, double az, double bx, double bz, int colour) {
        double x0 = (ax - centreX) / scale() + mapWidth / 2.0, z0 = (az - centreZ) / scale() + mapHeight / 2.0;
        double x1 = (bx - centreX) / scale() + mapWidth / 2.0, z1 = (bz - centreZ) / scale() + mapHeight / 2.0;
        // Clip first: a target can be far beyond the chart, but drawing the visible segment must stay cheap.
        double dx = x1 - x0, dz = z1 - z0, from = 0, to = 1;
        if (dx == 0) {
            if (x0 < 0 || x0 >= mapWidth) return;
        } else {
            double enter = -x0 / dx, exit = (mapWidth - 1 - x0) / dx;
            from = Math.max(from, Math.min(enter, exit));
            to = Math.min(to, Math.max(enter, exit));
        }
        if (dz == 0) {
            if (z0 < 0 || z0 >= mapHeight) return;
        } else {
            double enter = -z0 / dz, exit = (mapHeight - 1 - z0) / dz;
            from = Math.max(from, Math.min(enter, exit));
            to = Math.min(to, Math.max(enter, exit));
        }
        if (from > to) return;
        double startX = x0 + dx * from, startZ = z0 + dz * from;
        double endX = x0 + dx * to, endZ = z0 + dz * to;
        int steps = (int) Math.max(1, Math.hypot(endX - startX, endZ - startZ));
        for (int i = 0; i <= steps; i++) {
            if ((i / 3) % 2 == 1) continue;
            int px = (int) Math.floor(Mth.lerp((double) i / steps, startX, endX)), pz = (int) Math.floor(Mth.lerp((double) i / steps, startZ, endZ));
            if (px < 0 || pz < 0 || px >= mapWidth || pz >= mapHeight) continue;
            g.fill(mapLeft + px, mapTop + pz, mapLeft + px + 1, mapTop + pz + 1, colour);
        }
    }

    /** Grid lines every round number of blocks, labelled along the top and left edges. */
    private void grid(GuiGraphics g) {
        int spanX = mapWidth * scale(), spanZ = mapHeight * scale();
        int step = 16;
        while (Math.max(spanX, spanZ) / step > 10) step *= 2;
        double x0 = centreX - mapWidth / 2.0 * scale(), z0 = centreZ - mapHeight / 2.0 * scale();
        if (showGrid) {
            for (int gx = (int) Math.ceil(x0 / step) * step; gx < x0 + spanX; gx += step) {
                int px = mapLeft + (int) ((gx - x0) / scale());
                g.fill(px, mapTop, px + 1, mapTop + mapHeight, 0x285CC1D8);
                g.drawString(font, Integer.toString(gx), px + 2, mapTop + 2, 0xFF9CAFB7, false);
            }
            for (int gz = (int) Math.ceil(z0 / step) * step; gz < z0 + spanZ; gz += step) {
                int pz = mapTop + (int) ((gz - z0) / scale());
                g.fill(mapLeft, pz, mapLeft + mapWidth, pz + 1, 0x285CC1D8);
                if (pz > mapTop + 10) g.drawString(font, Integer.toString(gz), mapLeft + 2, pz + 2, 0xFF9CAFB7, false);
            }
        }
        int bar = step / scale(), bx = mapLeft + 8, by = mapTop + mapHeight - 9;
        g.fill(bx - 3, by - 11, bx + bar + 4 + font.width(step + " m"), by + 4, 0xC000080D);
        g.fill(bx, by, bx + bar, by + 2, 0xFFD8E7E9);
        g.fill(bx, by - 3, bx + 1, by + 2, 0xFFD8E7E9);
        g.fill(bx + bar - 1, by - 3, bx + bar, by + 2, 0xFFD8E7E9);
        g.drawString(font, step + " m", bx + bar + 4, by - 6, 0xFFD8E7E9, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = (mx - viewportX) / uiScale, y = (my - viewportY) / uiScale;
        if (super.mouseClicked(x, y, button)) return true;
        if (overMap(x, y)) {
            if (button == 0) {
                targetX = Mth.floor(centreX + (x - mapLeft - mapWidth / 2.0) * scale());
                targetZ = Mth.floor(centreZ + (y - mapTop - mapHeight / 2.0) * scale());
                hasTarget = true; setBoxes(); updateControls();
            } else if (button == 1 || button == 2) panning = true;
            return true;
        }
        int lx = left + GAP, ly = mapTop + 20;
        if (button == 0 && x >= lx && x < lx + LIST_W && y >= ly && y < ly + visibleCards() * CARD_H) {
            int row = (int) ((y - ly) / CARD_H) + listScroll;
            if (row < entries.size()) { selected = row; updateControls(); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        panning = false;
        return super.mouseReleased((mx - viewportX) / uiScale, (my - viewportY) / uiScale, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            centreX -= dx / uiScale * scale(); centreZ -= dy / uiScale * scale();
            mapDirty = true; return true;
        }
        return super.mouseDragged((mx - viewportX) / uiScale, (my - viewportY) / uiScale, button, dx / uiScale, dy / uiScale);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        double x = (mx - viewportX) / uiScale, y = (my - viewportY) / uiScale;
        if (overMap(x, y)) { zoom(sy > 0 ? -1 : 1); return true; }
        if (x >= left + GAP && x < left + GAP + LIST_W && y >= mapTop && y < mapTop + MAIN_H) {
            listScroll = Mth.clamp(listScroll - (int) Math.signum(sy), 0, Math.max(0, entries.size() - visibleCards()));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    private int visibleCards() { return Math.max(1, (MAIN_H - 24) / CARD_H); }

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
        updateControls();
        renderBackground(g, mouseX, mouseY, partialTick);
        int mx = Mth.floor((mouseX - viewportX) / uiScale), my = Mth.floor((mouseY - viewportY) / uiScale);
        g.pose().pushPose();
        g.pose().translate(viewportX, viewportY, 0);
        g.pose().scale(uiScale, uiScale, 1);
        g.fill(-3, -3, w + 3, h + 3, P_BEZEL_EDGE);
        g.fill(0, 0, w, h, P_BG);
        drawTopBar(g);
        drawUnits(g);
        drawMap(g, mx, my);
        drawMission(g);
        drawDetails(g);
        drawFooter(g);
        drawLights(g);
        for (var r : renderables) r.render(g, mx, my, partialTick);
        g.pose().popPose();
    }

    private void drawTopBar(GuiGraphics g) {
        g.fillGradient(0, 0, w, 44, 0xFF141D1D, P_BG);
        g.fill(0, 43, w, 44, P_RULE);
        Ui.brackets(g, 12, 9, 27, 25, 8, P_GO);
        Ui.icon(g, 22, 17, Ui.ICON_PLANE, P_TEXT);
        g.pose().pushPose();
        g.pose().translate(50, 8, 0);
        g.pose().scale(1.65f, 1.65f, 1);
        g.drawString(font, "FIRE CONTROL", 0, 0, P_TEXT, false);
        g.pose().popPose();
        g.drawString(font, "STEEL RAIN / COMMAND SYSTEM", 50, 28, P_DIM, false);
        int reachable = 0;
        for (Payloads.Entry e : entries) if (known(e) && e.reachable()) reachable++;
        Ui.icon(g, 656, 12, Ui.ICON_SIGNAL, reachable > 0 ? P_GO : P_AMBER);
        g.drawString(font, "LINK: " + (reachable > 0 ? "ONLINE" : "STANDBY"), 672, 11, reachable > 0 ? P_GO : P_DIM, false);
        g.drawString(font, "UNITS: " + reachable + "/" + entries.size(), 672, 26, P_DIM, false);
        if (minecraft.level != null) {
            long time = (minecraft.level.getDayTime() + 6000) % 24000;
            String clock = String.format(Locale.ROOT, "%02d:%02d:%02d", time / 1000, time % 1000 * 60 / 1000, time % 1000 * 3600 / 1000 % 60);
            g.drawString(font, clock, w - 12 - font.width(clock), 10, P_TEXT, false);
        }
        String grid = minecraft.player == null ? "GRID -- --" : "GRID " + Mth.floor(minecraft.player.getX()) + " " + Mth.floor(minecraft.player.getZ());
        g.drawString(font, "CT " + contactCount + " | " + grid, w - 12 - font.width("CT " + contactCount + " | " + grid), 27, P_DIM, false);
        plate(g, 8, 49, 218, 22);
        g.drawString(font, "AO: STEEL RAIN C2", 16, 56, P_GO, false);
        String weather = minecraft.level == null ? "--" : minecraft.level.isThundering() ? "STORM" : minecraft.level.isRaining() ? "RAIN" : "CLEAR";
        String wx = "WX: " + weather + " | " + (minecraft.level != null && minecraft.level.isDay() ? "DAY" : "NIGHT");
        g.drawString(font, wx, w - 12 - font.width(wx), 56, P_DIM, false);
    }

    private void drawUnits(GuiGraphics g) {
        int x = GAP, y = mapTop;
        plate(g, x, y, LIST_W, MAIN_H);
        header(g, font, x + 3, y + 3, LIST_W - 6, t("units"), Component.literal("[" + entries.size() + "]"));
        int ly = y + 20, shown = visibleCards();
        if (entries.isEmpty()) {
            Ui.icon(g, x + 94, y + 80, Ui.ICON_SIGNAL, P_FAINT);
            g.drawString(font, Ui.upper(t("no_links")), x + 13, y + 105, P_DIM, false);
            g.drawString(font, "HOLD TABLET + RIGHT-CLICK", x + 13, y + 126, P_FAINT, false);
            g.drawString(font, "A LAUNCHER TO LINK", x + 13, y + 139, P_FAINT, false);
        }
        for (int i = listScroll; i < entries.size() && i < listScroll + shown; i++) {
            Payloads.Entry e = entries.get(i);
            int cy = ly + (i - listScroll) * CARD_H;
            boolean sel = i == selected;
            g.fill(x + 6, cy, x + LIST_W - 6, cy + CARD_H - 3, sel ? P_GO : P_RULE);
            g.fillGradient(x + 7, cy + 1, x + LIST_W - 7, cy + CARD_H - 4, sel ? 0xFF153321 : 0xFF151E1D, P_BG);
            Ui.icon(g, x + 15, cy + 10, iconFor(e), sel ? P_GO : P_TEXT);
            String name = known(e) ? Component.translatable(e.name()).getString() : t("offline").getString();
            g.drawString(font, font.plainSubstrByWidth(String.format(Locale.ROOT, "%02d  %s", i + 1, name), LIST_W - 60), x + 33, cy + 5, sel ? P_GO : P_TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(Ui.upper(status(e)).getString(), 90), x + 33, cy + 19, statusColour(e), false);
            if (known(e)) {
                String ammo = e.station() ? "AIR" : "RDS " + (e.ammo() + (e.second() ? e.ammo2() : 0));
                g.drawString(font, ammo, x + LIST_W - 18 - font.width(ammo), cy + 19, P_DIM, false);
            }
            Ui.icon(g, x + LIST_W - 18, cy + 5, Ui.ICON_SIGNAL, e.reachable() ? P_GO : P_FAINT);
        }
        if (entries.size() > shown) {
            int track = shown * CARD_H, knob = Math.max(8, track * shown / entries.size());
            int ky = ly + (track - knob) * listScroll / Math.max(1, entries.size() - shown);
            g.fill(x + LIST_W - 4, ly, x + LIST_W - 2, ly + track, P_RULE);
            g.fill(x + LIST_W - 4, ky, x + LIST_W - 2, ky + knob, P_GO);
        }
        g.drawString(font, "LINKED ASSETS / " + entries.size() + " TOTAL", x + 10, y + MAIN_H - 13, P_FAINT, false);
    }

    private int contactCount;
    private boolean showGrid = true, showRanges = true, showContacts = true;

    private void drawMap(GuiGraphics g, int mouseX, int mouseY) {
        int mx = mapLeft, my = mapTop;
        Ui.well(g, mx, my, mapWidth, mapHeight);
        // the terrain is printed in muted tones, so the coloured markers stand out from it
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.blit(MAP_TEXTURE, mx, my, 0, 0f, 0f, mapWidth, mapHeight, mapWidth, mapHeight);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        scissor(g, mx, my, mx + mapWidth, my + mapHeight);
        grid(g);
        Payloads.Entry cur = current();
        boolean curType = cur != null && known(cur);
        if (curType && showRanges) {
            double cx = cur.pos().getX() + 0.5, cz = cur.pos().getZ() + 0.5;
            ring(g, cx, cz, cur.maxRange(), 0xD0F2B33D, false);
            ring(g, cx, cz, cur.minRange(), 0xC0E5534B, true);
            if (hasTarget) line(g, cx, cz, targetX + 0.5, targetZ + 0.5, 0xE0F2B33D);
        }
        // radar sweep: a fading wedge turning once every four seconds about the selected unit, or about you
        double sx = 0, sz = 0, sr = 0;
        if (curType) { sx = cur.pos().getX() + 0.5; sz = cur.pos().getZ() + 0.5; sr = Math.max(1, cur.maxRange()); }
        else if (minecraft.player != null) { sx = minecraft.player.getX(); sz = minecraft.player.getZ(); sr = 96; }
        if (sr > 0 && showRanges) {
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
            if (showRanges) ring(g, mk.x() + 0.5, mk.z() + 0.5, mk.radius(), 0xA04FC3F7, true);
            int[] d = mk.data();
            contacts += d.length / 3;
            if (!showContacts) continue;
            for (int i = 0; i + 2 < d.length; i += 3) {
                int colour = switch (d[i + 2]) {
                    case 0 -> 0xFFFF4040;   // hostile mob
                    case 1 -> 0xFFB0B0B0;   // animal / neutral
                    case 2 -> 0xFFFF40FF;   // enemy player
                    case 3 -> 0xFF4080FF;   // friendly player
                    case 5 -> 0xFFFFF040;   // enemy jammer
                    default -> 0xFFFF9020;  // enemy weapon system
                };
                int[] marker = onMap(d[i] + 0.5, d[i + 1] + 0.5, 5);
                if (marker != null) {
                    int radius = d[i + 2] >= 4 ? 5 : 4;
                    for (int edge = -radius; edge <= radius; edge++) {
                        int dy = radius - Math.abs(edge);
                        g.fill(marker[0] + edge, marker[1] - dy, marker[0] + edge + 1, marker[1] - dy + 1, colour);
                        g.fill(marker[0] + edge, marker[1] + dy, marker[0] + edge + 1, marker[1] + dy + 1, colour);
                    }
                    g.fill(marker[0] - 1, marker[1], marker[0] + 2, marker[1] + 1, colour);
                    g.fill(marker[0], marker[1] - 1, marker[0] + 1, marker[1] + 2, colour);
                }
            }
            dot(g, mk.x() + 0.5, mk.z() + 0.5, P_CYAN, 1);
        }
        for (int i = 0; i < entries.size(); i++) {
            Payloads.Entry e = entries.get(i);
            if (!known(e)) continue;
            unitIcon(g, e, e.pos().getX() + 0.5, e.pos().getZ() + 0.5, i == selected ? P_CYAN : e.reachable() ? 0xFF4EA8D8 : P_RED);
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
            int hx = Mth.floor(centreX + (mouseX - mx - mapWidth / 2.0) * scale());
            int hz = Mth.floor(centreZ + (mouseY - my - mapHeight / 2.0) * scale());
            g.fill(mouseX, my, mouseX + 1, my + mapHeight, 0x40FFFFFF);
            g.fill(mx, mouseY, mx + mapWidth, mouseY + 1, 0x40FFFFFF);
            int hy = heights.length == mapWidth * mapHeight ? heights[(mouseY - my) * mapWidth + (mouseX - mx)] : NO_HEIGHT;
            String c = hx + " " + hz + " | Y " + (hy == NO_HEIGHT ? "--" : hy);
            double fromX = curType ? cur.pos().getX() : centreX, fromZ = curType ? cur.pos().getZ() : centreZ;
            double dx = hx - fromX, dz = hz - fromZ;
            String distance = "DIST " + (int) Math.hypot(dx, dz) + " m";
            String bearing = String.format(Locale.ROOT, "BRG %05.1f DEG", (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360);
            int bw = Math.max(font.width(c), Math.max(font.width(distance), font.width(bearing))) + 8;
            int tx = Mth.clamp(mouseX + 12, mx + 4, mx + mapWidth - bw - 4);
            int ty = Mth.clamp(mouseY + 12, my + 14, my + mapHeight - 43);
            g.fill(tx - 1, ty - 1, tx + bw + 1, ty + 39, P_RULE);
            g.fill(tx, ty, tx + bw, ty + 38, 0xED030A0C);
            g.drawString(font, c, tx + 4, ty + 4, P_GO, false);
            g.drawString(font, distance, tx + 4, ty + 16, P_TEXT, false);
            g.drawString(font, bearing, tx + 4, ty + 28, P_DIM, false);
        }
        g.disableScissor();
        // the scope's readout, top left
        contactCount = contacts;
        g.fill(mx - 1, my - 1, mx + mapWidth + 1, my, P_RULE);
        g.fill(mx - 1, my + mapHeight, mx + mapWidth + 1, my + mapHeight + 1, P_RULE);
        if (heightMode && minecraft.level != null && heightMin <= heightMax && mapWidth >= 150) heightLegend(g, mx + mapWidth - 74, my + mapHeight - 16);
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
        int x = sideLeft, y = mapTop, ix = x + 10, iw = SIDE_W - 20;
        plate(g, x, y, SIDE_W, MAIN_H);
        header(g, font, x + 3, y + 3, SIDE_W - 6, t("mission"), Component.literal("///"));
        Payloads.Entry cur = current();
        boolean ok = cur != null && known(cur);
        g.drawString(font, "MISSION", ix, y + 26, P_DIM, false);
        String mode = !ok ? "AWAITING UNIT" : cur.manual() ? "FIRE SOLUTION" : cur.station() ? "AIRCRAFT TASKING" : "PRECISION STRIKE";
        g.drawString(font, mode, ix + 64, y + 26, ok ? P_TEXT : P_FAINT, false);
        g.fill(ix, y + 42, ix + iw, y + 43, P_RULE);
        g.drawString(font, "TARGET GRID", ix, y + 49, P_DIM, false);
        int ty = y + 63;
        Ui.well(g, ix + 12, ty, 86, 20); Ui.well(g, ix + 116, ty, 86, 20);
        g.drawString(font, "X", ix, ty + 5, P_DIM, false);
        g.drawString(font, "Z", ix + 104, ty + 5, P_DIM, false);
        if (!hasTarget) g.drawString(font, "SELECT MAP OR ENTER COORDINATES", ix, y + 90, P_FAINT, false);
        else {
            int gy = groundAt(targetX, targetZ);
            g.drawString(font, "FIX " + targetX + " " + targetZ + " | Y " + (gy == NO_HEIGHT ? "--" : gy), ix, y + 90, P_GO, false);
        }
        g.fill(ix, y + 106, ix + iw, y + 107, P_RULE);
        g.drawString(font, "WEAPON", ix, y + 115, P_DIM, false);
        String name = ok ? Component.translatable(cur.name()).getString() : t("select_unit").getString();
        g.drawString(font, font.plainSubstrByWidth(name.toUpperCase(Locale.ROOT), iw), ix, y + 129, P_TEXT, false);
        if (ok) {
            Ui.icon(g, ix + 4, y + 151, iconFor(cur), P_DIM);
            field(g, font, ix + 23, y + 148, iw - 23, t("f_range"), Component.literal(cur.minRange() + " - " + cur.maxRange() + " m"), P_TEXT);
            int dist = targetDistance(cur);
            field(g, font, ix + 23, y + 162, iw - 23, t("f_distance"), Component.literal(dist < 0 ? "--" : dist + " m"),
                    dist >= cur.minRange() && dist <= cur.maxRange() ? P_GO : P_AMBER);
            field(g, font, ix, y + 179, iw, t("f_reload"), Ui.upper(status(cur)), statusColour(cur));
            if (!cur.station() && !cur.manual()) {
                g.drawString(font, Ui.upper(t("rounds")), ix, y + MAIN_H - 52, P_DIM, false);
                g.drawString(font, Integer.toString(count), ix + 148, y + MAIN_H - 52, P_TEXT, false);
            }
        } else {
            g.drawString(font, "LINK A UNIT TO PLAN A MISSION", ix, y + 159, P_FAINT, false);
        }
    }

    private int targetDistance(Payloads.Entry e) {
        return hasTarget ? (int) Math.hypot((double) targetX - e.pos().getX(), (double) targetZ - e.pos().getZ()) : -1;
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
        scissor(g, x, y, x + w, y + hgt);
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
        int top = (int) (hi + apex + 6), bottom = lo - 4;
        if (top - bottom < 20) top = bottom + 20;
        double sy = (hgt - 4) / (double) (top - bottom);
        for (int i = 0; i < n; i++) {
            if (ground[i] == NO_HEIGHT) continue;
            int py = y + hgt - 2 - (int) ((ground[i] - bottom) * sy);
            g.fill(x + i, py, x + i + 1, y + hgt, i % 2 == 0 ? 0xFF3C4A32 : 0xFF343F2C);
            g.fill(x + i, py, x + i + 1, py + 1, 0xFF8FA06A);
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

    /** Scissors are in GUI coordinates; GuiGraphics 1.21 does not apply the pose to them. */
    private void scissor(GuiGraphics g, int x, int y, int right, int bottom) {
        g.enableScissor(viewportX + Mth.floor(x * uiScale), viewportY + Mth.floor(y * uiScale),
                viewportX + Mth.ceil(right * uiScale), viewportY + Mth.ceil(bottom * uiScale));
    }

    private void detailHeader(GuiGraphics g, int x, int width, String title, String tag) {
        plate(g, x, DETAIL_Y, width, DETAIL_H);
        header(g, font, x + 3, DETAIL_Y + 3, width - 6, Component.literal(title), Component.literal(tag));
    }

    private void detailField(GuiGraphics g, int x, int y, int width, String label, String value, int colour) {
        field(g, font, x, y, width, Component.literal(label), Component.literal(value), colour);
    }

    private void drawDetails(GuiGraphics g) {
        if (!detailPage.equals("overview")) {
            drawDetailPage(g);
            return;
        }
        int y = DETAIL_Y + 24;
        detailHeader(g, 8, 232, "AO OVERVIEW", "LIVE");
        g.blit(MAP_TEXTURE, 17, y, 84, 76, 0f, 0f, mapWidth, mapHeight, mapWidth, mapHeight);
        Ui.brackets(g, 17, y, 84, 76, 5, P_DIM);
        if (hasTarget) {
            double px = (targetX - centreX) / scale() + mapWidth / 2.0;
            double pz = (targetZ - centreZ) / scale() + mapHeight / 2.0;
            if (px >= 0 && pz >= 0 && px < mapWidth && pz < mapHeight)
                Ui.brackets(g, 17 + (int) (px * 84 / mapWidth) - 3, y + (int) (pz * 76 / mapHeight) - 3, 7, 7, 3, P_RED);
        }
        detailField(g, 109, y, 121, "GRID X", hasTarget ? "" + targetX : "--", P_TEXT);
        detailField(g, 109, y + 15, 121, "GRID Z", hasTarget ? "" + targetZ : "--", P_TEXT);
        int ground = hasTarget ? groundAt(targetX, targetZ) : NO_HEIGHT;
        detailField(g, 109, y + 30, 121, "ELEV", ground == NO_HEIGHT ? "--" : ground + " m", P_TEXT);
        detailField(g, 109, y + 45, 121, "SPAN", mapWidth * scale() + " m", P_DIM);
        detailField(g, 109, y + 60, 121, "CONTACTS", "" + contactCount, contactCount > 0 ? P_AMBER : P_GO);
        g.drawString(font, "LOADED + RECON TERRAIN", 17, DETAIL_Y + 107, P_FAINT, false);

        Payloads.Entry cur = current();
        boolean ok = cur != null && known(cur);
        detailHeader(g, 248, 232, "FIRE SOLUTION", hasTarget ? "FIX" : "NO FIX");
        if (ok && hasTarget) {
            int dist = targetDistance(cur);
            int bearing = (int) Math.round((Math.toDegrees(Math.atan2((double) targetX - cur.pos().getX(), (double) cur.pos().getZ() - targetZ)) + 360) % 360);
            detailField(g, 258, y, 212, "DIST", dist + " m", P_TEXT);
            detailField(g, 258, y + 14, 212, "BRG", String.format(Locale.ROOT, "%03d DEG / %s", bearing, compassPoint(bearing)), P_TEXT);
            MissileProfile prof = profileFor(cur);
            profile(g, 258, y + 30, 212, 48, cur, prof, dist);
            boolean inRange = dist >= cur.minRange() && dist <= cur.maxRange();
            g.drawString(font, Ui.upper(t(inRange ? "in_range" : "out_of_range")), 258, DETAIL_Y + 107, inRange ? P_GO : P_RED, false);
        } else {
            Ui.brackets(g, 318, y + 9, 80, 47, 12, P_FAINT);
            g.drawString(font, "AWAITING TARGET FIX", 258, DETAIL_Y + 93, P_DIM, false);
            g.drawString(font, "CLICK MAP TO DESIGNATE", 258, DETAIL_Y + 107, P_FAINT, false);
        }

        detailHeader(g, 488, 232, "RECON / CAMERA", "ISR");
        int reports = 0;
        for (ReconClient.Report ignored : ReconClient.fresh()) reports++;
        Ui.brackets(g, 499, y, 210, 73, 12, reports > 0 ? P_DIM : P_FAINT);
        Ui.icon(g, 599, y + 13, Ui.ICON_DRONE, reports > 0 ? P_CYAN : P_FAINT);
        String state = reports > 0 ? reports + " LIVE RECON REPORT(S)" : "NO RECON REPORTS";
        g.drawString(font, state, 604 - font.width(state) / 2, y + 35, reports > 0 ? P_CYAN : P_DIM, false);
        String hint = ok && cur.station() && cur.aircraft() >= 0 ? "CAMERA OPENS AIRCRAFT VIEW" : "SELECT AN AIRBORNE STATION";
        g.drawString(font, hint, 604 - font.width(hint) / 2, y + 53, P_FAINT, false);
        g.drawString(font, "CONTACTS " + contactCount + " | REPORT TTL 3 s", 499, DETAIL_Y + 107, P_DIM, false);

        detailHeader(g, 728, 224, "PLATFORM", ok ? String.format(Locale.ROOT, "%02d", selected + 1) : "--");
        if (ok) {
            String name = Component.translatable(cur.name()).getString();
            g.drawString(font, font.plainSubstrByWidth(name.toUpperCase(Locale.ROOT), 202), 738, y, P_TEXT, false);
            detailField(g, 738, y + 16, 204, "STATE", Ui.upper(status(cur)).getString(), statusColour(cur));
            detailField(g, 738, y + 30, 204, "GRID", (cur.aircraft() >= 0 ? cur.ax() : cur.pos().getX()) + " " + (cur.aircraft() >= 0 ? cur.az() : cur.pos().getZ()), P_TEXT);
            detailField(g, 738, y + 44, 204, "ROUNDS", "" + (cur.ammo() + (cur.second() ? cur.ammo2() : 0)), P_TEXT);
            detailField(g, 738, y + 58, 204, "ENERGY", cur.fuel() + " k", cur.fuel() > 200 ? P_GO : P_AMBER);
            Ui.gauge(g, 738, y + 76, 204, 4, cur.fuel() / 1000.0, P_GO, 25);
            g.drawString(font, "DATALINK: " + (cur.reachable() ? "CONNECTED" : "LOST"), 738, DETAIL_Y + 107, cur.reachable() ? P_GO : P_RED, false);
        } else {
            Ui.icon(g, 828, y + 19, Ui.ICON_PLANE, P_FAINT);
            g.drawString(font, "NO UNIT SELECTED", 738, y + 55, P_DIM, false);
            g.drawString(font, "AWAITING DATALINK", 738, DETAIL_Y + 107, P_FAINT, false);
        }
    }

    private void drawDetailPage(GuiGraphics g) {
        String title = switch (detailPage) {
            case "targets", "recon" -> "RECON CONTACTS";
            case "bda" -> "BATTLE DAMAGE ASSESSMENT";
            case "weapons" -> "LINKED WEAPONS / AMMUNITION";
            case "roe" -> "ENGAGEMENT / OPERATOR NOTES";
            case "airspace" -> "AIRSPACE / AIRCRAFT TELEMETRY";
            case "logs" -> "EVENT LOG";
            case "comms" -> "COMMUNICATIONS / UNIT DATALINKS";
            default -> "SYSTEM / DISPLAY STATUS";
        };
        detailHeader(g, 8, w - 16, title, detailPage.equals("logs") ? LOG.size() + " EVENTS" : "STEEL RAIN C2");
        int y = DETAIL_Y + 24;
        if (detailPage.equals("logs")) {
            String[] lines = LOG.toArray(new String[0]);
            if (lines.length == 0) g.drawString(font, "NO RECENT EVENTS. FIRE ORDERS AND SERVER MESSAGES APPEAR HERE.", 20, y, P_DIM, false);
            for (int i = 0; i < Math.min(6, lines.length); i++)
                g.drawString(font, font.plainSubstrByWidth(lines[lines.length - 1 - i], w - 42), 20, y + i * 14, i == 0 ? P_TEXT : P_DIM, false);
        } else if (detailPage.equals("targets") || detailPage.equals("recon")) {
            int index = 0;
            for (ReconClient.Report report : ReconClient.fresh()) {
                int[] data = report.markers().data();
                for (int i = 0; i + 2 < data.length && index < 15; i += 3) {
                    String kind = switch (data[i + 2]) {
                        case 0 -> "HOSTILE MOB"; case 1 -> "NEUTRAL"; case 2 -> "ENEMY PLAYER";
                        case 3 -> "FRIENDLY"; case 5 -> "JAMMER"; default -> "WEAPON SYSTEM";
                    };
                    int x = 20 + index / 5 * 310, row = y + index % 5 * 16;
                    g.fill(x, row + 2, x + 4, row + 6, data[i + 2] == 3 ? P_CYAN : P_AMBER);
                    String text = kind + " | " + data[i] + " " + data[i + 1];
                    g.drawString(font, font.plainSubstrByWidth(text, 294), x + 9, row, P_TEXT, false);
                    index++;
                }
            }
            if (index == 0) {
                g.drawString(font, "NO FRESH CONTACTS. LAUNCH A RECON AIRCRAFT TO SURVEY THE AREA.", 20, y + 8, P_DIM, false);
                g.drawString(font, "REPORTS EXPIRE AFTER 3 SECONDS. REMEMBERED TERRAIN REMAINS ON THE MAP.", 20, y + 28, P_FAINT, false);
            }
        } else if (detailPage.equals("weapons") || detailPage.equals("comms") || detailPage.equals("airspace")) {
            int index = 0;
            for (Payloads.Entry e : entries) {
                if (detailPage.equals("airspace") && e.aircraft() < 0) continue;
                int x = 20 + index / 4 * 468, row = y + index % 4 * 21;
                String name = known(e) ? Component.translatable(e.name()).getString() : "OFFLINE UNIT";
                String value = detailPage.equals("weapons") ? "RDS " + (e.ammo() + (e.second() ? e.ammo2() : 0)) + " | " + e.minRange() + "-" + e.maxRange() + " m"
                        : detailPage.equals("airspace") ? "GRID " + e.ax() + " " + e.az() : e.pos().getX() + " " + e.pos().getZ() + " | " + (e.reachable() ? "LINK OK" : "NO SIGNAL");
                g.drawString(font, font.plainSubstrByWidth(name, 200), x, row, P_TEXT, false);
                g.drawString(font, font.plainSubstrByWidth(value, 238), x + 210, row, statusColour(e), false);
                index++;
                if (index >= 8) break;
            }
            if (index == 0) g.drawString(font, detailPage.equals("airspace") ? "NO LINKED AIRCRAFT IN THE AIR." : "NO LINKED UNITS.", 20, y + 8, P_DIM, false);
        } else if (detailPage.equals("roe")) {
            g.drawString(font, "1  DESIGNATE A TARGET USING THE MAP OR THE X / Z COORDINATE FIELDS.", 20, y, P_TEXT, false);
            g.drawString(font, "2  VERIFY RANGE, AMMUNITION, LINK AND LINE OF FIRE BEFORE ISSUING AN ORDER.", 20, y + 19, P_TEXT, false);
            g.drawString(font, "3  MANUAL GUNS RECEIVE A SOLUTION; THE OPERATOR FIRES THE WEAPON.", 20, y + 38, P_DIM, false);
            g.drawString(font, "CLEAR TARGET REMOVES THE DESIGNATION. IT DOES NOT CANCEL A WEAPON ALREADY IN FLIGHT.", 20, y + 63, P_AMBER, false);
        } else if (detailPage.equals("bda")) {
            g.drawString(font, "NO DAMAGE ASSESSMENT TELEMETRY AVAILABLE.", 20, y + 8, P_DIM, false);
            g.drawString(font, "USE AN AIRBORNE STATION'S CAMERA TO INSPECT THE TARGET AREA AFTER IMPACT.", 20, y + 29, P_TEXT, false);
            g.drawString(font, "THE RECON CONTACTS TAB SHOWS CURRENT DETECTIONS, NOT CONFIRMED DAMAGE.", 20, y + 50, P_FAINT, false);
        } else {
            detailField(g, 20, y, 278, "DISPLAY", "ONLINE", P_GO);
            detailField(g, 20, y + 20, 278, "MAP", mapWidth + " x " + mapHeight, P_TEXT);
            detailField(g, 20, y + 40, 278, "SCALE", "1:" + scale(), P_TEXT);
            detailField(g, 336, y, 278, "LAYER", heightMode ? "ELEVATION" : "TERRAIN", P_TEXT);
            detailField(g, 336, y + 20, 278, "REFRESH", "1 s / TERRAIN 2 s", P_DIM);
            detailField(g, 336, y + 40, 278, "CONTACT TTL", "3 s", P_DIM);
            g.drawString(font, "MAP: CLICK TO DESIGNATE", 650, y, P_TEXT, false);
            g.drawString(font, "RIGHT-DRAG TO PAN / SCROLL TO ZOOM", 650, y + 20, P_DIM, false);
            g.drawString(font, "H: TOGGLE TERRAIN / ELEVATION", 650, y + 40, P_DIM, false);
            g.drawString(font, "ESC: CLOSE DISPLAY", 650, y + 60, P_DIM, false);
        }
    }

    private void drawFooter(GuiGraphics g) {
        g.fill(8, 365, w - 8, 366, P_RULE);
        int reachable = 0, rounds = 0;
        for (Payloads.Entry e : entries) if (known(e)) {
            if (e.reachable()) reachable++;
            if (!e.station()) rounds += e.ammo() + (e.second() ? e.ammo2() : 0);
        }
        int ping = 0;
        if (minecraft.getConnection() != null && minecraft.player != null) {
            var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
            if (info != null) ping = info.getLatency();
        }
        String status = "LINK " + reachable + "/" + entries.size() + " | RDS " + rounds + " | CT " + contactCount + " | PING " + ping + " ms";
        g.drawString(font, status, w - 12 - font.width(status), 348, P_GO, false);
        if (messageTicks > 0 && !message.getString().isEmpty()) {
            g.fill(8, 477, w - 8, 491, 0xF01B2118);
            g.drawString(font, font.plainSubstrByWidth(message.getString(), w - 32), 16, 480, P_AMBER, false);
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
