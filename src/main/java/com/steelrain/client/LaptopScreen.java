package com.steelrain.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.steelrain.ModRegistry;
import com.steelrain.net.Payloads;
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

import java.util.List;

/**
 * Field Laptop fire control, laid out like a rugged military tablet: linked units down the left with their state at a
 * glance, a live map with a grid, range rings and the line of fire in the middle, the fire mission on the right.
 */
public class LaptopScreen extends Screen {
    private static final ResourceLocation MAP_TEXTURE = ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "laptop_map");
    private static final int[] SCALES = {1, 2, 4, 8, 16, 32, 64};
    private static final int UNKNOWN = 0xFF181410;
    private static final int LIST_W = 120, SIDE_W = 132, GAP = 5, CARD_H = 28, TOP_BAR = 14, FOOT = 13;

    private List<Payloads.Entry> entries;
    private int selected, listScroll;
    private Component message = Component.empty();
    private int messageTicks;

    private double centreX, centreZ;
    private int scaleIndex = 1;
    private boolean hasTarget;
    private int targetX, targetZ;
    private int count = 1;

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
        });
    }

    private int scale() { return SCALES[scaleIndex]; }

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
        h = Math.min(height - 12, 270);
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
            box.setTextColor(Ui.TEXT);
            box.setMaxLength(9);
            box.setFilter(s -> s.matches("-?\\d*"));
            box.setResponder(s -> boxesChanged());
            addRenderableWidget(box);
        }
        if (hasTarget) setBoxes();
        int by = top + h - FOOT - GAP - 1;
        launchButton = addRenderableWidget(new Ui.TabletButton(sx, by - 22, SIDE_W - 12, 20, Component.translatable("gui.steelrain.launch"), true, this::launch));
        cameraButton = addRenderableWidget(new Ui.TabletButton(sx, by - 40, (SIDE_W - 14) / 2, 14, Component.translatable("gui.steelrain.camera"), false,
                () -> order(Payloads.AircraftOrder.CAMERA)));
        recallButton = addRenderableWidget(new Ui.TabletButton(sx + (SIDE_W - 14) / 2 + 2, by - 40, (SIDE_W - 14) / 2, 14, Component.translatable("gui.steelrain.recall"), false,
                () -> order(Payloads.AircraftOrder.RECALL)));
        minusButton = addRenderableWidget(new Ui.TabletButton(sx + 52, by - 40, 14, 14, Component.literal("-"), false, () -> count = Math.max(1, count - 1)));
        plusButton = addRenderableWidget(new Ui.TabletButton(sx + SIDE_W - 26, by - 40, 14, 14, Component.literal("+"), false, () -> count = Math.min(32, count + 1)));
        int zy = mapTop + map - 14;
        addRenderableWidget(new Ui.TabletButton(mapLeft + map + 3, zy - 32, 13, 13, Component.literal("+"), false, () -> zoom(-1)));
        addRenderableWidget(new Ui.TabletButton(mapLeft + map + 3, zy - 17, 13, 13, Component.literal("-"), false, () -> zoom(1)));
        addRenderableWidget(new Ui.TabletButton(mapLeft + map + 3, zy - 2, 13, 13, Component.literal("◎"), false, () -> {
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
        launchButton.active = ok && hasTarget && e.reachable();
        boolean airborne = e != null && e.aircraft() >= 0 && e.reachable();
        launchButton.setMessage(Component.translatable(airborne ? "gui.steelrain.retask" : ok && e.manual() ? "gui.steelrain.solution" : "gui.steelrain.launch"));
        cameraButton.visible = recallButton.visible = ok && e.station();
        minusButton.visible = plusButton.visible = ok && !e.station() && !e.manual();
        cameraButton.active = recallButton.active = airborne;
    }

    // ------------------------------------------------------------------ map

    private void rebuildMap() {
        mapDirty = false;
        ClientLevel level = minecraft.level;
        NativeImage img = mapTexture.getPixels();
        if (level == null || img == null) return;
        int s = scale();
        int x0 = Mth.floor(centreX) - map / 2 * s, z0 = Mth.floor(centreZ) - map / 2 * s;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int pz = 0; pz < map; pz++) {
            for (int px = 0; px < map; px++) {
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
                // a touch darker and cooler, so the overlays read clearly on top
                int r = colour & 0xFF, gg = (colour >> 8) & 0xFF, bb = (colour >> 16) & 0xFF;
                r = r * 78 / 100; gg = gg * 82 / 100; bb = Math.min(255, bb * 88 / 100 + 6);
                img.setPixelRGBA(px, pz, 0xFF000000 | bb << 16 | gg << 8 | r);
            }
        }
        mapTexture.upload();
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
        g.fill(bx, by, bx + bar, by + 2, Ui.TEXT);
        g.fill(bx, by - 3, bx + 1, by + 2, Ui.TEXT); g.fill(bx + bar - 1, by - 3, bx + bar, by + 2, Ui.TEXT);
        g.drawString(font, step + " m", bx + bar + 4, by - 6, Ui.TEXT, false);
        // north arrow, top right
        int nx = mapLeft + map - 10, ny = mapTop + 6;
        g.fill(nx - 6, ny - 3, nx + 7, ny + 17, 0xA0000000);
        Ui.icon(g, nx - 3, ny, new String[]{"   #   ", "  ###  ", " ##### ", "#######", "   #   ", "   #   "}, Ui.AMBER);
        g.drawString(font, "N", nx - 2, ny + 8, Ui.TEXT, false);
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
        if (!known(e) || !e.reachable()) return Ui.RED;
        if (e.aircraft() >= 0) return Ui.CYAN;
        if (e.busy() > 0 || (e.ammo() <= 0 && !e.manual())) return Ui.AMBER;
        return Ui.GREEN;
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
        g.fill(left - 6, top - 6, left + w + 6, top + h + 6, 0xFF0B0D0C);
        g.fill(left - 5, top - 5, left + w + 5, top + h + 5, 0xFF2A2E26);
        g.fill(left - 2, top - 2, left + w + 2, top + h + 2, 0xFF050607);
        g.fill(left, top, left + w, top + h, Ui.BG);
        drawTopBar(g);
        drawUnits(g);
        drawMap(g, mouseX, mouseY);
        drawMission(g);
        drawFooter(g);
        for (var r : renderables) r.render(g, mouseX, mouseY, partialTick);
        if (anim) {
            g.fill(left, top, left + w, top + h, ((int) ((1 - ease) * 200) << 24) | 0x0A0E10);
            g.pose().popPose();
        }
    }

    private void drawTopBar(GuiGraphics g) {
        g.fill(left, top, left + w, top + TOP_BAR, 0xFF0D1214);
        g.fill(left, top + TOP_BAR - 1, left + w, top + TOP_BAR, Ui.OLIVE);
        Ui.icon(g, left + 5, top + 3, Ui.ICON_SIGNAL, Ui.GREEN);
        g.drawString(font, Ui.upper(Component.translatable("gui.steelrain.laptop_title")), left + 16, top + 3, Ui.TEXT, false);
        g.drawString(font, "STEEL RAIN C2", left + 16 + font.width(Ui.upper(Component.translatable("gui.steelrain.laptop_title"))) + 8, top + 3, Ui.FAINT, false);
        if (minecraft.player != null && minecraft.level != null) {
            long t = (minecraft.level.getDayTime() + 6000) % 24000;
            String clock = String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
            String own = "GRID " + Mth.floor(minecraft.player.getX()) + " " + Mth.floor(minecraft.player.getZ());
            g.drawString(font, clock, left + w - 6 - font.width(clock), top + 3, Ui.AMBER, false);
            g.drawString(font, own, left + w - 14 - font.width(clock) - font.width(own), top + 3, Ui.DIM, false);
        }
    }

    private void drawUnits(GuiGraphics g) {
        int x = left + GAP, y = mapTop;
        Ui.panel(g, x, y - 1, LIST_W, map + 2);
        Ui.header(g, font, x + 1, y, LIST_W - 2, Component.translatable("gui.steelrain.units"), Component.literal(Integer.toString(entries.size())));
        int ly = y + 14;
        if (entries.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("gui.steelrain.no_links"), x + 5, ly + 4, LIST_W - 10, Ui.DIM);
            return;
        }
        int shown = visibleCards();
        for (int i = listScroll; i < entries.size() && i < listScroll + shown; i++) {
            Payloads.Entry e = entries.get(i);
            int cy = ly + (i - listScroll) * CARD_H;
            boolean sel = i == selected;
            g.fill(x + 3, cy + 1, x + LIST_W - 3, cy + CARD_H - 1, sel ? Ui.PANEL_3 : Ui.PANEL_2);
            if (sel) g.fill(x + 3, cy + 1, x + 5, cy + CARD_H - 1, Ui.AMBER);
            int colour = statusColour(e);
            Ui.icon(g, x + 8, cy + 4, iconFor(e), sel ? Ui.AMBER : Ui.TEXT);
            Component name = known(e) ? Component.translatable(e.name()) : Component.translatable("gui.steelrain.offline");
            g.drawString(font, font.plainSubstrByWidth(name.getString(), LIST_W - 28), x + 18, cy + 4, sel ? Ui.TEXT : 0xFFC8D2D8, false);
            // second line: status dot + text, then rounds and energy gauges
            g.fill(x + 8, cy + 16, x + 12, cy + 20, colour);
            String st = status(e).getString();
            g.drawString(font, font.plainSubstrByWidth(st, 50), x + 15, cy + 14, colour, false);
            if (known(e)) {
                String rounds = e.ammo() + (e.second() ? "+" + e.ammo2() : "");
                g.drawString(font, rounds, x + LIST_W - 8 - font.width(rounds), cy + 14, Ui.DIM, false);
                double energy = e.fuel() <= 0 ? 0 : Math.min(1, e.fuel() / 1000.0);
                Ui.gauge(g, x + 8, cy + CARD_H - 5, LIST_W - 19, 2, energy, Ui.CYAN, 20);
            }
        }
        if (entries.size() > shown) {
            int track = map - 16, knob = Math.max(8, track * shown / entries.size());
            int ky = ly + (track - knob) * listScroll / Math.max(1, entries.size() - shown);
            g.fill(x + LIST_W - 3, ly, x + LIST_W - 2, ly + track, 0xFF2A353C);
            g.fill(x + LIST_W - 3, ky, x + LIST_W - 2, ky + knob, Ui.AMBER);
        }
    }

    private void drawMap(GuiGraphics g, int mouseX, int mouseY) {
        int mx = mapLeft, my = mapTop;
        Ui.well(g, mx, my, map, map);
        g.blit(MAP_TEXTURE, mx, my, 0, 0f, 0f, map, map, map, map);
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
        // recon: what the drone can see and its contacts
        for (ReconClient.Report report : ReconClient.fresh()) {
            Payloads.Markers mk = report.markers();
            ring(g, mk.x() + 0.5, mk.z() + 0.5, mk.radius(), 0xA04FC3F7, true);
            int[] d = mk.data();
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
            dot(g, mk.x() + 0.5, mk.z() + 0.5, Ui.CYAN, 1);
        }
        for (int i = 0; i < entries.size(); i++) {
            Payloads.Entry e = entries.get(i);
            if (!known(e)) continue;
            unitIcon(g, e, e.pos().getX() + 0.5, e.pos().getZ() + 0.5, i == selected ? Ui.AMBER : e.reachable() ? Ui.GREEN : Ui.RED);
            if (e.aircraft() >= 0) unitIcon(g, e, e.ax() + 0.5, e.az() + 0.5, Ui.CYAN);
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
                Ui.brackets(g, p[0] - 6, p[1] - 6, 13, 13, 4, Ui.RED);
                g.fill(p[0] - 1, p[1], p[0] + 2, p[1] + 1, blink ? Ui.RED : 0xFFFFFFFF);
                g.fill(p[0], p[1] - 1, p[0] + 1, p[1] + 2, blink ? Ui.RED : 0xFFFFFFFF);
            }
        }
        if (overMap(mouseX, mouseY)) {
            int hx = Mth.floor(centreX + (mouseX - mx - map / 2.0) * scale());
            int hz = Mth.floor(centreZ + (mouseY - my - map / 2.0) * scale());
            g.fill(mouseX, my, mouseX + 1, my + map, 0x40FFFFFF);
            g.fill(mx, mouseY, mx + map, mouseY + 1, 0x40FFFFFF);
            String c = hx + " " + hz;
            int tx = Math.min(mouseX + 6, mx + map - font.width(c) - 4), ty = Math.max(mouseY - 12, my + 2);
            g.fill(tx - 2, ty - 2, tx + font.width(c) + 2, ty + 9, 0xD0000000);
            g.drawString(font, c, tx, ty, Ui.TEXT, false);
        }
        g.disableScissor();
        Ui.brackets(g, mx - 2, my - 2, map + 4, map + 4, 8, Ui.OLIVE_LIGHT);
        String zoom = "1:" + scale();
        g.drawString(font, zoom, mx + map + 3, mapTop + map - 60, Ui.DIM, false);
    }

    private void drawMission(GuiGraphics g) {
        int x = sideLeft, y = mapTop;
        Ui.panel(g, x, y - 1, SIDE_W, h - TOP_BAR - FOOT - GAP * 2);
        Payloads.Entry cur = current();
        boolean ok = cur != null && known(cur);
        Ui.header(g, font, x + 1, y, SIDE_W - 2, Component.translatable("gui.steelrain.mission"), null);
        int ix = x + 6, iw = SIDE_W - 12;
        if (!ok) {
            g.drawWordWrap(font, Component.translatable("gui.steelrain.select_unit"), ix, y + 18, iw, Ui.DIM);
            return;
        }
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(cur.name()).getString(), iw), ix, y + 16, Ui.TEXT, false);
        Ui.chip(g, font, ix, y + 27, status(cur), statusColour(cur));
        Ui.field(g, font, ix, y + 41, iw, Component.translatable("gui.steelrain.f_range"),
                Component.literal(cur.minRange() + "-" + cur.maxRange() + " m"), Ui.TEXT);
        Ui.field(g, font, ix, y + 52, iw, Component.translatable("gui.steelrain.f_rounds"),
                Component.literal(cur.ammo() + (cur.second() ? " + " + cur.ammo2() : "")), cur.ammo() > 0 || cur.manual() ? Ui.TEXT : Ui.RED);
        // target entry
        int ty = y + 70;
        g.drawString(font, "X", ix, ty + 5, Ui.DIM, false);
        Ui.well(g, ix + 10, ty + 2, 50, 12);
        g.drawString(font, "Z", ix + 64, ty + 5, Ui.DIM, false);
        Ui.well(g, ix + 72, ty + 2, 50, 12);
        if (hasTarget) {
            double dx = targetX + 0.5 - (cur.pos().getX() + 0.5), dz = targetZ + 0.5 - (cur.pos().getZ() + 0.5);
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            int bearing = (int) Math.round((Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360);
            boolean inRange = dist >= cur.minRange() && dist <= cur.maxRange();
            Ui.field(g, font, ix, ty + 20, iw, Component.translatable("gui.steelrain.f_distance"), Component.literal(dist + " m"), inRange ? Ui.TEXT : Ui.RED);
            Ui.field(g, font, ix, ty + 31, iw, Component.translatable("gui.steelrain.f_bearing"), Component.literal(String.format("%03d°", bearing)), Ui.TEXT);
            Ui.chip(g, font, ix, ty + 43, Component.translatable(inRange ? "gui.steelrain.in_range" : dist < cur.minRange() ? "gui.steelrain.too_close_short" : "gui.steelrain.out_of_range"),
                    inRange ? Ui.GREEN : Ui.RED);
        } else {
            g.drawWordWrap(font, Component.translatable("gui.steelrain.pick_target"), ix, ty + 20, iw, Ui.AMBER);
        }
        if (!cur.station() && !cur.manual()) {
            int by = top + h - FOOT - GAP - 1 - 40;
            g.drawString(font, Ui.upper(Component.translatable("gui.steelrain.rounds")), ix, by + 3, Ui.DIM, false);
            String n = Integer.toString(count);
            int cx = (ix + 52 + 14 + ix + iw - 26) / 2;
            g.drawString(font, n, cx - font.width(n) / 2, by + 3, Ui.AMBER, false);
        }
    }

    private void drawFooter(GuiGraphics g) {
        int y = top + h - FOOT;
        g.fill(left, y, left + w, top + h, 0xFF0D1214);
        g.fill(left, y, left + w, y + 1, Ui.EDGE);
        if (messageTicks > 0) {
            Ui.icon(g, left + 5, y + 3, Ui.ICON_WARN, Ui.AMBER);
            g.drawString(font, message, left + 16, y + 3, Ui.AMBER, false);
        } else {
            g.drawString(font, Component.translatable("gui.steelrain.map_hint"), left + 6, y + 3, Ui.FAINT, false);
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
        LaptopHold.closed();
    }
}
