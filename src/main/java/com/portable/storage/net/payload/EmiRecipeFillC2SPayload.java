package com.portable.storage.net.payload;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * EMI 配方填充请求包
 */
public record EmiRecipeFillC2SPayload(
    String recipeId,
    int[] slotIndices,
    int[] itemCounts
) implements CustomPayload {
    
    public static final Id<EmiRecipeFillC2SPayload> ID = new Id<>(Identifier.of("portable-storage", "emi_recipe_fill"));
    
    public static final Codec<EmiRecipeFillC2SPayload> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("recipe_id").forGetter(EmiRecipeFillC2SPayload::recipeId),
            Codec.INT.listOf().fieldOf("slot_indices").forGetter(payload -> {
                var list = new java.util.ArrayList<Integer>();
                for (int i : payload.slotIndices()) {
                    list.add(i);
                }
                return list;
            }),
            Codec.INT.listOf().fieldOf("item_counts").forGetter(payload -> {
                var list = new java.util.ArrayList<Integer>();
                for (int i : payload.itemCounts()) {
                    list.add(i);
                }
                return list;
            })
        ).apply(instance, (recipeId, slotList, countList) -> new EmiRecipeFillC2SPayload(
            recipeId,
            slotList.stream().mapToInt(Integer::intValue).toArray(),
            countList.stream().mapToInt(Integer::intValue).toArray()
        ))
    );
    
    public static final PacketCodec<RegistryByteBuf, EmiRecipeFillC2SPayload> PACKET_CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.recipeId());
            buf.writeIntArray(payload.slotIndices());
            buf.writeIntArray(payload.itemCounts());
        },
        buf -> new EmiRecipeFillC2SPayload(
            buf.readString(),
            buf.readIntArray(),
            buf.readIntArray()
        )
    );
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
