package me.imgalvin.restrictedflying;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EquipmentSlot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class RestrictedFlying implements ModInitializer {
    public static final String MOD_ID = "restricted-flying";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Set<RegistryKey<World>> allowedFlightDimensions = new HashSet<>();

    private static Path configPath;

    private static final SuggestionProvider<ServerCommandSource> DIMENSION_SUGGESTIONS = (context, builder) -> {
        return CommandSource.suggestIdentifiers(
                context.getSource().getServer().getRegistryManager().get(RegistryKeys.WORLD).getIds(),
                builder
        );
    };


    @Override
    public void onInitialize() {
        LOGGER.info("Restricted Flying mod is initializing!");

        // Register flight restriction logic
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.isFallFlying()) {
                    RegistryKey<World> dimension = player.getWorld().getRegistryKey();
                    if (!allowedFlightDimensions.contains(dimension)) {
                        player.stopFallFlying();
                        player.sendMessage(Text.literal("Elytra flight is disabled in this dimension."), true);

                        ItemStack chestItem = player.getEquippedStack(EquipmentSlot.CHEST);
                        if (chestItem.getItem().toString().toLowerCase().contains("elytra")) {
                            ItemStack elytraCopy = chestItem.copy();
                            player.getInventory().armor.set(2, ItemStack.EMPTY);
                            player.dropItem(elytraCopy, false, true);
                        }
                    }
                }
            }
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("noflying")
                    .then(CommandManager.literal("config")
                            .requires(source -> source.hasPermissionLevel(4)) // Admin only
                            .then(CommandManager.literal("reload")
                                    .executes(ctx -> {
                                        loadConfig(ctx.getSource());
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("add")
                                    .then(CommandManager.argument("dimension", IdentifierArgumentType.identifier())
                                            .suggests(DIMENSION_SUGGESTIONS)
                                            .executes(ctx -> {
                                                Identifier id = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(ctx, "dimension");
                                                if (id == null) {
                                                    ctx.getSource().sendError(Text.literal("Invalid dimension identifier."));
                                                    return 0;
                                                }
                                                RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
                                                if (allowedFlightDimensions.add(key)) {
                                                    saveConfig(ctx.getSource());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Added " + id + " to allowed flight dimensions."), false);
                                                } else {
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Dimension " + id + " is already allowed."), false);
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .then(CommandManager.literal("remove")
                                    .then(CommandManager.argument("dimension", IdentifierArgumentType.identifier())
                                            .suggests(DIMENSION_SUGGESTIONS)
                                            .executes(ctx -> {
                                                Identifier id = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(ctx, "dimension");
                                                if (id == null) {
                                                    ctx.getSource().sendError(Text.literal("Invalid dimension identifier."));
                                                    return 0;
                                                }
                                                RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
                                                if (allowedFlightDimensions.remove(key)) {
                                                    saveConfig(ctx.getSource());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Removed " + id + " from allowed flight dimensions."), false);
                                                } else {
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Dimension " + id + " is not in the allowed list."), false);
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .then(CommandManager.literal("show")
                                    .executes(ctx -> {
                                        StringBuilder sb = new StringBuilder("Allowed flight dimensions: ");
                                        if (allowedFlightDimensions.isEmpty()) {
                                            ctx.getSource().sendFeedback(() -> Text.literal("No flying is allowed in any dimensions").formatted(Formatting.RED), false);
                                            return 1;
                                        }
                                        for (RegistryKey<World> key : allowedFlightDimensions) {
                                            sb.append(key.getValue().toString()).append(", ");
                                        }
                                        if (!sb.isEmpty()) {
                                            sb.setLength(sb.length() - 2); // Remove trailing comma and space
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    // Config
    private static void loadConfig(ServerCommandSource source) {
        try {
            if (configPath == null) {
                Path worldDir = source.getServer().getSavePath(WorldSavePath.ROOT);
                configPath = worldDir.resolve("allowed_flight.txt");
            }

            allowedFlightDimensions.clear();

            if (Files.exists(configPath)) {
                String content = Files.readString(configPath).trim();
                if (!content.isEmpty()) {
                    String[] parts = content.split(",");
                    for (String part : parts) {
                        Identifier id = Identifier.tryParse(part.trim());
                        if (id != null) {
                            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
                            allowedFlightDimensions.add(key);
                        }
                    }
                }
            }
            source.sendFeedback(() -> Text.literal("Flight config reloaded."), false);
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
            source.sendError(Text.literal("Error loading config."));
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
                        if (id != null) {
                            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
                            allowedFlightDimensions.add(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
        }
    }

    private static void saveConfig(ServerCommandSource source) {
        try {
            if (configPath == null) {
                Path worldDir = source.getServer().getSavePath(WorldSavePath.ROOT);
                configPath = worldDir.resolve("allowed_flight.txt");
            }

            List<String> ids = new ArrayList<>();
            for (RegistryKey<World> key : allowedFlightDimensions) {
                ids.add(key.getValue().toString());
            }

            Files.writeString(configPath, String.join(",", ids), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            source.sendFeedback(() -> Text.literal("Config saved."), false);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
            source.sendError(Text.literal("Error saving config."));
        }
    }
}