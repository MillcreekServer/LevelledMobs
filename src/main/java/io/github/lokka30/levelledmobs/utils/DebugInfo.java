package io.github.lokka30.levelledmobs.utils;

public class DebugInfo {
    public int minLevel;
    public int maxLevel;
    public MobProcessReason rule;

    public DebugInfo(){
        this.rule = MobProcessReason.NONE;
    }
}
