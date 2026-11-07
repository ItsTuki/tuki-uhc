package cl.itstuki.uhc.game;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.util.Format;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;

/** Estadísticas finales: mensaje en el chat e imagen PNG en plugins/TukiUHC/stats/. */
final class EndReport {
    private EndReport() {
    }

    static void broadcast(TukiUHC plugin, List<UUID> winners, String winnerLabel, long seconds) {
        StatsTracker stats = plugin.stats();
        Lang lang = plugin.lang();
        lang.broadcast("stats.header");
        lang.broadcast("stats.winner", Lang.p("winner", winnerLabel));
        lang.broadcast("stats.duration", Lang.p("time", Format.time(seconds)));
        stats.topKiller().ifPresent(e -> lang.broadcast("stats.top-killer",
                Lang.p("player", stats.name(e.getKey())), Lang.p("kills", e.getValue())));
        stats.mostDamaged().ifPresent(e -> lang.broadcast("stats.most-damaged",
                Lang.p("player", stats.name(e.getKey())), Lang.p("hearts", Format.hearts(e.getValue()))));
        stats.topTeam().ifPresent(e -> lang.broadcast("stats.top-team",
                Lang.p("team", e.getKey()), Lang.p("kills", e.getValue())));
        stats.ironMan(winners).ifPresent(id -> lang.broadcast("stats.iron-man",
                Lang.p("player", stats.name(id)), Lang.p("hearts", Format.hearts(stats.damage(id)))));
        stats.strawMan().ifPresent(e -> lang.broadcast("stats.straw-man",
                Lang.p("player", stats.name(e.getKey())), Lang.p("time", Format.time(e.getValue()))));
        List<StatsTracker.TeamResult> podium = stats.podium();
        if (!podium.isEmpty()) {
            lang.broadcast("stats.podium");
            for (int i = 0; i < Math.min(3, podium.size()); i++) {
                lang.broadcast("stats.podium-entry", Lang.p("place", i + 1),
                        Lang.p("team", podium.get(i).name()), Lang.p("time", Format.time(podium.get(i).seconds())));
            }
        }
    }

    static void saveImage(TukiUHC plugin, List<UUID> winners, String winnerLabel, long seconds) {
        String match = plugin.settings().matchName();
        StatsTracker stats = plugin.stats();
        List<String[]> rows = new ArrayList<>();
        stats.topKiller().ifPresent(e -> rows.add(new String[]{"Más kills", stats.name(e.getKey()) + " (" + e.getValue() + ")"}));
        stats.mostDamaged().ifPresent(e -> rows.add(new String[]{"Más daño recibido", stats.name(e.getKey()) + " (" + Format.hearts(e.getValue()) + " corazones)"}));
        stats.topTeam().ifPresent(e -> rows.add(new String[]{"Equipo con más kills", e.getKey() + " (" + e.getValue() + ")"}));
        stats.ironMan(winners).ifPresent(id -> rows.add(new String[]{"Iron Man", stats.name(id) + " (" + Format.hearts(stats.damage(id)) + " corazones)"}));
        stats.strawMan().ifPresent(e -> rows.add(new String[]{"Straw Man", stats.name(e.getKey()) + " (" + Format.time(e.getValue()) + ")"}));
        List<StatsTracker.TeamResult> podium = stats.podium();
        List<Map.Entry<UUID, Integer>> killList = new ArrayList<>(stats.allKills().entrySet());
        killList.sort((a, b) -> b.getValue() - a.getValue());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                System.setProperty("java.awt.headless", "true");
                int width = 900;
                int height = 260 + rows.size() * 44 + Math.min(3, podium.size()) * 36 + 60;
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(24, 20, 37), 0, height, new Color(12, 10, 18)));
                g.fillRect(0, 0, width, height);
                g.setPaint(new GradientPaint(0, 0, new Color(255, 209, 34), width, 0, new Color(255, 120, 40)));
                g.fillRect(0, 0, width, 6);

                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
                g.setColor(Color.WHITE);
                g.drawString(match, 40, 64);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                g.setColor(new Color(170, 170, 190));
                g.drawString("TukiUHC · " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + " · Duración " + Format.time(seconds), 40, 92);

                int y = 140;
                g.setColor(new Color(255, 209, 34));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
                g.drawString("GANADORES", 40, y);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
                g.drawString(winnerLabel, 40, y + 36);

                y += 80;
                g.setColor(new Color(255, 209, 34));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
                g.drawString("ESTADÍSTICAS", 40, y);
                y += 14;
                g.setStroke(new BasicStroke(1));
                for (String[] row : rows) {
                    g.setColor(new Color(40, 36, 58));
                    g.fillRoundRect(40, y, width - 80, 36, 12, 12);
                    g.setColor(new Color(170, 170, 190));
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                    g.drawString(row[0], 56, y + 24);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                    g.drawString(row[1], 340, y + 24);
                    y += 44;
                }
                if (!podium.isEmpty()) {
                    y += 16;
                    g.setColor(new Color(255, 209, 34));
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
                    g.drawString("PODIO", 40, y);
                    y += 12;
                    Color[] medals = {new Color(255, 209, 34), new Color(200, 200, 210), new Color(205, 127, 50)};
                    for (int i = 0; i < Math.min(3, podium.size()); i++) {
                        g.setColor(medals[i]);
                        g.fillOval(44, y + 6, 22, 22);
                        g.setColor(Color.WHITE);
                        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                        g.drawString(podium.get(i).name() + "  ·  " + Format.time(podium.get(i).seconds()), 80, y + 24);
                        y += 36;
                    }
                }
                g.dispose();
                File folder = new File(plugin.getDataFolder(), "stats");
                folder.mkdirs();
                File file = new File(folder, "partida-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png");
                ImageIO.write(image, "png", file);
                plugin.getLogger().info("Estadísticas guardadas en " + file.getPath());
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo generar la imagen de estadísticas: " + ex.getMessage());
            }
        });
    }
}
