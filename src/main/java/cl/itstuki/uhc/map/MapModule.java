package cl.itstuki.uhc.map;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.util.Format;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Mapa de la partida: pregeneración de los chunks dentro del borde y mejoras bajo tierra (más minerales y
 * cuevas más grandes) en cada chunk nuevo o pregenerado. Los chunks ya mejorados se guardan en
 * plugins/TukiUHC/map/ para no tocarlos dos veces.
 */
public final class MapModule implements UhcModule {
    private final TukiUHC plugin;
    private final Set<Long> shaped = new HashSet<>();
    private @Nullable UUID shapedWorld;
    private boolean dirty;
    private @Nullable ChunkShaper shaper;

    // Pregeneración en curso
    private @Nullable BukkitTask task;
    private @Nullable CommandSender starter;
    private int radiusChunks;
    private long index;
    private long total;
    private long done;
    private int inFlight;
    private long startedAt;
    private long lastReport;

    public MapModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private World world() {
        return plugin.game().mainWorld();
    }

    private boolean shaping() {
        return plugin.settings().enabled(Feature.ORE_BOOST) || plugin.settings().enabled(Feature.BIG_CAVES);
    }

    // ── Registro de chunks mejorados ─────────────────────────

    private File file(World world) {
        return new File(plugin.getDataFolder(), "map/" + world.getName() + ".dat");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Carga el registro del mundo; si el mundo es otro (nuevo, mismo nombre) se empieza de cero. */
    private void ensureLoaded(World world) {
        if (world.getUID().equals(shapedWorld)) {
            return;
        }
        saveShaped();
        shaped.clear();
        shapedWorld = world.getUID();
        shaper = new ChunkShaper(world);
        File file = file(world);
        if (!file.exists()) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readLong() != world.getUID().getMostSignificantBits() || in.readLong() != world.getUID().getLeastSignificantBits()) {
                return; // Registro de otro mundo.
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                shaped.add(in.readLong());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo leer " + file.getName() + ": " + ex.getMessage());
        }
    }

