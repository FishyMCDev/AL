package fishy.avatarlegacy;

import fishy.avatarlegacy.commands.*;
import fishy.avatarlegacy.database.DatabaseManager;
import fishy.avatarlegacy.guis.*;
import fishy.avatarlegacy.integrations.*;
import fishy.avatarlegacy.items.*;
import fishy.avatarlegacy.listeners.*;
import fishy.avatarlegacy.managers.*;
import fishy.avatarlegacy.tasks.*;

import org.bukkit.plugin.java.JavaPlugin;

public class AvatarLegacy extends JavaPlugin {

    private static AvatarLegacy instance;

    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private PlaytimeManager playtimeManager;
    private ElementManager elementManager;
    private ProtectedMovesManager protectedMovesManager;
    private NotificationManager notificationManager;
    private EconomyManager economyManager;
    private CharacterManager characterManager;
    private StatsManager statsManager;
    private NationManager nationManager;
    private WarManager warManager;
    private TeleportManager teleportManager;
    private AvatarManager avatarManager;
    private ScrollManager scrollManager;
    private SkyFreezeManager skyFreezeManager;
    private fishy.avatarlegacy.hooks.AvatarLegacyBendingHook bendingHook;
    private fishy.avatarlegacy.utils.SilentCommandSender silentSender;

    private VaultIntegration vaultIntegration;
    private LuckPermsIntegration luckPermsIntegration;
    private WorldGuardIntegration worldGuardIntegration;
    private ProjectKorraIntegration projectKorraIntegration;

    private ShopGUI shopGUI;
    private fishy.avatarlegacy.guis.SellGUI sellGUI;
    private StatsRestorationGUI statsRestorationGUI;
    private NationUpgradeGUI nationUpgradeGUI;
    private CharacterDeletionGUI characterDeletionGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        fishy.avatarlegacy.utils.MessageUtil.init(getConfig());
        silentSender = new fishy.avatarlegacy.utils.SilentCommandSender(getServer());
        getLogger().info("Initializing AvatarLegacy...");

