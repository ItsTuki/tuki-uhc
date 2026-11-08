package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Text;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Chats: escribir normal habla con tu equipo, "!" al inicio habla con todos,
 * "@" con tu grupo y "#" con las Ratas (modo Trolls y Ratas). Los muertos hablan entre ellos.
 */
public final class ChatModule implements UhcModule {
    private static final String NAME_MARK = "name";
    private static final String MESSAGE_MARK = "msg";
    private final TukiUHC plugin;

    public ChatModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private enum Channel { GLOBAL, TEAM, GROUP, RATS, DEAD }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String plain = event.getMessage();
        Channel channel = channel(sender, plain);
        if (channel == Channel.GLOBAL && !plugin.game().inGame() && plugin.hooks().tukiCore()) {
            return; // En el lobby el chat lo formatea TukiCore.
        }
        if (channel == Channel.GLOBAL && plain.startsWith("!") || channel == Channel.GROUP || channel == Channel.RATS) {
            String stripped = plain.replaceFirst("^[!@#]\\s*", "");
            if (stripped.isEmpty()) {
                event.setCancelled(true);
                return;
            }
            event.setMessage(stripped);
        }
        final Party team = plugin.teams().team(sender.getUniqueId());
        final Party group = plugin.teams().group(sender.getUniqueId());

        // El staff que no está jugando (espectadores con tukiuhc.chat.spy) ve todos los canales privados.
        event.getRecipients().removeIf(p -> !canSee(channel, p, team, group)
                && !(channel != Channel.GLOBAL && p.hasPermission("tukiuhc.chat.spy") && !plugin.game().isAlive(p.getUniqueId())));

        Component teamTag = team != null ? Component.text(team.name(), team.color()) : Component.empty();
        Component groupTag = group != null ? Component.text(group.name(), group.color()) : Component.empty();
        TagResolver senderTags = plugin.hooks().tags(sender);
        Component format = plugin.lang().get("chat." + channel.name().toLowerCase(Locale.ROOT),
                Lang.p("player", NAME_MARK), Lang.p("message", MESSAGE_MARK), Lang.c("team", teamTag),
                Lang.c("group", groupTag), senderTags);
        // Bukkit arma el mensaje con %1$s (nombre) y %2$s (texto): el resto se escapa.
        String legacy = Text.legacy(format).replace("%", "%%")
                .replace(NAME_MARK, "%1$s").replace(MESSAGE_MARK, "%2$s");
        if (!legacy.contains("%2$s")) {
            legacy = legacy + " %2$s";
        }
        event.setFormat(legacy);
    }

    private boolean canSee(Channel channel, Player p, Party team, Party group) {
        switch (channel) {
            case TEAM:
                return team != null && team.members().contains(p.getUniqueId());
            case GROUP:
                return group != null && group.members().contains(p.getUniqueId());
            case RATS:
                return plugin.trolls().isRat(p.getUniqueId());
            case DEAD:
                return !plugin.game().isAlive(p.getUniqueId());
            default:
                return true;
        }
    }

    private Channel channel(Player sender, String plain) {
        boolean inGame = plugin.game().inGame();
        if (inGame && plugin.game().isParticipant(sender.getUniqueId()) && !plugin.game().isAlive(sender.getUniqueId())) {
            return Channel.DEAD;
        }
        if (plain.startsWith("!")) {
            return Channel.GLOBAL;
        }
        if (plain.startsWith("@") && plugin.teams().group(sender.getUniqueId()) != null) {
            return Channel.GROUP;
        }
        if (plain.startsWith("#") && plugin.settings().mode() == UhcMode.TROLLS_RATAS && plugin.trolls().isRat(sender.getUniqueId())) {
            return Channel.RATS;
        }
        if (inGame && plugin.teams().hasTeam(sender.getUniqueId()) && plugin.settings().mode() != UhcMode.SOLITARIOS) {
            return Channel.TEAM;
        }
        return Channel.GLOBAL;
    }
}
