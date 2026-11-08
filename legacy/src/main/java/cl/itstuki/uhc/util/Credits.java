package cl.itstuki.uhc.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import cl.itstuki.uhc.TukiUHC;

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

    public static void enableBanner(TukiUHC plugin) {
        send(plugin, java.util.Arrays.asList(
                "",
                "<dark_gray>  ======================================",
                "  " + TITLE + " <gray>v<version>",
                "  <gray>Creado por " + AUTHOR_TAG,
                "  <gray>Servidor: <white><server>",
                "  <green>Edición Legacy (1.8.8 – 26.3). Usa /uhcconfig para configurar la partida.",
                "<dark_gray>  ======================================",
                ""));
    }

    public static void disableBanner(TukiUHC plugin) {
        send(plugin, java.util.Arrays.asList("", "  " + TITLE + " <gray>v<version> <red>desactivado.",
                "  <gray>Gracias por jugar. Creado por " + AUTHOR_TAG, ""));
    }

    private static void send(TukiUHC plugin, List<String> lines) {
        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("version", plugin.getDescription().getVersion()),
                Placeholder.unparsed("server", plugin.getServer().getName() + " " + cl.itstuki.uhc.compat.Compat.versionName()));
        for (String line : lines) {
            plugin.adventure().console().sendMessage(MM.deserialize(line, resolver));
        }
    }
}