        if (!initializeIntegrations()) {
            getLogger().severe("Failed to initialize required integrations! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        initializeDatabase();
        initializeManagers();
        initializeGUIs();
        registerCommands();
        registerListeners();
        startTasks();

        getLogger().info("AvatarLegacy has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.saveAllPlayerData();
        if (databaseManager != null) databaseManager.closeConnection();
        getLogger().info("AvatarLegacy has been disabled.");
    }

    private boolean initializeIntegrations() {
        vaultIntegration = new VaultIntegration(this);
        if (!vaultIntegration.setupEconomy()) {
            getLogger().severe("Vault not found or economy not set up!");
            return false;
        }

        luckPermsIntegration = new LuckPermsIntegration(this);
        if (!luckPermsIntegration.isEnabled()) {
            getLogger().severe("LuckPerms not found!");
            return false;
        }

        worldGuardIntegration = new WorldGuardIntegration(this);
        if (!worldGuardIntegration.isEnabled()) {
            getLogger().severe("WorldGuard not found!");
            return false;
        }

        projectKorraIntegration = new ProjectKorraIntegration(this);
        if (!projectKorraIntegration.isEnabled()) {
            getLogger().severe("ProjectKorra not found!");
            return false;
        }

        return true;
    }

    private void initializeDatabase() {
        databaseManager = new DatabaseManager(this);
        databaseManager.initializeTables();
    }

    private void initializeManagers() {
        playerDataManager = new PlayerDataManager(this);
        playtimeManager = new PlaytimeManager(this);
        elementManager = new ElementManager(this);
        protectedMovesManager = new ProtectedMovesManager(this);
        notificationManager = new NotificationManager(this);
        economyManager = new EconomyManager(this);
        characterManager = new CharacterManager(this);
        statsManager = new StatsManager(this);
        nationManager = new NationManager(this);
        warManager = new WarManager(this);
        teleportManager = new TeleportManager(this);
        avatarManager = new AvatarManager(this);
        scrollManager = new ScrollManager(this);
        skyFreezeManager = new SkyFreezeManager(this);
        registerBendingHooks();
    }

    private void registerBendingHooks() {
        if (!projectKorraIntegration.isEnabled()) return;
        bendingHook = new fishy.avatarlegacy.hooks.AvatarLegacyBendingHook(this);
        com.projectkorra.projectkorra.BendingPlayer.registerCanBendHook(this, bendingHook);
        com.projectkorra.projectkorra.BendingPlayer.registerCanBindHook(this, bendingHook);
        getLogger().info("Registered AvatarLegacy bending hooks with ProjectKorra.");
    }

    private void initializeGUIs() {
        shopGUI = new ShopGUI(this);
        sellGUI = new fishy.avatarlegacy.guis.SellGUI(this);
        statsRestorationGUI = new StatsRestorationGUI(this);
        nationUpgradeGUI = new NationUpgradeGUI(this);
        characterDeletionGUI = new CharacterDeletionGUI(this);
    }

    private void registerCommands() {
        
        getCommand("sellgui").setExecutor(new fishy.avatarlegacy.commands.SellGUICommand(this));
        getCommand("elementtime").setExecutor(new ElementTimeCommand(this));
        getCommand("playtime").setExecutor(new PlaytimeCommand(this));
        getCommand("xp").setExecutor(new XPCommand(this));
        getCommand("admin").setExecutor(new AdminCommand(this));
        getCommand("shop").setExecutor(new ShopCommand(this));
        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("treasury").setExecutor(new TreasuryCommand(this));
        getCommand("economy").setExecutor(new EconomyCommand(this));
        getCommand("character").setExecutor(new CharacterCommand(this));
        getCommand("stats").setExecutor(new StatsCommand(this));
        getCommand("deaths").setExecutor(new DeathsCommand(this));
        getCommand("nation").setExecutor(new NationCommand(this));
        getCommand("avatar").setExecutor(new AvatarCommand(this));
        getCommand("characterwho").setExecutor(new CharacterWhoCommand(this));
        getCommand("xpconvert").setExecutor(new XpConvertCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ElementChooseListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockStatsListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new TerritoryEnterListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerKillListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerCommandPreprocessListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerBindAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ElementChangerListener(this), this);
        getServer().getPluginManager().registerEvents(new SpectatorRestrictionListener(this), this);
    }

    private void startTasks() {
        new PlaytimeTask(this).runTaskTimer(this, 0L, 20L);
        new AFKCheckTask(this).runTaskTimer(this, 0L, 20L * 60);
        new AutoSaveTask(this).runTaskTimer(this, 0L, 20L * 60 * 5);
        new TaxCollectionTask(this).runTaskTimer(this, 0L, 20L * 60);
        new AvatarDebuffTask(this).runTaskTimer(this, 0L, 20L * 60);

        
        getServer().getScheduler().runTaskAsynchronously(this,
                () -> avatarManager.updateCandidateScores());
    }

    public static AvatarLegacy getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public PlaytimeManager getPlaytimeManager() { return playtimeManager; }
    public ElementManager getElementManager() { return elementManager; }
    public ProtectedMovesManager getProtectedMovesManager() { return protectedMovesManager; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public CharacterManager getCharacterManager() { return characterManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public NationManager getNationManager() { return nationManager; }
    public WarManager getWarManager() { return warManager; }
    public TeleportManager getTeleportManager() { return teleportManager; }
    public AvatarManager getAvatarManager() { return avatarManager; }
    public ScrollManager getScrollManager() { return scrollManager; }
    public SkyFreezeManager getSkyFreezeManager() { return skyFreezeManager; }
    public VaultIntegration getVaultIntegration() { return vaultIntegration; }
    public LuckPermsIntegration getLuckPermsIntegration() { return luckPermsIntegration; }
    public WorldGuardIntegration getWorldGuardIntegration() { return worldGuardIntegration; }
    public ProjectKorraIntegration getProjectKorraIntegration() { return projectKorraIntegration; }
    public ShopGUI getShopGUI() { return shopGUI; }
    public fishy.avatarlegacy.guis.SellGUI getSellGUI() { return sellGUI; }
    public StatsRestorationGUI getStatsRestorationGUI() { return statsRestorationGUI; }
    public NationUpgradeGUI getNationUpgradeGUI() { return nationUpgradeGUI; }
    public CharacterDeletionGUI getCharacterDeletionGUI() { return characterDeletionGUI; }
    public fishy.avatarlegacy.hooks.AvatarLegacyBendingHook getBendingHook() { return bendingHook; }
    public fishy.avatarlegacy.utils.SilentCommandSender getSilentSender() { return silentSender; }

}
