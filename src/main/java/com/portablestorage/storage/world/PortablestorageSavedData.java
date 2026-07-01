package com.portablestorage.storage.world;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseDirectory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class PortablestorageSavedData extends SavedData implements WarehouseDirectory {
    public static final int SCHEMA_VERSION = 1;
    public static final String FILE_ID = "portablestorage";

    public static final Codec<PortablestorageSavedData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<T> encode(PortablestorageSavedData input, DynamicOps<T> ops, T prefix) {
            return CompoundTag.CODEC.encode(input.writeRoot(castTagOps(ops)), ops, prefix);
        }

        @Override
        public <T> DataResult<com.mojang.datafixers.util.Pair<PortablestorageSavedData, T>> decode(
                DynamicOps<T> ops,
                T input) {
            return CompoundTag.CODEC.decode(ops, input)
                    .map(pair -> pair.mapFirst(tag -> readRoot(tag, castTagOps(ops))));
        }
    };

    public static final SavedDataType<PortablestorageSavedData> TYPE = new SavedDataType<>(
            PortableStorage.id(FILE_ID),
            PortablestorageSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, PlayerWarehouse> warehouses = new LinkedHashMap<>();

    public static PortablestorageSavedData loadFor(HolderLookup.Provider registries, CompoundTag root) {
        return readRoot(root, registries);
    }

    @Override
    public PlayerWarehouse getWarehouse(UUID uuid) {
        return warehouses.get(uuid);
    }

    public PlayerWarehouse getOrCreateWarehouse(UUID uuid, String ownerName) {
        PlayerWarehouse warehouse = warehouses.computeIfAbsent(uuid, this::createWarehouse);
        if (ownerName != null && !ownerName.isBlank()) {
            warehouse.setOwnerName(ownerName);
        }
        return warehouse;
    }

    @Override
    public Collection<PlayerWarehouse> getAllWarehouses() {
        return warehouses.values();
    }

    public void markWarehouseDirty(UUID uuid, String reason) {
        setDirty();
    }

    public CompoundTag writeRoot(HolderLookup.Provider registries) {
        return writeRoot(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE));
    }

    public CompoundTag writeRoot(DynamicOps<Tag> ops) {
        CompoundTag root = new CompoundTag();
        root.putInt("portablestorage_schema_version", SCHEMA_VERSION);

        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerWarehouse> entry : warehouses.entrySet()) {
            CompoundTag warehouseTag = new CompoundTag();
            warehouseTag.putString("id", entry.getKey().toString());

            CompoundTag dataTag = new CompoundTag();
            entry.getValue().writeNbt(dataTag, ops);
            warehouseTag.put("data", dataTag);

            list.add(warehouseTag);
        }
        root.put("warehouses", list);
        return root;
    }

    private PlayerWarehouse createWarehouse(UUID uuid) {
        PlayerWarehouse warehouse = new PlayerWarehouse(uuid, ignored -> {
        });
        warehouse.setWarehouseDirectory(this);
        return warehouse;
    }

    private static PortablestorageSavedData readRoot(CompoundTag root, HolderLookup.Provider registries) {
        return readRoot(root, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE));
    }

    private static PortablestorageSavedData readRoot(CompoundTag root, DynamicOps<Tag> ops) {
        PortablestorageSavedData data = new PortablestorageSavedData();

        ListTag list = root.getList("warehouses").orElseGet(ListTag::new);
        for (int i = 0; i < list.size(); i++) {
            java.util.Optional<CompoundTag> warehouseTagOpt = list.getCompound(i);
            if (warehouseTagOpt.isEmpty()) {
                continue;
            }

            CompoundTag warehouseTag = warehouseTagOpt.get();
            UUID uuid = parseUuid(warehouseTag.getString("id").orElse(""));
            if (uuid == null) {
                continue;
            }

            CompoundTag dataTag = warehouseTag.getCompoundOrEmpty("data");
            PlayerWarehouse warehouse = data.createWarehouse(uuid);
            warehouse.loadFromNbt(dataTag, ops);
            data.warehouses.put(uuid, warehouse);
        }

        data.setDirty(false);
        return data;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> DynamicOps<Tag> castTagOps(DynamicOps<T> ops) {
        return (DynamicOps<Tag>) ops;
    }
}