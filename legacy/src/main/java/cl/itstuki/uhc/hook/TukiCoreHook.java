package cl.itstuki.uhc.hook;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Rangos de TukiCore (el plugin administrativo de ItsTuki). Se usa su API pública
 * {@code cl.itstuki.core.api.TukiCoreAPI} sin depender de ella al compilar.
 */
final class TukiCoreHook implements RankProvider {
    private final MethodHandle prefix;
    private final MethodHandle suffix;
    private final MethodHandle rank;
    private final MethodHandle weight;

    TukiCoreHook(Plugin core) throws ReflectiveOperationException {
        Class<?> api = Class.forName("cl.itstuki.core.api.TukiCoreAPI", true, core.getClass().getClassLoader());
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodType type = MethodType.methodType(String.class, Player.class);
        prefix = lookup.findStatic(api, "prefix", type);
        suffix = lookup.findStatic(api, "suffix", type);
        rank = lookup.findStatic(api, "rank", type);
        weight = lookup.findStatic(api, "weight", MethodType.methodType(int.class, Player.class));
    }

    static boolean present() {
        return Bukkit.getPluginManager().isPluginEnabled("TukiCore");
    }

    @Override
    public boolean available() {
        return present();
    }

    @Override
    public String prefix(Player player) {
        try {
            return (String) prefix.invokeExact(player);
        } catch (Throwable ex) {
            return "";
        }
    }

    @Override
    public String suffix(Player player) {
        try {
            return (String) suffix.invokeExact(player);
        } catch (Throwable ex) {
            return "";
        }
    }

    @Override
    public int weight(Player player) {
        try {
            return (int) weight.invokeExact(player);
        } catch (Throwable ex) {
            return 0;
        }
    }

    @Override
    public String group(Player player) {
        try {
            return (String) rank.invokeExact(player);
        } catch (Throwable ex) {
            return "";
        }
    }
}
