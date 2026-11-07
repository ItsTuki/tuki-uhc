package cl.itstuki.uhc.gui;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.config.TeamMode;
import cl.itstuki.uhc.config.UhcMode;
import cl.itstuki.uhc.config.UhcSettings;
import cl.itstuki.uhc.game.GameState;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.module.ResourceRushModule;
import cl.itstuki.uhc.team.Party;
import cl.itstuki.uhc.util.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Todos los menús de /uhcconfig. */
public final class Menus {
    private static final Map<NamedTextColor, Material> WOOL = Map.ofEntries(
            Map.entry(NamedTextColor.RED, Material.RED_WOOL), Map.entry(NamedTextColor.BLUE, Material.BLUE_WOOL),
            Map.entry(NamedTextColor.GREEN, Material.LIME_WOOL), Map.entry(NamedTextColor.YELLOW, Material.YELLOW_WOOL),
            Map.entry(NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_WOOL), Map.entry(NamedTextColor.AQUA, Material.LIGHT_BLUE_WOOL),
            Map.entry(NamedTextColor.GOLD, Material.ORANGE_WOOL), Map.entry(NamedTextColor.DARK_GREEN, Material.GREEN_WOOL),
            Map.entry(NamedTextColor.DARK_AQUA, Material.CYAN_WOOL), Map.entry(NamedTextColor.DARK_PURPLE, Material.PURPLE_WOOL),
            Map.entry(NamedTextColor.DARK_RED, Material.BROWN_WOOL), Map.entry(NamedTextColor.DARK_BLUE, Material.BLUE_WOOL),
            Map.entry(NamedTextColor.WHITE, Material.WHITE_WOOL), Map.entry(NamedTextColor.GRAY, Material.GRAY_WOOL));

    private final TukiUHC plugin;

    public Menus(TukiUHC plugin) {
        this.plugin = plugin;
    }

    private Lang lang() {
        return plugin.lang();
    }

    private UhcSettings settings() {
        return plugin.settings();
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
    }

    private void changed(Player player) {
        click(player);
        plugin.saveSettings();
    }

    private ItemStack back() {
        return Items.of(Material.ARROW).name(lang().item("menu.back")).build();
    }

    private Component onOff(boolean value) {
        return lang().get(value ? "menu.state-on" : "menu.state-off");
    }

    // ── Principal ────────────────────────────────────────────

    public void openMain(Player viewer) {
        new Menu(5, lang().get("menu.main.title")) {
            @Override
            protected void render() {
                set(10, Items.of(settings().mode().icon()).name(lang().item("menu.main.mode.name"))
                        .lore(lore("menu.main.mode.lore", Lang.c("value", lang().get("modes." + settings().mode().key() + ".name")))).hideAll().build(),
                        (p, c) -> { click(p); openModes(p); });
                set(12, Items.of(Material.WHITE_BANNER).name(lang().item("menu.main.teams.name"))
                        .lore(lore("menu.main.teams.lore", Lang.c("value", lang().get("team-modes." + settings().teamMode().key() + ".name")),
                                Lang.p("size", settings().teamSize()))).hideAll().build(),
                        (p, c) -> { click(p); openTeams(p); });
                set(14, Items.of(Material.COMPARATOR).name(lang().item("menu.main.settings.name"))
                        .lore(lore("menu.main.settings.lore")).build(), (p, c) -> { click(p); openSettings(p); });
                set(16, Items.of(Material.KNOWLEDGE_BOOK).name(lang().item("menu.main.features.name"))
                        .lore(lore("menu.main.features.lore")).build(), (p, c) -> { click(p); openFeatures(p, 0); });
                set(28, Items.of(Material.PAPER).name(lang().item("menu.main.language.name"))
                        .lore(lore("menu.main.language.lore", Lang.p("value", lang().code().toUpperCase()))).build(), (p, c) -> {
                    settings().language(lang().code().equals("es") ? "en" : "es");
                    lang().load(settings().language());
                    changed(p);
                    openMain(p);
                });
                GameState state = plugin.game().state();
                set(30, Items.of(Material.LIME_CONCRETE).name(lang().item("menu.main.prepare.name"))
                        .lore(lore("menu.main.prepare.lore")).glow(state == GameState.LOBBY).build(), (p, c) -> {
                    p.closeInventory();
                    plugin.game().prepare(p);
                });
                set(32, Items.of(Material.GREEN_CONCRETE).name(lang().item("menu.main.start.name"))
                        .lore(lore("menu.main.start.lore")).glow(state == GameState.PREPARED).build(), (p, c) -> {
                    p.closeInventory();
                    plugin.game().start(p, c.isShiftClick());
                });
                set(34, Items.of(Material.RED_CONCRETE).name(lang().item("menu.main.reset.name"))
                        .lore(lore("menu.main.reset.lore")).build(), (p, c) -> {
                    if (c.isShiftClick()) {
                        p.closeInventory();
                        plugin.game().reset();
                        lang().send(p, "commands.reset-done");
                    }
                });
                set(40, Items.of(Material.BARRIER).name(lang().item("menu.close")).build(), (p, c) -> p.closeInventory());
            }
        }.open(viewer);
    }

