package com.portablestorage.util;

import net.minecraft.util.StringRepresentable;

public enum WarehouseSetting implements StringRepresentable {
    FOLD(0),
    SORT_MODE(1),
    SORT_ORDER(2),
    QUICK_INTERACTION(3),
    SMART_COLLAPSE(4),
    CRAFT_REFILL(5);

    private final int id;

    WarehouseSetting(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static WarehouseSetting fromId(int id) {
        for (WarehouseSetting setting : values()) {
            if (setting.id == id) return setting;
        }
        return FOLD;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}