    public void saveShaped() {
        if (!dirty || shapedWorld == null) {
            return;
        }
        World world = Bukkit.getWorld(shapedWorld);
        if (world == null) {
            return;
        }
        File file = file(world);
        file.getParentFile().mkdirs();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeLong(shapedWorld.getMostSignificantBits());
            out.writeLong(shapedWorld.getLeastSignificantBits());
            out.writeInt(shaped.size());
            for (long value : shaped) {
                out.writeLong(value);
            }
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar " + file.getName() + ": " + ex.getMessage());
        }
    }

    /** Aplica las mejoras al chunk si corresponde y no se habían aplicado. */
    private void shape(Chunk chunk) {
        if (!shaping() || !chunk.getWorld().equals(world())) {
            return;
        }
        ensureLoaded(chunk.getWorld());
        long key = key(chunk.getX(), chunk.getZ());
        int limit = pregenRadius() + 32;
        if (shaped.contains(key) || Math.abs(chunk.getX() << 4) > limit || Math.abs(chunk.getZ() << 4) > limit) {
            return;
        }
        shaped.add(key);
        dirty = true;
        if (plugin.settings().enabled(Feature.BIG_CAVES)) {
            shaper.carveCaves(chunk, Math.max(0.5, plugin.getConfig().getDouble("map.caves.strength", 1.0)),
                    Math.max(4, plugin.getConfig().getInt("map.caves.min-depth", 10)));
        }
        if (plugin.settings().enabled(Feature.ORE_BOOST)) {
            List<ChunkShaper.Ore> ores = ChunkShaper.ores(plugin.getConfig().getConfigurationSection("map.ores"));
            shaper.addOres(chunk, ores, plugin.settings().value(Feature.ORE_BOOST));
        }
    }

    /** Chunks recién generados (fuera de la pregeneración) también se mejoran. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            shape(event.getChunk());
        }
    }

    // ── Pregeneración ────────────────────────────────────────

    /** Radio a pregenerar: el del borde más un margen. */
    public int pregenRadius() {
        return plugin.settings().borderRadius() + Math.max(0, plugin.getConfig().getInt("map.pregen-margin", 64));
    }

    public boolean running() {
        return task != null;
    }

    public String statusLine() {
        if (running()) {
            return plugin.lang().raw("map.status-running").replace("<percent>", String.valueOf(percent()));
        }
        return plugin.lang().raw("map.status-idle");
    }

    private int percent() {
        return total == 0 ? 0 : (int) Math.min(100, done * 100 / total);
    }

    public void start(CommandSender sender) {
        if (running()) {
            plugin.lang().send(sender, "map.already-running", Lang.p("percent", percent()));
            return;
        }
        if (plugin.game().state() != GameState.LOBBY) {
            plugin.lang().send(sender, "map.only-lobby");
            return;
        }
        World world = world();
        ensureLoaded(world);
        radiusChunks = (pregenRadius() >> 4) + 1;
        long side = radiusChunks * 2L + 1;
        total = side * side;
        index = 0;
        done = 0;
        inFlight = 0;
        starter = sender;
        startedAt = System.currentTimeMillis();
        lastReport = 0;
        plugin.lang().broadcastPermission("tukiuhc.command.pregen", "map.started", Lang.p("radius", pregenRadius()),
                Lang.p("chunks", total), Lang.p("world", world.getName()));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop(@Nullable CommandSender sender) {
        if (!running()) {
            if (sender != null) {
                plugin.lang().send(sender, "map.not-running");
            }
            return;
        }
        task.cancel();
        task = null;
        saveShaped();
        plugin.lang().broadcastPermission("tukiuhc.command.pregen", "map.stopped", Lang.p("percent", percent()));
    }

    public void status(CommandSender sender) {
        if (!running()) {
            plugin.lang().send(sender, "map.status", Lang.p("status", statusLine()), Lang.p("radius", pregenRadius()),
                    Lang.p("shaped", shaped.size()));
            return;
        }
        plugin.lang().send(sender, "map.progress", Lang.p("percent", percent()), Lang.p("done", done), Lang.p("total", total),
                Lang.p("eta", Format.time(eta())), Lang.p("tps", String.format(java.util.Locale.ROOT, "%.1f", Bukkit.getTPS()[0])));
    }

    private long eta() {
        long elapsed = Math.max(1, System.currentTimeMillis() - startedAt);
        return done == 0 ? 0 : (total - done) * elapsed / done / 1000;
    }

    /** Posición n de una espiral cuadrada desde el centro (así lo cercano al 0 0 queda listo primero). */
    private static int[] spiral(long n) {
        if (n == 0) {
            return new int[] {0, 0};
        }
        long k = (long) Math.ceil((Math.sqrt(n + 1) - 1) / 2);
        long t = 2 * k + 1;
        long m = t * t;
        t--;
        if (n >= m - t) {
            return new int[] {(int) (k - (m - n)), (int) -k};
        }
        m -= t;
        if (n >= m - t) {
            return new int[] {(int) -k, (int) (-k + (m - n))};
        }
        m -= t;
        if (n >= m - t) {
            return new int[] {(int) (-k + (m - n)), (int) k};
        }
        return new int[] {(int) k, (int) (k - (m - n - t))};
    }

    private void tick() {
        if (plugin.game().state() != GameState.LOBBY) {
            stop(null);
            return;
        }
        World world = world();
        double tps = Bukkit.getTPS()[0];
        int maxInFlight = Math.max(1, plugin.getConfig().getInt("map.pregen-parallel", 16));
        if (tps < plugin.getConfig().getDouble("map.pregen-min-tps", 17)) {
            maxInFlight = Math.max(1, maxInFlight / 4); // El servidor va lento: se baja el ritmo.
        }
        while (inFlight < maxInFlight && index < total) {
            int[] pos = spiral(index++);
            if (Math.abs(pos[0]) > radiusChunks || Math.abs(pos[1]) > radiusChunks) {
                done++;
                continue;
            }
            inFlight++;
            world.getChunkAtAsync(pos[0], pos[1], true).whenComplete((chunk, error) -> {
                inFlight--;
                done++;
                if (chunk != null) {
                    shape(chunk);
                }
            });
        }
        long now = System.currentTimeMillis();
        if (now - lastReport > 2000) {
            lastReport = now;
            Component bar = plugin.lang().get("map.actionbar", Lang.p("percent", percent()), Lang.p("done", done),
                    Lang.p("total", total), Lang.p("eta", Format.time(eta())), Lang.p("tps", String.format(java.util.Locale.ROOT, "%.1f", tps)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("tukiuhc.command.pregen")) {
                    player.sendActionBar(bar);
                }
            }
            if (done % 2000 < maxInFlight * 40L) {
                saveShaped();
            }
        }
        if (index >= total && inFlight == 0) {
            task.cancel();
            task = null;
            saveShaped();
            long seconds = (now - startedAt) / 1000;
            plugin.lang().broadcastPermission("tukiuhc.command.pregen", "map.finished", Lang.p("chunks", total),
                    Lang.p("time", Format.time(seconds)), Lang.p("shaped", shaped.size()));
            plugin.getLogger().info("Pregeneración terminada: " + total + " chunks en " + Format.time(seconds) + ".");
        }
    }

    @Override
    public void onPrepare() {
        if (running()) {
            stop(null);
            plugin.lang().broadcastPermission("tukiuhc.command.pregen", "map.stopped-by-prepare");
        }
    }

    /** Al apagar el plugin. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        saveShaped();
    }
}
