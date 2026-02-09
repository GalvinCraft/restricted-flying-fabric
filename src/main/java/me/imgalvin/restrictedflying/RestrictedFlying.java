package me.imgalvin.restrictedflying;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static net.minecraft.commands.arguments.DimensionArgument.getDimension;

public class RestrictedFlying implements ModInitializer {
    public static final String MOD_ID = "restricted-flying";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Set<ResourceKey<Level>> allowedFlightDimensions = new HashSet<>();

    private static Path configPath;

//    private static final SuggestionProvider<ServerCommandSource> DIMENSION_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(context.getSource().getServer().getWorldRegistryKeys().stream().map(RegistryKey::getValue), builder);

    @Override
    public void onInitialize() {
        LOGGER.info("Restricted Flying mod is initializing!");

        // Register flight restriction logic
        // TODO: For performance reasons, we should schedule this task to run every second instead of every tick
        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            for (ServerPlayer player : world.players()) {
                if (player.isFallFlying()) {
                    ResourceKey<Level> dimension = player.level().getLevel().dimension();
                    if (!allowedFlightDimensions.contains(dimension)) {
                        player.stopFallFlying();
                        player.sendSystemMessage(Component.literal("Elytra flight is disabled in this dimension."), true);

                        net.minecraft.world.item.ItemStack chestItem = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                        if (chestItem.getItem().toString().toLowerCase().contains("elytra")) {
                            net.minecraft.world.item.ItemStack elytraCopy = chestItem.copy();
                            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
                            player.drop(elytraCopy, false, true);
                        }
                    }
                }
            }
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("noflying")
                .then(Commands.literal("config")
                        // Require admin
                        // Normally we would ask for GAMEMASTERS_CHECK, but this makes changes to the world as a whole, so this is more appropriate
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    loadConfig(ctx.getSource());
                                    return 1;
                                })
                        )
                        .then(Commands.literal("add")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
//                                        .suggests(DIMENSION_SUGGESTIONS)
                                        .executes(ctx -> {
                                            ServerLevel serverLevel = getDimension(ctx, "dimension");
                                            ResourceKey<Level> resourceKey = serverLevel.dimension();
                                            if (allowedFlightDimensions.add(resourceKey)) {
                                                saveConfig(ctx.getSource());
                                                ctx.getSource().sendSuccess(() -> Component.literal("Added " + resourceKey + " to allowed flight dimensions."), false);
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("Dimension " + resourceKey + " is already allowed."));
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("remove")
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
//                                        .suggests(DIMENSION_SUGGESTIONS)
                                                        .executes(ctx -> {
                                                            ServerLevel serverLevel = getDimension(ctx, "dimension");
                                                            ResourceKey<Level> resourceKey = serverLevel.dimension();
                                                            if (allowedFlightDimensions.remove(resourceKey)) {
                                                                saveConfig(ctx.getSource());
                                                                ctx.getSource().sendSuccess(() -> Component.literal("Added " + resourceKey + " to allowed flight dimensions."), false);
                                                            } else {
                                                                ctx.getSource().sendFailure(Component.literal("Dimension " + resourceKey + " is already allowed."));
                                                            }
                                                            return 1;
                                                        })
                                        )
                        )
                        .then(Commands.literal("show")
                                .executes(ctx -> {
                                    StringBuilder sb = new StringBuilder("Allowed flight dimensions: ");
                                    if (allowedFlightDimensions.isEmpty()) {
                                        ctx.getSource().sendSystemMessage(Component.literal("No flying is allowed in any dimensions").withStyle(ChatFormatting.RED));
                                        return 1;
                                    }
                                    for (ResourceKey<Level> key : allowedFlightDimensions) {
                                        sb.append(key.toString()).append(", ");
                                    }
                                    if (!sb.isEmpty()) {
                                        sb.setLength(sb.length() - 2); // Remove trailing comma and space
                                    }
                                    ctx.getSource().sendSystemMessage((Component.literal(sb.toString())));
                                    return 1;
                                })
                        )
                )
        ));
    }

    // Config
    private static void loadConfig(net.minecraft.commands.CommandSourceStack source) {
        try {
            if (configPath == null) {
                Path worldDir = source.getServer().getWorldPath(LevelResource.ROOT);
                configPath = worldDir.resolve("allowed_flight.txt");
            }

            allowedFlightDimensions.clear();

            if (Files.exists(configPath)) {
                String content = Files.readString(configPath).trim();
                if (!content.isEmpty()) {
                    String[] parts = content.split(",");
                    for (String part : parts) {
                        Identifier id = Identifier.tryParse(part.trim());
                        if (id == null) {
                            LOGGER.warn("Invalid dimension ID in config: {}", part.trim());
                            continue;
                        }
                        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
                        allowedFlightDimensions.add(key);
                    }
                }
            }
            source.sendSystemMessage(Component.literal("Flight config reloaded."));
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
            source.sendSystemMessage(Component.literal("Error loading config.").withStyle(ChatFormatting.RED));
        }
    }

    // Sure lets overload this method
    public static void loadConfig(Path path) {
        allowedFlightDimensions.clear();
        configPath = path;

        LOGGER.info("Loading config from: {}", path.toString());

        try {
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath).trim();
                if (!content.isEmpty()) {
                    String[] parts = content.split(",");
                    for (String part : parts) {
                        Identifier id = Identifier.tryParse(part.trim());
                        if (id == null) {
                            LOGGER.warn("Invalid dimension ID in config: {}", part.trim());
                            continue;
                        }
                        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
                        allowedFlightDimensions.add(key);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
        }
    }

    private static void saveConfig(net.minecraft.commands.CommandSourceStack source) {
        try {
            if (configPath == null) {
                Path worldDir = source.getServer().getWorldPath(LevelResource.ROOT);
                configPath = worldDir.resolve("allowed_flight.txt");
            }

            List<String> ids = new ArrayList<>();
            for (ResourceKey<Level> key : allowedFlightDimensions) {
                LOGGER.info(String.valueOf(key));
                ids.add(key.identifier().toString());
            }

            Files.writeString(configPath, String.join(",", ids), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            source.sendSystemMessage(Component.literal("Config saved."));
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
            source.sendSystemMessage(Component.literal("Error saving config.").withStyle(ChatFormatting.RED));
        }
    }
}