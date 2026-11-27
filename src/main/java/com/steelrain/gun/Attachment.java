package com.steelrain.gun;

/** Something that can be fitted to a gun: a sight on the rail, a device on the muzzle or a grip under the handguard. */
public enum Attachment {
    RED_DOT("red_dot", Slot.OPTIC, 1f, new int[]{100, 200, 300}),
    ACOG("acog", Slot.OPTIC, 4f, new int[]{100}),
    PSO1("pso1", Slot.OPTIC, 4f, new int[]{100, 200, 300, 400, 500, 600, 700, 800, 900, 1000}),
    SUPPRESSOR("suppressor", Slot.MUZZLE, 1f, null),
    PISTOL_SUPPRESSOR("pistol_suppressor", Slot.MUZZLE, 1f, null),
    FOREGRIP("foregrip", Slot.UNDER, 1f, null),
    SCOPE10("scope_10x", Slot.OPTIC, 10f, new int[]{100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700, 1800});

    public enum Slot { OPTIC, MUZZLE, UNDER }

    public final String id;
    public final Slot slot;
    /** Magnification of an optic; above 1 the firer looks through it instead of over the gun. */
    public final float zoom;
    /** The ranges an optic can be zeroed to, or null if it has no adjustment of its own. */
    public final int[] zeros;

    Attachment(String id, Slot slot, float zoom, int[] zeros) {
        this.id = id; this.slot = slot; this.zoom = zoom; this.zeros = zeros;
    }

    public static final Attachment[] VALUES = values();
}
