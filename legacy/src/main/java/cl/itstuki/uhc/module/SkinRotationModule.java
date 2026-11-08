package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Identidades ocultas (edición Legacy): los nombres de los enemigos salen como "???" sobre la cabeza
 * y en el tab hasta golpearlos. En 1.8 no se pueden cambiar las skins sin tocar el servidor, así que
 * aquí solo se ocultan los nombres (en la edición Modern además rotan las skins).
 */
public final class SkinRotationModule implements UhcModule {
    private static boolean active;
    private final TukiUHC plugin;
    private final Set<UUID> revealed = new HashSet<>();

    public SkinRotationModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public static boolean hidingNames() {
        return active;
    }

    /** Si {@code viewer} sabe quién es {@code target}. */
    public boolean knows(UUID viewer, UUID target) {
        if (!active || viewer.equals(target) || plugin.teams().sameTeam(viewer, target)) {
            return true;
        }
        if (!plugin.game().isAlive(viewer) || !plugin.game().isAlive(target)) {
            return true;
        }
        return revealed.contains(target);
    }

    public boolean isRevealed(UUID target) {
        return !active || revealed.contains(target) || !plugin.game().isAlive(target);
    }

    @Override
    public void onStart() {
        if (!plugin.settings().enabled(Feature.SKIN_ROTATION)) {
            return;
        }
        active = true;
        revealed.clear();
        plugin.lang().broadcast("skins.names-hidden");
    }

    /** En cada parte impar se vuelven a ocultar todos. */
    @Override
    public void onEpisodeStart(int episode) {
        if (active && episode > 1 && episode % 2 == 1) {
            revealed.clear();
            plugin.lang().broadcast("skins.names-hidden");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!active || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = RulesModule.attacker(event);
        if (attacker == null || attacker.equals(victim) || plugin.teams().sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (!revealed.add(victim.getUniqueId())) {
            return;
        }
        plugin.lang().send(attacker, "skins.revealed", Lang.p("player", victim.getName()));
        plugin.lang().send(victim, "skins.you-were-revealed");
    }

    private void restore() {
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
