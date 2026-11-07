package cl.itstuki.uhc.game;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.config.TeamMode;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.config.UhcSettings;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
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
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Map<UUID, Location> spots = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Set<String> eliminatedTeams = new LinkedHashSet<>();

    private GameState state = GameState.LOBBY;
    private int episode;
    private int episodeSecond;
    private long totalSeconds;
    private int restLeft;
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
        deathLocations.clear();
        eliminatedTeams.clear();
        plugin.stats().reset();
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Juegan todos los conectados salvo el staff con tukiuhc.spectator.
            if (!player.hasPermission("tukiuhc.spectator")) {
                participants.add(player.getUniqueId());
                alive.add(player.getUniqueId());
                plugin.stats().register(player.getUniqueId(), player.getName());
            }
        }
        if (participants.isEmpty()) {
            lang().send(sender, "game.no-players");
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
        for (List<UUID> group : groups) {
            Location spot = randomSpot(world, used);
            used.add(spot);
            world.getChunkAtAsync(spot.getBlockX() >> 4, spot.getBlockZ() >> 4).thenAccept(chunk -> {
                Location safe = safeLocation(spot);
                for (UUID id : group) {
                    spots.put(id, safe);
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        resetPlayer(player);
                        player.teleportAsync(safe);
                        freeze(player);
                        player.getInventory().addItem(ItemStack.of(Material.OAK_BOAT));
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

    private static Location safeLocation(Location spot) {
        World world = spot.getWorld();
        Block top = world.getHighestBlockAt(spot.getBlockX(), spot.getBlockZ());
        if (top.getType() == Material.LAVA || top.getType() == Material.MAGMA_BLOCK) {
            top.setType(Material.STONE);
        }
        return top.getLocation().add(0.5, 1.2, 0.5);
    }

    public void resetPlayer(Player player) {
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
    }

    private void unfreeze(Player player) {
        for (PotionEffectType type : List.of(PotionEffectType.RESISTANCE, PotionEffectType.SLOWNESS,
                PotionEffectType.BLINDNESS, PotionEffectType.SATURATION)) {
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
        pvpAnnounced = false;
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                unfreeze(player);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
            }
        }
        Bukkit.getServer().showTitle(Title.title(lang().get("game.started-title"), lang().get("game.started-subtitle"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        lang().broadcast("game.started");
        each(UhcModule::onStart);
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
        lang().broadcast(overtime ? "game.overtime" : "game.episode-start", Lang.p("episode", number));
        if (!overtime) {
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
        return state == GameState.PLAYING && episode >= pvpEpisode();
    }

    public Component phaseLabel() {
        if (!pvpAllowed()) {
            return lang().get("phase.pve");
        }
        return lang().get(settings().mode() == UhcMode.DRAGON ? "phase.pvd" : "phase.pvp");
    }

    // ── Eliminaciones ────────────────────────────────────────

    public void eliminate(Player player, @Nullable Player killer, Location where) {
        UUID id = player.getUniqueId();
        if (!alive.remove(id)) {
            return;
        }
        deathLocations.put(id, where.clone());
        plugin.stats().death(id, totalSeconds);
        if (killer != null && !killer.equals(player)) {
            Party killerTeam = plugin.teams().team(killer.getUniqueId());
            plugin.stats().addKill(killer.getUniqueId(), killerTeam != null ? killerTeam.name() : killer.getName());
        }
        Party team = plugin.teams().team(id);
        boolean teamWiped = team == null || team.members().stream().noneMatch(alive::contains);
        if (teamWiped) {
            String name = team != null ? team.name() : player.getName();
            if (eliminatedTeams.add(name)) {
                plugin.stats().teamEliminated(name, totalSeconds);
                if (team != null) {
                    lang().broadcast("game.team-eliminated", Lang.p("team", name));
                }
            }
        }
        each(module -> module.onEliminate(id, killer));
        Bukkit.getScheduler().runTask(plugin, this::checkWin);
    }

    /** Vuelve a meter a un jugador muerto en la partida (resurrección o /uhcrevive). */
    public boolean revive(Player player, Location where) {
        UUID id = player.getUniqueId();
        if (!participants.contains(id) || alive.contains(id)) {
            return false;
        }
        alive.add(id);
        Party team = plugin.teams().team(id);
        eliminatedTeams.remove(team != null ? team.name() : player.getName());
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
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
            world.setGameRule(GameRules.LOCATOR_BAR, true);
        }
        if (previous != GameState.LOBBY) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (participants.contains(player.getUniqueId())) {
                    unfreeze(player);
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
        participants.clear();
        alive.clear();
        spots.clear();
    }

    // ── Consultas ────────────────────────────────────────────

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
