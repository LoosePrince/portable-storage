package com.portablestorage.component;

import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.world.entity.player.Player;

public class ModComponentRegistration implements EntityComponentInitializer {
    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(ModComponents.WAREHOUSE, MyWarehouseComponent::new, RespawnCopyStrategy.INVENTORY);
    }
}
