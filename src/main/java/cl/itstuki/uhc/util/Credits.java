package cl.itstuki.uhc.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.Plugin;

/** Banner de consola y créditos. */
public final class Credits {
    public static final String AUTHOR = "ItsTuki";
    public static final String REPOSITORY = "https://github.com/ItsTuki/tuki-uhc";
    private static final String TITLE = "<gradient:#FF5555:#FFAA00><bold>TukiUHC</bold></gradient>";
    private static final String AUTHOR_TAG = "<gradient:#55FFFF:#5555FF><bold>" + AUTHOR + "</bold></gradient>";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Credits() {
    }

    public static Component helpLine() {
        return MM.deserialize("<dark_gray>» </dark_gray><gray>Creado por </gray><click:open_url:'" + REPOSITORY
                + "'><hover:show_text:'<gray>Ver en GitHub'>" + AUTHOR_TAG + "</hover></click>");
    }

    public static void enableBanner(Plugin plugin) {
        send(plugin, List.of(
                "",
                "<dark_gray>  ======================================",
                "  " + TITLE + " <gray>v<version>",
                "  <gray>Creado por " + AUTHOR_TAG,
                "  <gray>Servidor: <white><server>",
                "  <green>Plugin activado. Usa /uhcconfig para configurar la partida.",
                "<dark_gray>  ======================================",
                ""));
    }

    public static void disableBanner(Plugin plugin) {
        send(plugin, List.of("", "  " + TITLE + " <gray>v<version> <red>desactivado.",
                "  <gray>Gracias por jugar. Creado por " + AUTHOR_TAG, ""));
    }

    private static void send(Plugin plugin, List<String> lines) {
        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()),
                Placeholder.unparsed("server", plugin.getServer().getName() + " " + plugin.getServer().getMinecraftVersion()));
        for (String line : lines) {
            plugin.getServer().getConsoleSender().sendMessage(MM.deserialize(line, resolver));
        }
    }
}
