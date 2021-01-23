package io.github.lokka30.levelledmobs;

import io.github.lokka30.levelledmobs.commands.LevelledMobsCommand;
import io.github.lokka30.levelledmobs.listeners.*;
import io.github.lokka30.levelledmobs.utils.ConfigUtils;
import io.github.lokka30.levelledmobs.utils.FileLoader;
import io.github.lokka30.levelledmobs.utils.Utils;
import me.lokka30.microlib.QuickTimer;
import me.lokka30.microlib.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * This is the main class of the plugin. Bukkit will call onLoad and onEnable on startup, and onDisable on shutdown.
 */
public class LevelledMobs extends JavaPlugin {

    public YamlConfiguration settingsCfg;
    public YamlConfiguration messagesCfg;
    public YamlConfiguration attributesCfg;
    public YamlConfiguration dropsCfg;
    public YamlConfiguration customDropsCfg;
    public ConfigUtils configUtils;
    public EntityDamageDebugListener entityDamageDebugListener;

    public MobDataManager mobDataManager;
    public LevelManager levelManager;

    public PluginManager pluginManager;

    public boolean hasWorldGuardInstalled;
    public boolean hasProtocolLibInstalled;
    public WorldGuardManager worldGuardManager;

    public boolean debugEntityDamageWasEnabled = false;

    public TreeMap<String, Integer> entityTypesLevelOverride_Min;
    public TreeMap<String, Integer> entityTypesLevelOverride_Max;
    public TreeMap<String, Integer> worldLevelOverride_Min;
    public TreeMap<String, Integer> worldLevelOverride_Max;
    public TreeMap<EntityType, CustomItemDrop> customDropsitems;

    private long loadTime;

    public int incompatibilitiesAmount;

    public void onLoad() {
        Utils.logger.info("&f~ Initiating start-up procedure ~");

        QuickTimer loadTimer = new QuickTimer();
        loadTimer.start(); // Record how long it takes for the plugin to load.

        mobDataManager = new MobDataManager(this);
        levelManager = new LevelManager(this);

        // Hook into WorldGuard, register LM's flags.
        // This cannot be moved to onEnable (stated in WorldGuard's documentation).
        hasWorldGuardInstalled = getServer().getPluginManager().getPlugin("WorldGuard") != null;
        if (hasWorldGuardInstalled) {
            worldGuardManager = new WorldGuardManager(this);
        }

        hasProtocolLibInstalled = getServer().getPluginManager().getPlugin("ProtocolLib") != null;

        loadTime = loadTimer.getTimer(); // combine the load time with enable time.
    }

    public void onEnable() {
        QuickTimer enableTimer = new QuickTimer();
        enableTimer.start(); // Record how long it takes for the plugin to enable.

        checkCompatibility();
        loadFiles();
        registerListeners();
        registerCommands();
        if (hasProtocolLibInstalled) {
            levelManager.startNametagAutoUpdateTask();
        }

        Utils.logger.info("&fStart-up: &7Running misc procedures...");
        setupMetrics();
        checkUpdates();

        Utils.logger.info("&f~ Start-up complete, took &b" + (enableTimer.getTimer() + loadTime) + "ms&f ~");
    }

    public void onDisable() {
        Utils.logger.info("&f~ Initiating shut-down procedure ~");

        QuickTimer disableTimer = new QuickTimer();
        disableTimer.start();

        levelManager.stopNametagAutoUpdateTask();

        Utils.logger.info("&f~ Shut-down complete, took &b" + disableTimer.getTimer() + "ms&f ~");
    }

    //Checks if the server version is supported
    public void checkCompatibility() {
        Utils.logger.info("&fCompatibility Checker: &7Checking compatibility with your server...");

        // Using a List system in case more compatibility checks are added.
        List<String> incompatibilities = new ArrayList<>();

        // Check the MC version of the server.
        final String currentServerVersion = getServer().getVersion();
        boolean isRunningSupportedVersion = false;
        for (String supportedServerVersion : Utils.getSupportedServerVersions()) {
            if (currentServerVersion.contains(supportedServerVersion)) {
                isRunningSupportedVersion = true;
                break;
            }
        }
        if (!isRunningSupportedVersion) {
            incompatibilities.add("Your server version &8(&b" + currentServerVersion + "&8)&7 is unsupported by &bLevelledMobs v" + getDescription().getVersion() + "&7!" +
                    "Compatible MC versions: &b" + String.join(", ", Utils.getSupportedServerVersions()) + "&7.");
        }

        if (!hasProtocolLibInstalled) {
            incompatibilities.add("Your server does not have &bProtocolLib&7 installed! This means that no levelled nametags will appear on the mobs. If you wish to see custom nametags above levelled mobs, then you must install ProtocolLib.");
        }

        incompatibilitiesAmount = incompatibilities.size();
        if (incompatibilities.isEmpty()) {
            Utils.logger.info("&fCompatibility Checker: &7No incompatibilities found.");
        } else {
            Utils.logger.warning("&fCompatibility Checker: &7Found the following possible incompatibilities:");
            incompatibilities.forEach(incompatibility -> Utils.logger.info("&8 - &7" + incompatibility));
        }
    }

