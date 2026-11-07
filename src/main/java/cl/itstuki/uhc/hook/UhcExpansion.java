package cl.itstuki.uhc.hook;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.game.GameManager;
import cl.itstuki.uhc.module.BorderModule;
import cl.itstuki.uhc.module.TrollsModule;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Format;
import java.util.Locale;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Placeholders %tukiuhc_...% para usar en otros plugins (scoreboards, hologramas, menús, TAB, etc.).
 * La lista completa está en el README.
 */
final class UhcExpansion extends PlaceholderExpansion {
    private final TukiUHC plugin;

    UhcExpansion(TukiUHC plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "tukiuhc";
    }

    @Override
    public String getAuthor() {
        return "ItsTuki";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private String plain(String langKey) {
        return PlainTextComponentSerializer.plainText().serialize(plugin.lang().get(langKey));
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
        GameManager game = plugin.game();
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "state":
                return plain("states." + game.state().name().toLowerCase(Locale.ROOT));
            case "state_raw":
                return game.state().name();
            case "mode":
                return plain("modes." + plugin.settings().mode().key() + ".name");
            case "team_mode":
                return plain("team-modes." + plugin.settings().teamMode().key() + ".name");
            case "team_size":
                return String.valueOf(plugin.settings().teamSize());
            case "match_name":
                return plugin.settings().matchName();
            case "episode":
                return String.valueOf(game.episode());
            case "episodes":
                return String.valueOf(plugin.settings().finalEpisode());
            case "phase":
                return PlainTextComponentSerializer.plainText().serialize(game.phaseLabel());
            case "pvp":
                return String.valueOf(game.pvpAllowed());
            case "time":
                return Format.time(game.totalSeconds());
            case "time_seconds":
                return String.valueOf(game.totalSeconds());
            case "time_left":
                return Format.time(game.secondsLeftInEpisode());
            case "alive":
                return String.valueOf(game.alive().size());
            case "alive_teams":
                return String.valueOf(game.aliveSides());
            case "participants":
                return String.valueOf(game.participants().size());
            case "border":
                return String.valueOf(BorderModule.currentRadius(game.mainWorld()));
            default:
                break;
        }
        if (player == null) {
            return null;
        }
        UUID id = player.getUniqueId();
        Party team = plugin.teams().team(id);
        return switch (key) {
            case "kills" -> String.valueOf(plugin.stats().kills(id));
            case "is_alive" -> String.valueOf(game.isAlive(id));
            case "is_playing" -> String.valueOf(game.isParticipant(id));
            case "team" -> team == null ? "" : team.name();
            case "team_color" -> team == null ? "" : "&" + legacyColor(team);
            case "team_alive" -> team == null ? "0" : String.valueOf(team.members().stream().filter(game::isAlive).count());
            case "role" -> {
                TrollsModule.Role role = plugin.trolls().role(id);
                yield role == null ? "" : plain("trolls.role." + role.name().toLowerCase(Locale.ROOT) + ".name");
            }
            case "hearts" -> player instanceof Player online ? Format.hearts(online.getHealth()) : "0";
            case "wins" -> String.valueOf(plugin.lobby().stat(id, "wins"));
            case "total_kills" -> String.valueOf(plugin.lobby().stat(id, "kills"));
            case "games" -> String.valueOf(plugin.lobby().stat(id, "games"));
            default -> null;
        };
    }

    private static char legacyColor(Party team) {
        return switch (team.color().toString()) {
            case "black" -> '0';
            case "dark_blue" -> '1';
            case "dark_green" -> '2';
            case "dark_aqua" -> '3';
            case "dark_red" -> '4';
            case "dark_purple" -> '5';
            case "gold" -> '6';
            case "gray" -> '7';
            case "dark_gray" -> '8';
            case "blue" -> '9';
            case "green" -> 'a';
            case "aqua" -> 'b';
            case "red" -> 'c';
            case "light_purple" -> 'd';
            case "yellow" -> 'e';
            default -> 'f';
        };
    }
}
