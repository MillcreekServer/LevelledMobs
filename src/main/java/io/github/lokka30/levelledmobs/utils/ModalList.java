package io.github.lokka30.levelledmobs.utils;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * This enum will significantly decrease clutter from checking lists from the settings file with configurable modes (e.g. whitelist/blacklist)
 */
public class ModalList {

    public enum ListMode {
        ALL,
        WHITELIST,
        BLACKLIST
    }

    public static ListMode fromString(String mode) {
        assert mode != null;
        switch (mode.toUpperCase()) {
            case "ALL":
                return ListMode.ALL;
            case "WHITELIST":
                return ListMode.WHITELIST;
            case "BLACKLIST":
                return ListMode.BLACKLIST;
            default:
                throw new IllegalStateException("Invalid ListMode '" + mode + "'!");
        }
    }

    public static boolean isEnabledInList(YamlConfiguration cfg, String path, String item) {
        if (cfg.contains(path + ".mode")) {
            @SuppressWarnings("ConstantConditions")
            ListMode listMode = ModalList.fromString(cfg.getString(path + ".mode"));

            switch (listMode) {
                case ALL:
                    return true;
                case WHITELIST:
                    return cfg.getStringList(path + ".list").contains(item);
                case BLACKLIST:
                    return !cfg.getStringList(path + ".list").contains(item);
                default:
                    throw new IllegalStateException("Invalid ListMode " + listMode.toString() + "!");
            }
        } else {
            Utils.logger.error("Mode is unset at path '" + path + ".mode'! The plugin will malfunction until this is fixed!");
            return false;
        }
    }
}