    private List<Component> lore(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        return lang().itemList(key, resolvers);
    }

    // ── Modos ────────────────────────────────────────────────

    public void openModes(Player viewer) {
        new Menu(3, lang().get("menu.modes.title")) {
            @Override
            protected void render() {
                int slot = 11;
                for (UhcMode mode : UhcMode.values()) {
                    boolean selected = settings().mode() == mode;
                    List<Component> lore = new ArrayList<>(lang().itemList("modes." + mode.key() + ".lore"));
                    lore.add(Component.empty());
                    lore.add(lang().item(selected ? "menu.selected" : "menu.click-select"));
                    set(slot++, Items.of(mode.icon()).name(lang().item("modes." + mode.key() + ".name")).lore(lore)
                            .glow(selected).hideAll().build(), (p, c) -> {
                        settings().mode(mode);
                        changed(p);
                        refresh();
                    });
                }
                set(22, Items.of(Material.WRITABLE_BOOK).name(lang().item("menu.modes.rush-edit.name"))
                        .lore(lore("menu.modes.rush-edit.lore")).build(), (p, c) -> { click(p); openRushTiers(p); });
                set(18, back(), (p, c) -> openMain(p));
            }
        }.open(viewer);
    }

    // ── Ajustes generales ────────────────────────────────────

    public void openSettings(Player viewer) {
        new Menu(4, lang().get("menu.settings.title")) {
            @Override
            protected void render() {
                set(10, Items.of(Material.NAME_TAG).name(lang().item("menu.settings.name.name"))
                        .lore(lore("menu.settings.name.lore", Lang.p("value", settings().matchName()))).build(), (p, c) ->
                        plugin.prompts().ask(p, "prompt.match-name", text -> {
                            settings().matchName(text.length() > 32 ? text.substring(0, 32) : text);
                            plugin.saveSettings();
                            lang().send(p, "prompt.saved");
                            openSettings(p);
                        }));
                number(11, Material.CLOCK, "minutes", settings()::episodeMinutes, settings()::episodeMinutes, 5);
                number(12, Material.PAPER, "episodes", settings()::episodes, settings()::episodes, 1);
                number(13, Material.IRON_SWORD, "pvp", settings()::pvpEpisode, settings()::pvpEpisode, 1);
                number(14, Material.GLASS, "border", settings()::borderRadius, settings()::borderRadius, 100);
                number(15, Material.TINTED_GLASS, "border-final", settings()::borderFinalRadius, settings()::borderFinalRadius, 25);
                number(16, Material.HOPPER, "border-episode", settings()::borderShrinkEpisodeRaw, settings()::borderShrinkEpisode, 1);
                preset(21, Material.REDSTONE_LAMP, "preset-live", 5, 60);
                preset(23, Material.BOOKSHELF, "preset-episodes", 10, 25);
                set(31, back(), (p, c) -> openMain(p));
            }

            /** Ajuste rápido de duración: Directo (5 × 60 min) o Episodios (10 × 25 min). */
            private void preset(int slot, Material icon, String key, int count, int minutes) {
                boolean selected = settings().episodes() == count && settings().episodeMinutes() == minutes;
                set(slot, Items.of(icon).name(lang().item("menu.settings." + key + ".name"))
                        .lore(lore("menu.settings." + key + ".lore")).glow(selected).build(), (p, c) -> {
                    settings().episodes(count);
                    settings().episodeMinutes(minutes);
                    changed(p);
                    refresh();
                });
            }

            private void number(int slot, Material icon, String key, IntSupplier get, IntConsumer setter, int step) {
                set(slot, Items.of(icon).name(lang().item("menu.settings." + key + ".name"))
                        .lore(numberLore("menu.settings." + key + ".lore", get.getAsInt(), step)).hideAll().build(), (p, c) -> {
                    if (c == ClickType.SHIFT_LEFT || c == ClickType.SHIFT_RIGHT) {
                        plugin.prompts().askNumber(p, "prompt.number", value -> {
                            setter.accept(value);
                            plugin.saveSettings();
                            lang().send(p, "prompt.saved");
                            openSettings(p);
                        });
                        return;
                    }
                    setter.accept(get.getAsInt() + (c.isRightClick() ? -step : step));
                    changed(p);
                    refresh();
                });
            }
        }.open(viewer);
    }

