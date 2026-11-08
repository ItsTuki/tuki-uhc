package cl.itstuki.uhc.lang;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Text;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Textos del plugin en español e inglés (lang/es.yml, lang/en.yml), formato MiniMessage. */
public final class Lang {
    public static final List<String> LANGUAGES = Arrays.asList("es", "en");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TukiUHC plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private TagResolver base = TagResolver.empty();
    private Function<Player, TagResolver> playerTags = p -> TagResolver.empty();
    private final ThreadLocal<Player> viewer = new ThreadLocal<>();
    private String code = "es";

    public Lang(TukiUHC plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        this.code = LANGUAGES.contains(language) ? language : "es";
        for (String lang : LANGUAGES) {
            File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
            if (file.exists()) {
                YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
                YamlConfiguration bundled = resource(lang);
                int bundledVersion = bundled == null ? 0 : bundled.getInt("lang-version", 0);
                if (current.getInt("lang-version", 0) < bundledVersion) {
                    File backup = new File(file.getParentFile(), lang + ".yml.old");
                    if (backup.exists()) {
                        backup.delete();
                    }
                    if (file.renameTo(backup)) {
                        plugin.getLogger().info("lang/" + lang + ".yml actualizado (copia anterior en " + backup.getName() + ")");
                    }
                }
            }
            if (!file.exists()) {
                plugin.saveResource("lang/" + lang + ".yml", false);
            }
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/" + code + ".yml"));
        YamlConfiguration defaults = resource(code);
        YamlConfiguration spanish = resource("es");
        if (defaults != null && spanish != null && defaults != spanish) {
            defaults.setDefaults(spanish);
        }
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        this.messages = loaded;
        String prefix = raw("prefix");
        this.base = TagResolver.resolver(Placeholder.parsed("prefix", prefix), Placeholder.parsed("p", prefix));
    }

    private YamlConfiguration resource(String lang) {
        try (InputStream in = plugin.getResource("lang/" + lang + ".yml")) {
            return in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        }
    }

    public String code() {
        return code;
    }

    public String raw(String key) {
        String value = messages.getString(key);
        return value != null ? value : key;
    }

    public List<String> rawList(String key) {
        return messages.getStringList(key);
    }

    public void playerTags(Function<Player, TagResolver> tags) {
        this.playerTags = tags;
    }

    /** Ejecuta {@code action} con {@code player} como contexto para <rank> y <papi:...>. */
    public void asViewer(Player player, Runnable action) {
        viewer.set(player);
        try {
            action.run();
        } finally {
            viewer.remove();
        }
    }

    private TagResolver all(Player player, TagResolver... resolvers) {
        // Los últimos mandan: las etiquetas propias del mensaje pisan a <prefix>, <rank>, <papi:...>.
        return TagResolver.resolver(base, playerTags.apply(player), TagResolver.resolver(resolvers));
    }

    public Component get(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), all(viewer.get(), resolvers));
    }

    /** Texto de un ítem (sin cursiva) ya convertido a "§". */
    public String item(String key, TagResolver... resolvers) {
        return Text.legacy(get(key, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
    }

    public List<String> itemList(String key, TagResolver... resolvers) {
        List<String> lines = new ArrayList<>();
        TagResolver all = all(viewer.get(), resolvers);
        for (String line : messages.getStringList(key)) {
            lines.add(Text.legacy(MM.deserialize(line, all)));
        }
        return lines;
    }

    public Component format(String text, Player player, TagResolver... resolvers) {
        return MM.deserialize(text, all(player, resolvers));
    }

    private Audience audience(CommandSender sender) {
        return plugin.adventure().sender(sender);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        Player player = sender instanceof Player ? (Player) sender : null;
        audience(sender).sendMessage(MM.deserialize(raw, all(player, resolvers)));
    }

    public void sendList(CommandSender sender, String key, TagResolver... resolvers) {
        Player player = sender instanceof Player ? (Player) sender : null;
        TagResolver all = all(player, resolvers);
        for (String line : messages.getStringList(key)) {
            audience(sender).sendMessage(MM.deserialize(line, all));
        }
    }

    public void sendComponent(CommandSender sender, Component component) {
        audience(sender).sendMessage(component);
    }

    /** A todos los jugadores y a la consola. */
    public void broadcast(String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        plugin.adventure().all().sendMessage(MM.deserialize(raw, all(null, resolvers)));
    }

    public static Component mm(String text, TagResolver... resolvers) {
        return MM.deserialize(text, resolvers);
    }

    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver c(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
