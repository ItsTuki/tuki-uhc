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
import cl.itstuki.uhc.util.Format;
import java.time.Duration;
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
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

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
    private List<UUID> lastWinners = List.of();
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
    private @Nullable BukkitTask ticker;

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
        if (!prepareEvent.callEvent()) {
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
            for (Party party : List.copyOf(plugin.teams().teams())) {
                for (UUID member : List.copyOf(party.members())) {
                    if (!participants.contains(member)) {
                        plugin.teams().remove(member, Party.Kind.TEAM);
                    }
                }
            }
            // Quien quedó sin equipo se reparte al azar para que nadie juegue solo sin querer.
            List<UUID> loose = participants.stream().filter(id -> !plugin.teams().hasTeam(id)).toList();
            if (!loose.isEmpty() && settings().teamSize() > 1) {
                plugin.teams().randomize(loose, settings().teamSize(), lang().raw("teams.default-name"));
                lang().broadcast("teams.auto-assigned", Lang.p("count", loose.size()));
            }
        }

        World world = mainWorld();
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setTime(0);
        world.setStorm(false);
        for (World other : Bukkit.getWorlds()) {
            other.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
            other.setGameRule(GameRules.LOCATOR_BAR, false);
        }

        each(UhcModule::onPrepare);

        state = GameState.SCATTERING;
        lang().broadcast("game.preparing");
        List<List<UUID>> groups = scatterGroups();
        pendingScatter = groups.size();
        List<Location> used = new ArrayList<>();
        int generation = matchId;
        for (List<UUID> group : groups) {
            findDrySpot(world, used, 0, generation, safe -> {
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
            });
        }
        return true;
    }

    /** Limpia al jugador, lo lleva a su punto de inicio y (antes de empezar) lo congela. */
    private void sendToSpot(Player player, Location spot, boolean freeze) {
        resetPlayer(player);
        player.teleportAsync(spot);
        scattered.add(player.getUniqueId());
        if (freeze) {
            freeze(player);
            frozen.add(player.getUniqueId());
        }
        player.getInventory().addItem(ItemStack.of(Material.OAK_BOAT));
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
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
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
                    if (item != null && !item.getType().isAir()) {
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
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location best = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            Location candidate = new Location(world, random.nextInt(-radius, radius + 1) + 0.5, 0, random.nextInt(-radius, radius + 1) + 0.5);
            best = candidate;
            boolean ok = used.stream().allMatch(other -> other.distanceSquared(candidate) >= minDistance * minDistance);
            if (ok) {
                break;
            }
        }
        return best;
    }

    /**
     * Punto al azar en tierra firme dentro del borde actual (para /uhcrevive), como en el reparto.
     * Carga los chunks en el momento: es un solo jugador.
     */
    public Location reviveSpot() {
        World world = mainWorld();
        double border = world.getWorldBorder().getSize() / 2 - 8;
        int radius = (int) Math.max(8, Math.min(settings().borderRadius() * 0.9, border));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location spot = null;
        for (int attempt = 0; attempt < 16; attempt++) {
            spot = new Location(world, random.nextInt(-radius, radius + 1) + 0.5, 0, random.nextInt(-radius, radius + 1) + 0.5);
            world.getChunkAt(spot.getBlockX() >> 4, spot.getBlockZ() >> 4);
            Block top = world.getBlockAt(spot.getBlockX(), groundY(world, spot.getBlockX(), spot.getBlockZ()), spot.getBlockZ());
            boolean wet = WET.contains(top.getType()) || top.isLiquid()
                    || (top.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged());
            if (!wet) {
                break;
            }
        }
        return safeLocation(spot);
    }

    private static final java.util.Set<Material> WET = java.util.EnumSet.of(Material.WATER, Material.LAVA, Material.KELP,
            Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS, Material.BUBBLE_COLUMN, Material.MAGMA_BLOCK,
            Material.LILY_PAD);

    /** Busca un punto en tierra firme (no océano ni lava). Tras 12 intentos usa el último y le pone una plataforma. */
    private void findDrySpot(World world, List<Location> used, int attempt, int generation, Consumer<Location> done) {
        Location spot = randomSpot(world, used);
        // Se reserva ya: todos los grupos piden su punto en el mismo tick, antes de que carguen los chunks.
        used.add(spot);
        world.getChunkAtAsync(spot.getBlockX() >> 4, spot.getBlockZ() >> 4).thenAccept(chunk -> {
            if (generation != matchId || state != GameState.SCATTERING) {
                return; // La partida se detuvo (/uhcstop, /sos) mientras se cargaba el chunk.
            }
            Block top = world.getBlockAt(spot.getBlockX(), groundY(world, spot.getBlockX(), spot.getBlockZ()), spot.getBlockZ());
            boolean wet = WET.contains(top.getType()) || top.isLiquid()
                    || (top.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged());
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
        });
    }

    private Location safeLocation(Location spot) {
        World world = spot.getWorld();
        Block top = world.getBlockAt(spot.getBlockX(), groundY(world, spot.getBlockX(), spot.getBlockZ()), spot.getBlockZ());
        if (top.getType() == Material.LAVA || top.getType() == Material.MAGMA_BLOCK) {
            top.setType(Material.STONE);
        }
        return top.getLocation().add(0.5, 1.2, 0.5);
    }

    public void resetPlayer(Player player) {
        plugin.spectators().clear(player);
        player.setGameMode(GameMode.SURVIVAL);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            for (AttributeModifier modifier : List.copyOf(maxHealth.getModifiers())) {
                if (modifier.getKey().getNamespace().equals(plugin.namespace())) {
                    maxHealth.removeModifier(modifier);
                }
            }
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        player.setLevel(0);
        player.setExp(0);
        player.setFireTicks(0);
        player.setInvulnerable(false);
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.getPersistentDataContainer().remove(plugin.key("hyper_count"));
        player.getPersistentDataContainer().remove(plugin.key("head_stolen"));
    }

    private void freeze(Player player) {
        int inf = PotionEffect.INFINITE_DURATION;
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, inf, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, inf, 9, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, inf, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, inf, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, inf, 0, false, false));
    }

    private void unfreeze(Player player) {
        for (PotionEffectType type : List.of(PotionEffectType.RESISTANCE, PotionEffectType.SLOWNESS,
                PotionEffectType.BLINDNESS, PotionEffectType.SATURATION, PotionEffectType.WATER_BREATHING)) {
            player.removePotionEffect(type);
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
        suddenDeath = false;
        pvpAnnounced = false;
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                unfreeze(player);
                frozen.remove(id);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
            } else if (alive.contains(id)) {
                scheduleOfflineElimination(id); // Se fue antes de empezar.
            }
        }
        // El lobby desaparece al empezar: el staff que no juega pasa a espectador sobre el mapa.
        Location watch = spots.values().stream().findFirst()
                .orElseGet(() -> new Location(mainWorld(), 0.5, groundY(mainWorld(), 0, 0) + 1, 0.5));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!participants.contains(player.getUniqueId()) && player.getGameMode() != GameMode.CREATIVE) {
                plugin.spectators().spectate(player);
                player.teleportAsync(watch.clone().add(0, 10, 0));
            }
        }
        Bukkit.getServer().showTitle(Title.title(lang().get("game.started-title"), lang().get("game.started-subtitle"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        lang().broadcast("game.started");
        each(UhcModule::onStart);
        new UhcStartEvent(java.util.Collections.unmodifiableSet(participants)).callEvent();
        startEpisode(1);
    }

    // ── Reloj ────────────────────────────────────────────────

    private void tick() {
        switch (state) {
            case STARTING -> tickCountdown();
            case PLAYING -> tickPlaying();
            default -> {
            }
        }
    }

    private void tickCountdown() {
        if (countdown <= 0) {
            begin();
            return;
        }
        Component title = Lang.mm("<gold><bold>" + countdown);
        Bukkit.getServer().showTitle(Title.title(title, lang().get("game.countdown-subtitle"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, countdown <= 3 ? 2f : 1f);
        }
        countdown--;
    }

    private void tickPlaying() {
        if (timed()) {
            tickTimed();
            return;
        }
        if (restLeft > 0) {
            restLeft--;
            Bukkit.getServer().sendActionBar(lang().get("game.rest-actionbar", Lang.p("seconds", restLeft)));
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
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, left <= 5 ? 1.6f : 1f);
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

    /** Partida por tiempo (Resource Rush siempre va por partes sin final). */
    public boolean timed() {
        return settings().timed() && !infiniteEpisodes();
    }

    private boolean suddenDeath;

    /** Por tiempo: un reloj total; las partes avanzan en silencio y al acabar el tiempo viene la muerte súbita. */
    private void tickTimed() {
        totalSeconds++;
        episodeSecond++;
        long limit = settings().timedSeconds();
        long left = limit - totalSeconds;
        if (left > 0 && (left == 1800 || left == 600 || left == 300 || left == 60 || left == 30 || left == 10 || left <= 5)) {
            lang().broadcast("game.timed-ending", Lang.p("time", Format.time(left)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, left <= 5 ? 1.6f : 1f);
            }
        }
        int length = settings().episodeSeconds();
        each(module -> module.onSecond(Math.max(0, length - episodeSecond)));
        if (state != GameState.PLAYING) {
            return;
        }
        if (left == 0) {
            timeUp();
        } else if (episodeSecond >= length && episode < settings().finalEpisode()) {
            episode++;
            episodeSecond = 0;
            if (!pvpAnnounced && pvpAllowed()) {
                pvpAnnounced = true;
                lang().broadcast("game.pvp-enabled");
                Bukkit.getServer().showTitle(Title.title(lang().get("game.pvp-title"), Component.empty()));
            }
            int number = episode;
            each(module -> module.onEpisodeStart(number));
            new UhcEpisodeStartEvent(number, pvpAllowed()).callEvent();
        }
    }

    /** Se acabó el tiempo: en Dragón gana la dragona; si no, muerte súbita hasta que quede un lado. */
    private void timeUp() {
        if (settings().mode() == UhcMode.DRAGON) {
            lang().broadcast("dragon.time-up");
            end(List.of(), "dragon.time-up-reason");
            return;
        }
        suddenDeath = true;
        Bukkit.getServer().showTitle(Title.title(lang().get("game.sudden-death-title"), lang().get("game.sudden-death-subtitle"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        lang().broadcast("game.sudden-death");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1f);
        }
        each(UhcModule::onSuddenDeath);
    }

    /** Muerte súbita: se acabó el tiempo y siguen dos o más lados vivos. */
    public boolean suddenDeath() {
        return suddenDeath;
    }

    /** Segundos que quedan de la partida por tiempo (0 en muerte súbita). */
    public long timeLeft() {
        return Math.max(0, settings().timedSeconds() - totalSeconds);
    }

    /** En Resource Rush el reloj no tiene final. */
    public boolean infiniteEpisodes() {
        return settings().mode() == UhcMode.RESOURCE_RUSH;
    }

    private void finishEpisode() {
        lang().broadcast("game.episode-end", Lang.p("episode", episode));
        if (settings().mode() == UhcMode.DRAGON && episode >= settings().finalEpisode()) {
            lang().broadcast("dragon.time-up");
            end(List.of(), "dragon.time-up-reason");
            return;
        }
        if (settings().enabled(Feature.REST)) {
            restLeft = settings().value(Feature.REST);
            Bukkit.getServer().showTitle(Title.title(lang().get("game.rest-title"),
                    lang().get("game.rest-subtitle", Lang.p("seconds", restLeft))));
            lang().broadcast("game.rest", Lang.p("episode", episode + 1), Lang.p("seconds", restLeft));
        } else {
            startEpisode(episode + 1);
        }
    }

    private void startEpisode(int number) {
        episode = number;
        episodeSecond = 0;
        boolean overtime = number > settings().finalEpisode() && !infiniteEpisodes();
        if (timed()) {
            // Por tiempo no hay títulos de parte: solo el aviso de inicio con la duración.
            lang().broadcast("game.timed-start", Lang.p("time", Format.time(settings().timedSeconds())));
        } else {
            lang().broadcast(overtime ? "game.overtime" : "game.episode-start", Lang.p("episode", number));
        }
        if (!overtime && !timed()) {
            Bukkit.getServer().showTitle(Title.title(lang().get("game.episode-title", Lang.p("episode", number)),
                    lang().get("game.episode-subtitle", Lang.c("phase", phaseLabel())),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(700))));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
        }
        if (!pvpAnnounced && pvpAllowed()) {
            pvpAnnounced = true;
            if (settings().enabled(Feature.KNIGHTS_PACT) && number > 1) {
                lang().broadcast("game.pact-ended");
            }
            lang().broadcast("game.pvp-enabled");
        }
        each(module -> module.onEpisodeStart(number));
        new UhcEpisodeStartEvent(number, pvpAllowed()).callEvent();
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
        return state == GameState.PLAYING && (devPvp || suddenDeath || episode >= pvpEpisode());
    }

    // ── Herramientas de desarrollo (/uhcdev, permiso tukiuhc.dev) ──

    /** Termina la parte actual (o el descanso) en el próximo segundo. */
    public boolean devSkip() {
        if (state != GameState.PLAYING) {
            return false;
        }
        if (restLeft > 0) {
            restLeft = 1;
        } else if (timed()) {
            // Salta al final del tramo actual (o al final de la partida en el último).
            long target = Math.min(settings().timedSeconds() - 1L, (long) episode * settings().episodeSeconds() - 1L);
            long jump = Math.max(0, target - totalSeconds);
            totalSeconds += jump;
            episodeSecond += (int) jump;
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
        int clamped = Math.max(0, Math.min(second, settings().episodeSeconds() - 1));
        if (timed()) {
            totalSeconds += clamped - episodeSecond;
        }
        episodeSecond = clamped;
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

    public void eliminate(Player player, @Nullable Player killer, Location where) {
        eliminate(player.getUniqueId(), player.getName(), killer, where);
    }

    /** Clave única de un lado (equipo o jugador suelto): los nombres de equipo se pueden repetir. */
    private String sideKey(UUID id) {
        Party team = plugin.teams().team(id);
        return team != null ? "t" + team.id() : "p" + id;
    }

    public void eliminate(UUID id, String playerName, @Nullable Player killer, Location where) {
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
        new UhcPlayerEliminatedEvent(id, killer, alive.size()).callEvent();
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
        plugin.spectators().clear(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(where);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max != null ? max.getValue() : 20);
        player.setFoodLevel(20);
        return true;
    }

    public void checkWin() {
        if (state != GameState.PLAYING) {
            return;
        }
        if (alive.isEmpty()) {
            end(List.of(), "game.no-winner");
            return;
        }
        UhcMode mode = settings().mode();
        if (mode == UhcMode.DRAGON) {
            return; // Solo se gana matando a la dragona.
        }
        if (plugin.trolls().onlyRatsAlive()) {
            end(List.copyOf(alive), "trolls.rats-win");
            return;
        }
        Set<String> sides = new LinkedHashSet<>();
        for (UUID id : alive) {
            Party team = plugin.teams().team(id);
            sides.add(team != null && mode != UhcMode.SOLITARIOS ? "t" + team.id() : "p" + id);
        }
        if (sides.size() <= 1 && participants.size() > 1) {
            end(List.copyOf(alive), "game.win-reason-last");
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
        List<UUID> winnerList = List.copyOf(winners);
        lastWinners = winnerList;
        String winnerNames = winnerList.isEmpty() ? lang().raw("game.nobody")
                : String.join(", ", winnerList.stream().map(plugin.stats()::name).toList());
        Party winnerTeam = winnerList.isEmpty() ? null : plugin.teams().team(winnerList.getFirst());
        String label = winnerTeam != null && settings().mode() != UhcMode.SOLITARIOS ? winnerTeam.name() + " (" + winnerNames + ")" : winnerNames;
        if (winnerTeam != null) {
            plugin.stats().teamEliminated(winnerTeam.name(), totalSeconds + 1);
        } else if (!winnerList.isEmpty()) {
            plugin.stats().teamEliminated(plugin.stats().name(winnerList.getFirst()), totalSeconds + 1);
        }

        Bukkit.getServer().showTitle(Title.title(lang().get("game.end-title"),
                winnerList.isEmpty() ? lang().get(reasonKey) : lang().get("game.end-subtitle", Lang.p("winner", label)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(6), Duration.ofSeconds(1))));
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
        new UhcGameEndEvent(winnerList, totalSeconds).callEvent();
    }

    /** Deja todo como en el lobby. */
    public void reset() {
        GameState previous = state;
        state = GameState.LOBBY;
        restLeft = 0;
        episode = 0;
        episodeSecond = 0;
        totalSeconds = 0;
        suddenDeath = false;
        each(UhcModule::onReset);
        matchId++;
        devPvp = false;
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
            world.setGameRule(GameRules.LOCATOR_BAR, true);
        }
        mainWorld().setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
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

    public @Nullable Location spot(UUID id) {
        return spots.get(id);
    }

    public World mainWorld() {
        return Bukkit.getWorlds().getFirst();
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
