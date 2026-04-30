package net.example.goldenamethyst;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

public class GoldenAmethystClient implements ClientModInitializer {

    public static boolean cutscenePlaying = false;
    public static int cutsceneTick = 0;
    public static final int CUTSCENE_DURATION = 160;
    public static boolean hasSeenCutscene = false;

    private static double playerX = 0;
    private static double playerY = 0;
    private static double playerZ = 0;
    private static float playerYaw = 0;
    private static Perspective originalPerspective;
    public static float domainCharge = 0f;
    public static boolean domainCharging = false;
    private static final int CHARGE_TICKS = 60;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            ClientWorld world = client.world;
            PlayerInventory inv = client.player.getInventory();

            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isOf(GoldenAmethystMod.VOID_CRYSTAL)) {
                    if (i == inv.selectedSlot) {
                        Vec3d pos = client.player.getPos();
                        world.addParticle(
                                ParticleTypes.DRAGON_BREATH,
                                pos.x + (Math.random() - 0.5) * 0.1,
                                pos.y + 0.3 + (Math.random() * 0.2),
                                pos.z + (Math.random() - 0.5) * 0.1,
                                (Math.random() - 0.5) * 0.01,
                                Math.random() * 0.02,
                                (Math.random() - 0.5) * 0.01
                        );
                    }
                }
            }
            MinecraftClient mc = client;
            if (mc.player != null) {
                ItemStack held = mc.player.getMainHandStack();
                boolean holdingCrystal = held.isOf(GoldenAmethystMod.VOID_CRYSTAL);
                boolean pressing = holdingCrystal && mc.options.useKey.isPressed();

                if (pressing) {
                    domainCharging = true;
                    domainCharge = Math.min(domainCharge + 1f / CHARGE_TICKS, 1f);
                    if (domainCharge >= 1f) {
                        domainCharge = 0f;
                        domainCharging = false;
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new GoldenAmethystMod.ActivateDomainPayload()
                        );
                    }
                } else {
                    domainCharge = Math.max(domainCharge - 0.05f, 0f);
                    if (domainCharge == 0f) domainCharging = false;
                }
            }

            if (!cutscenePlaying) return;
            cutsceneTick++;

            if (cutsceneTick == 1) {
                playerX = client.player.getX();
                playerY = client.player.getY();
                playerZ = client.player.getZ();
                playerYaw = client.player.getYaw();
                originalPerspective = client.options.getPerspective();
                client.player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                client.player.setPitch(-90f);
                client.player.setVelocity(0, 0, 0);
            }
            if (cutsceneTick >= 61 && cutsceneTick <= 140) {
                double progress = (cutsceneTick - 61) / 79.0;
                double eased = progress * progress;
                float pitch = (float) (-90f + 180f * eased);
                client.player.setPitch(pitch);
                client.player.setVelocity(0, 0, 0);
            }

            // End cutscene
            if (cutsceneTick >= CUTSCENE_DURATION) {
                cutscenePlaying = false;
                cutsceneTick = 0;
                client.options.setPerspective(originalPerspective);
                client.player.setPitch(0f);
                client.player.setYaw(playerYaw);
            }
        });
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!cutscenePlaying) return;

            MinecraftClient client = MinecraftClient.getInstance();
            int screenW = client.getWindow().getScaledWidth();
            int screenH = client.getWindow().getScaledHeight();

            if (cutsceneTick <= 60) {
                drawContext.fill(0, 0, screenW, screenH, 0xDD1A0033);

                String title = "§5§lThe Void Shard Captured";
                drawContext.drawText(client.textRenderer, title,
                        (screenW - client.textRenderer.getWidth(title)) / 2,
                        screenH / 2 - 60, 0xFFCC99FF, true);

                String line1 = "§oA fragment of an ancient presence, split and bound";
                drawContext.drawText(client.textRenderer, line1,
                        (screenW - client.textRenderer.getWidth(line1)) / 2,
                        screenH / 2 - 30, 0xFFAA88CC, true);

                String line2 = "§oin glass by the hands of forgotten gods.";
                drawContext.drawText(client.textRenderer, line2,
                        (screenW - client.textRenderer.getWidth(line2)) / 2,
                        screenH / 2 - 18, 0xFFAA88CC, true);

                String line3 = "§oUnbound by time, it waits. Neither dead. Nor sleeping.";
                drawContext.drawText(client.textRenderer, line3,
                        (screenW - client.textRenderer.getWidth(line3)) / 2,
                        screenH / 2, 0xFFAA88CC, true);

                String line4 = "§oYou hold a shard of a sundered master.";
                drawContext.drawText(client.textRenderer, line4,
                        (screenW - client.textRenderer.getWidth(line4)) / 2,
                        screenH / 2 + 18, 0xFFAA88CC, true);

                String warn = "§4§lThe glass is thin. Do not let it shatter.";
                drawContext.drawText(client.textRenderer, warn,
                        (screenW - client.textRenderer.getWidth(warn)) / 2,
                        screenH / 2 + 45, 0xFFFF3333, true);
            }

            if (cutsceneTick > 60) {
                drawContext.fill(0, 0, screenW, screenH, 0x331A0033);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            if (!domainCharging && domainCharge == 0f) return;

            int screenW = client.getWindow().getScaledWidth();
            int screenH = client.getWindow().getScaledHeight();
            int cx = screenW / 2;
            int cy = screenH / 2 + 40;

            int radius = 30;
            int segments = 64;
            float angle = domainCharge * 360f;

            drawContext.fill(cx - radius - 2, cy - radius - 2, cx + radius + 2, cy + radius + 2, 0x88000000);

            for (int i = 0; i < segments; i++) {
                float segAngle = (i / (float) segments) * 360f;
                if (segAngle > angle) break;

                float rad = (float) Math.toRadians(segAngle - 90f);
                int x1 = (int) (cx + Math.cos(rad) * (radius - 4));
                int y1 = (int) (cy + Math.sin(rad) * (radius - 4));
                int x2 = (int) (cx + Math.cos(rad) * radius);
                int y2 = (int) (cy + Math.sin(rad) * radius);

                int r = (int) (80 + 120 * domainCharge);
                int g = 0;
                int b = (int) (180 + 75 * domainCharge);
                int color = 0xFF000000 | (r << 16) | (g << 8) | b;

                drawContext.fill(Math.min(x1, x2), Math.min(y1, y2),
                        Math.max(x1, x2) + 2, Math.max(y1, y2) + 2, color);
            }

            drawContext.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0xFF1A0033);
            drawContext.fill(cx - 2, cy - 2, cx + 2, cy + 2,
                    domainCharge >= 1f ? 0xFFCC99FF : 0xFF6600AA);

            String label = domainCharge >= 0.99f ? "§5§lVOID REALM" : "§5Charging...";
            int lw = client.textRenderer.getWidth(label);
            drawContext.drawText(client.textRenderer, label, cx - lw / 2, cy + radius + 6, 0xFFCC99FF, true);
        });
        ClientPlayNetworking.registerGlobalReceiver(
                GoldenAmethystMod.StartCutscenePayload.ID,
                (payload, context) -> {
                    context.client().execute(GoldenAmethystClient::startCutscene);
                }
        );
    }

    public static void startCutscene() {
        cutscenePlaying = true;
        cutsceneTick = 0;
    }
}