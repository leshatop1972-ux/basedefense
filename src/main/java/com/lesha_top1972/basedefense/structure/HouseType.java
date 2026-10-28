package com.lesha_top1972.basedefense.structure;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Виды домов. Связывает блок дома с его чертежами (по одному на каждый дизайн {@link HouseDesign}).
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
    LUMBERJACK("lumberjack_house", HousePlans::lumberjack, false),
    TOWN_SQUARE("town_square", HousePlans::townSquare, false),
    /** Здание Базы: склад, блок «База» переносится внутрь, живёт курьер. */
    BASE("base_house", HousePlans::baseHouse, false);

    // Размер массивов — HouseDesign.values().length прямо в полях: в enum нельзя ссылаться
    // на свою статическую константу из инициализаторов полей.

    private final String id;
    private final Function<HouseDesign, StructurePlan> factory;
    private final boolean selfBuilt;

    // Чертёж, шаги постройки и список материалов считаются один раз на каждый дизайн (при первом обращении)
    private final StructurePlan[] plans = new StructurePlan[HouseDesign.values().length];
    @SuppressWarnings("unchecked")
    private final List<List<StructurePlan.Placement>>[] steps = new List[HouseDesign.values().length];
    @SuppressWarnings("unchecked")
    private final Map<net.minecraft.item.Item, Integer>[] requirements = new Map[HouseDesign.values().length];
    private final int[] costlyBlocks = new int[HouseDesign.values().length];

    HouseType(String id, Function<HouseDesign, StructurePlan> factory, boolean selfBuilt) {
        this.id = id;
        this.factory = factory;
        this.selfBuilt = selfBuilt;
        java.util.Arrays.fill(costlyBlocks, -1);
    }

    public String getId() {
        return id;
    }

    public boolean isSelfBuilt() {
        return selfBuilt;
    }

    /** Все постройки доступны сразу (исследований больше нет). */
    public boolean isUnlocked() {
        return true;
    }

    /** Название постройки (как у её блока). */
    public net.minecraft.text.Text getName() {
        return net.minecraft.text.Text.translatable("block.basedefense." + id);
    }

    @Nullable
    public static HouseType byIndex(int index) {
        HouseType[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    public synchronized StructurePlan getPlan(HouseDesign design) {
        int i = design.ordinal();
        if (plans[i] == null) {
            plans[i] = factory.apply(design);
        }
        return plans[i];
    }

    public synchronized List<List<StructurePlan.Placement>> getSteps(HouseDesign design) {
        int i = design.ordinal();
        if (steps[i] == null) {
            steps[i] = getPlan(design).toSteps();
        }
        return steps[i];
    }

    /** Все материалы на дом этого дизайна (по порядку постройки). */
    public synchronized Map<net.minecraft.item.Item, Integer> getRequirements(HouseDesign design) {
        int i = design.ordinal();
        if (requirements[i] == null) {
            requirements[i] = BuildCost.count(getSteps(design), 0, getPlan(design).getOrigin());
        }
        return requirements[i];
    }

    /** Сколько блоков-материалов в доме этого дизайна (от этого зависит время постройки). */
    public synchronized int getCostlyBlocks(HouseDesign design) {
        int i = design.ordinal();
        if (costlyBlocks[i] < 0) {
            int sum = 0;
            for (int n : getRequirements(design).values()) {
                sum += n;
            }
            costlyBlocks[i] = sum;
        }
        return costlyBlocks[i];
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
