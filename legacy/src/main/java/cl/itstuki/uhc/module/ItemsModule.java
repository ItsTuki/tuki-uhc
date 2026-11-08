package cl.itstuki.uhc.module;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.ItemTag;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.game.UhcModule;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.util.Items;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/** Ítems especiales (cabezas doradas, super/hiper manzanas, cristal de resurrección) y crafteos modificados. */
public final class ItemsModule implements UhcModule {
    public static final int MAX_HYPER_APPLES = 5;

    private final TukiUHC plugin;
    private final Map<UUID, Integer> hyperEaten = new HashMap<>();
    private final Set<UUID> headStolen = new HashSet<>();

    public ItemsModule(TukiUHC plugin) {
        this.plugin = plugin;
    }

    // ── Ítems ────────────────────────────────────────────────

    public ItemStack goldenHead() {
        return Items.of(XMaterial.GOLDEN_APPLE).name(plugin.lang().item("items.golden-head.name"))
                .lore(plugin.lang().itemList("items.golden-head.lore")).tag("golden_head").build();
    }

    public ItemStack goldenSkull() {
        return Items.of(XMaterial.GOLDEN_APPLE).name(plugin.lang().item("items.golden-skull.name"))
                .lore(plugin.lang().itemList("items.golden-skull.lore")).tag("golden_skull").build();
    }

    public ItemStack superApple() {
        return Items.of(XMaterial.GOLDEN_APPLE).name(plugin.lang().item("items.super-apple.name"))
                .lore(plugin.lang().itemList("items.super-apple.lore")).glow(true).tag("super_apple").build();
    }

    public ItemStack hyperApple() {
        return Items.of(XMaterial.GOLDEN_APPLE).name(plugin.lang().item("items.hyper-apple.name"))
                .lore(plugin.lang().itemList("items.hyper-apple.lore", Lang.p("max", MAX_HYPER_APPLES)))
                .glow(true).tag("hyper_apple").build();
    }

    /** En 1.8 no existe el cristal del End como ítem: se usa una estrella del Nether. */
    public ItemStack reviveCrystal() {
        return Items.of(Compat.firstItem(XMaterial.END_CRYSTAL, XMaterial.NETHER_STAR))
                .name(plugin.lang().item("items.revive-crystal.name"))
                .lore(plugin.lang().itemList("items.revive-crystal.lore")).glow(true).tag("revive_crystal").build();
    }

    public String id(ItemStack stack) {
        String id = ItemTag.read(stack);
        return id == null ? "" : id;
    }

    public boolean headStolen(UUID player) {
        return headStolen.contains(player);
    }

    public boolean stealHead(UUID player) {
        return headStolen.add(player);
    }

    /** Olvida lo que el jugador comió o le robaron (al reiniciarlo). */
    public void forget(UUID player) {
        hyperEaten.remove(player);
        headStolen.remove(player);
    }

    // ── Recetas ──────────────────────────────────────────────

    public void registerRecipes() {
        recipeCount = 0;
        Material glistering = Compat.first(XMaterial.GLISTERING_MELON_SLICE);
        Material goldenCarrot = Compat.mat(XMaterial.GOLDEN_CARROT);
        // Las versiones vanilla (con pepitas) se reemplazan por recetas con lingotes/bloques.
        removeRecipes(r -> {
            Material type = r.getResult().getType();
            return (type == glistering || type == goldenCarrot) && ItemTag.read(r.getResult()) == null;
        });

        Material gold = Material.GOLD_INGOT;
        Material head = Compat.mat(XMaterial.PLAYER_HEAD);
        shaped(goldenHead(), new String[]{"GGG", "GHG", "GGG"}, 'G', gold, 'H', XMaterial.PLAYER_HEAD);
        shaped(goldenSkull(), new String[]{"GGG", "GHG", "GGG"}, 'G', gold, 'H', XMaterial.WITHER_SKELETON_SKULL);
        shaped(superApple(), new String[]{"GGG", "GAG", "GGG"}, 'G', gold, 'A', XMaterial.GOLDEN_APPLE);
        shaped(hyperApple(), new String[]{"GGG", "GAG", "GGG"}, 'G', gold, 'A', XMaterial.GOLDEN_APPLE);
        shaped(new ItemStack(goldenCarrot), new String[]{"GGG", "GCG", "GGG"}, 'G', gold, 'C', XMaterial.CARROT);
        if (Compat.atLeast(9)) {
            // En 1.8 la manzana encantada ya se fabrica con bloques de oro.
            shaped(Compat.item(XMaterial.ENCHANTED_GOLDEN_APPLE), new String[]{"BBB", "BAB", "BBB"},
                    'B', Material.GOLD_BLOCK, 'A', XMaterial.APPLE);
            shaped(Compat.item(XMaterial.DRAGON_BREATH), new String[]{"NNN", "BPB", "NNN"},
                    'N', Material.GOLD_NUGGET, 'B', Material.BLAZE_POWDER, 'P', XMaterial.SPLASH_POTION);
        }
        shaped(reviveCrystal(), new String[]{"DBD", "BOB", "DBD"},
                'D', Material.DIAMOND, 'B', Material.BLAZE_POWDER, 'O', Material.OBSIDIAN);

        ShapelessRecipe melon = keyed(ShapelessRecipe.class, new ItemStack(glistering));
        melon.addIngredient(Material.GOLD_BLOCK);
        addIngredient(melon, XMaterial.MELON_SLICE);
        add(melon);
        if (head == Material.STONE) {
            plugin.getLogger().warning("No se encontró el material de cabeza de jugador.");
        }
    }

