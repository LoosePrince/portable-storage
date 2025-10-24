package com.portable.storage.client;

/**
 * 客户端容器显示配置状态管理
 * 用于存储从服务器同步的容器显示配置
 */
public class ClientContainerDisplayConfig {
    private static ClientContainerDisplayConfig INSTANCE = new ClientContainerDisplayConfig();
    
    // 容器显示配置
    private boolean stonecutter = false;
    private boolean cartographyTable = false;
    private boolean smithingTable = false;
    private boolean grindstone = false;
    private boolean loom = false;
    private boolean furnace = false;
    private boolean smoker = false;
    private boolean blastFurnace = false;
    private boolean anvil = false;
    private boolean enchantingTable = false;
    private boolean brewingStand = false;
    private boolean beacon = false;
    private boolean chest = false;
    private boolean barrel = false;
    private boolean enderChest = false;
    private boolean shulkerBox = false;
    private boolean dispenser = false;
    private boolean dropper = false;
    private boolean crafter = false;
    private boolean hopper = false;
    private boolean trappedChest = false;
    private boolean hopperMinecart = false;
    private boolean chestMinecart = false;
    private boolean chestBoat = false;
    private boolean bambooChestRaft = false;
    
    private ClientContainerDisplayConfig() {}
    
    public static ClientContainerDisplayConfig getInstance() {
        return INSTANCE;
    }
    
    /**
     * 更新配置
     */
    public void updateConfig(
        boolean stonecutter,
        boolean cartographyTable,
        boolean smithingTable,
        boolean grindstone,
        boolean loom,
        boolean furnace,
        boolean smoker,
        boolean blastFurnace,
        boolean anvil,
        boolean enchantingTable,
        boolean brewingStand,
        boolean beacon,
        boolean chest,
        boolean barrel,
        boolean enderChest,
        boolean shulkerBox,
        boolean dispenser,
        boolean dropper,
        boolean crafter,
        boolean hopper,
        boolean trappedChest,
        boolean hopperMinecart,
        boolean chestMinecart,
        boolean chestBoat,
        boolean bambooChestRaft
    ) {
        this.stonecutter = stonecutter;
        this.cartographyTable = cartographyTable;
        this.smithingTable = smithingTable;
        this.grindstone = grindstone;
        this.loom = loom;
        this.furnace = furnace;
        this.smoker = smoker;
        this.blastFurnace = blastFurnace;
        this.anvil = anvil;
        this.enchantingTable = enchantingTable;
        this.brewingStand = brewingStand;
        this.beacon = beacon;
        this.chest = chest;
        this.barrel = barrel;
        this.enderChest = enderChest;
        this.shulkerBox = shulkerBox;
        this.dispenser = dispenser;
        this.dropper = dropper;
        this.crafter = crafter;
        this.hopper = hopper;
        this.trappedChest = trappedChest;
        this.hopperMinecart = hopperMinecart;
        this.chestMinecart = chestMinecart;
        this.chestBoat = chestBoat;
        this.bambooChestRaft = bambooChestRaft;
    }
    
    /**
     * 根据容器标识符检查是否应该显示仓库
     */
    public boolean shouldShowStorageInContainer(String containerId) {
        if (containerId == null) return false;

        switch (containerId) {
            case "minecraft:stonecutter": return stonecutter;
            case "minecraft:cartography_table": return cartographyTable;
            case "minecraft:smithing": return smithingTable;
            case "minecraft:grindstone": return grindstone;
            case "minecraft:loom": return loom;
            case "minecraft:furnace": return furnace;
            case "minecraft:smoker": return smoker;
            case "minecraft:blast_furnace": return blastFurnace;
            case "minecraft:anvil": return anvil;
            case "minecraft:enchantment": return enchantingTable;
            case "minecraft:brewing_stand": return brewingStand;
            case "minecraft:beacon": return beacon;
            case "minecraft:generic_9x3": return chest;
            case "minecraft:generic_9x6": return chest;
            case "minecraft:generic_3x3": return barrel;
            case "minecraft:ender_chest": return enderChest;
            case "minecraft:shulker_box": return shulkerBox;
            case "minecraft:dispenser": return dispenser;
            case "minecraft:dropper": return dropper;
            case "minecraft:crafter_3x3": return crafter;
            case "minecraft:hopper": return hopper;
            case "minecraft:trapped_chest": return trappedChest;
            case "minecraft:hopper_minecart": return hopperMinecart;
            case "minecraft:chest_minecart": return chestMinecart;
            case "minecraft:oak_chest_boat":
            case "minecraft:birch_chest_boat":
            case "minecraft:spruce_chest_boat":
            case "minecraft:jungle_chest_boat":
            case "minecraft:acacia_chest_boat":
            case "minecraft:dark_oak_chest_boat":
            case "minecraft:mangrove_chest_boat":
            case "minecraft:cherry_chest_boat":
                return chestBoat;
            case "minecraft:bamboo_chest_raft": return bambooChestRaft;
            default: return false;
        }
    }
    
    // Getter 方法
    public boolean isStonecutter() { return stonecutter; }
    public boolean isCartographyTable() { return cartographyTable; }
    public boolean isSmithingTable() { return smithingTable; }
    public boolean isGrindstone() { return grindstone; }
    public boolean isLoom() { return loom; }
    public boolean isFurnace() { return furnace; }
    public boolean isSmoker() { return smoker; }
    public boolean isBlastFurnace() { return blastFurnace; }
    public boolean isAnvil() { return anvil; }
    public boolean isEnchantingTable() { return enchantingTable; }
    public boolean isBrewingStand() { return brewingStand; }
    public boolean isBeacon() { return beacon; }
    public boolean isChest() { return chest; }
    public boolean isBarrel() { return barrel; }
    public boolean isEnderChest() { return enderChest; }
    public boolean isShulkerBox() { return shulkerBox; }
    public boolean isDispenser() { return dispenser; }
    public boolean isDropper() { return dropper; }
    public boolean isCrafter() { return crafter; }
    public boolean isHopper() { return hopper; }
    public boolean isTrappedChest() { return trappedChest; }
    public boolean isHopperMinecart() { return hopperMinecart; }
    public boolean isChestMinecart() { return chestMinecart; }
    public boolean isChestBoat() { return chestBoat; }
    public boolean isBambooChestRaft() { return bambooChestRaft; }
}
