package com.lesha_top1972.basedefense.structure;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Ведёт все постройки, которые сейчас возводятся. Только сервер. */
public final class BuildManager {

    private static final List<BuildTask> TASKS = new ArrayList<>();

    private BuildManager() {
    }

    public static void start(ServerWorld world, StructurePlan plan, BlockPos placedPos, Direction facing) {
        TASKS.add(new BuildTask(world, plan, placedPos, facing));
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            Iterator<BuildTask> it = TASKS.iterator();
            while (it.hasNext()) {
                BuildTask task = it.next();
                if (task.getWorld() == world && task.tick()) {
                    it.remove();
                }
            }
        });
        // При выходе из мира незаконченные постройки забываются
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TASKS.clear());
    }
}
