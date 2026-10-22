package com.lesha_top1972.basedefense.block;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Где стоит единственная База сервера (позиция + измерение).
 * Сохраняется в data/basedefense_base.dat в папке мира.
 */
public class BaseLocationState extends PersistentState {

    private static final String SAVE_ID = "basedefense_base";

    @Nullable
    private BlockPos pos;
    @Nullable
    private RegistryKey<World> dimension;

    public static BaseLocationState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(BaseLocationState::fromNbt, BaseLocationState::new, SAVE_ID);
    }

    public boolean hasBase() {
        return pos != null && dimension != null;
    }

    public boolean isAt(World world, BlockPos blockPos) {
        return hasBase() && world.getRegistryKey().equals(dimension) && blockPos.equals(pos);
    }

    @Nullable
    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public RegistryKey<World> getDimension() {
        return dimension;
    }

    public void set(World world, BlockPos blockPos) {
        this.pos = blockPos.toImmutable();
        this.dimension = world.getRegistryKey();
        markDirty();
    }

    public void clear() {
        this.pos = null;
        this.dimension = null;
        markDirty();
    }

    private static BaseLocationState fromNbt(NbtCompound nbt) {
        BaseLocationState state = new BaseLocationState();
        if (nbt.contains("Pos") && nbt.contains("Dimension")) {
            state.pos = NbtHelper.toBlockPos(nbt.getCompound("Pos"));
            state.dimension = RegistryKey.of(RegistryKeys.WORLD, new Identifier(nbt.getString("Dimension")));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (hasBase()) {
            nbt.put("Pos", NbtHelper.fromBlockPos(pos));
            nbt.putString("Dimension", dimension.getValue().toString());
        }
        return nbt;
    }
}