    private List<Component> numberLore(String key, int value, int step) {
        List<Component> lore = new ArrayList<>(lang().itemList(key, Lang.p("value", value)));
        lore.add(Component.empty());
        lore.addAll(lang().itemList("menu.number-help", Lang.p("step", step)));
        return lore;
    }

    // ── Opciones ─────────────────────────────────────────────

    public void openFeatures(Player viewer, int page) {
        new Menu(6, lang().get("menu.features.title")) {
            @Override
            protected void render() {
                Feature[] all = Feature.values();
                for (int i = 0; i < all.length && i < 45; i++) {
                    Feature feature = all[i];
                    boolean on = settings().enabled(feature);
                    List<Component> lore = new ArrayList<>(lang().itemList("features." + feature.key() + ".lore"));
                    lore.add(Component.empty());
                    lore.add(lang().item("menu.features.state", Lang.c("state", onOff(on))));
                    if (feature.valueType() != Feature.ValueType.NONE) {
                        lore.add(lang().item("menu.features.value", Lang.c("value", valueLabel(feature))));
                    }
                    lore.add(Component.empty());
                    lore.add(lang().item("menu.features.help-toggle"));
                    if (feature.valueType() != Feature.ValueType.NONE) {
                        lore.add(lang().item("menu.features.help-value"));
                    }
                    set(i, Items.of(feature.icon()).name(lang().item("features." + feature.key() + ".name")).lore(lore)
                            .glow(on).hideAll().build(), (p, c) -> {
                        if (c.isRightClick() && feature.valueType() != Feature.ValueType.NONE) {
                            plugin.prompts().askNumber(p, "prompt.feature-value", value -> {
                                settings().setValue(feature, value);
                                settings().setEnabled(feature, true);
                                plugin.saveSettings();
                                lang().send(p, "prompt.feature-saved", Lang.c("feature", lang().get("features." + feature.key() + ".name")),
                                        Lang.c("value", valueLabel(feature)));
                                openFeatures(p, 0);
                            }, Lang.c("hint", lang().get("value-types." + feature.valueType().name().toLowerCase(java.util.Locale.ROOT)
                                    + ".hint")));
                            return;
                        }
                        settings().setEnabled(feature, !settings().enabled(feature));
                        changed(p);
                        refresh();
                    });
                }
                set(49, back(), (p, c) -> openMain(p));
            }
        }.open(viewer);
    }

    public Component valueLabel(Feature feature) {
        int value = settings().value(feature);
        String type = feature.valueType().name().toLowerCase(java.util.Locale.ROOT);
        if (feature.valueType() == Feature.ValueType.EPISODE && value <= 0) {
            return lang().get("value-types.episode.last", Lang.p("value", settings().finalEpisode()));
        }
        return lang().get("value-types." + type + ".format", Lang.p("value", value));
    }

    // ── Equipos ──────────────────────────────────────────────

