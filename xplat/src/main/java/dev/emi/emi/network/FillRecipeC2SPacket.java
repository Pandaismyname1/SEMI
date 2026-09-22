package dev.emi.emi.network;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import dev.emi.emi.runtime.EmiLog;
import io.netty.handler.codec.DecoderException;

public class FillRecipeC2SPacket implements EmiPacket {
	private final int syncId;
	private final int action;
	private final List<Integer> slots, crafting;
	private final int output;
	private final List<ItemStack> stacks;

	public FillRecipeC2SPacket(AbstractContainerMenu handler, int action, List<Slot> slots, List<Slot> crafting, @Nullable Slot output, List<ItemStack> stacks) {
		this.syncId = handler.containerId;
		this.action = action;
		this.slots = slots.stream().map(s -> s == null ? -1 : s.index).toList();
		this.crafting = crafting.stream().map(s -> s == null ? -1 : s.index).toList();
		this.output = output == null ? -1 : output.index;
		this.stacks = stacks;
	}

	public FillRecipeC2SPacket(RegistryFriendlyByteBuf buf) {
		syncId = buf.readInt();
		action = buf.readByte();
		slots = parseCompressedSlots(buf);
		crafting = Lists.newArrayList();
		int craftingSize = buf.readVarInt();
		checkCount(craftingSize, "crafting slots");
		for (int i = 0; i < craftingSize; i++) {
			int s = buf.readVarInt();
			crafting.add(s);
		}
		if (buf.readBoolean()) {
			output = buf.readVarInt();
		} else {
			output = -1;
		}
		int size = buf.readVarInt();
		checkCount(size, "stacks");
		stacks = Lists.newArrayList();
		for (int i = 0; i < size; i++) {
			stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
		}
	}

