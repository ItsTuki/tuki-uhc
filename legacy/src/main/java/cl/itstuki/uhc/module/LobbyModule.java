package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.ItemTag;
import cl.itstuki.uhc.compat.Text;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Credits;
import cl.itstuki.uhc.util.Format;
import cl.itstuki.uhc.util.Items;
import com.cryptomorin.xseries.XMaterial;
import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Mini lobby flotante: zona de espera con la estatua del creador y zona de resultados con podio,
 * estatuas de los 3 mejores y tops. En la edición Legacy las estatuas y los textos son armor stands.
 */
public final class LobbyModule implements UhcModule {
    private static final int HALF = 10;
    private static final int RESULTS_OFFSET = 40;
    private static final double LINE = 0.28;

    private final TukiUHC plugin;
    private final File boardFile;
    private final YamlConfiguration board;
    private final List<Entity> lobbyEntities = new ArrayList<>();
    private final List<Entity> resultEntities = new ArrayList<>();
    private final Set<UUID> statues = new HashSet<>();
    private final Set<Long> keptChunks = new HashSet<>();
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
        return world != null ? world : Bukkit.getWorlds().get(0);
    }

    private Location center() {
        World world = world();
        // En 1.8–1.17 el mundo llega hasta la altura 256.
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

    /** Construye el lobby y coloca la estatua. Se llama al iniciar el plugin. */
    public void setup() {
        if (!enabled()) {
            return;
        }
        Location c = center();
        Location r = resultsCenter();
        for (Location zone : Arrays.asList(c, r)) {
            for (int cx = (zone.getBlockX() - HALF) >> 4; cx <= (zone.getBlockX() + HALF) >> 4; cx++) {
                for (int cz = (zone.getBlockZ() - HALF) >> 4; cz <= (zone.getBlockZ() + HALF) >> 4; cz++) {
                    keepLoaded(zone.getWorld().getChunkAt(cx, cz));
                }
            }
        }
        purgeOldStands(c);
        purgeOldStands(r);
        buildPlatform(c, Compat.first(XMaterial.POLISHED_BLACKSTONE_BRICKS, XMaterial.STONE_BRICKS), Material.GOLD_BLOCK);
        clear(lobbyEntities);
        demolishResults();

        String creator = plugin.getConfig().getString("lobby.mannequin", "ItsTuki");
        Location statue = c.clone().add(0, 1, HALF - 4);
        statue.setYaw(180);
        statue(statue, creator, plugin.lang().get("lobby.creator-description"), lobbyEntities, Color.fromRGB(85, 255, 255));
        hologram(statue.clone().add(0, 3.0, 0), plugin.lang().get("lobby.creator-title", Lang.p("player", creator)), lobbyEntities);
        hologram(c.clone().add(0, 3.2, -HALF + 6), plugin.lang().get("lobby.welcome"), lobbyEntities);
        world().setSpawnLocation(spawn().getBlockX(), spawn().getBlockY(), spawn().getBlockZ());
        ready = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.game().state() == GameState.LOBBY) {
                toLobby(player);
            }
        }
    }

    /** 1.13+: chunk forzado. Antes se impide que se descargue. */
    private void keepLoaded(Chunk chunk) {
        chunk.load(true);
        keptChunks.add(key(chunk));
        try {
            Chunk.class.getMethod("setForceLoaded", boolean.class).invoke(chunk, true);
        } catch (ReflectiveOperationException ignored) {
            // 1.8–1.12: se cancela la descarga en onUnload.
        }
    }

    private static long key(Chunk chunk) {
        return ((long) chunk.getX() << 32) ^ (chunk.getZ() & 0xffffffffL);
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        if (event instanceof org.bukkit.event.Cancellable && event.getWorld().equals(world()) && keptChunks.contains(key(event.getChunk()))) {
            ((org.bukkit.event.Cancellable) event).setCancelled(true);
        }
    }

    /** Armor stands que quedaron de una sesión anterior (en 1.8 no se pueden marcar). */
    private void purgeOldStands(Location zone) {
        for (Entity entity : zone.getWorld().getEntities()) {
            if (entity instanceof ArmorStand && Math.abs(entity.getLocation().getX() - zone.getX()) <= HALF + 1
                    && Math.abs(entity.getLocation().getZ() - zone.getZ()) <= HALF + 1
                    && Math.abs(entity.getLocation().getY() - zone.getY()) <= 10) {
                entity.remove();
            }
        }
    }

    private void buildPlatform(Location center, Material floor, Material accent) {
        World world = center.getWorld();
        Material lantern = Compat.first(XMaterial.SEA_LANTERN, XMaterial.GLOWSTONE);
        Material barrier = Compat.first(XMaterial.BARRIER, XMaterial.GLASS);
        int x0 = center.getBlockX();
        int y0 = center.getBlockY();
        int z0 = center.getBlockZ();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                boolean light = (dx % 5 == 0) && (dz % 5 == 0) && !edge;
                Material m = edge ? accent : light ? lantern : floor;
                world.getBlockAt(x0 + dx, y0, z0 + dz).setType(m, false);
                world.getBlockAt(x0 + dx, y0 - 1, z0 + dz).setType(barrier, false);
                for (int dy = 1; dy <= 6; dy++) {
                    Block block = world.getBlockAt(x0 + dx, y0 + dy, z0 + dz);
                    if (edge) {
                        block.setType(dy == 1 ? Material.GLASS : barrier, false);
                    } else if (!Compat.isAir(block.getType())) {
                        block.setType(Material.AIR, false);
                    }
                }
                world.getBlockAt(x0 + dx, y0 + 7, z0 + dz).setType(barrier, false);
            }
        }
    }

    private void demolishLobby() {
        clear(lobbyEntities);
        demolish(center());
    }

    private void demolishResults() {
        clear(resultEntities);
        demolish(resultsCenter());
    }

    private void demolish(Location r) {
        World world = r.getWorld();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                for (int dy = -1; dy <= 7; dy++) {
                    Block block = world.getBlockAt(r.getBlockX() + dx, r.getBlockY() + dy, r.getBlockZ() + dz);
                    if (!Compat.isAir(block.getType())) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void buildPodium(Location center) {
        World world = center.getWorld();
        int x0 = center.getBlockX();
        int y0 = center.getBlockY();
        int z0 = center.getBlockZ() + HALF - 5;
        for (int dy = 1; dy <= 3; dy++) {
            world.getBlockAt(x0, y0 + dy, z0).setType(Material.GOLD_BLOCK, false);
        }
        for (int dy = 1; dy <= 2; dy++) {
            world.getBlockAt(x0 - 2, y0 + dy, z0).setType(Material.IRON_BLOCK, false);
        }
        world.getBlockAt(x0 + 2, y0 + 1, z0).setType(Compat.first(XMaterial.COPPER_BLOCK, XMaterial.BRICKS), false);
    }

    private void clear(List<Entity> entities) {
        for (Entity entity : entities) {
            statues.remove(entity.getUniqueId());
            entity.remove();
        }
        entities.clear();
    }

    /** Al apagar el plugin: quita las estatuas y los textos. */
    public void removeEntities() {
        clear(lobbyEntities);
        clear(resultEntities);
    }

    /** Estatua: armor stand con la cabeza del jugador y armadura de cuero del color dado. */
    private void statue(Location where, String name, Component description, List<Entity> into, Color color) {
        ArmorStand stand = where.getWorld().spawn(where, ArmorStand.class);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setCustomName(Text.legacy(Component.text(name)));
        stand.setCustomNameVisible(true);
        stand.setHelmet(Items.of(XMaterial.PLAYER_HEAD).owner(name).build());
        stand.setChestplate(leather(XMaterial.LEATHER_CHESTPLATE, color));
        stand.setLeggings(leather(XMaterial.LEATHER_LEGGINGS, color));
        stand.setBoots(leather(XMaterial.LEATHER_BOOTS, color));
        into.add(stand);
        statues.add(stand.getUniqueId());
        if (description != null) {
            hologram(where.clone().add(0, 2.35, 0), description, into);
        }
    }

    private static ItemStack leather(XMaterial type, Color color) {
        ItemStack item = Compat.item(type);
        if (item.getItemMeta() instanceof LeatherArmorMeta) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Texto flotante; cada línea es un armor stand invisible (de arriba hacia abajo). */
    private void hologram(Location top, Component text, List<Entity> into) {
        String[] lines = Text.legacy(text).split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty() && i > 0 && i < lines.length - 1) {
                continue;
            }
            ArmorStand stand = top.getWorld().spawn(top.clone().add(0, -LINE * i, 0), ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setCustomName(lines[i].isEmpty() ? " " : lines[i]);
            stand.setCustomNameVisible(!lines[i].trim().isEmpty());
            into.add(stand);
            statues.add(stand.getUniqueId());
        }
    }

    // ── Movimiento de jugadores ──────────────────────────────

    public void toLobby(Player player) {
        if (!ready) {
            return;
        }
        player.teleport(spawn());
        player.setGameMode(GameMode.ADVENTURE);
        lobbyItems(player);
    }

    /** En el lobby el inventario queda limpio; los organizadores reciben el libro de configuración. */
    private void lobbyItems(Player player) {
        player.getInventory().clear();
        if (player.hasPermission("tukiuhc.command.config")) {
            player.getInventory().setItem(4, Items.of(XMaterial.WRITABLE_BOOK)
                    .name(plugin.lang().item("lobby.book.name")).lore(plugin.lang().itemList("lobby.book.lore"))
                    .glow(true).tag("lobby_book").build());
        }
    }

    private static boolean isBook(ItemStack item) {
        return "lobby_book".equals(ItemTag.read(item));
    }

    @EventHandler
    public void onBook(PlayerInteractEvent event) {
        if (!isBook(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction().name().startsWith("RIGHT") && !Compat.isOffHand(event)
                && event.getPlayer().hasPermission("tukiuhc.command.config")) {
            plugin.menus().openMain(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropBook(PlayerDropItemEvent event) {
        if (isBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    public void toResults(Player player) {
        if (!ready) {
            return;
        }
        player.teleport(resultsSpawn());
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
        Location to = event.getTo();
        if (!ready || to == null || to.getBlockY() == event.getFrom().getBlockY()) {
            return;
        }
        GameState state = plugin.game().state();
        if ((state == GameState.LOBBY || state == GameState.ENDED)
                && to.getWorld().equals(world())
                && to.getY() < center().getY() - 20
                && event.getPlayer().getGameMode() != GameMode.CREATIVE
                && event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            event.setTo(state == GameState.LOBBY ? spawn() : resultsSpawn());
        }
    }

    /** Bloque sólido más alto en (x, z) sin contar el lobby ni la zona de resultados flotantes. */
    public int groundY(World world, int x, int z) {
        int y = Compat.highestSolidY(world, x, z);
        if (!world.equals(world())) {
            return y;
        }
        Location c = center();
        int below = c.getBlockY() - 3;
        if (y < below) {
            return y;
        }
        boolean underLobby = false;
        for (Location zone : Arrays.asList(c, resultsCenter())) {
            if (Math.abs(x + 0.5 - zone.getX()) <= HALF + 2 && Math.abs(z + 0.5 - zone.getZ()) <= HALF + 2) {
                underLobby = true;
            }
        }
        if (!underLobby) {
            return y;
        }
        for (int yy = below; yy > Compat.minHeight(world); yy--) {
            if (!Compat.isAir(world.getBlockAt(x, yy, z).getType())) {
                return yy;
            }
        }
        return Compat.minHeight(world);
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
        for (Location zone : Arrays.asList(c, resultsCenter())) {
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
        if (statues.contains(entity.getUniqueId()) || (entity instanceof Player && inLobby(entity.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (statues.contains(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (Compat.isOffHand(event) || !lobbyEntities.contains(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        Component link = plugin.lang().get("lobby.creator-link")
                .clickEvent(ClickEvent.openUrl(Credits.REPOSITORY))
                .hoverEvent(HoverEvent.showText(Component.text(Credits.REPOSITORY)));
        plugin.lang().send(event.getPlayer(), "lobby.creator-click", Lang.c("link", link));
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

    /** Al empezar la partida el lobby desaparece; se vuelve a construir con /sos. */
    @Override
    public void onStart() {
        if (ready) {
            demolishLobby();
        }
    }

    @Override
    public void onReset() {
        if (!ready || !plugin.isEnabled()) {
            return; // Al apagar el servidor no hay que reconstruir nada.
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            setup();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    toLobby(player);
                }
            }
        });
    }

    // ── Resultados y tops ────────────────────────────────────

    private static final class Ranked {
        final UUID id;
        final String name;
        final boolean winner;
        final long survived;
        final int kills;

        Ranked(UUID id, String name, boolean winner, long survived, int kills) {
            this.id = id;
            this.name = name;
            this.winner = winner;
            this.survived = survived;
            this.kills = kills;
        }
    }

    /** Ganadores primero; luego los que aguantaron más; a igual tiempo, más kills. */
    private List<Ranked> ranking() {
        List<Ranked> list = new ArrayList<>();
        long total = plugin.game().totalSeconds();
        Set<UUID> winners = new HashSet<>(plugin.game().lastWinners());
        for (UUID id : plugin.game().participants()) {
            boolean alive = plugin.game().isAlive(id);
            long survived = alive ? total + 1 : plugin.stats().deathSecond(id).orElse(0L);
            list.add(new Ranked(id, plugin.stats().name(id), winners.contains(id), survived, plugin.stats().kills(id)));
        }
        list.sort(Comparator.comparing((Ranked r) -> r.winner).reversed()
                .thenComparing(Comparator.comparingLong((Ranked r) -> r.survived).reversed())
                .thenComparing(Comparator.comparingInt((Ranked r) -> r.kills).reversed()));
        return list;
    }

    private void showResults(List<Ranked> ranking) {
        clear(resultEntities);
        Location r = resultsCenter();
        buildPlatform(r, Compat.first(XMaterial.QUARTZ_BRICKS, XMaterial.QUARTZ_BLOCK), Material.DIAMOND_BLOCK);
        buildPodium(r);
        int podiumZ = HALF - 5;
        double[][] spots = {{0, 4, podiumZ}, {-2, 3, podiumZ}, {2, 2, podiumZ}};
        String[] medals = {"<gold><bold>1°</bold>", "<gray><bold>2°</bold>", "<#C87533><bold>3°</bold>"};
        Color[] colors = {Color.fromRGB(255, 200, 40), Color.fromRGB(200, 200, 210), Color.fromRGB(200, 117, 51)};
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            Ranked rank = ranking.get(i);
            Location where = r.clone().add(spots[i][0], spots[i][1], spots[i][2]);
            where.setYaw(180);
            Party team = plugin.teams().team(rank.id);
            Component description = Lang.mm(medals[i] + " <gray>· " + rank.kills + " kills"
                    + (team != null ? " · " + team.name() : ""));
            statue(where, rank.name, description, resultEntities, colors[i]);
        }

        Lang lang = plugin.lang();
        String winner = ranking.isEmpty() || !ranking.get(0).winner ? lang.raw("game.nobody")
                : ranking.stream().filter(x -> x.winner).map(x -> x.name).collect(Collectors.joining(", "));
        hologram(r.clone().add(0, 7.0, podiumZ), lang.get("lobby.results-title",
                Lang.p("match", plugin.settings().matchName()), Lang.p("winner", winner),
                Lang.p("time", Format.time(plugin.game().totalSeconds()))), resultEntities);

        List<Component> killLines = new ArrayList<>();
        killLines.add(lang.get("lobby.top-kills-title"));
        List<Ranked> byKills = new ArrayList<>(ranking);
        byKills.sort(Comparator.comparingInt((Ranked x) -> x.kills).reversed());
        for (int i = 0; i < Math.min(5, byKills.size()); i++) {
            killLines.add(lang.get("lobby.top-line", Lang.p("place", i + 1), Lang.p("player", byKills.get(i).name),
                    Lang.p("value", byKills.get(i).kills)));
        }
        killLines.add(Component.empty());
        killLines.add(lang.get("lobby.top-survival-title"));
        for (int i = 0; i < Math.min(5, ranking.size()); i++) {
            Ranked rank = ranking.get(i);
            killLines.add(lang.get("lobby.top-line", Lang.p("place", i + 1), Lang.p("player", rank.name),
                    Lang.p("value", rank.winner ? lang.raw("lobby.survived") : Format.time(rank.survived))));
        }
        hologram(r.clone().add(-6.5, 4.6, 2), join(killLines), resultEntities);

        List<Component> allTime = new ArrayList<>();
        allTime.add(lang.get("lobby.top-wins-title"));
        addTop(allTime, "wins", 5);
        allTime.add(Component.empty());
        allTime.add(lang.get("lobby.top-alltime-kills-title"));
        addTop(allTime, "kills", 5);
        hologram(r.clone().add(6.5, 4.6, 2), join(allTime), resultEntities);
    }

    private static Component join(List<Component> lines) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            out = out.append(lines.get(i));
            if (i < lines.size() - 1) {
                out = out.append(Component.newline());
            }
        }
        return out;
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
                entries.add(new AbstractMap.SimpleEntry<>(players.getString(id + ".name", "?"), value));
            }
        }
        entries.sort((a, b) -> b.getValue() - a.getValue());
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
            String path = "players." + rank.id;
            board.set(path + ".name", rank.name);
            board.set(path + ".games", board.getInt(path + ".games") + 1);
            board.set(path + ".kills", board.getInt(path + ".kills") + rank.kills);
            if (rank.winner) {
                board.set(path + ".wins", board.getInt(path + ".wins") + 1);
            }
        }
        try {
            board.save(boardFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar leaderboard.yml: " + ex.getMessage());
        }
    }

    /** Estadística histórica de un jugador (wins, kills o games) guardada en leaderboard.yml. */
    public int stat(UUID id, String field) {
        return board.getInt("players." + id + "." + field, 0);
    }

    public Location lobbySpawnIfReady() {
        return ready ? spawn() : null;
    }
}
