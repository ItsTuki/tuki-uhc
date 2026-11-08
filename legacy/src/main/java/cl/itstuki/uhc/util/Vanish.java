package cl.itstuki.uhc.util;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/** Jugadores en vanish (TukiCore, EssentialsX, SuperVanish… ponen el metadata "vanished"). */
public final class Vanish {
    private Vanish() {
    }

    @SuppressWarnings("deprecation")
    public static boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
