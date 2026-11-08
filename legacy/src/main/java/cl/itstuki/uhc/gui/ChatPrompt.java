package cl.itstuki.uhc.gui;

import cl.itstuki.uhc.TukiUHC;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Pide al jugador que escriba un valor en el chat (nombres, números…). */
public final class ChatPrompt implements Listener {
    private final TukiUHC plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPrompt(TukiUHC plugin) {
        this.plugin = plugin;
    }

    /** Cierra el menú y espera la respuesta. "cancelar" anula. */
    public void ask(Player player, String messageKey, Consumer<String> handler, TagResolver... resolvers) {
        player.closeInventory();
        pending.put(player.getUniqueId(), handler);
        plugin.lang().send(player, messageKey, resolvers);
        plugin.lang().send(player, "prompt.cancel-hint");
    }

    /** Igual que ask, pero solo acepta números enteros. */
    public void askNumber(Player player, String messageKey, Consumer<Integer> handler, TagResolver... resolvers) {
        ask(player, messageKey, text -> {
            try {
                handler.accept(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ex) {
                plugin.lang().send(player, "prompt.not-number");
            }
        }, resolvers);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> handler = pending.remove(event.getPlayer().getUniqueId());
        if (handler == null) {
            return;
        }
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancelar") || text.equalsIgnoreCase("cancel")) {
                plugin.lang().send(player, "prompt.cancelled");
                return;
            }
            handler.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
