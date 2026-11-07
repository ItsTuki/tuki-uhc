package cl.itstuki.uhc.hook;

import cl.itstuki.uhc.TukiUHC;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Puente con PlaceholderAPI. Solo se carga si PlaceholderAPI está instalado. */
final class PlaceholderApiBridge {
    private final UhcExpansion expansion;

    PlaceholderApiBridge(TukiUHC plugin) {
        this.expansion = new UhcExpansion(plugin);
        this.expansion.register();
    }

    String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    void unregister() {
        expansion.unregister();
    }
}
