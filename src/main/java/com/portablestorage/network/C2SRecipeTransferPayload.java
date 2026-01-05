package com.portablestorage.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRecipeTransferPayload(ResourceLocation recipeId, boolean maxStack) implements CustomPacketPayload {
    public static final Type<C2SRecipeTransferPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "recipe_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRecipeTransferPayload> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, C2SRecipeTransferPayload::recipeId,
            ByteBufCodecs.BOOL, C2SRecipeTransferPayload::maxStack,
            C2SRecipeTransferPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
