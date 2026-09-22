package dev.emi.emi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.platform.fabric.EmiClientFabric;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;

/**
 * EMI reloads once both halves of the server's data have arrived. On Fabric the halves are:
 * <ul>
 * <li>tags: {@code CommonLifecycleEvents.TAGS_LOADED} with {@code client == true}, which Fabric
 * fires for the configuration phase packet (join) and for the play phase packet ({@code /reload}),
 * so EMI does not hook the tag packets itself;</li>
 * <li>recipes: the vanilla {@link ClientboundUpdateRecipesPacket} below, <em>not</em> Fabric's
 * {@code ClientRecipeSynchronizedEvent}.</li>
 * </ul>
 * The vanilla packet is the trigger because it is the only one that always arrives: Fabric's
 * {@code ClientboundRecipeSyncPayload} is skipped entirely on a server that does not run Fabric's
 * recipe synchronization. Fabric always queues its payload before the vanilla packet, so by the
 * time this runs the synchronized map is either already in place or is never coming. See D-F2b in
 * {@code docs/AUTOPILOT_26.1_DECISIONS.md}.
 */
@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

	@Inject(at = @At("RETURN"), method = "handleUpdateRecipes")
	private void onSynchronizeRecipes(ClientboundUpdateRecipesPacket packet, CallbackInfo info) {
		EmiClientFabric.onVanillaRecipesReceived();
	}

	@Inject(at = @At("RETURN"), method = "handleLogin")
	private void onGameJoin(CallbackInfo info) {
		EmiLog.info("Joining server, EMI waiting for data from server...");
	}
}
