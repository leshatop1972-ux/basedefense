package com.lesha_top1972.basedefense.structure;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Виды домов. Связывает блок дома с его чертежом.
 * selfBuilt — дом строится сам, без ресурсов (только дом строителя: строить его ещё некому).
 * Остальные дома строит житель-строитель из материалов в сундуках своего дома.
 */
public enum HouseType {
    BUILDER("builder_house", HousePlans::builder, true),
    FARMER("farmer_house", HousePlans::farmer, false),
    RANCHER("rancher_house", HousePlans::rancher, false),
    COOK("cook_house", HousePlans::cook, false),
    MINER("miner_house", HousePlans::miner, false),
    BLACKSMITH("blacksmith_house", HousePlans::blacksmith, false),
    LUMBERJACK("lumberjack_house", HousePlans::lumberjack, false);

    private final String id;
    private final Supplier<StructurePlan> factory;
    private final boolean selfBuilt;

    // Чертёж, шаги постройки и список материалов считаются один раз (при первом обращении)
    @Nullable
    private StructurePlan plan;
    @Nullable
    private List<List<StructurePlan.Placement>> steps;
    @Nullable
    private Map<net.minecraft.item.Item, Integer> requirements;
    private int costlyBlocks = -1;

    HouseType(String id, Supplier<StructurePlan> factory, boolean selfBuilt) {
        this.id = id;
        this.factory = factory;
        this.selfBuilt = selfBuilt;
    }

    public String getId() {
        return id;
    }

    public boolean isSelfBuilt() {
        return selfBuilt;
    }

    public synchronized StructurePlan getPlan() {
        if (plan == null) {
            plan = factory.get();
        }
        return plan;
    }

    public synchronized List<List<StructurePlan.Placement>> getSteps() {
        if (steps == null) {
            steps = getPlan().toSteps();
        }
        return steps;
    }

    /** Все материалы на дом (по порядку постройки). */
    public synchronized Map<net.minecraft.item.Item, Integer> getRequirements() {
        if (requirements == null) {
            requirements = BuildCost.count(getSteps(), 0, getPlan().getOrigin());
        }
        return requirements;
    }

    /** Сколько блоков-материалов в доме (от этого зависит время постройки). */
    public synchronized int getCostlyBlocks() {
        if (costlyBlocks < 0) {
            int sum = 0;
            for (int n : getRequirements().values()) {
                sum += n;
            }
            costlyBlocks = sum;
        }
        return costlyBlocks;
    }

    @Nullable
    public static HouseType byId(String id) {
        for (HouseType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
