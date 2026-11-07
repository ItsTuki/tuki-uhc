package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Drowned;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/** Cambios de botín: tridentes, diamantes en puestos de saqueadores, campamentos y ciudades antiguas. */
public final class LootModule implements UhcModule {
    private static final List<Material> CAMP_RARE = List.of(Material.MACE, Material.TRIDENT, Material.ELYTRA);
    private final TukiUHC plugin;

    public LootModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private boolean roll(Feature feature) {
        return plugin.settings().enabled(feature)
                && ThreadLocalRandom.current().nextInt(100) < plugin.settings().value(feature);
    }

    /** Los ahogados con tridente lo sueltan con la probabilidad configurada, con su durabilidad. */
    @EventHandler
    public void onDrownedDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Drowned drowned) || !plugin.settings().enabled(Feature.TRIDENTS)) {
            return;
        }
        EntityEquipment equipment = drowned.getEquipment();
        ItemStack held = equipment.getItemInMainHand();
        if (held.getType() != Material.TRIDENT) {
            return;
        }
        event.getDrops().removeIf(item -> item.getType() == Material.TRIDENT);
        if (roll(Feature.TRIDENTS)) {
            event.getDrops().add(held.clone());
        }
    }

    @EventHandler
    public void onLoot(LootGenerateEvent event) {
        String table = event.getLootTable().getKey().getKey();
        switch (table) {
            case "chests/pillager_outpost" -> {
                if (roll(Feature.OUTPOST_DIAMONDS)) {
                    int amount = ThreadLocalRandom.current().nextInt(1, 3);
                    event.getLoot().add(ItemStack.of(Material.DIAMOND, amount));
                }
            }
            case "chests/abandoned_camp_secret_chest" -> {
                if (roll(Feature.CAMP_LOOT)) {
                    Material rare = CAMP_RARE.get(ThreadLocalRandom.current().nextInt(CAMP_RARE.size()));
                    event.getLoot().add(ItemStack.of(rare));
                }
            }
            case "chests/ancient_city" -> {
                if (plugin.settings().enabled(Feature.ANCIENT_CITY_CAP)) {
                    event.getLoot().removeIf(LootModule::isHealing);
                }
            }
            default -> {
            }
        }
    }

    private static boolean isHealing(ItemStack item) {
        if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            return true;
        }
        if (item.getItemMeta() instanceof PotionMeta potion) {
            PotionType type = potion.getBasePotionType();
            return type == PotionType.REGENERATION || type == PotionType.LONG_REGENERATION
                    || type == PotionType.STRONG_REGENERATION;
        }
        return false;
    }
}
