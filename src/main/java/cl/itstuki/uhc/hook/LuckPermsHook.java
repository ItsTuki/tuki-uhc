package cl.itstuki.uhc.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.entity.Player;

/**
 * Lectura de prefijos de LuckPerms para chat, tab y scoreboard (cuando no está TukiCore).
 * Solo se carga si LuckPerms está instalado.
 */
final class LuckPermsHook implements RankProvider {
    private final LuckPerms api;

    LuckPermsHook() {
        this.api = LuckPermsProvider.get();
    }

    @Override
    public boolean available() {
        return true;
    }

    private CachedMetaData meta(Player player) {
        return api.getPlayerAdapter(Player.class).getMetaData(player);
    }

    @Override
    public String prefix(Player player) {
        String prefix = meta(player).getPrefix();
        return prefix == null ? "" : prefix;
    }

    @Override
    public String suffix(Player player) {
        String suffix = meta(player).getSuffix();
        return suffix == null ? "" : suffix;
    }

    @Override
    public String group(Player player) {
        String group = meta(player).getPrimaryGroup();
        return group == null ? "" : group;
    }
}
