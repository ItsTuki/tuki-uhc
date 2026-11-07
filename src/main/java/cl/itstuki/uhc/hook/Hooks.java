package cl.itstuki.uhc.hook;

import cl.itstuki.uhc.TukiUHC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Integraciones opcionales (PlaceholderAPI y LuckPerms). Las clases que usan esas APIs solo se cargan
 * si el plugin correspondiente está instalado, así TukiUHC funciona igual sin ellos.
 */
public final class Hooks {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private final TukiUHC plugin;
    private RankProvider ranks = RankProvider.NONE;
    private @Nullable PlaceholderApiBridge papi;
    private boolean tukiCore;

    public Hooks(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        // TukiCore manda en rangos, chat del lobby y tab; si no está, se leen los prefijos de LuckPerms.
        if (TukiCoreHook.present()) {
            try {
                ranks = new TukiCoreHook(Bukkit.getPluginManager().getPlugin("TukiCore"));
                tukiCore = true;
                plugin.getLogger().info("TukiCore detectado: se usan sus rangos y su chat fuera de la partida.");
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo conectar con TukiCore: " + ex.getMessage());
            }
        }
        if (!tukiCore && Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                ranks = new LuckPermsHook();
                plugin.getLogger().info("LuckPerms detectado: prefijos de rango activos.");
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo conectar con LuckPerms: " + ex.getMessage());
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                papi = new PlaceholderApiBridge(plugin);
                plugin.getLogger().info("PlaceholderAPI detectado: placeholders %tukiuhc_...% registrados.");
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo conectar con PlaceholderAPI: " + ex.getMessage());
            }
        }
    }

    public void disable() {
        if (papi != null) {
            papi.unregister();
            papi = null;
        }
    }

    /** Hay un sistema de rangos (TukiCore o LuckPerms) del que leer prefijos. */
    public boolean hasRanks() {
        return ranks.available();
    }

    /** TukiCore está instalado: él formatea el chat y el tab fuera de la partida. */
    public boolean tukiCore() {
        return tukiCore;
    }

    public boolean placeholderApi() {
        return papi != null;
    }

    public RankProvider ranks() {
        return ranks;
    }

    public static Component legacy(String text) {
        return text == null || text.isEmpty() ? Component.empty() : LEGACY.deserialize(text.replace('§', '&'));
    }

    /**
     * Etiquetas para los textos de lang/*.yml relacionadas a un jugador:
     * {@code <rank>} (prefijo de LuckPerms), {@code <rank_suffix>}, {@code <lp_group>} y
     * {@code <papi:nombre>} (cualquier placeholder de PlaceholderAPI, ej. {@code <papi:vault_eco_balance>}).
     */
    public TagResolver tags(@Nullable Player player) {
        if (player == null) {
            return TagResolver.resolver(
                    TagResolver.resolver("rank", Tag.selfClosingInserting(Component.empty())),
                    TagResolver.resolver("rank_suffix", Tag.selfClosingInserting(Component.empty())),
                    TagResolver.resolver("lp_group", Tag.selfClosingInserting(Component.empty())),
                    TagResolver.resolver("papi", (args, ctx) -> Tag.selfClosingInserting(Component.empty())));
        }
        return TagResolver.resolver(
                TagResolver.resolver("rank", (args, ctx) -> Tag.selfClosingInserting(legacy(ranks.prefix(player)))),
                TagResolver.resolver("rank_suffix", (args, ctx) -> Tag.selfClosingInserting(legacy(ranks.suffix(player)))),
                TagResolver.resolver("lp_group", (args, ctx) -> Tag.selfClosingInserting(Component.text(ranks.group(player)))),
                TagResolver.resolver("papi", (args, ctx) -> {
                    String name = args.hasNext() ? args.pop().value() : "";
                    if (papi == null || name.isEmpty()) {
                        return Tag.selfClosingInserting(Component.empty());
                    }
                    return Tag.selfClosingInserting(legacy(papi.apply(player, "%" + name + "%")));
                }));
    }
}
