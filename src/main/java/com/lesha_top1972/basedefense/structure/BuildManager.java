package com.lesha_top1972.basedefense.structure;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Ведёт все постройки, которые сейчас возводятся. Только сервер. */
public final class BuildManager {

    private static final List<BuildTask> TASKS = new ArrayList<>();
    /** Постройки, которые надо поставить целиком за один тик (творческий режим). */
    private static final List<BuildTask> INSTANT = new ArrayList<>();

    private BuildManager() {
    }

    public static void start(ServerWorld world, StructurePlan plan, BlockPos placedPos, Direction facing) {
        TASKS.add(new BuildTask(world, plan, placedPos, facing));
    }

    /** Построить сразу целиком (на следующем тике сервера). */
    public static void startInstant(ServerWorld world, StructurePlan plan, BlockPos placedPos, Direction facing) {
        BuildTask task = new BuildTask(world, plan, placedPos, facing);
        task.setQuiet(true);
        INSTANT.add(task);
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            Iterator<BuildTask> instant = INSTANT.iterator();
            while (instant.hasNext()) {
                BuildTask task = instant.next();
                if (task.getWorld() == world) {
                    // Защита от бесконечного цикла: постройка точно заканчивается за конечное число шагов
                    for (int i = 0; i < 100000 && !task.tick(); i++) {
                    }
                    BlockPos at = task.toWorld(task.getPlan().getOrigin().getX(), 1, task.getPlan().getOrigin().getZ() + 3);
                    world.playSound(null, at, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
                    world.spawnParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5,
                            40, 3.0, 2.0, 3.0, 0.02);
                    instant.remove();
                }
            }
            Iterator<BuildTask> it = TASKS.iterator();
            while (it.hasNext()) {
                BuildTask task = it.next();
                if (task.getWorld() == world && task.tick()) {
                    it.remove();
                }
            }
        });
        // При выходе из мира незаконченные постройки забываются
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            TASKS.clear();
            INSTANT.clear();
        });
    }
}
