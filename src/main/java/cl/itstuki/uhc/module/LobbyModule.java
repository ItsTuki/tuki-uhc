package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Format;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jspecify.annotations.Nullable;

/**
 * Mini lobby flotante: zona de espera con el maniquí del creador y zona de resultados
 * con podio, maniquíes de los 3 mejores y tops de la partida e históricos.
 */
public final class LobbyModule implements UhcModule {
    private static final String TAG = "tukiuhc_lobby";
    private static final String RESULTS_TAG = "tukiuhc_results";
    private static final int HALF = 10;
    private static final int RESULTS_OFFSET = 40;

    private final TukiUHC plugin;
    private final File boardFile;
    private YamlConfiguration board;
    private boolean ready;

    public LobbyModule(TukiUHC plugin) {
        this.plugin = plugin;
        this.boardFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        this.board = YamlConfiguration.loadConfiguration(boardFile);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("lobby.enabled", true);
    }

    private World world() {
        String name = plugin.getConfig().getString("lobby.world", "");
        World world = name.isEmpty() ? null : Bukkit.getWorld(name);
        return world != null ? world : Bukkit.getWorlds().getFirst();
    }

    private Location center() {
        World world = world();
        int y = Math.min(world.getMaxHeight() - 12, plugin.getConfig().getInt("lobby.y", 300));
        return new Location(world, plugin.getConfig().getInt("lobby.x", 0) + 0.5, y, plugin.getConfig().getInt("lobby.z", 0) + 0.5);
    }

    private Location resultsCenter() {
        return center().add(RESULTS_OFFSET, 0, 0);
    }

    public Location spawn() {
        Location spawn = center().add(0, 1, -HALF + 3);
        spawn.setYaw(0);
        return spawn;
    }

    public Location resultsSpawn() {
        Location spawn = resultsCenter().add(0, 1, -HALF + 3);
        spawn.setYaw(0);
        return spawn;
    }

