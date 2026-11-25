package com.steelrain.util;

import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.UUID;

public final class Teams {
    /** Same owner, or both owners on the same scoreboard team. Unowned things are friends with nobody. */
    public static boolean friendly(Level level, @Nullable UUID a, @Nullable String aName, @Nullable UUID b, @Nullable String bName) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        if (aName == null || bName == null) return false;
        PlayerTeam ta = level.getScoreboard().getPlayersTeam(aName);
        PlayerTeam tb = level.getScoreboard().getPlayersTeam(bName);
        return ta != null && ta == tb;
    }

    private Teams() {}
}
