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
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoldenAmethystMod implements ModInitializer {

    public static final String MOD_ID = "goldenamethyst";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ── Item Registration ──────────────────────────────────────────────────────
    public static final Item GOLDEN_AMETHYST = new Item(new Item.Settings().maxCount(16));

    @Override
    public void onInitialize() {
        // Register the item
        Registry.register(
                Registries.ITEM,
                Identifier.of(MOD_ID, "golden_amethyst"),
                GOLDEN_AMETHYST
        );

        LOGGER.info("GoldenAmethystMod initialised!");

        // ── Feature 1 & 2: UseItemCallback (right-click air) ──────────────────
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(GOLDEN_AMETHYST)) return TypedActionResult.pass(stack);

            // Feature 1 – Kinetic Blink (dash): sneak + airborne + right-click air
            if (player.isSneaking() && !player.isOnGround()) {
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

        // ── Feature 2: UseBlockCallback (right-click block) ───────────────────
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(GOLDEN_AMETHYST)) return ActionResult.PASS;

            // Cooldown check (200 ticks = 10 s)
            if (player.getItemCooldownManager().isCoolingDown(GOLDEN_AMETHYST)) {
                player.sendMessage(Text.literal("§cTreasure Sense is cooling down!"), true);
                return ActionResult.FAIL;
            }

            if (!world.isClient) {
                ServerWorld serverWorld = (ServerWorld) world;
                BlockPos origin = player.getBlockPos();
                boolean found = false;

                for (BlockPos pos : BlockPos.iterate(
                        origin.add(-10, -10, -10),
                        origin.add(10, 10, 10))) {

                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
                        found = true;
                        Vec3d target = Vec3d.ofCenter(pos);
                        Vec3d start  = player.getEyePos();

                        // Spawn WAX_OFF particles along the line toward each container
                        for (int i = 1; i <= 10; i++) {
                            double t = i / 10.0;
                            double px = start.x + (target.x - start.x) * t;
                            double py = start.y + (target.y - start.y) * t;
                            double pz = start.z + (target.z - start.z) * t;
                            serverWorld.spawnParticles(
                                    ParticleTypes.WAX_OFF,
                                    px, py, pz,
                                    1, 0, 0, 0, 0.05
                            );
                        }
                    }
                }

                if (found) {
                    player.sendMessage(Text.literal("§aTreasure Sense: containers nearby!"), true);
                } else {
                    player.sendMessage(Text.literal("§7No containers found within 10 blocks."), true);
                }

                // Apply 10-second cooldown
                player.getItemCooldownManager().set(GOLDEN_AMETHYST, 200);
            }
            return ActionResult.SUCCESS;
        });

        // ── Feature 3: Stasis Strike ───────────────────────────────────────────
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(GOLDEN_AMETHYST)) return ActionResult.PASS;

            if (entity instanceof LivingEntity living && !world.isClient) {
                // Slowness X + Weakness X for 4 seconds (80 ticks), amplifier 9 = level X
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,  80, 9));
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,  80, 9));
                stack.decrement(1);
                player.sendMessage(Text.literal("§9Stasis Strike!"), true);
            }
            return ActionResult.PASS; // PASS so the normal hit still registers
        });
    }
}