    /** Construye ambas zonas y coloca el maniquí. Se llama al iniciar el plugin. */
    public void setup() {
        if (!enabled()) {
            return;
        }
        Location c = center();
        Location r = resultsCenter();
        for (Location zone : List.of(c, r)) {
            for (int cx = (zone.getBlockX() - HALF) >> 4; cx <= (zone.getBlockX() + HALF) >> 4; cx++) {
                for (int cz = (zone.getBlockZ() - HALF) >> 4; cz <= (zone.getBlockZ() + HALF) >> 4; cz++) {
                    zone.getWorld().getChunkAt(cx, cz).setForceLoaded(true);
                }
            }
        }
        buildPlatform(c, Material.POLISHED_BLACKSTONE_BRICKS, Material.GOLD_BLOCK);
        buildPlatform(r, Material.QUARTZ_BRICKS, Material.DIAMOND_BLOCK);
        buildPodium(r);
        clearEntities(TAG);
        clearEntities(RESULTS_TAG);

        String creator = plugin.getConfig().getString("lobby.mannequin", "ItsTuki");
        Location statue = c.clone().add(0, 1, HALF - 4);
        statue.setYaw(180);
        mannequin(statue, creator, plugin.lang().get("lobby.creator-description"), TAG);
        hologram(statue.clone().add(0, 2.9, 0), plugin.lang().get("lobby.creator-title", Lang.p("player", creator)), TAG, 1.6f);
        hologram(c.clone().add(0, 3.2, -HALF + 6), plugin.lang().get("lobby.welcome"), TAG, 1.2f);
        hologram(resultsCenter().add(0, 4.5, -HALF + 6), plugin.lang().get("lobby.results-waiting"), RESULTS_TAG, 1.2f);
        world().setSpawnLocation(spawn());
        ready = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.game().state() == GameState.LOBBY) {
                toLobby(player);
            }
        }
    }

    private void buildPlatform(Location center, Material floor, Material accent) {
        World world = center.getWorld();
        int x0 = center.getBlockX();
        int y0 = center.getBlockY();
        int z0 = center.getBlockZ();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                boolean light = (dx % 5 == 0) && (dz % 5 == 0) && !edge;
                Material m = edge ? accent : light ? Material.SEA_LANTERN : floor;
                world.getBlockAt(x0 + dx, y0, z0 + dz).setType(m, false);
                world.getBlockAt(x0 + dx, y0 - 1, z0 + dz).setType(Material.BARRIER, false);
                for (int dy = 1; dy <= 6; dy++) {
                    Block block = world.getBlockAt(x0 + dx, y0 + dy, z0 + dz);
                    if (edge) {
                        block.setType(dy == 1 ? Material.GLASS : Material.BARRIER, false);
                    } else if (!block.getType().isAir() && block.getType() != Material.GOLD_BLOCK
                            && block.getType() != Material.IRON_BLOCK && block.getType() != Material.COPPER_BLOCK) {
                        block.setType(Material.AIR, false);
                    }
                }
                world.getBlockAt(x0 + dx, y0 + 7, z0 + dz).setType(Material.BARRIER, false);
            }
        }
    }

    private void buildPodium(Location center) {
        World world = center.getWorld();
        int x0 = center.getBlockX();
        int y0 = center.getBlockY();
        int z0 = center.getBlockZ() + HALF - 5;
        for (int dy = 1; dy <= 3; dy++) {
            world.getBlockAt(x0, y0 + dy, z0).setType(dy <= 3 ? Material.GOLD_BLOCK : Material.AIR, false);
        }
        for (int dy = 1; dy <= 2; dy++) {
            world.getBlockAt(x0 - 2, y0 + dy, z0).setType(Material.IRON_BLOCK, false);
        }
        world.getBlockAt(x0 + 2, y0 + 1, z0).setType(Material.COPPER_BLOCK, false);
    }

    private void clearEntities(String tag) {
        for (Entity entity : world().getEntities()) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
    }

    private void mannequin(Location where, String name, Component description, String tag) {
        where.getWorld().spawn(where, Mannequin.class, m -> {
            m.setProfile(ResolvableProfile.resolvableProfile().name(name).build());
            m.customName(Component.text(name));
            m.setCustomNameVisible(true);
            m.setDescription(description);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setPersistent(false);
            m.addScoreboardTag(tag);
        });
    }

    private void hologram(Location where, Component text, String tag, float scale) {
        where.getWorld().spawn(where, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            d.setShadowed(true);
            d.setLineWidth(260);
            d.setPersistent(false);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                    new org.joml.AxisAngle4f(), new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            d.addScoreboardTag(tag);
        });
    }

    // ── Movimiento de jugadores ──────────────────────────────

    public void toLobby(Player player) {
        if (!ready) {
            return;
        }
        player.teleportAsync(spawn());
        player.setGameMode(GameMode.ADVENTURE);
    }

    public void toResults(Player player) {
        if (!ready) {
            return;
        }
        player.teleportAsync(resultsSpawn());
        player.setGameMode(GameMode.ADVENTURE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        GameState state = plugin.game().state();
        if (state == GameState.LOBBY) {
            toLobby(event.getPlayer());
        } else if (state == GameState.ENDED) {
            toResults(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!ready) {
            return;
        }
        GameState state = plugin.game().state();
        if (state == GameState.LOBBY) {
            event.setRespawnLocation(spawn());
        } else if (state == GameState.ENDED) {
            event.setRespawnLocation(resultsSpawn());
        }
    }

    /** Si alguien sale del lobby (por ejemplo con un comando) vuelve al centro. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!ready || !event.hasChangedBlock()) {
            return;
        }
        GameState state = plugin.game().state();
        if ((state == GameState.LOBBY || state == GameState.ENDED)
                && event.getTo().getWorld().equals(world())
                && event.getTo().getY() < center().getY() - 20
                && event.getPlayer().getGameMode() != GameMode.CREATIVE
                && event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            event.setTo(state == GameState.LOBBY ? spawn() : resultsSpawn());
        }
    }

    private boolean inLobby(Location location) {
        if (!ready || !location.getWorld().equals(world())) {
            return false;
        }
        Location c = center();
        double y = location.getY() - c.getY();
        if (y < -2 || y > 8) {
            return false;
        }
        for (Location zone : List.of(c, resultsCenter())) {
            if (Math.abs(location.getX() - zone.getX()) <= HALF + 1 && Math.abs(location.getZ() - zone.getZ()) <= HALF + 1) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inLobby(event.getBlock().getLocation()) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inLobby(event.getBlock().getLocation()) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity.getScoreboardTags().contains(TAG) || entity.getScoreboardTags().contains(RESULTS_TAG)
                || (entity instanceof Player && inLobby(entity.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains(TAG)) {
            plugin.lang().send(event.getPlayer(), "lobby.creator-click", Lang.p("url", cl.itstuki.uhc.util.Credits.REPOSITORY));
        }
    }

    // ── Momentos de la partida ───────────────────────────────

    @Override
    public void onEnd() {
        if (!enabled()) {
            return;
        }
        List<Ranked> ranking = ranking();
        saveLeaderboard(ranking);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.game().state() != GameState.ENDED) {
                return;
            }
            showResults(ranking);
            for (Player player : Bukkit.getOnlinePlayers()) {
                toResults(player);
            }
            plugin.lang().broadcast("lobby.results-teleport");
        }, 20L * plugin.getConfig().getInt("lobby.results-delay", 8));
    }

    @Override
    public void onReset() {
        if (!ready) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    toLobby(player);
                }
            }
        });
    }

    // ── Resultados y tops ────────────────────────────────────

    private record Ranked(UUID id, String name, boolean winner, long survived, int kills, double damage) {
    }

    /** Ganadores primero; luego los que aguantaron más; a igual tiempo, más kills. */
    private List<Ranked> ranking() {
        List<Ranked> list = new ArrayList<>();
        long total = plugin.game().totalSeconds();
        for (UUID id : plugin.game().participants()) {
            boolean alive = plugin.game().isAlive(id);
            long survived = alive ? total + 1 : plugin.stats().deathSecond(id).orElse(0L);
            list.add(new Ranked(id, plugin.stats().name(id), alive, survived, plugin.stats().kills(id), plugin.stats().damage(id)));
        }
        list.sort(Comparator.comparing(Ranked::winner).reversed()
                .thenComparing(Comparator.comparingLong(Ranked::survived).reversed())
                .thenComparing(Comparator.comparingInt(Ranked::kills).reversed()));
        return list;
    }

    private void showResults(List<Ranked> ranking) {
        clearEntities(RESULTS_TAG);
        Location r = resultsCenter();
        int podiumZ = HALF - 5;
        double[][] spots = {{0, 4, podiumZ}, {-2, 3, podiumZ}, {2, 2, podiumZ}};
        String[] medals = {"<gold><bold>1°", "<gray><bold>2°", "<#C87533><bold>3°"};
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            Ranked rank = ranking.get(i);
            Location where = r.clone().add(spots[i][0], spots[i][1], spots[i][2]);
            where.setYaw(180);
            Party team = plugin.teams().team(rank.id());
            Component description = Lang.mm(medals[i] + " <gray>· " + rank.kills() + " kills"
                    + (team != null ? " · " + team.name() : ""));
            mannequin(where, rank.name(), description, RESULTS_TAG);
        }

        Lang lang = plugin.lang();
        String winner = ranking.isEmpty() || !ranking.getFirst().winner() ? lang.raw("game.nobody")
                : String.join(", ", ranking.stream().filter(Ranked::winner).map(Ranked::name).toList());
        hologram(r.clone().add(0, 6.2, podiumZ), lang.get("lobby.results-title",
                Lang.p("match", plugin.settings().matchName()), Lang.p("winner", winner),
                Lang.p("time", Format.time(plugin.game().totalSeconds()))), RESULTS_TAG, 1.4f);

        List<Component> killLines = new ArrayList<>();
        killLines.add(lang.get("lobby.top-kills-title"));
        List<Ranked> byKills = new ArrayList<>(ranking);
        byKills.sort(Comparator.comparingInt(Ranked::kills).reversed());
        for (int i = 0; i < Math.min(5, byKills.size()); i++) {
            killLines.add(lang.get("lobby.top-line", Lang.p("place", i + 1), Lang.p("player", byKills.get(i).name()),
                    Lang.p("value", byKills.get(i).kills())));
        }
        killLines.add(Component.empty());
        killLines.add(lang.get("lobby.top-survival-title"));
        for (int i = 0; i < Math.min(5, ranking.size()); i++) {
            Ranked rank = ranking.get(i);
            killLines.add(lang.get("lobby.top-line", Lang.p("place", i + 1), Lang.p("player", rank.name()),
                    Lang.p("value", rank.winner() ? lang.raw("lobby.survived") : Format.time(rank.survived()))));
        }
        hologram(r.clone().add(-6.5, 2.6, 2), join(killLines), RESULTS_TAG, 1.0f);

        List<Component> allTime = new ArrayList<>();
        allTime.add(lang.get("lobby.top-wins-title"));
        addTop(allTime, "wins", 5);
        allTime.add(Component.empty());
        allTime.add(lang.get("lobby.top-alltime-kills-title"));
        addTop(allTime, "kills", 5);
        hologram(r.clone().add(6.5, 2.6, 2), join(allTime), RESULTS_TAG, 1.0f);
    }

    private static Component join(List<Component> lines) {
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private void addTop(List<Component> lines, String field, int limit) {
        ConfigurationSection players = board.getConfigurationSection("players");
        if (players == null) {
            lines.add(plugin.lang().get("lobby.top-empty"));
            return;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (String id : players.getKeys(false)) {
            int value = players.getInt(id + "." + field);
            if (value > 0) {
                entries.add(Map.entry(players.getString(id + ".name", "?"), value));
            }
        }
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        if (entries.isEmpty()) {
            lines.add(plugin.lang().get("lobby.top-empty"));
        }
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            lines.add(plugin.lang().get("lobby.top-line", Lang.p("place", i + 1), Lang.p("player", entries.get(i).getKey()),
                    Lang.p("value", entries.get(i).getValue())));
        }
    }

    private void saveLeaderboard(List<Ranked> ranking) {
        if (ranking.isEmpty() || plugin.game().totalSeconds() < 1) {
            return;
        }
        for (Ranked rank : ranking) {
            String path = "players." + rank.id();
            board.set(path + ".name", rank.name());
            board.set(path + ".games", board.getInt(path + ".games") + 1);
            board.set(path + ".kills", board.getInt(path + ".kills") + rank.kills());
            if (rank.winner()) {
                board.set(path + ".wins", board.getInt(path + ".wins") + 1);
            }
        }
        try {
            board.save(boardFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar leaderboard.yml: " + ex.getMessage());
        }
    }

    public @Nullable Location lobbySpawnIfReady() {
        return ready ? spawn() : null;
    }
}
