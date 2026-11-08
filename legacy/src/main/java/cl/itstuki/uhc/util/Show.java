package cl.itstuki.uhc.util;

import cl.itstuki.uhc.TukiUHC;
import java.time.Duration;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/** Títulos y barra de acción en cualquier versión (con Adventure). */
public final class Show {
    private Show() {
    }

    private static Audience of(Player player) {
        return TukiUHC.get().adventure().player(player);
    }

    private static Audience all() {
        return TukiUHC.get().adventure().players();
    }

    public static Title title(Component title, Component subtitle, long fadeInMs, long stayMs, long fadeOutMs) {
        return Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(fadeInMs), Duration.ofMillis(stayMs),
                Duration.ofMillis(fadeOutMs)));
    }

    public static void title(Player player, Title title) {
        of(player).showTitle(title);
    }

    public static void titleAll(Title title) {
        all().showTitle(title);
    }

    public static void actionBar(Player player, Component text) {
        of(player).sendActionBar(text);
    }

    public static void actionBarAll(Component text) {
        all().sendActionBar(text);
    }

    public static void tab(Player player, Component header, Component footer) {
        of(player).sendPlayerListHeaderAndFooter(header, footer);
    }
}
