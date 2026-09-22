package dev.emi.emi.mixin.neoforge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.platform.neoforge.EmiClientNeoForge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;

/**
 * EMI reloads once both halves of the server's data have arrived. On NeoForge the recipes half is
 * normally reported by {@code RecipesReceivedEvent}, which only fires when the server sent a
 * {@code RecipeContentPayload} - and {@code CommonHooks.sendRecipes} skips that payload entirely
 * unless {@code player.connection.getConnectionType().isNeoForge()}. On a vanilla, Paper or Fabric
 * server the event therefore never fires and EMI would wait forever with the tags half latched.
 * <p>
 * The vanilla {@link ClientboundUpdateRecipesPacket} always arrives, so it is used to arm a bounded
 * fallback. Unlike Fabric, it cannot be the direct trigger: NeoForge's patched {@code PlayerList}
 * sends the payload <em>after</em> the vanilla packet on both paths, so this runs before the real
 * map on a NeoForge server. See D-F4b.1 in {@code docs/AUTOPILOT_26.1_DECISIONS.md}.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

	@Inject(at = @At("RETURN"), method = "handleUpdateRecipes")
	private void emi$onUpdateRecipes(ClientboundUpdateRecipesPacket packet, CallbackInfo info) {
		EmiClientNeoForge.onVanillaRecipesReceived();
	}
}
