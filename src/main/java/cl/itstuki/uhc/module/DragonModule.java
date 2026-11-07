package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.jspecify.annotations.Nullable;

/** Modo Dragón: portal al End en 0 0 con 9 de 12 ojos; gana quien remate a la dragona. */
public final class DragonModule implements UhcModule {
    private final TukiUHC plugin;
    private @Nullable UUID lastDamager;

    public DragonModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private boolean active() {
        return plugin.settings().mode() == UhcMode.DRAGON;
    }

    @Override
    public void onPrepare() {
        lastDamager = null;
        if (active()) {
            buildPortal(plugin.game().mainWorld());
        }
    }

    @Override
    public void onStart() {
        if (active()) {
            long hours = (long) plugin.settings().finalEpisode() * plugin.settings().episodeMinutes();
            plugin.lang().broadcast("dragon.objective", Lang.p("time", cl.itstuki.uhc.util.Format.time(hours * 60)));
        }
    }

    private void buildPortal(World world) {
        int y = world.getHighestBlockYAt(0, 0) + 1;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                world.getBlockAt(x, y - 1, z).setType(Material.STONE_BRICKS);
                for (int dy = 0; dy <= 3; dy++) {
                    world.getBlockAt(x, y + dy, z).setType(Material.AIR);
                }
            }
        }
        List<int[]> frames = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            frames.add(new int[]{i, -2, 0});
            frames.add(new int[]{i, 2, 1});
            frames.add(new int[]{-2, i, 2});
            frames.add(new int[]{2, i, 3});
        }
        Collections.shuffle(frames);
        BlockFace[] faces = {BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST};
        for (int i = 0; i < frames.size(); i++) {
            int[] f = frames.get(i);
            Block block = world.getBlockAt(f[0], y, f[1]);
            block.setType(Material.END_PORTAL_FRAME, false);
            EndPortalFrame data = (EndPortalFrame) block.getBlockData();
            data.setFacing(faces[f[2]]);
            data.setEye(i < 9);
            block.setBlockData(data, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonHit(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (target instanceof ComplexEntityPart part) {
            target = part.getParent();
        }
        if (target instanceof EnderDragon) {
            Player attacker = RulesModule.attacker(event);
            if (attacker != null) {
                lastDamager = attacker.getUniqueId();
            }
        }
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || !active() || !plugin.game().isRunning()) {
            return;
        }
        Player killer = dragon.getKiller();
        UUID winner = killer != null ? killer.getUniqueId() : lastDamager;
        if (winner == null) {
            return;
        }
        List<UUID> winners = new ArrayList<>();
        Party team = plugin.teams().team(winner);
        if (team != null) {
            team.members().stream().filter(plugin.game()::isAlive).forEach(winners::add);
        }
        if (winners.isEmpty()) {
            winners.add(winner);
        }
        plugin.lang().broadcast("dragon.killed", Lang.p("player", plugin.stats().name(winner)));
        plugin.game().end(winners, "dragon.killed");
    }
}