	@Override
	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(syncId);
		buf.writeByte(action);
		writeCompressedSlots(slots, buf);
		buf.writeVarInt(crafting.size());
		for (Integer s : crafting) {
			buf.writeVarInt(s);
		}
		if (output != -1) {
			buf.writeBoolean(true);
			buf.writeVarInt(output);
		} else {
			buf.writeBoolean(false);
		}
		buf.writeVarInt(stacks.size());
		for (ItemStack stack : stacks) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
		}
	}

	@Override
	public void apply(Player player) {
		AbstractContainerMenu handler = player.containerMenu;
		if (handler == null || handler.containerId != syncId) {
			EmiLog.warn("Client requested fill but screen handler has changed, aborting");
			return;
		}
		List<Slot> slots = Lists.newArrayList();
		List<Slot> crafting = Lists.newArrayList();
		Slot output = null;
		for (int i : this.slots) {
			if (i < 0 || i >= handler.slots.size()) {
				EmiLog.error("Client requested fill but passed input slots don't exist, aborting");
				return;
			}
			slots.add(handler.slots.get(i));
		}
		for (int i : this.crafting) {
			if (i >= 0 && i < handler.slots.size()) {
				crafting.add(handler.slots.get(i));
			} else {
				crafting.add(null);
			}
		}
		if (this.output != -1) {
			if (this.output >= 0 && this.output < handler.slots.size()) {
				output = handler.slots.get(this.output);
			}
		}
		if (crafting.size() >= stacks.size()) {
			List<ItemStack> rubble = Lists.newArrayList();
			for (int i = 0; i < crafting.size(); i++) {
				Slot s = crafting.get(i);
				if (s != null && s.mayPickup(player) && !s.getItem().isEmpty()) {
					ItemStack taken = s.getItem();
					rubble.add(taken.copy());
					s.setByPlayer(ItemStack.EMPTY);
					s.onTake(player, taken);
				}
			}
			try {	
				for (int i = 0; i < stacks.size(); i++) {
					ItemStack stack = stacks.get(i);
					if (stack.isEmpty()) {
						continue;
					}
					int gotten = grabMatching(player, slots, rubble, crafting, stack);
					if (gotten != stack.getCount()) {
						if (gotten > 0) {
							stack.setCount(gotten);
							player.getInventory().placeItemBackInInventory(stack);
						}
						return;
					} else {
						Slot s = crafting.get(i);
						if (s != null && s.mayPlace(stack) && stack.getCount() <= s.getMaxStackSize() && stack.getCount() <= stack.getMaxStackSize()) {
							if (!s.getItem().isEmpty()) { // Make sure we don't accidentally delete any items that could have been placed in this slot
								if (s.mayPickup(player)) {
									ItemStack taken = s.getItem();
									rubble.add(taken.copy());
									s.setByPlayer(ItemStack.EMPTY);
									s.onTake(player, taken);
								} else {
									player.getInventory().placeItemBackInInventory(stack);
									continue;
								}
							}
							s.setByPlayer(stack);
						} else {
							player.getInventory().placeItemBackInInventory(stack);
						}
					}
				}
				if (output != null) {
					// clicked() takes the menu index, which is what the packet carries, not the
					// container-local slot.
					if (action == 1) {
						handler.clicked(output.index, 0, ContainerInput.PICKUP, player);
					} else if (action == 2) {
						handler.clicked(output.index, 0, ContainerInput.QUICK_MOVE, player);
					}
				}
			} finally {
				for (ItemStack stack : rubble) {
					player.getInventory().placeItemBackInInventory(stack);
				}
			}
		}
	}

	/**
	 * An upper bound on every count and slot index this packet carries. Modded menus can be large,
	 * so this is generous, but the values come from the client and have to be bounded to keep a
	 * hostile client from making the server expand a range of two billion slots. Every index is
	 * validated against the menu's real slot count in {@link #apply} anyway.
	 */
	private static final int MAX_SLOTS = 65536;

	/**
	 * An upper bound on how many input slots the compressed ranges may expand to, independent of how
	 * large the indices themselves may be. Without it a six byte packet declaring one range of
	 * {@code 0..65535} costs the server 65536 boxed integers; no real menu has anywhere near that
	 * many input sources.
	 */
	private static final int MAX_EXPANDED_SLOTS = 4096;

	/**
	 * Rejects the packet outright rather than returning a half-read value. Throwing is the clean
	 * way out: {@code PacketDecoder} is decoding a single framed packet, so the exception discards
	 * exactly this packet and the rest of the connection is unaffected.
	 */
	private static void checkCount(int count, String what) {
		if (count < 0 || count > MAX_SLOTS) {
			throw new DecoderException("EMI fill recipe packet declared " + count + " " + what
				+ ", which is outside of 0.." + MAX_SLOTS);
		}
	}

	private static List<Integer> parseCompressedSlots(FriendlyByteBuf buf) {
		List<Integer> list = Lists.newArrayList();
		int amount = buf.readVarInt();
		checkCount(amount, "input slot ranges");
		for (int i = 0; i < amount; i++) {
			int low = buf.readVarInt();
			int high = buf.readVarInt();
			if (low < 0 || high < low || high > MAX_SLOTS) {
				throw new DecoderException("EMI fill recipe packet declared an input slot range of "
					+ low + ".." + high + ", which is outside of 0.." + MAX_SLOTS);
			}
			// Large indices stay legal, but the expansion of every range together does not.
			if ((long) list.size() + (high - low + 1) > MAX_EXPANDED_SLOTS) {
				throw new DecoderException("EMI fill recipe packet declared input slot ranges that"
					+ " expand to more than " + MAX_EXPANDED_SLOTS + " slots");
			}
			for (int j = low; j <= high; j++) {
				list.add(j);
			}
		}
		return list;
	}
	
	private static void writeCompressedSlots(List<Integer> list, FriendlyByteBuf buf) {
		List<Consumer<FriendlyByteBuf>> postWrite = Lists.newArrayList();
		int groups = 0;
		int i = 0;
		while (i < list.size()) {
			groups++;
			int start = i;
			int startValue = list.get(start);
			while (i < list.size() && i - start == list.get(i) - startValue) {
				i++;
			}
			int end = i - 1;
			postWrite.add(b -> {
				b.writeVarInt(startValue);
				b.writeVarInt(list.get(end));
			});
		}
		buf.writeVarInt(groups);
		for (Consumer<FriendlyByteBuf> consumer : postWrite) {
			consumer.accept(buf);
		}
	}

	private static int grabMatching(Player player, List<Slot> slots, List<ItemStack> rubble, List<Slot> crafting, ItemStack stack) {
		int amount = stack.getCount();
		int grabbed = 0;
		for (int i = 0; i < rubble.size(); i++) {
			if (grabbed >= amount) {
				return grabbed;
			}
			ItemStack r = rubble.get(i);
			if (ItemStack.isSameItemSameComponents(stack, r)) {
				int wanted = amount - grabbed;
				if (r.getCount() <= wanted) {
					grabbed += r.getCount();
					rubble.remove(i);
					i--;
				} else {
					grabbed = amount;
					r.setCount(r.getCount() - wanted);
				}
			}
		}
		for (Slot s : slots) {
			if (grabbed >= amount) {
				return grabbed;
			}
			if (crafting.contains(s) || !s.mayPickup(player)) {
				continue;
			}
			ItemStack st = s.getItem();
			if (ItemStack.isSameItemSameComponents(stack, st)) {
				int wanted = amount - grabbed;
				ItemStack taken = st.copy();
				if (st.getCount() <= wanted) {
					grabbed += st.getCount();
					s.setByPlayer(ItemStack.EMPTY);
				} else {
					grabbed = amount;
					st.setCount(st.getCount() - wanted);
				}
				s.onTake(player, taken);
			}
		}
		return grabbed;
	}

	@Override
	public Type<FillRecipeC2SPacket> type() {
		return EmiNetwork.FILL_RECIPE;
	}
}
