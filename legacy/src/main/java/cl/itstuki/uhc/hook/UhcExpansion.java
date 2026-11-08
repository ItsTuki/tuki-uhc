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
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private String plain(String langKey) {
        return PlainTextComponentSerializer.plainText().serialize(plugin.lang().get(langKey));
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
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
        switch (key) {
            case "kills":
                return String.valueOf(plugin.stats().kills(id));
            case "is_alive":
                return String.valueOf(game.isAlive(id));
            case "is_playing":
                return String.valueOf(game.isParticipant(id));
            case "team":
                return team == null ? "" : team.name();
            case "team_color":
                return team == null ? "" : "&" + legacyColor(team);
            case "team_alive":
                return team == null ? "0" : String.valueOf(team.members().stream().filter(game::isAlive).count());
            case "role": {
                TrollsModule.Role role = plugin.trolls().role(id);
                return role == null ? "" : plain("trolls.role." + role.name().toLowerCase(Locale.ROOT) + ".name");
            }
            case "hearts":
                return player instanceof Player ? Format.hearts(((Player) player).getHealth()) : "0";
            case "wins":
                return String.valueOf(plugin.lobby().stat(id, "wins"));
            case "total_kills":
                return String.valueOf(plugin.lobby().stat(id, "kills"));
            case "games":
                return String.valueOf(plugin.lobby().stat(id, "games"));
            default:
                return null;
        }
    }

    private static char legacyColor(Party team) {
        return cl.itstuki.uhc.compat.Text.code(team.color()).charAt(1);
    }
}
