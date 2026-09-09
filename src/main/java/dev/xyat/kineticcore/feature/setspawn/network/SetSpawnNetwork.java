package dev.xyat.kineticcore.feature.setspawn.network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.kineticcore.KineticCore;
import dev.xyat.kineticcore.bootstrap.annotation.KTNetwork;
import dev.xyat.kineticcore.feature.setspawn.config.SetSpawnConfig;
import dev.xyat.kineticcore.feature.setspawn.util.StructureUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@KTNetwork
public class SetSpawnNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(KineticCore.MODID, "setspawn"),
                () -> PROTOCOL_VERSION,
                KTNetworkProtocol::acceptsAnyVersion,
                KTNetworkProtocol::acceptsAnyVersion
        );

        int id = 0;
        CHANNEL.registerMessage(id++, OpenSetSpawnGuiPacket.class, OpenSetSpawnGuiPacket::toBytes, OpenSetSpawnGuiPacket::new, OpenSetSpawnGuiPacket::handle);
        CHANNEL.registerMessage(id++, SaveSetSpawnPacket.class, SaveSetSpawnPacket::toBytes, SaveSetSpawnPacket::new, SaveSetSpawnPacket::handle);
        CHANNEL.registerMessage(id++, SaveSetSpawnResultPacket.class, SaveSetSpawnResultPacket::toBytes, SaveSetSpawnResultPacket::new, SaveSetSpawnResultPacket::handle);
        CHANNEL.registerMessage(id, RequestOpenSetSpawnGuiPacket.class, RequestOpenSetSpawnGuiPacket::toBytes, RequestOpenSetSpawnGuiPacket::new, RequestOpenSetSpawnGuiPacket::handle);
    }

    public static void requestOpenEditor() {
        if (CHANNEL != null) {
            CHANNEL.sendToServer(new RequestOpenSetSpawnGuiPacket());
        }
    }

    public static void openEditorForPlayer(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        SetSpawnConfig.load();
        MinecraftServer server = player.server;
        String dim = player.level().dimension().location().toString();
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
        List<String> structures = StructureUtils.getStructuresAt(player.serverLevel(), player.blockPosition());
        String structure = structures.isEmpty() ? "none" : structures.get(0);

        List<String> allDims = server.levelKeys().stream()
                .map(key -> key.location().toString())
                .sorted()
                .collect(Collectors.toList());
        List<String> allBiomes = server.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.toList());
        List<String> allStructs = server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.toList());

        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenSetSpawnGuiPacket(
                        SetSpawnConfig.enableCustomSpawn,
                        SetSpawnConfig.enableDimensions,
                        SetSpawnConfig.setspawnDimensions,
                        SetSpawnConfig.enableBiomes,
                        SetSpawnConfig.setspawnBiomes,
                        SetSpawnConfig.enableStructures,
                        SetSpawnConfig.setspawnStructures,
                        dim,
                        biome,
                        structure,
                        allDims,
                        allBiomes,
                        allStructs
                )
        );
    }

    public record RequestOpenSetSpawnGuiPacket() {
        public RequestOpenSetSpawnGuiPacket(FriendlyByteBuf buf) {
            this();
        }

        public void toBytes(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    openEditorForPlayer(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record OpenSetSpawnGuiPacket(boolean globalEnable, boolean dimEnable, List<String> dims,
                                        boolean biomeEnable, List<String> biomes,
                                        boolean structEnable, List<String> structs,
                                        String playerDim, String playerBiome, String playerStruct,
                                        List<String> allDims, List<String> allBiomes, List<String> allStructs) {
        public OpenSetSpawnGuiPacket(FriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readBoolean(), buf.readList(FriendlyByteBuf::readUtf),
                    buf.readBoolean(), buf.readList(FriendlyByteBuf::readUtf),
                    buf.readBoolean(), buf.readList(FriendlyByteBuf::readUtf),
                    buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readList(FriendlyByteBuf::readUtf), buf.readList(FriendlyByteBuf::readUtf), buf.readList(FriendlyByteBuf::readUtf));
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBoolean(globalEnable);
            buf.writeBoolean(dimEnable);
            buf.writeCollection(dims, FriendlyByteBuf::writeUtf);
            buf.writeBoolean(biomeEnable);
            buf.writeCollection(biomes, FriendlyByteBuf::writeUtf);
            buf.writeBoolean(structEnable);
            buf.writeCollection(structs, FriendlyByteBuf::writeUtf);
            buf.writeUtf(playerDim);
            buf.writeUtf(playerBiome);
            buf.writeUtf(playerStruct);
            buf.writeCollection(allDims, FriendlyByteBuf::writeUtf);
            buf.writeCollection(allBiomes, FriendlyByteBuf::writeUtf);
            buf.writeCollection(allStructs, FriendlyByteBuf::writeUtf);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SetSpawnNetworkClient.handleOpenGui(this)
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveSetSpawnResultPacket(boolean success) {
        public SaveSetSpawnResultPacket(FriendlyByteBuf buf) {
            this(buf.readBoolean());
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SetSpawnNetworkClient.handleSaveResult(success)
            ));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveSetSpawnPacket(boolean globalEnable, boolean dimEnable, List<String> dims,
                                     boolean biomeEnable, List<String> biomes,
                                     boolean structEnable, List<String> structs) {
        public SaveSetSpawnPacket(FriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readBoolean(), buf.readList(FriendlyByteBuf::readUtf),
                    buf.readBoolean(), buf.readList(FriendlyByteBuf::readUtf),
                    buf.readBoolean(), buf.readList(FriendlyByteBuf::readUtf));
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBoolean(globalEnable);
            buf.writeBoolean(dimEnable);
            buf.writeCollection(dims, FriendlyByteBuf::writeUtf);
            buf.writeBoolean(biomeEnable);
            buf.writeCollection(biomes, FriendlyByteBuf::writeUtf);
            buf.writeBoolean(structEnable);
            buf.writeCollection(structs, FriendlyByteBuf::writeUtf);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) {
                    sendSaveResult(player, false);
                    return;
                }

                Set<String> allowedDimensions = player.server.levelKeys().stream()
                        .map(key -> key.location().toString())
                        .collect(Collectors.toSet());
                allowedDimensions.remove("minecraft:overworld");
                Set<String> allowedBiomes = player.server.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream()
                        .map(ResourceLocation::toString)
                        .collect(Collectors.toSet());
                Set<String> allowedStructures = player.server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                        .map(ResourceLocation::toString)
                        .collect(Collectors.toSet());

                if (!containsOnlyAllowed(dims, allowedDimensions)
                        || !containsOnlyAllowed(biomes, allowedBiomes)
                        || !containsOnlyAllowed(structs, allowedStructures)) {
                    sendSaveResult(player, false);
                    return;
                }

                try {
                    SetSpawnConfig.enableCustomSpawn = globalEnable;
                    SetSpawnConfig.enableDimensions = dimEnable;
                    SetSpawnConfig.setspawnDimensions = sanitizeStrings(dims, allowedDimensions);
                    SetSpawnConfig.enableBiomes = biomeEnable;
                    SetSpawnConfig.setspawnBiomes = sanitizeStrings(biomes, allowedBiomes);
                    SetSpawnConfig.enableStructures = structEnable;
                    SetSpawnConfig.setspawnStructures = sanitizeStrings(structs, allowedStructures);
                    SetSpawnConfig.save();
                    sendSaveResult(player, true);
                } catch (Throwable throwable) {
                    KineticCore.LOGGER.error("Failed to save SetSpawn config", throwable);
                    sendSaveResult(player, false);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private static void sendSaveResult(ServerPlayer player, boolean success) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SaveSetSpawnResultPacket(success)
        );
    }

    private static boolean containsOnlyAllowed(List<String> rawIds, Set<String> allowed) {
        if (rawIds == null) return false;
        for (String raw : rawIds) {
            if (raw == null) return false;
            String id = raw.trim();
            if (id.isEmpty() || !allowed.contains(id)) return false;
        }
        return true;
    }

    private static List<String> sanitizeStrings(List<String> rawIds, Set<String> allowed) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : rawIds) {
            String id = raw.trim();
            if (allowed.contains(id) && seen.add(id)) {
                result.add(id);
            }
        }
        return result;
    }

}
