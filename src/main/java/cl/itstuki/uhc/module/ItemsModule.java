package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.util.Items;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Ítems especiales (cabezas doradas, super/hiper manzanas, cristal de resurrección) y crafteos modificados. */
public final class ItemsModule implements UhcModule {
    public static final int MAX_HYPER_APPLES = 5;

    private final TukiUHC plugin;
    private final NamespacedKey itemKey;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public ItemsModule(TukiUHC plugin) {
        this.plugin = plugin;
        this.itemKey = plugin.key("uhc_item");
    }

    // ── Ítems ────────────────────────────────────────────────

    public ItemStack goldenHead() {
        ItemStack item = Items.of(Material.GOLDEN_APPLE).name(plugin.lang().item("items.golden-head.name"))
                .lore(plugin.lang().itemList("items.golden-head.lore")).tag(itemKey, "golden_head").build();
        item.editMeta(meta -> meta.setItemModel(NamespacedKey.minecraft("player_head")));
        return item;
    }

    public ItemStack goldenSkull() {
        ItemStack item = Items.of(Material.GOLDEN_APPLE).name(plugin.lang().item("items.golden-skull.name"))
                .lore(plugin.lang().itemList("items.golden-skull.lore")).tag(itemKey, "golden_skull").build();
        item.editMeta(meta -> meta.setItemModel(NamespacedKey.minecraft("wither_skeleton_skull")));
        return item;
    }

    public ItemStack superApple() {
        return Items.of(Material.GOLDEN_APPLE).name(plugin.lang().item("items.super-apple.name"))
                .lore(plugin.lang().itemList("items.super-apple.lore")).glow(true).tag(itemKey, "super_apple").build();
    }

    public ItemStack hyperApple() {
        return Items.of(Material.GOLDEN_APPLE).name(plugin.lang().item("items.hyper-apple.name"))
                .lore(plugin.lang().itemList("items.hyper-apple.lore", Lang.p("max", MAX_HYPER_APPLES)))
                .glow(true).tag(itemKey, "hyper_apple").build();
    }

    public ItemStack reviveCrystal() {
        return Items.of(Material.END_CRYSTAL).name(plugin.lang().item("items.revive-crystal.name"))
                .lore(plugin.lang().itemList("items.revive-crystal.lore")).glow(true).tag(itemKey, "revive_crystal").build();
    }

    public String id(ItemStack stack) {
        String id = Items.tag(stack, itemKey);
        return id == null ? "" : id;
    }

    public NamespacedKey itemKey() {
        return itemKey;
    }

    // ── Recetas ──────────────────────────────────────────────