    public void openTeams(Player viewer) {
        new Menu(5, lang().get("menu.teams.title")) {
            @Override
            protected void render() {
                int slot = 10;
                for (TeamMode mode : TeamMode.values()) {
                    boolean selected = settings().teamMode() == mode;
                    List<Component> lore = new ArrayList<>(lang().itemList("team-modes." + mode.key() + ".lore"));
                    lore.add(Component.empty());
                    lore.add(lang().item(selected ? "menu.selected" : "menu.click-select"));
                    set(slot++, Items.of(mode.icon()).name(lang().item("team-modes." + mode.key() + ".name")).lore(lore)
                            .glow(selected).hideAll().build(), (p, c) -> {
                        settings().teamMode(mode);
                        changed(p);
                        refresh();
                    });
                }
                set(15, Items.of(Material.PLAYER_HEAD).name(lang().item("menu.teams.size.name"))
                        .lore(numberLore("menu.teams.size.lore", settings().teamSize(), 1)).build(), (p, c) -> {
                    settings().teamSize(settings().teamSize() + (c.isRightClick() ? -1 : 1));
                    changed(p);
                    refresh();
                });
                set(16, Items.of(Material.CLOCK).name(lang().item("menu.teams.reveal.name"))
                        .lore(numberLore("menu.teams.reveal.lore", settings().randomRevealEpisode(), 1)).build(), (p, c) -> {
                    settings().randomRevealEpisode(settings().randomRevealEpisode() + (c.isRightClick() ? -1 : 1));
                    changed(p);
                    refresh();
                });
                set(29, Items.of(Material.CRAFTER).name(lang().item("menu.teams.manage-teams.name"))
                        .lore(lore("menu.teams.manage-teams.lore", Lang.p("count", plugin.teams().teams().size()))).build(),
                        (p, c) -> { click(p); openParties(p, Party.Kind.TEAM, false); });
                set(31, Items.of(Material.BOOKSHELF).name(lang().item("menu.teams.manage-groups.name"))
                        .lore(lore("menu.teams.manage-groups.lore", Lang.p("count", plugin.teams().groups().size()))).build(),
                        (p, c) -> { click(p); openParties(p, Party.Kind.GROUP, false); });
                set(33, Items.of(Material.DISPENSER).name(lang().item("menu.teams.shuffle.name"))
                        .lore(lore("menu.teams.shuffle.lore")).build(), (p, c) -> {
                    plugin.teams().clearTeams();
                    plugin.teams().randomize(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList(),
                            settings().teamSize(), lang().raw("teams.default-name"));
                    changed(p);
                    lang().send(p, "teams.shuffled", Lang.p("count", plugin.teams().teams().size()));
                    refresh();
                });
                set(36, back(), (p, c) -> openMain(p));
            }
        }.open(viewer);
    }

    public void openParties(Player viewer, Party.Kind kind, boolean deleteMode) {
        String k = kind == Party.Kind.TEAM ? "team" : "group";
        new Menu(6, lang().get("menu.parties." + k + ".title")) {
            @Override
            protected void render() {
                set(0, Items.of(Material.LIME_WOOL).name(lang().item("menu.parties." + k + ".create")).build(), (p, c) ->
                        plugin.prompts().ask(p, "prompt.party-name", text -> {
                            Party party = plugin.teams().create(kind, text.length() > 24 ? text.substring(0, 24) : text);
                            lang().send(p, "teams.created", Lang.p("team", party.name()));
                            openParties(p, kind, false);
                        }));
                set(1, Items.of(Material.RED_WOOL).name(lang().item("menu.parties." + k + ".delete"))
                        .lore(lore(deleteMode ? "menu.parties.delete-active" : "menu.parties.delete-help")).glow(deleteMode).build(),
                        (p, c) -> openParties(p, kind, !deleteMode));
                int slot = 9;
                for (Party party : plugin.teams().all(kind)) {
                    if (slot >= 45) {
                        break;
                    }
                    List<Component> lore = new ArrayList<>();
                    for (UUID member : party.members()) {
                        lore.add(Component.text("• " + name(member), NamedTextColor.GRAY));
                    }
                    if (lore.isEmpty()) {
                        lore.add(lang().item("menu.parties.empty"));
                    }
                    lore.add(Component.empty());
                    lore.add(lang().item(deleteMode ? "menu.parties.click-delete" : "menu.parties.click-manage"));
                    set(slot++, Items.of(WOOL.getOrDefault(party.color(), Material.WHITE_WOOL))
                            .name(Component.text(party.name(), party.color())).lore(lore).build(), (p, c) -> {
                        if (deleteMode) {
                            plugin.teams().delete(party);
                            lang().send(p, "teams.deleted", Lang.p("team", party.name()));
                            openParties(p, kind, false);
                        } else {
                            openParty(p, party);
                        }
                    });
                }
                set(49, back(), (p, c) -> openTeams(p));
            }
        }.open(viewer);
    }