    private int recipeCount;

    /** 1.12+: receta con NamespacedKey (tukiuhc:receta_N). Antes, el constructor clásico. */
    @SuppressWarnings("deprecation")
    private <T extends Recipe> T keyed(Class<T> type, ItemStack result) {
        recipeCount++;
        if (Compat.atLeast(12)) {
            try {
                Class<?> keyClass = Class.forName("org.bukkit.NamespacedKey");
                Object key = keyClass.getConstructor(org.bukkit.plugin.Plugin.class, String.class)
                        .newInstance(plugin, "receta_" + recipeCount);
                return type.getConstructor(keyClass, ItemStack.class).newInstance(key, result);
            } catch (ReflectiveOperationException ignored) {
                // Se usa el constructor antiguo.
            }
        }
        try {
            return type.getConstructor(ItemStack.class).newInstance(result);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void add(Recipe recipe) {
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalStateException ignored) {
            // Ya estaba registrada (por ejemplo tras /reload).
        }
    }

    private void shaped(ItemStack result, String[] shape, Object... ingredients) {
        ShapedRecipe recipe = keyed(ShapedRecipe.class, result);
        recipe.shape(shape);
        for (int i = 0; i < ingredients.length; i += 2) {
            char key = (Character) ingredients[i];
            Object value = ingredients[i + 1];
            if (value instanceof Material) {
                recipe.setIngredient(key, (Material) value);
            } else {
                setIngredient(recipe, key, (XMaterial) value);
            }
        }
        add(recipe);
    }

    /** En 1.8–1.12 las cabezas y otros ítems se distinguen por "data" (cabeza de jugador = SKULL_ITEM:3). */
    @SuppressWarnings("deprecation")
    private static void setIngredient(ShapedRecipe recipe, char key, XMaterial material) {
        ItemStack stack = Compat.item(material);
        if (Compat.atLeast(13)) {
            recipe.setIngredient(key, stack.getType());
        } else {
            recipe.setIngredient(key, stack.getType(), stack.getDurability());
        }
    }

    @SuppressWarnings("deprecation")
    private static void addIngredient(ShapelessRecipe recipe, XMaterial material) {
        ItemStack stack = Compat.item(material);
        if (Compat.atLeast(13)) {
            recipe.addIngredient(stack.getType());
        } else {
            recipe.addIngredient(stack.getType(), stack.getDurability());
        }
    }

    private interface RecipeFilter {
        boolean test(Recipe recipe);
    }

    private static void removeRecipes(RecipeFilter filter) {
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            try {
                if (recipe != null && recipe.getResult() != null && filter.test(recipe)) {
                    it.remove();
                }
            } catch (RuntimeException ignored) {
                // Algunas recetas especiales no se pueden quitar.
            }
        }
    }

    public void unregisterRecipes() {
        removeRecipes(r -> ItemTag.read(r.getResult()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }
        String id = id(recipe.getResult());
        if (id.isEmpty()) {
            return;
        }
        CraftingInventory inventory = event.getInventory();
        Feature required = null;
        if (id.equals("super_apple") || id.equals("hyper_apple")) {
            required = Feature.SUPER_APPLES;
            // El centro decide: manzana normal → super; super manzana → hiper.
            ItemStack[] matrix = inventory.getMatrix();
            ItemStack center = matrix.length >= 5 ? matrix[4] : null;
            String centerId = id(center);
            boolean wantsHyper = centerId.equals("super_apple");
            if (!centerId.isEmpty() && !wantsHyper) {
                inventory.setResult(null);
                return;
            }
            inventory.setResult(wantsHyper ? hyperApple() : superApple());
        } else if (id.equals("revive_crystal")) {
            required = Feature.RESURRECTION;
        }
        if (required != null && !plugin.settings().enabled(required)) {
            inventory.setResult(null);
        }
    }

    // ── Consumo ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        final String id = id(event.getItem());
        if (id.isEmpty()) {
            return;
        }
        final Player player = event.getPlayer();
        if (id.equals("hyper_apple") && hyperEaten.getOrDefault(player.getUniqueId(), 0) >= MAX_HYPER_APPLES) {
            event.setCancelled(true);
            plugin.lang().send(player, "items.hyper-apple.limit", Lang.p("max", MAX_HYPER_APPLES));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyEffects(player, id));
    }

    private void applyEffects(Player player, String id) {
        switch (id) {
            case "golden_head":
            case "golden_skull":
                Compat.removeEffect(player, XPotion.REGENERATION);
                Compat.removeEffect(player, XPotion.ABSORPTION);
                // Regeneración II durante 10 s ≈ 4 corazones; Absorción I = 2 corazones durante 2 min.
                Compat.effect(player, XPotion.REGENERATION, 200, 1, true);
                Compat.effect(player, XPotion.ABSORPTION, 2400, 0, true);
                break;
            case "super_apple":
                Compat.removeEffect(player, XPotion.REGENERATION);
                Compat.heal(player, 8);
                break;
            case "hyper_apple":
                Compat.removeEffect(player, XPotion.REGENERATION);
                int eaten = hyperEaten.merge(player.getUniqueId(), 1, Integer::sum);
                Compat.addMaxHealth(player, 4);
                Compat.heal(player, 4);
                plugin.lang().send(player, "items.hyper-apple.eaten", Lang.p("count", eaten), Lang.p("max", MAX_HYPER_APPLES));
                break;
            default:
                break;
        }
    }

    public static void heal(Player player, double amount) {
        Compat.heal(player, amount);
    }

    @Override
    public void onReset() {
        hyperEaten.clear();
        headStolen.clear();
    }
}
