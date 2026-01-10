package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

public record C2SRecipeTransferPayload(ResourceLocation recipeId, boolean maxStack) implements FabricPacket {
    public static final PacketType<C2SRecipeTransferPayload> TYPE = PacketType.create(
        PortableStorage.id("recipe_transfer"), C2SRecipeTransferPayload::read
    );

    public C2SRecipeTransferPayload(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readBoolean());
    }

    private static C2SRecipeTransferPayload read(FriendlyByteBuf buf) {
        return new C2SRecipeTransferPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(recipeId);
        buf.writeBoolean(maxStack);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
