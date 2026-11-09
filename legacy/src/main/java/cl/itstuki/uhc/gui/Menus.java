package cl.itstuki.uhc.gui;

import cl.itstuki.uhc.TukiUHC;
import cl.itstuki.uhc.config.Feature;
import cl.itstuki.uhc.config.MatchFormat;
import cl.itstuki.uhc.config.Scenario;
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
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.Text;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Todos los menús de /uhcconfig. */
public final class Menus {
    private static final Map<NamedTextColor, XMaterial> WOOL = new HashMap<>();

    static {
        WOOL.put(NamedTextColor.RED, XMaterial.RED_WOOL);
        WOOL.put(NamedTextColor.BLUE, XMaterial.BLUE_WOOL);
        WOOL.put(NamedTextColor.GREEN, XMaterial.LIME_WOOL);
        WOOL.put(NamedTextColor.YELLOW, XMaterial.YELLOW_WOOL);
        WOOL.put(NamedTextColor.LIGHT_PURPLE, XMaterial.MAGENTA_WOOL);
        WOOL.put(NamedTextColor.AQUA, XMaterial.LIGHT_BLUE_WOOL);
        WOOL.put(NamedTextColor.GOLD, XMaterial.ORANGE_WOOL);
        WOOL.put(NamedTextColor.DARK_GREEN, XMaterial.GREEN_WOOL);
        WOOL.put(NamedTextColor.DARK_AQUA, XMaterial.CYAN_WOOL);
        WOOL.put(NamedTextColor.DARK_PURPLE, XMaterial.PURPLE_WOOL);
        WOOL.put(NamedTextColor.DARK_RED, XMaterial.BROWN_WOOL);
        WOOL.put(NamedTextColor.DARK_BLUE, XMaterial.BLUE_WOOL);
        WOOL.put(NamedTextColor.WHITE, XMaterial.WHITE_WOOL);
        WOOL.put(NamedTextColor.GRAY, XMaterial.GRAY_WOOL);
    }

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
        Compat.sound(player, XSound.UI_BUTTON_CLICK, 0.6f, 1f);
    }

    private void changed(Player player) {
        click(player);
        plugin.saveSettings();
    }

    private ItemStack back() {
        return Items.of(XMaterial.ARROW).name(lang().item("menu.back")).build();
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
                set(12, Items.of(XMaterial.WHITE_BANNER).name(lang().item("menu.main.teams.name"))
                        .lore(lore("menu.main.teams.lore", Lang.c("value", lang().get("team-modes." + settings().teamMode().key() + ".name")),
                                Lang.p("size", settings().teamSize()))).hideAll().build(),
                        (p, c) -> { click(p); openTeams(p); });
                set(14, Items.of(Compat.firstItem(XMaterial.COMPARATOR, XMaterial.REDSTONE)).name(lang().item("menu.main.settings.name"))
                        .lore(lore("menu.main.settings.lore")).build(), (p, c) -> { click(p); openSettings(p); });
                set(16, Items.of(Compat.firstItem(XMaterial.KNOWLEDGE_BOOK, XMaterial.ENCHANTED_BOOK)).name(lang().item("menu.main.features.name"))
                        .lore(lore("menu.main.features.lore")).build(), (p, c) -> { click(p); openFeatures(p, 0); });
                set(20, Items.of(XMaterial.FILLED_MAP).name(lang().item("menu.main.map.name"))
                        .lore(lore("menu.main.map.lore", Lang.p("status", plugin.map().statusLine()))).build(),
                        (p, c) -> { click(p); openMap(p); });
                set(24, Items.of(XMaterial.BREWING_STAND).name(lang().item("menu.main.scenarios.name"))
                        .lore(lore("menu.main.scenarios.lore", Lang.p("count", settings().scenarios().size()))).build(),
                        (p, c) -> { click(p); openScenarios(p); });
                set(28, Items.of(XMaterial.PAPER).name(lang().item("menu.main.language.name"))
                        .lore(lore("menu.main.language.lore", Lang.p("value", lang().code().toUpperCase()))).build(), (p, c) -> {
                    settings().language(lang().code().equals("es") ? "en" : "es");
                    lang().load(settings().language());
                    changed(p);
                    openMain(p);
                });
                GameState state = plugin.game().state();
                set(30, Items.of(Compat.firstItem(XMaterial.LIME_CONCRETE, XMaterial.LIME_TERRACOTTA)).name(lang().item("menu.main.prepare.name"))
                        .lore(lore("menu.main.prepare.lore")).glow(state == GameState.LOBBY).build(), (p, c) -> {
                    p.closeInventory();
                    plugin.game().prepare(p);
                });
                set(32, Items.of(Compat.firstItem(XMaterial.GREEN_CONCRETE, XMaterial.GREEN_TERRACOTTA)).name(lang().item("menu.main.start.name"))
                        .lore(lore("menu.main.start.lore")).glow(state == GameState.PREPARED).build(), (p, c) -> {
                    p.closeInventory();
                    plugin.game().start(p, c.isShiftClick());
                });
                set(34, Items.of(Compat.firstItem(XMaterial.RED_CONCRETE, XMaterial.RED_TERRACOTTA)).name(lang().item("menu.main.reset.name"))
                        .lore(lore("menu.main.reset.lore")).build(), (p, c) -> {
                    if (c.isShiftClick()) {
                        p.closeInventory();
                        plugin.game().reset();
                        lang().send(p, "commands.reset-done");
                    }
                });
                set(40, Items.of(XMaterial.BARRIER).name(lang().item("menu.close")).build(), (p, c) -> p.closeInventory());
            }
        }.open(viewer);
    }

    private List<String> lore(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
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
                    List<String> lore = new ArrayList<>(lang().itemList("modes." + mode.key() + ".lore"));
                    lore.add("");
                    lore.add(lang().item(selected ? "menu.selected" : "menu.click-select"));
                    set(slot++, Items.of(mode.icon()).name(lang().item("modes." + mode.key() + ".name")).lore(lore)
                            .glow(selected).hideAll().build(), (p, c) -> {
                        settings().mode(mode);
                        changed(p);
                        refresh();
                    });
                }
                set(22, Items.of(XMaterial.WRITABLE_BOOK).name(lang().item("menu.modes.rush-edit.name"))
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
                set(10, Items.of(XMaterial.NAME_TAG).name(lang().item("menu.settings.name.name"))
                        .lore(lore("menu.settings.name.lore", Lang.p("value", settings().matchName()))).build(), (p, c) ->
                        plugin.prompts().ask(p, "prompt.match-name", text -> {
                            settings().matchName(text.length() > 32 ? text.substring(0, 32) : text);
                            plugin.saveSettings();
                            lang().send(p, "prompt.saved");
                            openSettings(p);
                        }));
                number(11, XMaterial.CLOCK, "minutes", settings()::episodeMinutes, settings()::episodeMinutes, 5);
                number(12, XMaterial.PAPER, "episodes", settings()::episodes, settings()::episodes, 1);
                number(13, XMaterial.IRON_SWORD, "pvp", settings()::pvpEpisode, settings()::pvpEpisode, 1);
                number(14, XMaterial.GLASS, "border", settings()::borderRadius, settings()::borderRadius, 100);
                number(15, XMaterial.BLACK_STAINED_GLASS, "border-final", settings()::borderFinalRadius, settings()::borderFinalRadius, 25);
                number(16, XMaterial.HOPPER, "border-episode", settings()::borderShrinkEpisodeRaw, settings()::borderShrinkEpisode, 1);
                boolean timed = settings().timed();
                set(19, Items.of(settings().format().icon()).name(lang().item("menu.settings.format.name"))
                        .lore(lore("menu.settings.format.lore", Lang.c("value", lang().get("formats." + settings().format().key() + ".name")),
                                Lang.c("description", lang().get("formats." + settings().format().key() + ".description"))))
                        .glow(timed).build(), (p, c) -> {
                    settings().format(timed ? MatchFormat.EPISODES : MatchFormat.TIMED);
                    changed(p);
                    refresh();
                });
                if (timed) {
                    number(20, XMaterial.CLOCK, "timed-minutes", settings()::timedMinutes, settings()::timedMinutes, 10);
                    number(21, XMaterial.HOPPER, "border-start", settings()::borderStartMinutes, settings()::borderStartMinutes, 5);
                }
                preset(23, XMaterial.REDSTONE_LAMP, "preset-live", 5, 60);
                preset(24, XMaterial.BOOKSHELF, "preset-episodes", 10, 25);
                set(31, back(), (p, c) -> openMain(p));
            }

            /** Ajuste rápido de duración: Directo (5 × 60 min) o Episodios (10 × 25 min). */
            private void preset(int slot, XMaterial icon, String key, int count, int minutes) {
                boolean selected = !settings().timed() && settings().episodes() == count && settings().episodeMinutes() == minutes;
                set(slot, Items.of(icon).name(lang().item("menu.settings." + key + ".name"))
                        .lore(lore("menu.settings." + key + ".lore")).glow(selected).build(), (p, c) -> {
                    settings().format(MatchFormat.EPISODES);
                    settings().episodes(count);
                    settings().episodeMinutes(minutes);
                    changed(p);
                    refresh();
                });
            }

            private void number(int slot, XMaterial icon, String key, IntSupplier get, IntConsumer setter, int step) {
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

    private List<String> numberLore(String key, int value, int step) {
        List<String> lore = new ArrayList<>(lang().itemList(key, Lang.p("value", value)));
        lore.add("");
        lore.addAll(lang().itemList("menu.number-help", Lang.p("step", step)));
        return lore;
    }

    // ── Opciones ─────────────────────────────────────────────

    public void openFeatures(Player viewer, int page) {
        new Menu(6, lang().get("menu.features.title")) {
            @Override
            protected void render() {
                List<Feature> all = new ArrayList<>();
                for (Feature f : Feature.values()) {
                    if (f.supported()) {
                        all.add(f);
                    }
                }
                for (int i = 0; i < all.size() && i < 45; i++) {
                    Feature feature = all.get(i);
                    boolean on = settings().enabled(feature);
                    List<String> lore = new ArrayList<>(lang().itemList("features." + feature.key() + ".lore"));
                    lore.add("");
                    lore.add(lang().item("menu.features.state", Lang.c("state", onOff(on))));
                    if (feature.valueType() != Feature.ValueType.NONE) {
                        lore.add(lang().item("menu.features.value", Lang.c("value", valueLabel(feature))));
                    }
                    lore.add("");
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

    // ── Escenarios ───────────────────────────────────────────

    public void openScenarios(Player viewer) {
        new Menu(6, lang().get("menu.scenarios.title")) {
            @Override
            protected void render() {
                // Una fila por categoría: el icono de la categoría y luego sus escenarios.
                java.util.Map<Scenario.Category, Integer> next = new java.util.EnumMap<>(Scenario.Category.class);
                for (Scenario.Category category : Scenario.Category.values()) {
                    int row = category.ordinal();
                    set(row * 9, Items.of(category.icon()).name(lang().item("scenarios.categories." + category.key()))
                            .hideAll().build(), null);
                    next.put(category, row * 9 + 1);
                }
                for (final Scenario scenario : Scenario.values()) {
                    int slot = next.get(scenario.category());
                    if (slot % 9 == 0) {
                        continue;
                    }
                    next.put(scenario.category(), slot + 1);
                    boolean on = settings().scenario(scenario);
                    List<String> lore = new ArrayList<>(lang().itemList("scenarios." + scenario.key() + ".lore"));
                    lore.add("");
                    lore.add(lang().item("menu.features.state", Lang.c("state", onOff(on))));
                    lore.add(lang().item("menu.scenarios.help"));
                    set(slot, Items.of(scenario.icon()).name(lang().item("scenarios." + scenario.key() + ".name")).lore(lore)
                            .glow(on).hideAll().build(), (p, c) -> {
                        settings().scenario(scenario, !settings().scenario(scenario));
                        changed(p);
                        refresh();
                    });
                }
                set(49, back(), (p, c) -> openMain(p));
            }
        }.open(viewer);
    }

    // ── Mapa: pregeneración, minerales y cuevas ──────────────

    public void openMap(Player viewer) {
        new Menu(3, lang().get("menu.map.title")) {
            @Override
            protected void render() {
                boolean running = plugin.map().running();
                set(10, Items.of(running ? Compat.firstItem(XMaterial.RED_CONCRETE, XMaterial.RED_TERRACOTTA)
                                : Compat.firstItem(XMaterial.LIME_CONCRETE, XMaterial.LIME_TERRACOTTA))
                        .name(lang().item(running ? "menu.map.stop.name" : "menu.map.start.name"))
                        .lore(lore("menu.map.start.lore", Lang.p("radius", plugin.map().pregenRadius()),
                                Lang.p("status", plugin.map().statusLine()))).glow(running).build(), (p, c) -> {
                    if (plugin.map().running()) {
                        plugin.map().stop(p);
                    } else {
                        plugin.map().start(p);
                    }
                    click(p);
                    refresh();
                });
                for (final Feature feature : new Feature[] {Feature.ORE_BOOST, Feature.BIG_CAVES}) {
                    int slot = feature == Feature.ORE_BOOST ? 12 : 14;
                    boolean on = settings().enabled(feature);
                    List<String> lore = new ArrayList<>(lang().itemList("features." + feature.key() + ".lore"));
                    lore.add("");
                    lore.add(lang().item("menu.features.state", Lang.c("state", onOff(on))));
                    if (feature.valueType() != Feature.ValueType.NONE) {
                        lore.add(lang().item("menu.features.value", Lang.c("value", valueLabel(feature))));
                    }
                    lore.add("");
                    lore.add(lang().item("menu.features.help-toggle"));
                    if (feature.valueType() != Feature.ValueType.NONE) {
                        lore.add(lang().item("menu.features.help-value"));
                    }
                    set(slot, Items.of(feature.icon()).name(lang().item("features." + feature.key() + ".name")).lore(lore)
                            .glow(on).hideAll().build(), (p, c) -> {
                        if (c.isRightClick() && feature.valueType() != Feature.ValueType.NONE) {
                            plugin.prompts().askNumber(p, "prompt.feature-value", value -> {
                                settings().setValue(feature, value);
                                settings().setEnabled(feature, true);
                                plugin.saveSettings();
                                openMap(p);
                            }, Lang.c("hint", lang().get("value-types." + feature.valueType().name().toLowerCase(java.util.Locale.ROOT)
                                    + ".hint")));
                            return;
                        }
                        settings().setEnabled(feature, !settings().enabled(feature));
                        changed(p);
                        refresh();
                    });
                }
                set(16, Items.of(XMaterial.BOOK).name(lang().item("menu.map.info.name")).lore(lore("menu.map.info.lore")).build(), null);
                set(18, back(), (p, c) -> openMain(p));
            }
        }.open(viewer);
    }

    // ── Equipos ──────────────────────────────────────────────

    public void openTeams(Player viewer) {
        new Menu(5, lang().get("menu.teams.title")) {
            @Override
            protected void render() {
                int slot = 10;
                for (TeamMode mode : TeamMode.values()) {
                    boolean selected = settings().teamMode() == mode;
                    List<String> lore = new ArrayList<>(lang().itemList("team-modes." + mode.key() + ".lore"));
                    lore.add("");
                    lore.add(lang().item(selected ? "menu.selected" : "menu.click-select"));
                    set(slot++, Items.of(mode.icon()).name(lang().item("team-modes." + mode.key() + ".name")).lore(lore)
                            .glow(selected).hideAll().build(), (p, c) -> {
                        settings().teamMode(mode);
                        changed(p);
                        refresh();
                    });
                }
                set(15, Items.of(XMaterial.PLAYER_HEAD).name(lang().item("menu.teams.size.name"))
                        .lore(numberLore("menu.teams.size.lore", settings().teamSize(), 1)).build(), (p, c) -> {
                    settings().teamSize(settings().teamSize() + (c.isRightClick() ? -1 : 1));
                    changed(p);
                    refresh();
                });
                set(16, Items.of(XMaterial.CLOCK).name(lang().item("menu.teams.reveal.name"))
                        .lore(numberLore("menu.teams.reveal.lore", settings().randomRevealEpisode(), 1)).build(), (p, c) -> {
                    settings().randomRevealEpisode(settings().randomRevealEpisode() + (c.isRightClick() ? -1 : 1));
                    changed(p);
                    refresh();
                });
                set(29, Items.of(Compat.firstItem(XMaterial.CRAFTER, XMaterial.CRAFTING_TABLE)).name(lang().item("menu.teams.manage-teams.name"))
                        .lore(lore("menu.teams.manage-teams.lore", Lang.p("count", plugin.teams().teams().size()))).build(),
                        (p, c) -> { click(p); openParties(p, Party.Kind.TEAM, false); });
                set(31, Items.of(XMaterial.BOOKSHELF).name(lang().item("menu.teams.manage-groups.name"))
                        .lore(lore("menu.teams.manage-groups.lore", Lang.p("count", plugin.teams().groups().size()))).build(),
                        (p, c) -> { click(p); openParties(p, Party.Kind.GROUP, false); });
                set(33, Items.of(XMaterial.DISPENSER).name(lang().item("menu.teams.shuffle.name"))
                        .lore(lore("menu.teams.shuffle.lore")).build(), (p, c) -> {
                    plugin.teams().clearTeams();
                    plugin.teams().randomize(Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toList()),
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
                set(0, Items.of(XMaterial.LIME_WOOL).name(lang().item("menu.parties." + k + ".create")).build(), (p, c) ->
                        plugin.prompts().ask(p, "prompt.party-name", text -> {
                            Party party = plugin.teams().create(kind, text.length() > 24 ? text.substring(0, 24) : text);
                            lang().send(p, "teams.created", Lang.p("team", party.name()));
                            openParties(p, kind, false);
                        }));
                set(1, Items.of(XMaterial.RED_WOOL).name(lang().item("menu.parties." + k + ".delete"))
                        .lore(lore(deleteMode ? "menu.parties.delete-active" : "menu.parties.delete-help")).glow(deleteMode).build(),
                        (p, c) -> openParties(p, kind, !deleteMode));
                int slot = 9;
                for (Party party : plugin.teams().all(kind)) {
                    if (slot >= 45) {
                        break;
                    }
                    List<String> lore = new ArrayList<>();
                    for (UUID member : party.members()) {
                        lore.add("§7• " + name(member));
                    }
                    if (lore.isEmpty()) {
                        lore.add(lang().item("menu.parties.empty"));
                    }
                    lore.add("");
                    lore.add(lang().item(deleteMode ? "menu.parties.click-delete" : "menu.parties.click-manage"));
                    set(slot++, Items.of(WOOL.containsKey(party.color()) ? WOOL.get(party.color()) : XMaterial.WHITE_WOOL)
                            .name(Text.code(party.color()) + party.name()).lore(lore).build(), (p, c) -> {
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
                for (UUID member : new ArrayList<>(party.members())) {
                    if (slot >= 36) {
                        break;
                    }
                    set(slot++, Items.of(XMaterial.PLAYER_HEAD).owner(name(member))
                            .name(Text.code(party.color()) + name(member)).lore(Collections.singletonList(lang().item("menu.party.click-remove"))).build(),
                            (p, c) -> {
                                plugin.teams().remove(member, party.kind());
                                click(p);
                                refresh();
                            });
                }
                set(45, back(), (p, c) -> openParties(p, party.kind(), false));
                set(48, Items.of(XMaterial.NAME_TAG).name(lang().item("menu.party.rename")).build(), (p, c) ->
                        plugin.prompts().ask(p, "prompt.party-name", text -> {
                            plugin.teams().rename(party, text.length() > 24 ? text.substring(0, 24) : text);
                            lang().send(p, "prompt.saved");
                            openParty(p, party);
                        }));
                set(50, Items.of(XMaterial.EMERALD).name(lang().item("menu.party.add")).build(), (p, c) -> openAddPlayers(p, party));
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
                    List<String> lore = new ArrayList<>();
                    lore.add(current == null ? lang().item("menu.party.no-team") : lang().item("menu.party.current", Lang.p("team", current.name())));
                    lore.add(lang().item("menu.party.click-add"));
                    set(slot++, Items.of(XMaterial.PLAYER_HEAD).owner(online.getName()).name("§f" + online.getName())
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
                ItemStack[] icons = {Compat.item(XMaterial.OAK_LOG), Compat.item(XMaterial.IRON_INGOT), Compat.item(XMaterial.DIAMOND),
                        Compat.firstItem(XMaterial.NETHERITE_INGOT, XMaterial.EMERALD)};
                for (int tier = 1; tier <= ResourceRushModule.TIERS; tier++) {
                    int t = tier;
                    set(10 + tier, Items.of(icons[tier - 1].clone()).amount(tier)
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
                List<XMaterial> pool = plugin.resourceRush().pool(tier);
                for (int i = 0; i < pool.size() && i < 45; i++) {
                    XMaterial material = pool.get(i);
                    if (!material.isSupported()) {
                        continue;
                    }
                    set(i, Items.of(material).lore(Collections.singletonList(lang().item("menu.rush.click-remove"))).build(), (p, c) -> {
                        pool.remove(material);
                        plugin.resourceRush().savePools();
                        click(p);
                        refresh();
                    });
                }
                set(49, Items.of(XMaterial.BOOK).name(lang().item("menu.rush.help")).build(), null);
                set(45, back(), (p, c) -> openRushTiers(p));
            }

            @Override
            public void clickBottom(Player player, ItemStack item, ClickType click) {
                if (item == null || Compat.isAir(item.getType())) {
                    return;
                }
                List<XMaterial> pool = plugin.resourceRush().pool(tier);
                XMaterial type = XMaterial.matchXMaterial(item);
                if (!pool.contains(type) && pool.size() < 45) {
                    pool.add(type);
                    plugin.resourceRush().savePools();
                    click(player);
                    refresh();
                }
            }
        }.open(viewer);
    }
}