    public void openParty(Player viewer, Party party) {
        new Menu(6, Component.text(party.name(), party.color())) {
            @Override
            protected void render() {
                int slot = 0;
                for (UUID member : List.copyOf(party.members())) {
                    if (slot >= 36) {
                        break;
                    }
                    set(slot++, Items.of(Material.PLAYER_HEAD).owner(Bukkit.getOfflinePlayer(member))
                            .name(Component.text(name(member), party.color())).lore(List.of(lang().item("menu.party.click-remove"))).build(),
                            (p, c) -> {
                                plugin.teams().remove(member, party.kind());
                                click(p);
                                refresh();
                            });
                }
                set(45, back(), (p, c) -> openParties(p, party.kind(), false));
                set(48, Items.of(Material.NAME_TAG).name(lang().item("menu.party.rename")).build(), (p, c) ->
                        plugin.prompts().ask(p, "prompt.party-name", text -> {
                            plugin.teams().rename(party, text.length() > 24 ? text.substring(0, 24) : text);
                            lang().send(p, "prompt.saved");
                            openParty(p, party);
                        }));
                set(50, Items.of(Material.EMERALD).name(lang().item("menu.party.add")).build(), (p, c) -> openAddPlayers(p, party));
            }
        }.open(viewer);
    }

    public void openAddPlayers(Player viewer, Party party) {
        new Menu(6, lang().get("menu.party.add-title", Lang.p("team", party.name()))) {
            @Override
            protected void render() {
                int slot = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (slot >= 45 || party.members().contains(online.getUniqueId())) {
                        continue;
                    }
                    Party current = party.kind() == Party.Kind.TEAM ? plugin.teams().team(online.getUniqueId()) : plugin.teams().group(online.getUniqueId());
                    List<Component> lore = new ArrayList<>();
                    lore.add(current == null ? lang().item("menu.party.no-team") : lang().item("menu.party.current", Lang.p("team", current.name())));
                    lore.add(lang().item("menu.party.click-add"));
                    set(slot++, Items.of(Material.PLAYER_HEAD).owner(online).name(Component.text(online.getName(), NamedTextColor.WHITE))
                            .lore(lore).build(), (p, c) -> {
                        plugin.teams().add(party, online.getUniqueId());
                        click(p);
                        refresh();
                    });
                }
                set(49, back(), (p, c) -> openParty(p, party));
            }
        }.open(viewer);
    }

    private String name(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            return player.getName();
        }
        String offline = Bukkit.getOfflinePlayer(id).getName();
        return offline != null ? offline : id.toString().substring(0, 8);
    }

    // ── Resource Rush ────────────────────────────────────────

    public void openRushTiers(Player viewer) {
        new Menu(3, lang().get("menu.rush.title")) {
            @Override
            protected void render() {
                Material[] icons = {Material.OAK_LOG, Material.IRON_INGOT, Material.DIAMOND, Material.NETHERITE_INGOT};
                for (int tier = 1; tier <= ResourceRushModule.TIERS; tier++) {
                    int t = tier;
                    set(10 + tier, Items.of(icons[tier - 1]).amount(tier)
                            .name(lang().item("menu.rush.tier", Lang.p("tier", tier)))
                            .lore(lore("menu.rush.tier-lore", Lang.p("count", plugin.resourceRush().pool(tier).size()))).build(),
                            (p, c) -> openRushTier(p, t));
                }
                set(18, back(), (p, c) -> openModes(p));
            }
        }.open(viewer);
    }

    public void openRushTier(Player viewer, int tier) {
        new Menu(6, lang().get("menu.rush.tier", Lang.p("tier", tier))) {
            @Override
            protected void render() {
                List<Material> pool = plugin.resourceRush().pool(tier);
                for (int i = 0; i < pool.size() && i < 45; i++) {
                    Material material = pool.get(i);
                    set(i, Items.of(material).lore(List.of(lang().item("menu.rush.click-remove"))).build(), (p, c) -> {
                        pool.remove(material);
                        plugin.resourceRush().savePools();
                        click(p);
                        refresh();
                    });
                }
                set(49, Items.of(Material.BOOK).name(lang().item("menu.rush.help")).build(), null);
                set(45, back(), (p, c) -> openRushTiers(p));
            }

            @Override
            public void clickBottom(Player player, @Nullable ItemStack item, ClickType click) {
                if (item == null || item.isEmpty()) {
                    return;
                }
                List<Material> pool = plugin.resourceRush().pool(tier);
                if (!pool.contains(item.getType()) && pool.size() < 45) {
                    pool.add(item.getType());
                    plugin.resourceRush().savePools();
                    click(player);
                    refresh();
                }
            }
        }.open(viewer);
    }
}
