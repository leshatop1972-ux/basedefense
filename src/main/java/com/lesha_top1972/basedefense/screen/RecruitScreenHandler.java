package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.block.RecruitmentOfficeBlock;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.lesha_top1972.basedefense.research.ResearchManager;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Серверная часть меню военкомата. Клиенту передаётся: сколько стражников уже живёт в казарме,
 * сколько в ней коек (кроватей рядом с военкоматом), сколько монет у игрока и творческий ли режим.
 * Кнопка {@link #BUTTON_RECRUIT} — завербовать стражника: списывает {@link #COST} монет исследований
 * (в творческом — бесплатно), стражник появляется перед столом и селится в казарме.
 */
public class RecruitScreenHandler extends ScreenHandler {

    /** Цена вербовки одного стражника (монеты исследований). */
    public static final int COST = 150;
    /** Больше коек, чем столько, не считаем (на случай огромной спальни рядом). */
    public static final int MAX_BEDS = 12;

    public static final int PROP_GUARDS = 0;
    public static final int PROP_BEDS = 1;
    public static final int PROP_COINS = 2;
    public static final int PROP_CREATIVE = 3;
    public static final int PROP_COUNT = 4;

    public static final int BUTTON_RECRUIT = 0;

    /** Где искать стражников этой казармы (они живут рядом с ней). */
    private static final double GUARD_SEARCH = 96.0;

    private final ScreenHandlerContext context;
    private final PropertyDelegate props;

    /** Конструктор для клиента. */
    public RecruitScreenHandler(int syncId, PlayerInventory inventory) {
        this(syncId, ScreenHandlerContext.EMPTY, new ArrayPropertyDelegate(PROP_COUNT));
    }

    /** Конструктор для сервера. */
    public RecruitScreenHandler(int syncId, PlayerInventory inventory, ScreenHandlerContext context) {
        this(syncId, context, new ServerProps(inventory.player, context));
    }

    private RecruitScreenHandler(int syncId, ScreenHandlerContext context, PropertyDelegate props) {
        super(ModScreenHandlers.RECRUIT, syncId);
        this.context = context;
        this.props = props;
        this.addProperties(props);
    }

    // ---------- Для экрана (клиент) ----------

    public int getProp(int index) {
        return props.get(index);
    }

    public int guards() {
        return props.get(PROP_GUARDS);
    }

    public int beds() {
        return props.get(PROP_BEDS);
    }

    public int coins() {
        return props.get(PROP_COINS);
    }

    public boolean isCreative() {
        return props.get(PROP_CREATIVE) != 0;
    }

    public boolean canAfford() {
        return isCreative() || coins() >= COST;
    }

    public boolean hasFreeBed() {
        return guards() < beds();
    }

    // ---------- Подсчёт (сервер) ----------

    /** Стражники, завербованные в этом военкомате. */
    public static int countGuards(World world, BlockPos office) {
        Box box = new Box(office).expand(GUARD_SEARCH);
        return world.getEntitiesByClass(GuardEntity.class, box,
                g -> g.isAlive() && office.equals(g.getBarracks())).size();
    }

    /** Кровати (изголовья) рядом с военкоматом — там же, где их ищет житель (±8 по горизонтали). */
    public static int countBeds(World world, BlockPos office) {
        int beds = 0;
        for (BlockPos pos : BlockPos.iterate(office.add(-8, -2, -8), office.add(8, 3, 8))) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD) {
                beds++;
            }
        }
        return Math.min(beds, MAX_BEDS);
    }

    /** Значения для клиента; стражников и кровати пересчитываем раз в секунду, а не каждый тик. */
    private static final class ServerProps implements PropertyDelegate {
        private final PlayerEntity player;
        private final ScreenHandlerContext context;
        private long lastCount;
        private boolean counted;
        private int guards;
        private int beds;

        ServerProps(PlayerEntity player, ScreenHandlerContext context) {
            this.player = player;
            this.context = context;
        }

        void refresh(boolean force) {
            long now = player.getWorld().getTime();
            // Раньше здесь было переполнение (now − Long.MIN_VALUE < 0) и счёт не делался никогда
            if (!force && counted && Math.abs(now - lastCount) < 20) {
                return;
            }
            counted = true;
            lastCount = now;
            context.run((world, pos) -> {
                guards = countGuards(world, pos);
                beds = countBeds(world, pos);
            });
        }

        @Override
        public int get(int index) {
            return switch (index) {
                case PROP_GUARDS -> {
                    refresh(false);
                    yield guards;
                }
                case PROP_BEDS -> {
                    refresh(false);
                    yield beds;
                }
                case PROP_COINS -> Math.min(ResearchManager.countCoins(player), 9999);
                case PROP_CREATIVE -> player.isCreative() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int size() {
            return PROP_COUNT;
        }
    }

    // ---------- Логика ----------

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != BUTTON_RECRUIT || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        context.run((world, pos) -> recruit(serverPlayer, (ServerWorld) world, pos));
        if (props instanceof ServerProps server) {
            server.refresh(true);
        }
        return true;
    }

    private static void recruit(ServerPlayerEntity player, ServerWorld world, BlockPos office) {
        BlockState state = world.getBlockState(office);
        if (!(state.getBlock() instanceof RecruitmentOfficeBlock)) {
            return;
        }
        int beds = countBeds(world, office);
        if (beds == 0) {
            deny(player, world, office, "message.basedefense.recruit.no_beds");
            return;
        }
        if (countGuards(world, office) >= beds) {
            deny(player, world, office, "message.basedefense.recruit.full");
            return;
        }
        if (!player.isCreative() && ResearchManager.countCoins(player) < COST) {
            deny(player, world, office, "message.basedefense.recruit.no_coins");
            return;
        }
        GuardEntity guard = ModEntities.GUARD.create(world);
        if (guard == null) {
            return;
        }
        if (!player.isCreative()) {
            ResearchManager.removeCoins(player, COST);
        }
        // Стражник встаёт перед столом (если там тесно — рядом со столом или на нём)
        Direction facing = state.get(RecruitmentOfficeBlock.FACING);
        BlockPos spawn = findSpawn(world, office, facing);
        guard.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                facing.getOpposite().asRotation(), 0.0f);
        guard.setHome(office);
        guard.setBarracks(office);
        guard.setPersistent();
        world.spawnEntity(guard);

        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                14, 0.35, 0.6, 0.35, 0.0);
        world.spawnParticles(ParticleTypes.CLOUD, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5,
                8, 0.3, 0.05, 0.3, 0.02);
        world.playSound(null, spawn, SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.NEUTRAL, 1.0f, 1.0f);
        world.playSound(null, spawn, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.8f, 1.0f);
        player.sendMessage(Text.translatable("message.basedefense.recruit.done", guard.getDisplayName())
                .formatted(Formatting.GREEN), true);
    }

    private static BlockPos findSpawn(ServerWorld world, BlockPos office, Direction facing) {
        BlockPos[] tries = {office.offset(facing), office.offset(facing).offset(facing),
                office.offset(facing.rotateYClockwise()), office.offset(facing.rotateYCounterclockwise()),
                office.offset(facing.getOpposite())};
        for (BlockPos pos : tries) {
            if (fits(world, pos)) {
                return pos;
            }
        }
        return office.up();
    }

    /** Есть ли место для жителя: две клетки сверху проходимы, под ногами твёрдо. */
    private static boolean fits(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                && world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
                && !world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty();
    }

    private static void deny(ServerPlayerEntity player, ServerWorld world, BlockPos office, String key) {
        world.playSound(null, office, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1.0f);
        player.sendMessage(Text.translatable(key, COST).formatted(Formatting.RED), true);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(context, player, com.lesha_top1972.basedefense.block.ModBlocks.RECRUITMENT_OFFICE);
    }
}
