package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import java.time.Duration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/** Borde del mapa: tamaño inicial, cierre progresivo y batalla final en 0 0. */
public final class BorderModule implements UhcModule {
    private final TukiUHC plugin;

    public BorderModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private boolean shrinks() {
        UhcMode mode = plugin.settings().mode();
        return mode != UhcMode.DRAGON && mode != UhcMode.RESOURCE_RUSH;
    }

    @Override
    public void onPrepare() {
        int radius = plugin.settings().borderRadius();
        for (World world : Bukkit.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0, 0);
            double size = switch (world.getEnvironment()) {
                case NETHER -> Math.max(64, radius * 2 / 8.0);
                case THE_END -> 2 * 1000;
                default -> radius * 2.0;
            };
            border.setSize(size);
            border.setDamageAmount(0.5);
            border.setDamageBuffer(2);
            border.setWarningDistance(25);
        }
    }

    @Override
    public void onEpisodeStart(int episode) {
        if (!shrinks()) {
            return;
        }
        int shrink = plugin.settings().shrinkEpisode();
        int last = plugin.settings().finalEpisode();
        if (episode == shrink) {
            int seconds = plugin.settings().episodeSeconds();
            int finalRadius = plugin.settings().borderFinalRadius();
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    world.getWorldBorder().changeSize(finalRadius * 2.0, seconds * 20L);
                } else if (world.getEnvironment() == World.Environment.NETHER) {
                    world.getWorldBorder().changeSize(Math.max(16, finalRadius * 2.0 / 8), seconds * 20L);
                }
            }
            plugin.lang().broadcast("border.shrinking", Lang.p("radius", finalRadius), Lang.p("episode", episode));
            plugin.lang().broadcast("border.final-warning");
        }
        if (episode == last && last > 1) {
            plugin.lang().broadcast("border.final-battle");
            Bukkit.getServer().showTitle(Title.title(plugin.lang().get("border.final-battle-title"),
                    plugin.lang().get("border.final-battle-subtitle"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        }
    }

    @Override
    public void onReset() {
        for (World world : Bukkit.getWorlds()) {
            world.getWorldBorder().reset();
        }
    }

    /** Radio actual del borde del mundo principal. */
    public static int currentRadius(World world) {
        return (int) Math.round(world.getWorldBorder().getSize() / 2);
    }
}
