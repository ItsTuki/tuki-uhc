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

    /** Cierra el borde del mundo normal (y del Nether a escala) hasta {@code radius} en {@code seconds}. */
    private void shrinkTo(int radius, long seconds) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.getWorldBorder().changeSize(radius * 2.0, Math.max(1, seconds) * 20L);
            } else if (world.getEnvironment() == World.Environment.NETHER) {
                world.getWorldBorder().changeSize(Math.max(16, radius * 2.0 / 8), Math.max(1, seconds) * 20L);
            }
        }
    }

    // ── Por tiempo: cierre continuo ──────────────────────────

    /** Por tiempo: si el cierre continuo ya empezó (aunque el reloj salte con /uhcdev). */
    private boolean timedShrinkStarted;

    private void startTimedShrink() {
        timedShrinkStarted = true;
        int finalRadius = plugin.settings().borderFinalRadius();
        long seconds = plugin.settings().timedSeconds() - plugin.game().totalSeconds();
        shrinkTo(finalRadius, seconds);
        plugin.lang().broadcast("border.timed-shrinking", Lang.p("radius", finalRadius),
                Lang.p("time", cl.itstuki.uhc.util.Format.time(seconds)));
    }

    @Override
    public void onStart() {
        timedShrinkStarted = false;
        if (plugin.game().timed() && shrinks() && plugin.settings().borderStartSecond() <= 0) {
            startTimedShrink();
        }
    }

    @Override
    public void onSecond(int secondsToNextEpisode) {
        if (!timedShrinkStarted && plugin.game().timed() && shrinks() && !plugin.game().suddenDeath()
                && plugin.game().totalSeconds() >= plugin.settings().borderStartSecond()
                && plugin.settings().borderStartSecond() < plugin.settings().timedSeconds()) {
            startTimedShrink();
        }
    }

    /** Muerte súbita: el borde sigue cerrándose hasta un círculo pequeño y todos brillan. */
    @Override
    public void onSuddenDeath() {
        int radius = Math.max(5, Math.min(plugin.settings().borderFinalRadius(),
                plugin.getConfig().getInt("timed.sudden-death-radius", 10)));
        long seconds = Math.max(30, plugin.getConfig().getInt("timed.sudden-death-seconds", 180));
        if (shrinks()) {
            shrinkTo(radius, seconds);
        }
        for (java.util.UUID id : plugin.game().alive()) {
            org.bukkit.entity.Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING,
                        Integer.MAX_VALUE, 0, false, false));
            }
        }
    }

    @Override
    public void onEpisodeStart(int episode) {
        if (!shrinks() || plugin.game().timed()) {
            return;
        }
        int shrink = plugin.settings().shrinkEpisode();
        int last = plugin.settings().finalEpisode();
        if (episode == shrink) {
            int seconds = plugin.settings().episodeSeconds();
            int finalRadius = plugin.settings().borderFinalRadius();
            shrinkTo(finalRadius, seconds);
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

    /** Al terminar se quita el borde: la zona de resultados (junto al lobby) queda fuera de un borde pequeño. */
    @Override
    public void onEnd() {
        for (World world : Bukkit.getWorlds()) {
            world.getWorldBorder().reset();
        }
    }

    @Override
    public void onReset() {
        timedShrinkStarted = false;
        for (World world : Bukkit.getWorlds()) {
            world.getWorldBorder().reset();
        }
    }

    /** Radio actual del borde del mundo principal. */
    public static int currentRadius(World world) {
        return (int) Math.round(world.getWorldBorder().getSize() / 2);
    }
}
