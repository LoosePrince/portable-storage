package com.portable.storage.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

/**
 * 玩家启用状态持久化存储
 * 记录哪些玩家已经启用了随身仓库功能
 */
public class PlayerEnablementState extends PersistentState {
    public static final String NAME = "portable_storage_enablement";

    private final Map<String, Boolean> enabledPlayers = new HashMap<>();

    public static PlayerEnablementState get(MinecraftServer server) {
        PersistentStateManager mgr = server.getOverworld().getPersistentStateManager();
        return mgr.getOrCreate(new PersistentState.Type<>(PlayerEnablementState::new, PlayerEnablementState::fromNbt, null), NAME);
    }

    public PlayerEnablementState() {}

    public static PlayerEnablementState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        PlayerEnablementState state = new PlayerEnablementState();
        if (nbt != null && nbt.contains("enabled_players")) {
            NbtCompound players = nbt.getCompound("enabled_players");
            for (String uuid : players.getKeys()) {
                state.enabledPlayers.put(uuid, players.getBoolean(uuid));
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound players = new NbtCompound();
        for (Map.Entry<String, Boolean> entry : enabledPlayers.entrySet()) {
            players.putBoolean(entry.getKey(), entry.getValue());
        }
        nbt.put("enabled_players", players);
        return nbt;
    }

    /**
     * 检查玩家是否已启用随身仓库
     */
    public boolean isPlayerEnabled(UUID uuid) {
        return enabledPlayers.getOrDefault(uuid.toString(), false);
    }

    /**
     * 设置玩家启用状态
     */
    public void setPlayerEnabled(UUID uuid, boolean enabled) {
        String uuidStr = uuid.toString();
        if (enabled) {
            enabledPlayers.put(uuidStr, true);
        } else {
            enabledPlayers.remove(uuidStr);
        }
        markDirty();
    }

    /**
     * 获取所有已启用玩家的数量
     */
    public int getEnabledPlayerCount() {
        return enabledPlayers.size();
    }
}
