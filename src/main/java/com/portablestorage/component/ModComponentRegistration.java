package com.portablestorage.component;

import org.ladysnake.cca.api.v3.scoreboard.ScoreboardComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.scoreboard.ScoreboardComponentInitializer;

public class ModComponentRegistration implements ScoreboardComponentInitializer {
    @Override
    public void registerScoreboardComponentFactories(ScoreboardComponentFactoryRegistry registry) {
        // 根据 CCA 6.1.1 的 API，Scoreboard 组件注册方法为 registerScoreboardComponent
        // 且工厂函数需要 (Scoreboard, MinecraftServer) 两个参数
        registry.registerScoreboardComponent(ModComponents.WAREHOUSE, (scoreboard, server) -> new MyWarehouseComponent(scoreboard, server));
    }
}
