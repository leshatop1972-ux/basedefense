package com.lesha_top1972.basedefense.entity.settler;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Стражник — военный поселенец. Его вербуют в военкомате казармы за монеты исследований,
 * живёт в казарме (спит на своей койке). Бой и команды добавим позже.
 */
public class GuardEntity extends SettlerEntity {

    /** Военкомат, где завербован стражник (по нему считается, сколько коек занято). */
    @Nullable
    private BlockPos barracks;

    public GuardEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected ItemStack getDefaultTool() {
        return new ItemStack(Items.IRON_SWORD);
    }

    /** Сразу при вербовке — меч в руку (иначе он появлялся бы только на следующем тике). */
    public void equipDefaultGear() {
        if (this.getMainHandStack().isEmpty()) {
            this.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, getDefaultTool());
            this.setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.MAINHAND, 0.0f);
        }
    }

    public void setBarracks(@Nullable BlockPos office) {
        this.barracks = office == null ? null : office.toImmutable();
    }

    @Nullable
    public BlockPos getBarracks() {
        return barracks;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (barracks != null) {
            nbt.put("Barracks", NbtHelper.fromBlockPos(barracks));
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        barracks = nbt.contains("Barracks") ? NbtHelper.toBlockPos(nbt.getCompound("Barracks")) : null;
    }
}
