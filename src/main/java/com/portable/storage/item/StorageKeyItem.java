package com.portable.storage.item;

import java.util.UUID;

import com.portable.storage.PortableStorage;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.storage.StorageType;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * 仓库钥匙物品
 * 玩家死亡时掉落，包含仓库绑定信息，拾取时重新激活仓库
 */
public class StorageKeyItem extends Item {
    
    // NBT 键名
    public static final String NBT_OWNER_UUID = "storage_key_owner_uuid";
    public static final String NBT_OWNER_NAME = "storage_key_owner_name";
    public static final String NBT_DROP_TICK = "storage_key_drop_tick";
    
    public StorageKeyItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }
        
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) user;
        ItemStack stack = user.getStackInHand(hand);
        
        // 检查是否为钥匙的拥有者
        if (!isOwner(serverPlayer, stack)) {
            user.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.key_not_owner")
                    .formatted(Formatting.RED), false);
            return TypedActionResult.fail(stack);
        }
        
        // 检查玩家是否已经激活仓库
        PlayerStorageAccess access = (PlayerStorageAccess) serverPlayer;
        if (access.portableStorage$isStorageEnabled()) {
            user.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.storage_already_enabled")
                    .formatted(Formatting.YELLOW), false);
            return TypedActionResult.fail(stack);
        }
        
        // 重新激活仓库（死亡掉落的钥匙激活初级仓库）
        access.portableStorage$setStorageEnabled(true);
        access.portableStorage$setStorageType(StorageType.PRIMARY);
        
        // 消耗钥匙
        if (stack.getCount() > 1) {
            stack.decrement(1);
        } else {
            user.setStackInHand(hand, ItemStack.EMPTY);
        }
        
        // 发送成功消息
        user.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.primary_storage_reactivated")
                .formatted(Formatting.GREEN), false);
        
        PortableStorage.LOGGER.info("Player {} reactivated storage using storage key", serverPlayer.getName().getString());
        
        return TypedActionResult.success(user.getStackInHand(hand));
    }
    
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }
    
    
    /**
     * 创建仓库钥匙物品
     */
    public static ItemStack createStorageKey(ServerPlayerEntity player) {
        ItemStack key = new ItemStack(PortableStorage.STORAGE_KEY_ITEM);
        // 设置自定义物品
        key.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(new net.minecraft.nbt.NbtCompound()));
        
        // 写入拥有者信息
        net.minecraft.nbt.NbtCompound nbt = getCustomData(key);
        nbt.putUuid(NBT_OWNER_UUID, player.getUuid());
        String playerName = player.getGameProfile() != null ? 
                player.getGameProfile().getName() : player.getName().getString();
        nbt.putString(NBT_OWNER_NAME, playerName);
        nbt.putLong(NBT_DROP_TICK, player.getWorld().getTime());
        setCustomData(key, nbt);
        
        // 设置显示名称
        key.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item." + PortableStorage.MOD_ID + ".storage_key")
                .formatted(Formatting.GOLD));
        
        // 添加附魔效果（视觉上看起来像附魔的物品）
        key.set(DataComponentTypes.ENCHANTMENTS, 
                net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);
        
        return key;
    }
    
    /**
     * 检查玩家是否为钥匙的拥有者
     */
    public static boolean isOwner(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (!isStorageKey(stack)) return false;
        
        net.minecraft.nbt.NbtCompound nbt = getCustomData(stack);
        if (nbt == null || !nbt.containsUuid(NBT_OWNER_UUID)) return false;
        
        try {
            UUID owner = nbt.getUuid(NBT_OWNER_UUID);
            return owner.equals(player.getUuid());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查物品是否为仓库钥匙
     */
    public static boolean isStorageKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() != PortableStorage.STORAGE_KEY_ITEM) return false;
        
        net.minecraft.nbt.NbtCompound nbt = getCustomData(stack);
        return nbt != null && nbt.containsUuid(NBT_OWNER_UUID);
    }
    
    /**
     * 获取绑定信息提示文本
     */
    public static Text getBoundTooltip(ItemStack stack) {
        if (!isStorageKey(stack)) return null;
        
        net.minecraft.nbt.NbtCompound nbt = getCustomData(stack);
        if (nbt == null || !nbt.containsUuid(NBT_OWNER_UUID)) return null;
        
        String name = nbt.contains(NBT_OWNER_NAME) ? nbt.getString(NBT_OWNER_NAME) : "?";
        UUID uuid = nbt.getUuid(NBT_OWNER_UUID);
        String uuidPart = uuid.toString();
        
        return Text.translatable(PortableStorage.MOD_ID + ".tooltip.bound_to", name, uuidPart)
                .formatted(Formatting.GRAY);
    }
    
    /**
     * 标记掉落时间
     */
    public static void markDropTick(ItemStack stack, long tick) {
        net.minecraft.nbt.NbtCompound nbt = getCustomData(stack);
        nbt.putLong(NBT_DROP_TICK, tick);
        setCustomData(stack, nbt);
    }
    
    /**
     * 获取掉落时间
     */
    public static long getDropTick(ItemStack stack) {
        net.minecraft.nbt.NbtCompound nbt = getCustomData(stack);
        return nbt.contains(NBT_DROP_TICK) ? nbt.getLong(NBT_DROP_TICK) : -1;
    }
    
    private static net.minecraft.nbt.NbtCompound getCustomData(ItemStack stack) {
        try {
            NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
            return comp != null ? comp.copyNbt() : new net.minecraft.nbt.NbtCompound();
        } catch (Exception e) {
            return new net.minecraft.nbt.NbtCompound();
        }
    }
    
    private static void setCustomData(ItemStack stack, net.minecraft.nbt.NbtCompound nbt) {
        try {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        } catch (Exception ignored) {
        }
    }
}