    public void registerRecipes() {
        Bukkit.removeRecipe(NamespacedKey.minecraft("glistering_melon_slice"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_carrot"));

        shaped("golden_head", goldenHead(), new String[]{"GGG", "GHG", "GGG"},
                'G', new RecipeChoice.MaterialChoice(Material.GOLD_INGOT), 'H', new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
        shaped("golden_skull", goldenSkull(), new String[]{"GGG", "GHG", "GGG"},
                'G', new RecipeChoice.MaterialChoice(Material.GOLD_INGOT), 'H', new RecipeChoice.MaterialChoice(Material.WITHER_SKELETON_SKULL));
        shaped("super_apple", superApple(), new String[]{"GGG", "GAG", "GGG"},
                'G', new RecipeChoice.MaterialChoice(Material.GOLD_INGOT), 'A', RecipeChoice.exactChoice(ItemStack.of(Material.GOLDEN_APPLE)));
        shaped("hyper_apple", hyperApple(), new String[]{"GGG", "GAG", "GGG"},
                'G', new RecipeChoice.MaterialChoice(Material.GOLD_INGOT), 'A', RecipeChoice.exactChoice(superApple()));
        shaped("golden_carrot", ItemStack.of(Material.GOLDEN_CARROT), new String[]{"GGG", "GCG", "GGG"},
                'G', new RecipeChoice.MaterialChoice(Material.GOLD_INGOT), 'C', new RecipeChoice.MaterialChoice(Material.CARROT));
        shaped("enchanted_golden_apple", ItemStack.of(Material.ENCHANTED_GOLDEN_APPLE), new String[]{"BBB", "BAB", "BBB"},
                'B', new RecipeChoice.MaterialChoice(Material.GOLD_BLOCK), 'A', new RecipeChoice.MaterialChoice(Material.APPLE));
        shaped("dried_ghast", ItemStack.of(Material.DRIED_GHAST), new String[]{"BGB", "GSG", "BGB"},
                'B', new RecipeChoice.MaterialChoice(Material.BONE_BLOCK), 'G', new RecipeChoice.MaterialChoice(Material.GOLD_INGOT),
                'S', new RecipeChoice.MaterialChoice(Material.SOUL_SAND));
        shaped("dragon_breath", ItemStack.of(Material.DRAGON_BREATH), new String[]{"NNN", "BPB", "NNN"},
                'N', new RecipeChoice.MaterialChoice(Material.GOLD_NUGGET), 'B', new RecipeChoice.MaterialChoice(Material.BLAZE_POWDER),
                'P', new RecipeChoice.MaterialChoice(Material.SPLASH_POTION));
        shaped("revive_crystal", reviveCrystal(), new String[]{"DBD", "BOB", "DBD"},
                'D', new RecipeChoice.MaterialChoice(Material.DIAMOND), 'B', new RecipeChoice.MaterialChoice(Material.BLAZE_POWDER),
                'O', new RecipeChoice.MaterialChoice(Material.OBSIDIAN));

        NamespacedKey melon = plugin.key("glistering_melon");
        ShapelessRecipe glistering = new ShapelessRecipe(melon, ItemStack.of(Material.GLISTERING_MELON_SLICE));
        glistering.addIngredient(Material.GOLD_BLOCK);
        glistering.addIngredient(Material.MELON_SLICE);
        Bukkit.addRecipe(glistering);
        registered.add(melon);
        Bukkit.updateRecipes();
    }

    private void shaped(String name, ItemStack result, String[] shape, Object... ingredients) {
        NamespacedKey key = plugin.key(name);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        for (int i = 0; i < ingredients.length; i += 2) {
            recipe.setIngredient((Character) ingredients[i], (RecipeChoice) ingredients[i + 1]);
        }
        Bukkit.addRecipe(recipe);
        registered.add(key);
    }

    public void unregisterRecipes() {
        registered.forEach(Bukkit::removeRecipe);
        registered.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(registered);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !keyed.getKey().getNamespace().equals(plugin.namespace())) {
            return;
        }
        Feature required = switch (keyed.getKey().getKey()) {
            case "super_apple", "hyper_apple" -> Feature.SUPER_APPLES;
            case "dragon_breath" -> Feature.DRAGON_BREATH;
            case "revive_crystal" -> Feature.RESURRECTION;
            default -> null;
        };
        if (required != null && !plugin.settings().enabled(required)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(org.bukkit.event.block.CrafterCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !keyed.getKey().getNamespace().equals(plugin.namespace())) {
            return;
        }
        Feature required = switch (keyed.getKey().getKey()) {
            case "super_apple", "hyper_apple" -> Feature.SUPER_APPLES;
            case "dragon_breath" -> Feature.DRAGON_BREATH;
            case "revive_crystal" -> Feature.RESURRECTION;
            default -> null;
        };
        if (required != null && !plugin.settings().enabled(required)) {
            event.setCancelled(true);
        }
    }

    // ── Consumo ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        String id = id(event.getItem());
        if (id.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        NamespacedKey countKey = plugin.key("hyper_count");
        if (id.equals("hyper_apple")) {
            int eaten = player.getPersistentDataContainer().getOrDefault(countKey, PersistentDataType.INTEGER, 0);
            if (eaten >= MAX_HYPER_APPLES) {
                event.setCancelled(true);
                plugin.lang().send(player, "items.hyper-apple.limit", Lang.p("max", MAX_HYPER_APPLES));
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyEffects(player, id));
    }

    private void applyEffects(Player player, String id) {
        switch (id) {
            case "golden_head", "golden_skull" -> {
                player.removePotionEffect(PotionEffectType.REGENERATION);
                player.removePotionEffect(PotionEffectType.ABSORPTION);
                // Regeneración II durante 10 s ≈ 4 corazones; Absorción I = 2 corazones durante 2 min.
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
            }
            case "super_apple" -> {
                player.removePotionEffect(PotionEffectType.REGENERATION);
                heal(player, 8);
            }
            case "hyper_apple" -> {
                player.removePotionEffect(PotionEffectType.REGENERATION);
                NamespacedKey countKey = plugin.key("hyper_count");
                int eaten = player.getPersistentDataContainer().getOrDefault(countKey, PersistentDataType.INTEGER, 0) + 1;
                player.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, eaten);
                AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    max.addModifier(new AttributeModifier(plugin.key("hyper_" + eaten), 4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                }
                heal(player, 4);
                plugin.lang().send(player, "items.hyper-apple.eaten", Lang.p("count", eaten), Lang.p("max", MAX_HYPER_APPLES));
            }
            default -> {
            }
        }
    }

    public static void heal(Player player, double amount) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double limit = max != null ? max.getValue() : 20;
        player.setHealth(Math.min(limit, player.getHealth() + amount));
    }
}
