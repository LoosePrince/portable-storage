package com.portablestorage.network;

import com.portablestorage.PortableStorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SRecipeTransferPayload(String recipeId, boolean maxStack) implements CustomPacketPayload {
    public static final Type<C2SRecipeTransferPayload> TYPE = new Type<>(
            PortableStorage.id("recipe_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRecipeTransferPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SRecipeTransferPayload::recipeId,
            ByteBufCodecs.BOOL, C2SRecipeTransferPayload::maxStack,
            C2SRecipeTransferPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
