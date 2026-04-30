package net.example.goldenamethyst;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;

public class GoldenAmethystMod implements ModInitializer {

    public static final String MOD_ID = "goldenamethyst";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    // ── Item Registration ──────────────────────────────────────────────────────
    public static final Item GOLDEN_AMETHYST = new Item(new Item.Settings().maxCount(16));
    public static final Item VOID_ESSENCE = new Item(new Item.Settings().maxCount(1));
    public static final Item VOID_CRYSTAL = new Item(new Item.Settings().maxCount(16));
    public static final Map<BlockPos, Integer> VOID_PORTALS = new HashMap<>();
    public static final Set<UUID> DRAGON_DROPPED = new HashSet<>();
    public static final Identifier START_CUTSCENE_PACKET = Identifier.of(MOD_ID, "start_cutscene");
    public static final Deque<PlayerSnapshot> snapshots = new ArrayDeque<>();
    public static int recount = 0;
    private static long rewstart = 0;
    private static final java.util.Set<UUID> sneakDropping = new java.util.HashSet<>();
    public static final Map<UUID, Long> domainCooldowns = new HashMap<>();
    public static final Map<UUID, java.util.List<BlockPos>> activeDomes = new HashMap<>();
    public static final Map<UUID, Integer> domainTicks = new HashMap<>();

