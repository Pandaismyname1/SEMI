package dev.emi.emi.platform.neoforge;

import dev.emi.emi.data.ContextIntValues;
import dev.emi.emi.network.CommandS2CPacket;
import dev.emi.emi.network.ContextIntValuesS2CPacket;
import dev.emi.emi.network.CreateItemC2SPacket;
import dev.emi.emi.network.EmiChessPacket;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import dev.emi.emi.network.FillRecipeC2SPacket;
import dev.emi.emi.network.PingS2CPacket;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class EmiPacketHandler {

    public static void init(RegisterPayloadHandlersEvent event) {
        // The argument is the channel's protocol version, not a namespace.
        var registrar = event.registrar("1").optional();

        registrar.playToServer(EmiNetwork.FILL_RECIPE, makeReader(EmiNetwork.FILL_RECIPE, FillRecipeC2SPacket::new), EmiPacketHandler::handleServerbound);
        registrar.playToServer(EmiNetwork.CREATE_ITEM, makeReader(EmiNetwork.CREATE_ITEM, CreateItemC2SPacket::new), EmiPacketHandler::handleServerbound);
        registrar.playToClient(EmiNetwork.PING, makeReader(EmiNetwork.PING, PingS2CPacket::new), EmiPacketHandler::handleClientbound);
        registrar.playToClient(EmiNetwork.COMMAND, makeReader(EmiNetwork.COMMAND, CommandS2CPacket::new), EmiPacketHandler::handleClientbound);
        // Chess uses one id in both directions, matching Fabric, so that the two loaders stay wire
        // compatible and EmiNetwork's hasChannel check sees a registered channel.
        // The resolved number provider values go out in the configuration phase, and again in the
        // play phase after a datapack reload, which is exactly what commonToClient registers for.
        registrar.commonToClient(EmiNetwork.CONTEXT_INT_VALUES, ContextIntValuesS2CPacket.STREAM_CODEC,
                EmiPacketHandler::handleContextIntValues);
        registrar.playBidirectional(EmiNetwork.CHESS, makeReader(EmiNetwork.CHESS, EmiChessPacket.C2S::new),
                (packet, context) -> handleServerbound(packet.asServerbound(), context),
                (packet, context) -> handleClientbound(packet.asClientbound(), context));
    }

    /**
     * Values arriving in the configuration phase are stored quietly, before EMI has loaded
     * anything; a play phase update means a datapack reload changed them, so EMI reloads, but only
     * if they actually differ from what it already has.
     */
    private static void handleContextIntValues(ContextIntValuesS2CPacket packet, IPayloadContext context) {
        boolean play = context.protocol() == ConnectionProtocol.PLAY;
        context.enqueueWork(() -> ContextIntValues.set(packet.values(), play));
    }

    public static EmiPacket wrap(EmiPacket packet) {
        return packet;
    }

    private static <T extends EmiPacket> StreamCodec<RegistryFriendlyByteBuf, T> makeReader(CustomPacketPayload.Type<T> id, StreamDecoder<RegistryFriendlyByteBuf, T> reader) {
        return StreamCodec.ofMember(EmiPacket::write, reader);
    }

    private static void handleServerbound(EmiPacket packet, IPayloadContext context) {
        if (!context.flow().isServerbound()) {
            throw new IllegalArgumentException("Trying to handle serverbound packet on client: " + packet);
        }
        var player = context.player();
        context.enqueueWork(() -> packet.apply(player));
    }

    private static void handleClientbound(EmiPacket packet, IPayloadContext context) {
        if (!context.flow().isClientbound()) {
            throw new IllegalArgumentException("Trying to handle clientbound packet on server: " + packet);
        }
        var player = context.player();
        context.enqueueWork(() -> packet.apply(player));
    }
}
