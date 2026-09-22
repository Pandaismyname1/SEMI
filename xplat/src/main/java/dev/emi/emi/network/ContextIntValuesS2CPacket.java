package dev.emi.emi.network;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import dev.emi.emi.data.ContextIntValues;

/**
 * The expected value of every entry of the server's number provider registry, which a client cannot
 * work out for itself because the registry is never synchronized. See {@link ContextIntValues}.
 * <p>
 * Unlike the other EMI packets this is a plain {@link CustomPacketPayload} rather than an
 * {@link EmiPacket}: it is sent during the configuration phase, where there is no player to hand a
 * handler and where the buffer is a plain {@link FriendlyByteBuf} with no registry access.
 */
public record ContextIntValuesS2CPacket(Map<Identifier, Float> values) implements CustomPacketPayload {
	public static final StreamCodec<FriendlyByteBuf, ContextIntValuesS2CPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.FLOAT, ContextIntValues.MAX_ENTRIES),
		ContextIntValuesS2CPacket::values,
		ContextIntValuesS2CPacket::new);

	@Override
	public Type<ContextIntValuesS2CPacket> type() {
		return EmiNetwork.CONTEXT_INT_VALUES;
	}
}
