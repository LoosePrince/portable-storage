package com.portablestorage.upgrade;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UpgradeRegistry {
    private static final Map<ResourceLocation, UpgradeType> UPGRADES = new LinkedHashMap<>();
    private static final List<ResourceLocation> SORTED_IDS = new ArrayList<>();

    public static void register(UpgradeType upgrade) {
        UPGRADES.put(upgrade.getId(), upgrade);
        if (!SORTED_IDS.contains(upgrade.getId())) {
            SORTED_IDS.add(upgrade.getId());
        }
    }

    public static UpgradeType get(ResourceLocation id) {
        return UPGRADES.get(id);
    }

    public static List<UpgradeType> getAllUpgrades() {
        return new ArrayList<>(UPGRADES.values());
    }

    public static int getUpgradeCount() {
        return UPGRADES.size();
    }

    public static UpgradeType getByItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (UpgradeType type : UPGRADES.values()) {
            if (type.isItemValid(stack)) return type;
        }
        return null;
    }
}