    private static class PlayerSnapshot {
        final double x, y, z;
        final float health;
        final int food;
        final float saturation;
        final java.util.List<StatusEffectInstance> effects;
        final long time;
        PlayerSnapshot(double x, double y, double z, float health, int food, float saturation, java.util.List<StatusEffectInstance> effects, long time) {
            this.x = x; this.y = y; this.z = z;
            this.health = health; this.food = food; this.saturation = saturation;
            this.effects = effects; this.time = time;
        }
    }

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
                .register(StartCutscenePayload.ID, StartCutscenePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(ActivateDomainPayload.ID, ActivateDomainPayload.CODEC);
        // Register the item
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "golden_amethyst"), GOLDEN_AMETHYST);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "void_essence"), VOID_ESSENCE);
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "void_crystal"), VOID_CRYSTAL);
        LOGGER.info("GoldenAmethystMod initialised!");

        // Domain tick handler
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
                    UUID uid = player.getUuid();
                    if (!domainTicks.containsKey(uid)) continue;

                    int tick = domainTicks.get(uid);
                    domainTicks.put(uid, tick + 1);

                    net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                            player.getX() - 15, player.getY() - 15, player.getZ() - 15,
                            player.getX() + 15, player.getY() + 15, player.getZ() + 15);

                    java.util.List<LivingEntity> mobs = world.getEntitiesByClass(
                            LivingEntity.class, box,
                            e -> !(e instanceof net.minecraft.entity.player.PlayerEntity));

                    for (LivingEntity mob : mobs) {
                        double dx = mob.getX() - player.getX();
                        double dy = mob.getY() - player.getY();
                        double dz = mob.getZ() - player.getZ();
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist > 14.5) {
                            mob.setVelocity(-(dx / dist) * 0.5, -(dy / dist) * 0.3, -(dz / dist) * 0.5);
                            mob.velocityModified = true;
                        }
                    }

                    if (tick % 20 == 0) {
                        for (LivingEntity damaged : mobs) {
                            damaged.damage(world.getDamageSources().magic(), 2.0f);
                            damaged.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 200, 1));
                            damaged.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 400, 1));
                            damaged.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 400, 0));
                            damaged.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 400, 0));
                        }
                        world.spawnParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 1, player.getZ(), 30, 10, 5, 10, 0.05);
                        world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 10, 5, 10, 0.1);
                    }

                    boolean allDead = mobs.isEmpty();
                    boolean expired = tick >= 1800;

                    if (allDead || expired) {
                        if (activeDomes.containsKey(uid)) {
                            for (BlockPos bp : activeDomes.get(uid)) {
                                world.setBlockState(bp, net.minecraft.block.Blocks.AIR.getDefaultState());
                            }
                            activeDomes.remove(uid);
                        }
                        domainTicks.remove(uid);
                        domainCooldowns.put(uid, System.currentTimeMillis());
                        player.sendMessage(Text.literal("§5The domain collapses..."), true);
                    }
                }
            }
        });

        // Snapshot recorder
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
                    long now = System.currentTimeMillis();
                    snapshots.addLast(new PlayerSnapshot(
                            player.getX(), player.getY(), player.getZ(),
                            player.getHealth(),
                            player.getHungerManager().getFoodLevel(),
                            player.getHungerManager().getSaturationLevel(),
                            new java.util.ArrayList<>(player.getStatusEffects()),
                            now));
                    while (!snapshots.isEmpty() && now - snapshots.peekFirst().time > 3000) {
                        snapshots.pollFirst();
                    }
                }
            }
        });

        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> true);

        // Rewind handler
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
                    ItemStack held = player.getMainHandStack();
                    if (!held.isOf(VOID_CRYSTAL)) continue;
                    if (!player.isSneaking()) continue;

                    net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(player.getBlockPos()).expand(2.0);
                    java.util.List<net.minecraft.entity.ItemEntity> dropped =
                            world.getEntitiesByType(net.minecraft.entity.EntityType.ITEM, box,
                                    e -> e.getStack().isOf(VOID_CRYSTAL) && e.age < 2);

                    if (!dropped.isEmpty() && !sneakDropping.contains(player.getUuid())) {
                        sneakDropping.add(player.getUuid());

                        for (net.minecraft.entity.ItemEntity ie : dropped) {
                            player.getInventory().offerOrDrop(ie.getStack());
                            ie.discard();
                        }

                        long now = System.currentTimeMillis();
                        if (player.getItemCooldownManager().isCoolingDown(VOID_CRYSTAL)) {
                            player.sendMessage(Text.literal("§cbro is NOT the void master"), true);
                            continue;
                        }
                        if (now - rewstart > 60000) { rewstart = now; recount = 0; }
                        recount++;
                        if (recount >= 5) {
                            player.getItemCooldownManager().set(VOID_CRYSTAL, 1200);
                            recount = 0;
                            continue;
                        }
                        if (!snapshots.isEmpty()) {
                            PlayerSnapshot snap = snapshots.peekFirst();
                            player.teleport((ServerWorld) world, snap.x, snap.y, snap.z, player.getYaw(), player.getPitch());
                            player.setHealth(snap.health);
                            player.getHungerManager().setFoodLevel(snap.food);
                            player.getHungerManager().setSaturationLevel(snap.saturation);
                            player.setFireTicks(0);
                            player.clearStatusEffects();
                            for (StatusEffectInstance effect : snap.effects) {
                                player.addStatusEffect(new StatusEffectInstance(effect));
                            }
                            held.decrement(1);
                            player.sendMessage(Text.literal("§5Time flows backwards..."), true);
                        } else {
                            player.sendMessage(Text.literal("§cNo moment to return to."), true);
                        }
                    } else {
                        sneakDropping.remove(player.getUuid());
                    }
                }
            }
        });

        // Void floor handler
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
                    boolean hasVoidCrystal = false;
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        if (player.getInventory().getStack(i).isOf(VOID_CRYSTAL)) { hasVoidCrystal = true; break; }
                    }
                    if (hasVoidCrystal && player.getY() <= -68.0 && player.getY() >= -72.0 && player.getVelocity().y < 0) {
                        player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                        player.teleport(player.getX(), -70.0, player.getZ(), false);
                        player.fallDistance = 0;
                        player.velocityModified = true;
                    }
                }
            }
        });

        // Dragon Egg + Treasure Sense
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();

            if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.DRAGON_EGG)) {
                if (!world.isClient) {
                    int total = 0;
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        ItemStack s = player.getInventory().getStack(i);
                        if (s.isOf(GOLDEN_AMETHYST)) total += s.getCount();
                    }
                    if (total == 0) {
                        player.sendMessage(Text.literal("You don't have any Golden Amethyst yet, come back later"), true);
                        return ActionResult.FAIL;
                    }
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        ItemStack s = player.getInventory().getStack(i);
                        if (s.isOf(GOLDEN_AMETHYST)) {
                            player.getInventory().setStack(i, new ItemStack(VOID_CRYSTAL, s.getCount()));
                        }
                    }
                    if (world instanceof ServerWorld sw) {
                        sw.spawnParticles(ParticleTypes.DRAGON_BREATH, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 100, 0.5, 0.5, 0.5, 0.1);
                        sw.spawnParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 100, 0.5, 0.5, 0.5, 0.2);
                    }
                    boolean firstTime = !player.getCommandTags().contains("void_awakened");
                    if (firstTime) {
                        player.addCommandTag("void_awakened");
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                                (net.minecraft.server.network.ServerPlayerEntity) player,
                                new StartCutscenePayload());
                    } else {
                        player.sendMessage(Text.literal("§5Your crystals have been awakened by the void"), true);
                    }
                }
                return ActionResult.SUCCESS;
            }

            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(GOLDEN_AMETHYST) && !stack.isOf(VOID_CRYSTAL)) return ActionResult.PASS;

            if (player.getItemCooldownManager().isCoolingDown(GOLDEN_AMETHYST) || player.getItemCooldownManager().isCoolingDown(VOID_CRYSTAL)) {
                player.sendMessage(Text.literal("§cTreasure Sense is cooling down!"), true);
                return ActionResult.FAIL;
            }

            if (!world.isClient) {
                ServerWorld serverWorld = (ServerWorld) world;
                BlockPos origin = player.getBlockPos();
                boolean found = false;

                for (BlockPos p : BlockPos.iterate(origin.add(-10, -10, -10), origin.add(10, 10, 10))) {
                    BlockEntity be = world.getBlockEntity(p);
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
                        found = true;
                        Vec3d target = Vec3d.ofCenter(p);
                        Vec3d start = player.getEyePos();
                        for (int i = 1; i <= 10; i++) {
                            double t = i / 10.0;
                            serverWorld.spawnParticles(ParticleTypes.WAX_OFF,
                                    start.x + (target.x - start.x) * t,
                                    start.y + (target.y - start.y) * t,
                                    start.z + (target.z - start.z) * t,
                                    1, 0, 0, 0, 0.05);
                        }
                    }
                }

                player.sendMessage(found
                        ? Text.literal("§aTreasure Sense: containers nearby!")
                        : Text.literal("§7No containers found within 10 blocks."), true);
                player.getItemCooldownManager().set(GOLDEN_AMETHYST, 200);
                player.getItemCooldownManager().set(VOID_CRYSTAL, 200);
            }
            return ActionResult.SUCCESS;
        });

        // Kinetic Blink
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(GOLDEN_AMETHYST) && !stack.isOf(VOID_CRYSTAL)) return TypedActionResult.pass(stack);
            if (player.isSneaking() && !player.isOnGround() && player.getY() >= -64.0) {
                if (!world.isClient) {
                    Vec3d look = player.getRotationVec(1.0f);
                    player.setVelocity(look.multiply(1.5));
                    player.velocityModified = true;
                    stack.decrement(1);
                    player.sendMessage(Text.literal("§6Kinetic Blink!"), true);
                }
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // Void escape
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(VOID_CRYSTAL)) return TypedActionResult.pass(stack);
            if (!world.isClient && player.getY() < -64.0) {
                double groundY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        (int) player.getX(), (int) player.getZ());
                player.teleport(player.getX(), groundY, player.getZ(), false);
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // Stasis Strike
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(GOLDEN_AMETHYST) && !stack.isOf(VOID_CRYSTAL)) return ActionResult.PASS;
            if (entity instanceof LivingEntity living && !world.isClient) {
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 9));
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 9));
                stack.decrement(1);
                player.sendMessage(Text.literal("§9Stasis Strike!"), true);
            }
            return ActionResult.PASS;
        });

        // Domain activation receiver
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                ActivateDomainPayload.ID,
                (payload, context) -> {
                    net.minecraft.server.network.ServerPlayerEntity player = context.player();
                    ServerWorld world = (ServerWorld) player.getWorld();
                    UUID uid = player.getUuid();

                    if (domainCooldowns.containsKey(uid)) {
                        long elapsed = System.currentTimeMillis() - domainCooldowns.get(uid);
                        if (elapsed < 1800000) {
                            long remaining = (1800000 - elapsed) / 1000;
                            player.sendMessage(Text.literal("§cDomain on cooldown! " + remaining + "s remaining"), true);
                            return;
                        }
                    }

                    if (domainTicks.containsKey(uid)) {
                        player.sendMessage(Text.literal("§cDomain already active!"), true);
                        return;
                    }

                    java.util.List<BlockPos> domeBlocks = new java.util.ArrayList<>();
                    int radius = 15;
                    BlockPos center = player.getBlockPos();

                    for (int x = -radius; x <= radius; x++) {
                        for (int y = -radius; y <= radius; y++) {
                            for (int z = -radius; z <= radius; z++) {
                                double dist = Math.sqrt(x * x + y * y + z * z);
                                if (dist >= radius - 0.5 && dist <= radius + 0.5) {
                                    BlockPos bp = center.add(x, y, z);
                                    if (world.getBlockState(bp).isAir()) {
                                        world.setBlockState(bp, net.minecraft.block.Blocks.CRYING_OBSIDIAN.getDefaultState());
                                        domeBlocks.add(bp);
                                    }
                                }
                            }
                        }
                    }

                    activeDomes.put(uid, domeBlocks);
                    domainTicks.put(uid, 0);
                    world.spawnParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 1, player.getZ(), 100, 5, 5, 5, 0.2);
                    player.sendMessage(Text.literal("§5§lDomain Expansion: Void Realm!"), true);
                }
        );
    }

    public record StartCutscenePayload() implements net.minecraft.network.packet.CustomPayload {
        public static final net.minecraft.network.packet.CustomPayload.Id<StartCutscenePayload> ID =
                new net.minecraft.network.packet.CustomPayload.Id<>(Identifier.of(MOD_ID, "start_cutscene"));
        public static final net.minecraft.network.codec.PacketCodec<net.minecraft.network.PacketByteBuf, StartCutscenePayload> CODEC =
                net.minecraft.network.codec.PacketCodec.unit(new StartCutscenePayload());
        @Override
        public net.minecraft.network.packet.CustomPayload.Id<? extends net.minecraft.network.packet.CustomPayload> getId() {
            return ID;
        }
    }

    public record ActivateDomainPayload() implements net.minecraft.network.packet.CustomPayload {
        public static final net.minecraft.network.packet.CustomPayload.Id<ActivateDomainPayload> ID =
                new net.minecraft.network.packet.CustomPayload.Id<>(Identifier.of(MOD_ID, "activate_domain"));
        public static final net.minecraft.network.codec.PacketCodec<net.minecraft.network.PacketByteBuf, ActivateDomainPayload> CODEC =
                net.minecraft.network.codec.PacketCodec.unit(new ActivateDomainPayload());
        @Override
        public net.minecraft.network.packet.CustomPayload.Id<? extends net.minecraft.network.packet.CustomPayload> getId() {
            return ID;
        }
    }
}