    // Note: also called by the reload subcommand.
    public void loadFiles() {
        Utils.logger.info("&fFile Loader: &7Loading files...");

        // save license.txt
        FileLoader.saveResourceIfNotExists(this, new File(getDataFolder(), "license.txt"));

        // load configurations
        settingsCfg = FileLoader.loadFile(this, "settings", FileLoader.SETTINGS_FILE_VERSION);
        messagesCfg = FileLoader.loadFile(this, "messages", FileLoader.MESSAGES_FILE_VERSION);

        this.entityTypesLevelOverride_Min = getMapFromConfigSection("entitytype-level-override.min-level");
        this.entityTypesLevelOverride_Max = getMapFromConfigSection("entitytype-level-override.max-level");
        this.worldLevelOverride_Min = getMapFromConfigSection("world-level-override.min-level");
        this.worldLevelOverride_Max = getMapFromConfigSection("world-level-override.max-level");
        this.customDropsitems = new TreeMap<>();

        // Replace/copy attributes file
        saveResource("attributes.yml", true);
        attributesCfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "attributes.yml"));

        // Replace/copy drops file
        saveResource("drops.yml", true);
        dropsCfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "drops.yml"));

        saveResource("customdrops.yml", false);
        customDropsCfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "customdrops.yml"));
        if (settingsCfg.getBoolean("use-custom-item-drops-for-mobs")) processCustomDropsConfig();

        // load configutils
        configUtils = new ConfigUtils(this);
    }

    private void processCustomDropsConfig(){
        //TODO: stumper66 is actively working on this section:
        for (Map.Entry<String, Object> map: customDropsCfg.getValues(true).entrySet()){
            String mobType = map.getKey();
            EntityType entityType;
            try{
                entityType = EntityType.valueOf(mobType.toUpperCase()); }
            catch (Exception e){
                Utils.logger.warning("invalid mob type in customdrops.yml: " + mobType);
                continue;
            }

            // now we have the mob type start parsing the materials next
            Map<String, Object> materials = (Map<String, Object>) map.getValue();

            for (String materialName : materials.keySet()){
                // example: diamond_sword
                Map<String, Object> materialAttributes = (Map<String, Object>) materials.get(materialName);

                for (String attribute : materialAttributes.keySet()){
                    // example: amount

                    Object value = materialAttributes.get(attribute);
                    // example 0.1
                }
            }
        }
    }

    private TreeMap<String, Integer> getMapFromConfigSection(String configPath){
        TreeMap<String, Integer> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        ConfigurationSection cs = settingsCfg.getConfigurationSection(configPath);
        if (cs == null){ return result; }

        Set<String> set = cs.getKeys(false);

        for (String item : set) {
            Object value = cs.get(item);
            if (value != null && Utils.isInteger(value.toString())) {
                result.put(item, Integer.parseInt(value.toString()));
            }
        }

        return result;
    }

    private void registerListeners() {
        Utils.logger.info("&fListeners: &7Registering event listeners...");

        pluginManager = getServer().getPluginManager();

        levelManager.creatureSpawnListener = new CreatureSpawnListener(this); // we're saving this reference so the summon command has access to it
        entityDamageDebugListener = new EntityDamageDebugListener(this);

        if (settingsCfg.getBoolean("debug-entity-damage")) {
            // we'll load and unload this listener based on the above setting when reloading
            debugEntityDamageWasEnabled = true;
            pluginManager.registerEvents(this.entityDamageDebugListener, this);
        }

        pluginManager.registerEvents(levelManager.creatureSpawnListener, this);
        pluginManager.registerEvents(new EntityDamageListener(this), this);
        pluginManager.registerEvents(new EntityDeathListener(this), this);
        pluginManager.registerEvents(new EntityRegainHealthListener(this), this);
        pluginManager.registerEvents(new PlayerJoinWorldNametagListener(this), this);
        pluginManager.registerEvents(new EntityTransformListener(this), this);
        pluginManager.registerEvents(new EntityNametagListener(this), this);
        pluginManager.registerEvents(new EntityTargetListener(this), this);
        pluginManager.registerEvents(new PlayerJoinListener(this), this);
    }

    private void registerCommands() {
        Utils.logger.info("&fCommands: &7Registering commands...");

        PluginCommand levelledMobsCommand = getCommand("levelledmobs");
        if (levelledMobsCommand == null) {
            Utils.logger.error("Command &b/levelledmobs&7 is unavailable, is it not registered in plugin.yml?");
        } else {
            levelledMobsCommand.setExecutor(new LevelledMobsCommand(this));
        }
    }

    private void setupMetrics() {
        new Metrics(this, 6269);
    }

    //Check for updates on the Spigot page.
    private void checkUpdates() {
        if (settingsCfg.getBoolean("use-update-checker")) {
            UpdateChecker updateChecker = new UpdateChecker(this, 74304);
            updateChecker.getLatestVersion(latestVersion -> {
                if (!updateChecker.getCurrentVersion().equals(latestVersion)) {
                    Utils.logger.warning("&fUpdate Checker: &7The plugin has an update available! You're running &bv" + updateChecker.getCurrentVersion() + "&7, latest version is &bv" + latestVersion + "&7.");
                }
            });
        }
    }


}
