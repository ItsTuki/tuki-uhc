package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Rotación de skins: al empezar y en cada parte impar, los jugadores intercambian skins y se ocultan
 * los nombres. Golpear a un enemigo revela quién es. No necesita SkinRestorer.
 */
public final class SkinRotationModule implements UhcModule {
    private static boolean active;
    private final TukiUHC plugin;
    private final Map<UUID, ProfileProperty> originals = new HashMap<>();
    private final Map<String, Long> revealCooldown = new HashMap<>();
    /** Jugadores revelados a todos (recuperaron su skin al ser golpeados). */
    private final java.util.Set<UUID> revealed = new java.util.HashSet<>();

    public SkinRotationModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    /** Si los nombres deben ocultarse (lo usa el scoreboard). */
    public static boolean hidingNames() {
        return active;
    }

    /**
     * Si {@code viewer} sabe quién es {@code target}: siempre a sí mismo, a su equipo,
     * a los enemigos que golpeó, y todo si es espectador o no participa.
     */
    public boolean knows(UUID viewer, UUID target) {
        if (!active || viewer.equals(target) || plugin.teams().sameTeam(viewer, target)) {
            return true;
        }
        if (!plugin.game().isAlive(viewer) || !plugin.game().isAlive(target)) {
            return true;
        }
        return revealed.contains(target);
    }

    /** Si el jugador ya fue revelado a todos (o no hay rotación activa). */
    public boolean isRevealed(UUID target) {
        return !active || revealed.contains(target) || !plugin.game().isAlive(target);
    }

    @Override
    public void onStart() {
        if (!plugin.settings().enabled(Feature.SKIN_ROTATION)) {
            return;
        }
        originals.clear();
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                texture(player.getPlayerProfile()).ifPresent(t -> originals.put(id, t));
            }
        }
        active = true;
        rotate();
    }

    @Override
    public void onEpisodeStart(int episode) {
        if (active && episode > 1 && episode % 2 == 1) {
            rotate();
        }
    }

    private void rotate() {
        List<Player> players = new ArrayList<>();
        List<ProfileProperty> skins = new ArrayList<>();
        for (UUID id : plugin.game().alive()) {
            Player player = Bukkit.getPlayer(id);
            ProfileProperty skin = originals.get(id);
            if (player != null && skin != null) {
                players.add(player);
                skins.add(skin);
            }
        }
        if (players.size() < 2) {
            plugin.lang().broadcast("skins.not-enough");
            return;
        }
        // Con skins nuevas hay que volver a descubrir quién es quién.
        revealed.clear();
        revealCooldown.clear();
        // Desplaza la lista al azar para que nadie conserve su propia skin.
        Collections.shuffle(players);
        List<ProfileProperty> ordered = new ArrayList<>();
        for (Player player : players) {
            ordered.add(originals.get(player.getUniqueId()));
        }
        for (int i = 0; i < players.size(); i++) {
            apply(players.get(i), ordered.get((i + 1) % ordered.size()));
        }
        plugin.lang().broadcast("skins.rotated");
    }

    private void apply(Player player, ProfileProperty skin) {
        PlayerProfile profile = player.getPlayerProfile();
        profile.removeProperty("textures");
        profile.setProperty(skin);
        player.setPlayerProfile(profile);
    }

    private static Optional<ProfileProperty> texture(PlayerProfile profile) {
        return profile.getProperties().stream().filter(p -> p.getName().equals("textures")).findFirst();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!active || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = RulesModule.attacker(event);
        if (attacker == null || attacker.equals(victim) || plugin.teams().sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (!revealed.add(victim.getUniqueId())) {
            return;
        }
        // Como en la guía: el golpeado recupera su skin original y queda revelado a todos.
        ProfileProperty original = originals.get(victim.getUniqueId());
        if (original != null) {
            apply(victim, original);
        }
        plugin.lang().send(attacker, "skins.revealed", Lang.p("player", victim.getName()));
        plugin.lang().send(victim, "skins.you-were-revealed");
    }

    private void restore() {
        for (Map.Entry<UUID, ProfileProperty> entry : originals.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                apply(player, entry.getValue());
            }
        }
        originals.clear();
        revealCooldown.clear();
        revealed.clear();
        active = false;
    }

    @Override
    public void onEnd() {
        restore();
    }

    @Override
    public void onReset() {
        restore();
    }
}
