package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Cambios de botín. En la edición Legacy solo están los tridentes de los ahogados (1.13+): los cofres
 * de puestos, campamentos y ciudades antiguas son de versiones más nuevas.
 */
public final class LootModule implements UhcModule {
    private final TukiUHC plugin;

    public LootModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    /** Los ahogados con tridente lo sueltan con la probabilidad configurada, con su durabilidad. */
    @EventHandler
    @SuppressWarnings("deprecation")
    public void onDrownedDeath(EntityDeathEvent event) {
        if (!plugin.settings().enabled(Feature.TRIDENTS) || !event.getEntityType().name().equals("DROWNED")) {
            return;
        }
        EntityEquipment equipment = ((LivingEntity) event.getEntity()).getEquipment();
        ItemStack held = equipment == null ? null : equipment.getItemInHand();
        if (held == null || !held.getType().name().equals("TRIDENT")) {
            return;
        }
        event.getDrops().removeIf(item -> item.getType().name().equals("TRIDENT"));
        if (ThreadLocalRandom.current().nextInt(100) < plugin.settings().value(Feature.TRIDENTS)) {
            event.getDrops().add(held.clone());
        }
    }
}
