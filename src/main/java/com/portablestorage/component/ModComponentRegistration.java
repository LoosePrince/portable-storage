package com.portablestorage.component;

import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;

public class ModComponentRegistration implements WorldComponentInitializer {
    @Override
    public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry) {
        // 注册到世界（Level）
        registry.register(ModComponents.WAREHOUSE, MyWarehouseComponent::new);
    }
}
