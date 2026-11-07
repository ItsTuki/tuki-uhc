package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.team.Party;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/**
 * Chats: escribir normal habla con tu equipo, "!" al inicio habla con todos,
 * "@" con tu grupo y "#" con las Ratas (modo Trolls y Ratas). Los muertos hablan entre ellos.
 */
public final class ChatModule implements UhcModule {
    private final TukiUHC plugin;

    public ChatModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private enum Channel { GLOBAL, TEAM, GROUP, RATS, DEAD }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        Channel channel = channel(sender, plain);
        if (channel == Channel.GLOBAL && !plugin.game().inGame() && plugin.hooks().tukiCore()) {
            return; // En el lobby el chat lo formatea TukiCore.
        }
        Component message = event.message();
        if (channel == Channel.GLOBAL && plain.startsWith("!") || channel == Channel.GROUP || channel == Channel.RATS) {
            message = strip(message);
        }
        Party team = plugin.teams().team(sender.getUniqueId());
        Party group = plugin.teams().group(sender.getUniqueId());

        Predicate<Player> canSee = switch (channel) {
            case GLOBAL -> p -> true;
            case TEAM -> p -> team != null && team.members().contains(p.getUniqueId());
            case GROUP -> p -> group != null && group.members().contains(p.getUniqueId());
            case RATS -> p -> plugin.trolls().isRat(p.getUniqueId());
            case DEAD -> p -> !plugin.game().isAlive(p.getUniqueId());
        };
        // El staff que no está jugando (espectadores con tukiuhc.chat.spy) ve todos los canales privados.
        Predicate<Player> spy = p -> channel != Channel.GLOBAL && p.hasPermission("tukiuhc.chat.spy")
                && !plugin.game().isAlive(p.getUniqueId());
        event.viewers().removeIf(audience -> audience instanceof Player player && !canSee.test(player) && !spy.test(player));

        String formatKey = "chat." + channel.name().toLowerCase(java.util.Locale.ROOT);
        Component teamTag = team != null ? Component.text(team.name(), team.color()) : Component.empty();
        Component groupTag = group != null ? Component.text(group.name(), group.color()) : Component.empty();
        Component finalMessage = message;
        event.message(finalMessage);
        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver senderTags = plugin.hooks().tags(sender);
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, msg) -> plugin.lang().get(formatKey,
                Lang.c("player", displayName), Lang.c("message", msg), Lang.c("team", teamTag), Lang.c("group", groupTag),
                senderTags)));
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

    private static Component strip(Component message) {
        return message.replaceText(TextReplacementConfig.builder().match("^[!@#]\\s*").replacement("").once().build());
    }
}
