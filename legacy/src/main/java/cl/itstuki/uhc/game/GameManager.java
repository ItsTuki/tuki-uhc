package cl.itstuki.uhc.game;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.api.event.UhcEpisodeStartEvent;
import cl.itstuki.uhc.api.event.UhcGameEndEvent;
import cl.itstuki.uhc.api.event.UhcPlayerEliminatedEvent;
import cl.itstuki.uhc.api.event.UhcPrepareEvent;
import cl.itstuki.uhc.api.event.UhcStartEvent;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.config.TeamMode;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.config.UhcSettings;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.util.Show;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Estado y reloj de la partida: preparar, iniciar, episodios, eliminaciones y final. */
public final class GameManager {
    private final TukiUHC plugin;
    private final List<UhcModule> modules = new ArrayList<>();
    // Concurrentes: el chat (asíncrono) los lee mientras el hilo principal los cambia.
    private final Set<UUID> participants = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> alive = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> spots = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Set<String> eliminatedTeams = new LinkedHashSet<>();
    private List<UUID> lastWinners = Collections.emptyList();
    /** Jugadores ya llevados a su punto de inicio y los que siguen congelados. */
    private final Set<UUID> scattered = new java.util.HashSet<>();
    private final Set<UUID> frozen = new java.util.HashSet<>();
    /** Desconectados en partida: dónde y con qué se fueron (para soltarlo si no vuelven). */
    private final Map<UUID, Location> offlineAt = new HashMap<>();
    private final Map<UUID, ItemStack[]> offlineItems = new HashMap<>();
    private final Set<UUID> offlineEliminated = new java.util.HashSet<>();
    /** Sube con cada partida: las tareas pendientes de una partida anterior se descartan. */
    private int matchId;

    private GameState state = GameState.LOBBY;
    private int episode;
    private int episodeSecond;
    private long totalSeconds;
    private int restLeft;
    /** Modo dev: PvP forzado aunque no haya llegado su parte. */
    private boolean devPvp;
    private int countdown;
    private int pendingScatter;
    private boolean pvpAnnounced;
    private BukkitTask ticker;

