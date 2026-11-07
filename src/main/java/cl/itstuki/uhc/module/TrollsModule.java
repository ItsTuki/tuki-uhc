package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jspecify.annotations.Nullable;

/**
 * Trolls y Ratas: en cada equipo hay un Troll (busca a la Rata, no puede dañar a su equipo),
 * una Rata (elimina a sus compañeros solo de forma indirecta) y Normales (pueden ejecutar a sospechosos).
 */
public final class TrollsModule implements UhcModule {
    public enum Role { TROLL, RATA, NORMAL }

    private static final int ROLES_EPISODE = 2;
    private static final double TRAP_DISTANCE = 6;
    private final TukiUHC plugin;
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Set<UUID> markedNormals = new HashSet<>();

    public TrollsModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private boolean active() {
        return plugin.settings().mode() == UhcMode.TROLLS_RATAS;
    }

    public @Nullable Role role(UUID id) {
        return roles.get(id);
    }

    public boolean isRat(UUID id) {
        return roles.get(id) == Role.RATA;
    }

    @Override
    public void onStart() {
        roles.clear();
        markedNormals.clear();
        if (active()) {
            plugin.lang().broadcast("trolls.intro", Lang.p("episode", ROLES_EPISODE));
        }
    }

    @Override
    public void onEpisodeStart(int episode) {
        if (!active() || episode != ROLES_EPISODE) {
            return;
        }
        for (Party team : plugin.teams().teams()) {
            List<UUID> members = new ArrayList<>(team.members().stream().filter(plugin.game()::isAlive).toList());
            Collections.shuffle(members);
            for (int i = 0; i < members.size(); i++) {
                Role role = members.size() < 2 ? Role.NORMAL : i == 0 ? Role.RATA : i == 1 ? Role.TROLL : Role.NORMAL;
                roles.put(members.get(i), role);
            }
        }
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                tellRole(player, entry.getValue());
            }
        }
        plugin.lang().broadcast("trolls.assigned");
    }

    public void tellRole(Player player, Role role) {
        String key = "trolls.role." + role.name().toLowerCase(java.util.Locale.ROOT);
        player.showTitle(Title.title(plugin.lang().get(key + ".title"), plugin.lang().get(key + ".subtitle"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        plugin.lang().sendList(player, key + ".description");
    }

    public void reveal(Player player) {
        Role role = roles.get(player.getUniqueId());
        if (role == null) {
            plugin.lang().send(player, "trolls.no-role");
            return;
        }
        for (UUID mate : plugin.teams().teammates(player.getUniqueId())) {
            Player other = Bukkit.getPlayer(mate);
            if (other != null) {
                plugin.lang().send(other, "trolls.revealed", Lang.p("player", player.getName()),
                        Lang.c("role", plugin.lang().get("trolls.role." + role.name().toLowerCase(java.util.Locale.ROOT) + ".name")));
            }
        }
        plugin.lang().send(player, "trolls.you-revealed");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamDamage(EntityDamageByEntityEvent event) {
        if (!active() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = RulesModule.attacker(event);
        if (attacker == null || attacker.equals(victim) || !plugin.teams().sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        Role role = roles.get(attacker.getUniqueId());
        if (role == null) {
            event.setCancelled(true); // Antes de repartir roles no hay fuego amigo.
        } else if (role == Role.TROLL) {
            event.setCancelled(true);
            plugin.lang().send(attacker, "trolls.troll-cannot");
        } else if (role == Role.RATA && !markedNormals.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            plugin.lang().send(attacker, "trolls.rat-cannot");
        }
    }

    private boolean ratNearTeammate(Player rat, Location where) {
        for (UUID mate : plugin.teams().teammates(rat.getUniqueId())) {
            Player other = Bukkit.getPlayer(mate);
            if (other != null && plugin.game().isAlive(mate) && other.getWorld().equals(where.getWorld())
                    && other.getLocation().distance(where) <= TRAP_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLava(PlayerBucketEmptyEvent event) {
        if (active() && event.getBucket() == Material.LAVA_BUCKET && isRat(event.getPlayer().getUniqueId())
                && ratNearTeammate(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "trolls.rat-cannot");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (active() && event.getPlayer() != null && isRat(event.getPlayer().getUniqueId())
                && ratNearTeammate(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            plugin.lang().send(event.getPlayer(), "trolls.rat-cannot");
        }
    }

    @Override
    public void onEliminate(UUID victim, @Nullable Player killer) {
        if (!active() || killer == null || roles.get(victim) != Role.TROLL
                || roles.get(killer.getUniqueId()) != Role.NORMAL || !plugin.teams().sameTeam(victim, killer.getUniqueId())) {
            return;
        }
        markedNormals.add(killer.getUniqueId());
        AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && max.getModifier(plugin.key("troll_penalty")) == null) {
            max.addModifier(new AttributeModifier(plugin.key("troll_penalty"), -0.5,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
            killer.setHealth(Math.min(killer.getHealth(), max.getValue()));
        }
        plugin.lang().send(killer, "trolls.killed-troll");
        for (UUID mate : plugin.teams().teammates(killer.getUniqueId())) {
            if (isRat(mate)) {
                Player rat = Bukkit.getPlayer(mate);
                if (rat != null) {
                    plugin.lang().send(rat, "trolls.rat-can-attack", Lang.p("player", killer.getName()));
                }
            }
        }
    }

    @Override
    public void onReset() {
        roles.clear();
        markedNormals.clear();
    }
}
