package cl.itstuki.uhc.lang;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** Textos del plugin en español e inglés (lang/es.yml, lang/en.yml), formato MiniMessage. */
public final class Lang {
    public static final List<String> LANGUAGES = List.of("es", "en");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private TagResolver base = TagResolver.empty();
    private String code = "es";

    public Lang(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        this.code = LANGUAGES.contains(language) ? language : "es";
        for (String lang : LANGUAGES) {
            File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
            if (file.exists()) {
                // Si el archivo es de una versión anterior se respalda y se reemplaza por el nuevo.
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
        this.base = TagResolver.resolver(
                Placeholder.parsed("prefix", loaded.getString("prefix", "")),
                Placeholder.parsed("p", loaded.getString("prefix", "")));
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
        return messages.getString(key, key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), TagResolver.resolver(base, TagResolver.resolver(resolvers)));
    }

    /** Igual que {@link #get} pero sin cursiva, para nombres y lore de ítems. */
    public Component item(String key, TagResolver... resolvers) {
        return get(key, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public List<Component> itemList(String key, TagResolver... resolvers) {
        List<Component> lines = new ArrayList<>();
        TagResolver all = TagResolver.resolver(base, TagResolver.resolver(resolvers));
        for (String line : messages.getStringList(key)) {
            lines.add(MM.deserialize(line, all).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return lines;
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (!raw.isEmpty()) {
            audience.sendMessage(get(key, resolvers));
        }
    }

    public void sendList(Audience audience, String key, TagResolver... resolvers) {
        TagResolver all = TagResolver.resolver(base, TagResolver.resolver(resolvers));
        for (String line : messages.getStringList(key)) {
            audience.sendMessage(MM.deserialize(line, all));
        }
    }

    public void broadcast(String key, TagResolver... resolvers) {
        send(Bukkit.getServer(), key, resolvers);
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