    public GameManager(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public void register(UhcModule module) {
        modules.add(module);
        Bukkit.getPluginManager().registerEvents(module, plugin);
    }

    public void startTicker() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
        }
    }

    private void each(Consumer<UhcModule> action) {
        for (UhcModule module : modules) {
            try {
                action.accept(module);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Error en " + module.getClass().getSimpleName() + ": " + ex);
                ex.printStackTrace();
            }
        }
    }

    private UhcSettings settings() {
        return plugin.settings();
    }

    private Lang lang() {
        return plugin.lang();
    }

    // ── Preparar ─────────────────────────────────────────────

    public boolean prepare(CommandSender sender) {
        if (state != GameState.LOBBY) {
            lang().send(sender, "game.already-running");
            return false;
        }
        participants.clear();
        alive.clear();
        spots.clear();
        scattered.clear();
        frozen.clear();
        offlineAt.clear();
        offlineItems.clear();
        offlineEliminated.clear();
        deathLocations.clear();
        eliminatedTeams.clear();
        plugin.stats().reset();
        matchId++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Juegan todos los conectados salvo el staff con tukiuhc.spectator y quien está en vanish.
            if (!player.hasPermission("tukiuhc.spectator") && !cl.itstuki.uhc.util.Vanish.isVanished(player)) {
                participants.add(player.getUniqueId());
                alive.add(player.getUniqueId());
                plugin.stats().register(player.getUniqueId(), player.getName());
            }
        }
        if (participants.isEmpty()) {
            lang().send(sender, "game.no-players");
            return false;
        }
        if (settings().mode() == UhcMode.TROLLS_RATAS && participants.size() < 3) {
            lang().send(sender, "trolls.need-three");
            participants.clear();
            alive.clear();
            return false;
        }
        UhcPrepareEvent prepareEvent = new UhcPrepareEvent(java.util.Collections.unmodifiableSet(participants));
        Bukkit.getPluginManager().callEvent(prepareEvent);
        if (prepareEvent.isCancelled()) {
            lang().send(sender, "game.prepare-cancelled");
            participants.clear();
            alive.clear();
            return false;
        }

        TeamMode teamMode = settings().teamMode();
        if (teamMode == TeamMode.SOLO || teamMode == TeamMode.RANDOM || teamMode == TeamMode.PROXIMITY
                || settings().mode() == UhcMode.SOLITARIOS) {
            plugin.teams().clearTeams();
        } else {
            // En modo manual se sacan de los equipos a quienes no van a jugar.
            for (Party party : new ArrayList<>(plugin.teams().teams())) {
                for (UUID member : new ArrayList<>(party.members())) {
                    if (!participants.contains(member)) {
                        plugin.teams().remove(member, Party.Kind.TEAM);
                    }
                }
            }
            // Quien quedó sin equipo se reparte al azar para que nadie juegue solo sin querer.
            List<UUID> loose = participants.stream().filter(id -> !plugin.teams().hasTeam(id)).collect(Collectors.toList());
            if (!loose.isEmpty() && settings().teamSize() > 1) {
                plugin.teams().randomize(loose, settings().teamSize(), lang().raw("teams.default-name"));
                lang().broadcast("teams.auto-assigned", Lang.p("count", loose.size()));
            }
        }

        World world = mainWorld();
        Compat.gameRule(world, "doImmediateRespawn", true);
        Compat.gameRule(world, "locatorBar", false);
        world.setTime(0);
        world.setStorm(false);
        for (World other : Bukkit.getWorlds()) {
            Compat.gameRule(other, "naturalRegeneration", false);
            Compat.gameRule(other, "locatorBar", false);
        }

        each(UhcModule::onPrepare);

        state = GameState.SCATTERING;
        lang().broadcast("game.preparing");
        List<List<UUID>> groups = scatterGroups();
        pendingScatter = groups.size();
        List<Location> used = new ArrayList<>();
        int generation = matchId;
        int delay = 0;
        for (List<UUID> group : groups) {
            // Un grupo por tick: en 1.8 los chunks se cargan en el hilo principal.
            Bukkit.getScheduler().runTaskLater(plugin, () -> findDrySpot(world, used, 0, generation, safe -> {
                for (UUID id : group) {
                    spots.put(id, safe);
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        sendToSpot(player, safe, true);
                    }
                }
                pendingScatter--;
                if (pendingScatter <= 0 && state == GameState.SCATTERING) {
                    state = GameState.PREPARED;
                    lang().broadcast("game.prepared");
                }
            }), delay++);
        }
        return true;
    }

    /** Limpia al jugador, lo lleva a su punto de inicio y (antes de empezar) lo congela. */
    private void sendToSpot(Player player, Location spot, boolean freeze) {
        resetPlayer(player);
        player.teleport(spot);
        scattered.add(player.getUniqueId());
        if (freeze) {
            freeze(player);
            frozen.add(player.getUniqueId());
        }
        player.getInventory().addItem(Compat.item(XMaterial.OAK_BOAT));
    }

    /**
     * Un participante vivo volvió a entrar: si no alcanzó a ser repartido se le lleva a su punto;
     * si la partida ya empezó y seguía congelado, se le descongela.
     */
    public void handleRejoin(Player player) {
        UUID id = player.getUniqueId();
        if (!alive.contains(id) || state == GameState.LOBBY || state == GameState.ENDED) {
            return;
        }
        offlineAt.remove(id);
        offlineItems.remove(id);
        Location spot = spots.get(id);
        if (!scattered.contains(id) && spot != null) {
            sendToSpot(player, spot, state != GameState.PLAYING);
            if (state == GameState.PLAYING) {
                player.setHealth(player.getMaxHealth());
            }
        } else if (state == GameState.PLAYING && frozen.remove(id)) {
            unfreeze(player);
        }
    }

    /** Un eliminado mientras estaba desconectado vuelve: sus cosas ya se soltaron. */
    public boolean consumeOfflineElimination(UUID id) {
        return offlineEliminated.remove(id);
    }

    /** Un participante vivo se desconectó con la partida en curso. */
    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        if (state != GameState.PLAYING || !alive.contains(id)) {
            return;
        }
        offlineAt.put(id, player.getLocation().clone());
        offlineItems.put(id, player.getInventory().getContents().clone());
        scheduleOfflineElimination(id);
    }

    private void scheduleOfflineElimination(UUID id) {
        int minutes = plugin.getConfig().getInt("death.offline-elimination-minutes", 10);
        if (minutes <= 0) {
            return;
        }
        int generation = matchId;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (generation != matchId || state != GameState.PLAYING || !alive.contains(id) || Bukkit.getPlayer(id) != null) {
                return;
            }
            Location where = offlineAt.remove(id);
            if (where == null) {
                where = spots.getOrDefault(id, mainWorld().getSpawnLocation());
            }
            ItemStack[] items = offlineItems.remove(id);
            if (items != null) {
                for (ItemStack item : items) {
                    if (item != null && !Compat.isAir(item.getType())) {
                        where.getWorld().dropItemNaturally(where, item);
                    }
                }
            }
            offlineEliminated.add(id);
            lang().broadcast("game.offline-eliminated", Lang.p("player", plugin.stats().name(id)), Lang.p("minutes", minutes));
            eliminate(id, plugin.stats().name(id), null, where);
        }, 20L * 60 * minutes);
    }

    /** Altura del suelo en (x, z) sin contar el lobby flotante. */
    public int groundY(World world, int x, int z) {
        return plugin.lobby().groundY(world, x, z);
    }

    private List<List<UUID>> scatterGroups() {
        List<List<UUID>> groups = new ArrayList<>();
        Set<UUID> done = new LinkedHashSet<>();
        boolean together = settings().enabled(Feature.TEAMMATE_TELEPORT);
        for (UUID id : participants) {
            if (done.contains(id)) {
                continue;
            }
            Party team = plugin.teams().team(id);
            List<UUID> group = new ArrayList<>();
            if (together && team != null) {
                for (UUID member : team.members()) {
                    if (participants.contains(member) && done.add(member)) {
                        group.add(member);
                    }
                }
            } else {
                done.add(id);
                group.add(id);
            }
            groups.add(group);
        }
        return groups;
    }

    private Location randomSpot(World world, List<Location> used) {
        int radius = (int) (settings().borderRadius() * 0.9);
        double minDistance = Math.min(150, radius / 4.0);
        java.util.Random random = ThreadLocalRandom.current();
        Location best = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            Location candidate = new Location(world, random.nextInt(2 * radius + 1) - radius + 0.5, 0, random.nextInt(2 * radius + 1) - radius + 0.5);
            best = candidate;
            boolean ok = used.stream().allMatch(other -> other.distanceSquared(candidate) >= minDistance * minDistance);
            if (ok) {
                break;
            }
        }
        return best;
    }

    /** Agua, lava, algas, nenúfares… (por nombre: así sirve en todas las versiones). */
    private static boolean wet(Block block) {
        String name = block.getType().name();
        return block.isLiquid() || name.contains("WATER") || name.contains("LAVA") || name.contains("KELP")
                || name.contains("SEAGRASS") || name.contains("BUBBLE") || name.contains("MAGMA") || name.contains("LILY");
    }

    /** Busca un punto en tierra firme (no océano ni lava). Tras 12 intentos usa el último y le pone una plataforma. */
    private void findDrySpot(World world, List<Location> used, int attempt, int generation, Consumer<Location> done) {
        Location spot = randomSpot(world, used);
        // Se reserva ya: todos los grupos piden su punto en el mismo tick, antes de que carguen los chunks.
        used.add(spot);
        {
            if (generation != matchId || state != GameState.SCATTERING) {
                return; // La partida se detuvo (/uhcstop, /sos) mientras se repartía.
            }
            world.getChunkAt(spot.getBlockX() >> 4, spot.getBlockZ() >> 4).load(true);
            Block top = world.getBlockAt(spot.getBlockX(), groundY(world, spot.getBlockX(), spot.getBlockZ()), spot.getBlockZ());
            boolean wet = wet(top);
            if (wet && attempt < 12) {
                used.remove(spot);
                findDrySpot(world, used, attempt + 1, generation, done);
                return;
            }
            if (wet) {
                // Sin tierra cerca: plataforma de 3x3 sobre el agua.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        top.getRelative(dx, 0, dz).setType(Material.STONE);
                    }
                }
            }
            done.accept(safeLocation(spot));
        }
    }

    private Location safeLocation(Location spot) {
        World world = spot.getWorld();
        Block top = world.getBlockAt(spot.getBlockX(), groundY(world, spot.getBlockX(), spot.getBlockZ()), spot.getBlockZ());
        if (top.getType().name().contains("LAVA") || top.getType().name().contains("MAGMA")) {
            top.setType(Material.STONE);
        }
        return top.getLocation().add(0.5, 1.2, 0.5);
    }

    public void resetPlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        Compat.resetMaxHealth(player);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        player.setLevel(0);
        player.setExp(0);
        player.setFireTicks(0);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getEnderChest().clear();
        Compat.clearEffects(player);
        plugin.items().forget(player.getUniqueId());
    }

    private static final XPotion[] FREEZE = {XPotion.RESISTANCE, XPotion.SLOWNESS, XPotion.BLINDNESS,
            XPotion.SATURATION, XPotion.WATER_BREATHING};

    private void freeze(Player player) {
        int inf = Integer.MAX_VALUE;
        Compat.effect(player, XPotion.RESISTANCE, inf, 4, false);
        Compat.effect(player, XPotion.SLOWNESS, inf, 9, false);
        Compat.effect(player, XPotion.BLINDNESS, inf, 0, false);
        Compat.effect(player, XPotion.SATURATION, inf, 0, false);
        Compat.effect(player, XPotion.WATER_BREATHING, inf, 0, false);
    }

    private void unfreeze(Player player) {
        for (XPotion type : FREEZE) {
            Compat.removeEffect(player, type);
        }
    }

    // ── Iniciar ──────────────────────────────────────────────

    public boolean start(CommandSender sender, boolean force) {
        if (state == GameState.SCATTERING) {
            lang().send(sender, "game.still-scattering", Lang.p("count", pendingScatter));
            return false;
        }
        if (state != GameState.PREPARED) {
            lang().send(sender, "game.prepare-first");
            return false;
        }
        if (!force) {
            List<String> missing = new ArrayList<>();
            for (UUID id : participants) {
                if (Bukkit.getPlayer(id) == null) {
                    missing.add(plugin.stats().name(id));
                }
            }
            if (!missing.isEmpty()) {
                lang().send(sender, "game.not-ready", Lang.p("players", String.join(", ", missing)));
                return false;
            }
        }
        state = GameState.STARTING;
        countdown = 10;
        return true;
    }

    private void begin() {
        state = GameState.PLAYING;
        totalSeconds = 0;
        pvpAnnounced = false;
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                unfreeze(player);
                frozen.remove(id);
                player.setHealth(player.getMaxHealth());
                Compat.sound(player, XSound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
            } else if (alive.contains(id)) {
                scheduleOfflineElimination(id); // Se fue antes de empezar.
            }
        }
        // El lobby desaparece al empezar: el staff que no juega pasa a espectador sobre el mapa.
        Location watch = spots.values().stream().findFirst()
                .orElseGet(() -> new Location(mainWorld(), 0.5, groundY(mainWorld(), 0, 0) + 1, 0.5));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!participants.contains(player.getUniqueId()) && player.getGameMode() != GameMode.CREATIVE) {
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(watch.clone().add(0, 10, 0));
            }
        }
        Show.titleAll(Show.title(lang().get("game.started-title"), lang().get("game.started-subtitle"), 200, 3000, 1000));
        lang().broadcast("game.started");
        each(UhcModule::onStart);
        Bukkit.getPluginManager().callEvent(new UhcStartEvent(java.util.Collections.unmodifiableSet(participants)));
        startEpisode(1);
    }

    // ── Reloj ────────────────────────────────────────────────

    private void tick() {
        if (state == GameState.STARTING) {
            tickCountdown();
        } else if (state == GameState.PLAYING) {
            tickPlaying();
        }
    }

    private void tickCountdown() {
        if (countdown <= 0) {
            begin();
            return;
        }
        Component title = Lang.mm("<gold><bold>" + countdown);
        Show.titleAll(Show.title(title, lang().get("game.countdown-subtitle"), 0, 1100, 0));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Compat.sound(player, XSound.BLOCK_NOTE_BLOCK_PLING, 1f, countdown <= 3 ? 2f : 1f);
        }
        countdown--;
    }

    private void tickPlaying() {
        if (restLeft > 0) {
            restLeft--;
            Show.actionBarAll(lang().get("game.rest-actionbar", Lang.p("seconds", restLeft)));
            if (restLeft == 0) {
                startEpisode(episode + 1);
            }
            return;
        }
        totalSeconds++;
        episodeSecond++;
        int length = settings().episodeSeconds();
        int left = Math.max(0, length - episodeSecond);
        if (!infiniteEpisodes() && (left == 30 || left == 10 || (left >= 1 && left <= 5))) {
            lang().broadcast("game.episode-ending", Lang.p("episode", episode), Lang.p("seconds", left));
            for (Player player : Bukkit.getOnlinePlayers()) {
                Compat.sound(player, XSound.BLOCK_NOTE_BLOCK_HAT, 0.8f, left <= 5 ? 1.6f : 1f);
            }
        }
        each(module -> module.onSecond(left));
        if (state != GameState.PLAYING) {
            return;
        }
        if (episodeSecond >= length && !infiniteEpisodes()) {
            finishEpisode();
        } else if (episodeSecond >= length) {
            episode++;
            episodeSecond = 0;
            each(module -> module.onEpisodeStart(episode));
        }
    }

    /** En Resource Rush el reloj no tiene final. */
    public boolean infiniteEpisodes() {
        return settings().mode() == UhcMode.RESOURCE_RUSH;
    }

    private void finishEpisode() {
        lang().broadcast("game.episode-end", Lang.p("episode", episode));
        if (settings().mode() == UhcMode.DRAGON && episode >= settings().finalEpisode()) {
            lang().broadcast("dragon.time-up");
            end(Collections.emptyList(), "dragon.time-up-reason");
            return;
        }
        if (settings().enabled(Feature.REST)) {
            restLeft = settings().value(Feature.REST);
            Show.titleAll(Show.title(lang().get("game.rest-title"),
                    lang().get("game.rest-subtitle", Lang.p("seconds", restLeft)), 500, 3500, 1000));
            lang().broadcast("game.rest", Lang.p("episode", episode + 1), Lang.p("seconds", restLeft));
        } else {
            startEpisode(episode + 1);
        }
    }

    private void startEpisode(int number) {
        episode = number;
        episodeSecond = 0;
        boolean overtime = number > settings().finalEpisode() && !infiniteEpisodes();
        lang().broadcast(overtime ? "game.overtime" : "game.episode-start", Lang.p("episode", number));
        if (!overtime) {
            Show.titleAll(Show.title(lang().get("game.episode-title", Lang.p("episode", number)),
                    lang().get("game.episode-subtitle", Lang.c("phase", phaseLabel())), 300, 2000, 700));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Compat.sound(player, Compat.atLeast(14) ? XSound.BLOCK_BELL_USE : XSound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        }
        if (!pvpAnnounced && pvpAllowed()) {
            pvpAnnounced = true;
            if (settings().enabled(Feature.KNIGHTS_PACT) && number > 1) {
                lang().broadcast("game.pact-ended");
            }
            lang().broadcast("game.pvp-enabled");
        }
        each(module -> module.onEpisodeStart(number));
        Bukkit.getPluginManager().callEvent(new UhcEpisodeStartEvent(number, pvpAllowed()));
    }

    // ── Reglas ───────────────────────────────────────────────

    public int pvpEpisode() {
        int pvp = settings().pvpEpisode();
        if (settings().enabled(Feature.KNIGHTS_PACT)) {
            pvp = Math.max(pvp, settings().featureEpisode(Feature.KNIGHTS_PACT));
        }
        return pvp;
    }

    public boolean pvpAllowed() {
        return state == GameState.PLAYING && (devPvp || episode >= pvpEpisode());
    }

    // ── Herramientas de desarrollo (/uhcdev, permiso tukiuhc.dev) ──

    /** Termina la parte actual (o el descanso) en el próximo segundo. */
    public boolean devSkip() {
        if (state != GameState.PLAYING) {
            return false;
        }
        if (restLeft > 0) {
            restLeft = 1;
        } else {
            episodeSecond = Math.max(episodeSecond, settings().episodeSeconds() - 1);
        }
        return true;
    }

    /** Mueve el reloj de la parte actual al segundo indicado. */
    public boolean devSetSecond(int second) {
        if (state != GameState.PLAYING || restLeft > 0) {
            return false;
        }
        episodeSecond = Math.max(0, Math.min(second, settings().episodeSeconds() - 1));
        return true;
    }

    public boolean devTogglePvp() {
        devPvp = !devPvp;
        return devPvp;
    }

    public int episodeSecond() {
        return episodeSecond;
    }

    public Component phaseLabel() {
        if (!pvpAllowed()) {
            return lang().get("phase.pve");
        }
        return lang().get(settings().mode() == UhcMode.DRAGON ? "phase.pvd" : "phase.pvp");
    }

    // ── Eliminaciones ────────────────────────────────────────

    public void eliminate(Player player, Player killer, Location where) {
        eliminate(player.getUniqueId(), player.getName(), killer, where);
    }

    /** Clave única de un lado (equipo o jugador suelto): los nombres de equipo se pueden repetir. */
    private String sideKey(UUID id) {
        Party team = plugin.teams().team(id);
        return team != null ? "t" + team.id() : "p" + id;
    }

    public void eliminate(UUID id, String playerName, Player killer, Location where) {
        if (!alive.remove(id)) {
            return;
        }
        deathLocations.put(id, where.clone());
        plugin.stats().death(id, totalSeconds);
        if (killer != null && !killer.getUniqueId().equals(id)) {
            Party killerTeam = plugin.teams().team(killer.getUniqueId());
            plugin.stats().addKill(killer.getUniqueId(), sideKey(killer.getUniqueId()),
                    killerTeam != null ? killerTeam.name() : killer.getName());
        }
        Party team = plugin.teams().team(id);
        boolean teamWiped = team == null || team.members().stream().noneMatch(alive::contains);
        if (teamWiped) {
            String name = team != null ? team.name() : playerName;
            if (eliminatedTeams.add(sideKey(id))) {
                plugin.stats().teamEliminated(name, totalSeconds);
                if (team != null) {
                    lang().broadcast("game.team-eliminated", Lang.p("team", name));
                }
            }
        }
        each(module -> module.onEliminate(id, killer));
        Bukkit.getPluginManager().callEvent(new UhcPlayerEliminatedEvent(id, killer, alive.size()));
        Bukkit.getScheduler().runTask(plugin, this::checkWin);
    }

    /** Vuelve a meter a un jugador muerto en la partida (resurrección o /uhcrevive). */
    public boolean revive(Player player, Location where) {
        UUID id = player.getUniqueId();
        if (!participants.contains(id) || alive.contains(id)) {
            return false;
        }
        alive.add(id);
        eliminatedTeams.remove(sideKey(id));
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(where);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        return true;
    }

    public void checkWin() {
        if (state != GameState.PLAYING) {
            return;
        }
        if (alive.isEmpty()) {
            end(Collections.emptyList(), "game.no-winner");
            return;
        }
        UhcMode mode = settings().mode();
        if (mode == UhcMode.DRAGON) {
            return; // Solo se gana matando a la dragona.
        }
        if (plugin.trolls().onlyRatsAlive()) {
            end(new ArrayList<>(alive), "trolls.rats-win");
            return;
        }
        Set<String> sides = new LinkedHashSet<>();
        for (UUID id : alive) {
            Party team = plugin.teams().team(id);
            sides.add(team != null && mode != UhcMode.SOLITARIOS ? "t" + team.id() : "p" + id);
        }
        if (sides.size() <= 1 && participants.size() > 1) {
            end(new ArrayList<>(alive), "game.win-reason-last");
        }
    }

    // ── Final ────────────────────────────────────────────────

    public void end(Collection<UUID> winners, String reasonKey) {
        if (state == GameState.ENDED || state == GameState.LOBBY) {
            return;
        }
        if (state != GameState.PLAYING) {
            // Aún no empezaba: no hay estadísticas que mostrar, se vuelve al lobby.
            reset();
            return;
        }
        state = GameState.ENDED;
        restLeft = 0;
        List<UUID> winnerList = Collections.unmodifiableList(new ArrayList<>(winners));
        lastWinners = winnerList;
        String winnerNames = winnerList.isEmpty() ? lang().raw("game.nobody")
                : winnerList.stream().map(plugin.stats()::name).collect(Collectors.joining(", "));
        Party winnerTeam = winnerList.isEmpty() ? null : plugin.teams().team(winnerList.get(0));
        String label = winnerTeam != null && settings().mode() != UhcMode.SOLITARIOS ? winnerTeam.name() + " (" + winnerNames + ")" : winnerNames;
        if (winnerTeam != null) {
            plugin.stats().teamEliminated(winnerTeam.name(), totalSeconds + 1);
        } else if (!winnerList.isEmpty()) {
            plugin.stats().teamEliminated(plugin.stats().name(winnerList.get(0)), totalSeconds + 1);
        }

        Show.titleAll(Show.title(lang().get("game.end-title"),
                winnerList.isEmpty() ? lang().get(reasonKey) : lang().get("game.end-subtitle", Lang.p("winner", label)), 300, 6000, 1000));
        EndReport.broadcast(plugin, winnerList, label, totalSeconds);
        EndReport.saveImage(plugin, winnerList, label, totalSeconds);
        for (UUID id : winnerList) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                for (int i = 0; i < 3; i++) {
                    player.getWorld().spawn(player.getLocation(), Firework.class);
                }
            }
        }
        each(UhcModule::onEnd);
        Bukkit.getPluginManager().callEvent(new UhcGameEndEvent(winnerList, totalSeconds));
    }

    /** Deja todo como en el lobby. */
    public void reset() {
        GameState previous = state;
        state = GameState.LOBBY;
        restLeft = 0;
        episode = 0;
        episodeSecond = 0;
        totalSeconds = 0;
        each(UhcModule::onReset);
        matchId++;
        devPvp = false;
        for (World world : Bukkit.getWorlds()) {
            Compat.gameRule(world, "naturalRegeneration", true);
            Compat.gameRule(world, "locatorBar", true);
        }
        Compat.gameRule(mainWorld(), "doImmediateRespawn", false);
        if (previous != GameState.LOBBY) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (participants.contains(player.getUniqueId()) && player.getGameMode() != GameMode.CREATIVE) {
                    // Vuelven sin restos de la partida (vida extra, efectos, ítems).
                    resetPlayer(player);
                } else if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
        participants.clear();
        alive.clear();
        spots.clear();
        scattered.clear();
        frozen.clear();
        offlineAt.clear();
        offlineItems.clear();
    }

    // ── Consultas ────────────────────────────────────────────

    /** Ganadores de la última partida terminada (vacío si no hubo). */
    public List<UUID> lastWinners() {
        return lastWinners;
    }

    public GameState state() {
        return state;
    }

    public boolean isRunning() {
        return state == GameState.PLAYING;
    }

    public boolean inGame() {
        return state != GameState.LOBBY && state != GameState.ENDED;
    }

    public int episode() {
        return episode;
    }

    public int secondsLeftInEpisode() {
        return Math.max(0, settings().episodeSeconds() - episodeSecond);
    }

    public int restLeft() {
        return restLeft;
    }

    public long totalSeconds() {
        return totalSeconds;
    }

    public boolean isParticipant(UUID id) {
        return participants.contains(id);
    }

    public boolean isAlive(UUID id) {
        return alive.contains(id);
    }

    public Set<UUID> alive() {
        return alive;
    }

    public Set<UUID> participants() {
        return participants;
    }

    public Map<UUID, Location> deathLocations() {
        return deathLocations;
    }

    public Location spot(UUID id) {
        return spots.get(id);
    }

    public World mainWorld() {
        return Bukkit.getWorlds().get(0);
    }

    /** Equipos con al menos un jugador vivo. */
    public long aliveSides() {
        Set<String> sides = new LinkedHashSet<>();
        for (UUID id : alive) {
            Party team = plugin.teams().team(id);
            sides.add(team != null ? "t" + team.id() : "p" + id);
        }
        return sides.size();
    }
}
