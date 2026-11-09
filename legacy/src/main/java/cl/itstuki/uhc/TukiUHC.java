package cl.itstuki.uhc;

import cl.itstuki.uhc.command.UhcCommands;
import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.config.UhcSettings;
import cl.itstuki.uhc.game.GameManager;
import cl.itstuki.uhc.game.StatsTracker;
import cl.itstuki.uhc.gui.ChatPrompt;
import cl.itstuki.uhc.gui.MenuListener;
import cl.itstuki.uhc.gui.Menus;
import cl.itstuki.uhc.hook.Hooks;
import cl.itstuki.uhc.lang.Lang;
import cl.itstuki.uhc.module.AfkModule;
import cl.itstuki.uhc.module.AutoStartModule;
import cl.itstuki.uhc.module.BorderModule;
import cl.itstuki.uhc.module.ChatModule;
import cl.itstuki.uhc.module.CompassModule;
import cl.itstuki.uhc.module.DragonModule;
import cl.itstuki.uhc.module.EpisodeRewardsModule;
import cl.itstuki.uhc.module.EscortModule;
import cl.itstuki.uhc.module.HeadStealModule;
import cl.itstuki.uhc.module.ItemsModule;
import cl.itstuki.uhc.module.LobbyModule;
import cl.itstuki.uhc.module.LootModule;
import cl.itstuki.uhc.module.ProximityTeamsModule;
import cl.itstuki.uhc.module.ProxyModule;
import cl.itstuki.uhc.module.RandomTeamsModule;
import cl.itstuki.uhc.module.ResourceRushModule;
import cl.itstuki.uhc.module.ResurrectionModule;
import cl.itstuki.uhc.module.RewardsModule;
import cl.itstuki.uhc.module.RulesModule;
import cl.itstuki.uhc.module.ScoreboardModule;
import cl.itstuki.uhc.module.SkinRotationModule;
import cl.itstuki.uhc.module.TrollsModule;
import cl.itstuki.uhc.team.TeamManager;
import cl.itstuki.uhc.util.Credits;
import java.util.Locale;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.java.JavaPlugin;

/** TukiUHC — edición Legacy para Spigot/PaperSpigot 1.8.8 – 1.20.6. */
public final class TukiUHC extends JavaPlugin {
    private static TukiUHC instance;
    private final UhcSettings settings = new UhcSettings();
    private final TeamManager teams = new TeamManager();
    private final StatsTracker stats = new StatsTracker();
    private BukkitAudiences adventure;
    private Lang lang;
    private GameManager game;
    private ChatPrompt prompts;
    private ItemsModule items;
    private ScoreboardModule scoreboard;
    private ResourceRushModule resourceRush;
    private TrollsModule trolls;
    private AfkModule afk;
    private ResurrectionModule resurrection;
    private Menus menus;
    private Hooks hooks;
    private ProxyModule proxy;
    private SkinRotationModule skinRotation;
    private LobbyModule lobby;
    private cl.itstuki.uhc.module.SpectatorModule spectators;
    private cl.itstuki.uhc.map.MapModule map;

    public static TukiUHC get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        adventure = BukkitAudiences.create(this);
        saveDefaultConfig();
        settings.load(getConfig());
        lang = new Lang(this);
        lang.load(settings.language());
        hooks = new Hooks(this);
        lang.playerTags(hooks::tags);
        game = new GameManager(this);
        prompts = new ChatPrompt(this);
        menus = new Menus(this);
        getServer().getPluginManager().registerEvents(prompts, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        items = new ItemsModule(this);
        scoreboard = new ScoreboardModule(this);
        resourceRush = new ResourceRushModule(this);
        trolls = new TrollsModule(this);
        afk = new AfkModule(this);
        resurrection = new ResurrectionModule(this);
        // El orden importa: los equipos se forman antes de repartir roles.
        game.register(new RulesModule(this));
        game.register(items);
        game.register(new BorderModule(this));
        game.register(new RandomTeamsModule(this));
        game.register(new ProximityTeamsModule(this));
        game.register(trolls);
        game.register(new ChatModule(this));
        game.register(new CompassModule(this));
        game.register(new EscortModule(this));
        game.register(resurrection);
        game.register(new EpisodeRewardsModule(this));
        game.register(new LootModule(this));
        game.register(new HeadStealModule(this));
        skinRotation = new SkinRotationModule(this);
        game.register(skinRotation);
        game.register(new DragonModule(this));
        game.register(resourceRush);
        game.register(afk);
        game.register(scoreboard);
        lobby = new LobbyModule(this);
        game.register(lobby);
        proxy = new ProxyModule(this);
        game.register(proxy);
        proxy.registerChannel();
        game.register(new RewardsModule(this));
        game.register(new cl.itstuki.uhc.module.ScenariosModule(this));
        spectators = new cl.itstuki.uhc.module.SpectatorModule(this);
        game.register(spectators);
        map = new cl.itstuki.uhc.map.MapModule(this);
        game.register(map);
        AutoStartModule autoStart = new AutoStartModule(this);
        game.register(autoStart);
        autoStart.startTask();
        hooks.enable();
        getServer().getScheduler().runTask(this, lobby::setup);
        items.registerRecipes();
        game.startTicker();
        scoreboard.startTask();
        new UhcCommands(this).register();

        Credits.enableBanner(this);
        getLogger().info("Servidor Minecraft " + Compat.versionName() + " detectado.");
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stopTicker();
            game.reset();
        }
        if (lobby != null) {
            lobby.removeEntities();
        }
        if (items != null) {
            items.unregisterRecipes();
        }
        if (map != null) {
            map.shutdown();
        }
        if (hooks != null) {
            hooks.disable();
        }
        if (proxy != null) {
            proxy.unregisterChannel();
        }
        saveSettings();
        if (adventure != null) {
            Credits.disableBanner(this);
            adventure.close();
            adventure = null;
        }
        instance = null;
    }

    public void saveSettings() {
        settings.save(getConfig());
        saveConfig();
    }

    public void reloadAll() {
        reloadConfig();
        settings.load(getConfig());
        lang.load(settings.language());
        resourceRush.reloadPools();
    }

    public String namespace() {
        return getName().toLowerCase(Locale.ROOT);
    }

    public BukkitAudiences adventure() {
        if (adventure == null) {
            throw new IllegalStateException("TukiUHC está desactivado");
        }
        return adventure;
    }

    public UhcSettings settings() { return settings; }
    public TeamManager teams() { return teams; }
    public StatsTracker stats() { return stats; }
    public Lang lang() { return lang; }
    public GameManager game() { return game; }
    public ChatPrompt prompts() { return prompts; }
    public ItemsModule items() { return items; }
    public ScoreboardModule scoreboard() { return scoreboard; }
    public ResourceRushModule resourceRush() { return resourceRush; }
    public TrollsModule trolls() { return trolls; }
    public AfkModule afk() { return afk; }
    public ResurrectionModule resurrection() { return resurrection; }
    public Menus menus() { return menus; }
    public Hooks hooks() { return hooks; }
    public ProxyModule proxy() { return proxy; }
    public SkinRotationModule skinRotation() { return skinRotation; }
    public LobbyModule lobby() { return lobby; }
    public cl.itstuki.uhc.module.SpectatorModule spectators() { return spectators; }
    public cl.itstuki.uhc.map.MapModule map() { return map; }
}
