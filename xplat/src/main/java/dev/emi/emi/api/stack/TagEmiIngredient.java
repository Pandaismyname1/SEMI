package dev.emi.emi.api.stack;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.ApiStatus;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiTagKey;
import dev.emi.emi.screen.tooltip.EmiTextTooltipWrapper;
import dev.emi.emi.screen.tooltip.RemainderTooltipComponent;
import dev.emi.emi.screen.tooltip.TagTooltipComponent;

@ApiStatus.Internal
public class TagEmiIngredient implements EmiIngredient {
	private final Identifier id;
	private List<EmiStack> stacks;
	public final TagKey<?> key;
	private final EmiTagKey<?> tagKey;
	private long amount;
	private float chance = 1;
	/**
	 * Lazily built stack for a tag model that just inherits an item's model, so that rendering it
	 * does not allocate one per slot per frame.
	 */
	private EmiStack iconStack;

	@ApiStatus.Internal
	public TagEmiIngredient(TagKey<?> key, long amount) {
		this(EmiTagKey.of(key), amount);
	}

	@ApiStatus.Internal
	public TagEmiIngredient(TagKey<?> key, List<EmiStack> stacks, long amount) {
		this(EmiTagKey.of(key), stacks, amount);
	}

	@ApiStatus.Internal
	public TagEmiIngredient(EmiTagKey<?> key, long amount) {
		this(key, EmiTags.getValues(key), amount);
	}

	private TagEmiIngredient(EmiTagKey<?> key, List<EmiStack> stacks, long amount) {
		this.id = key.id();
		this.key = key.raw();
		this.tagKey = key;
		this.stacks = stacks;
		this.amount = amount;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof TagEmiIngredient tag && tag.key.equals(this.key);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public EmiIngredient copy() {
		EmiIngredient stack = new TagEmiIngredient(tagKey, amount);
		stack.setChance(chance);
		return stack;
	}

	@Override
	public List<EmiStack> getEmiStacks() {
		return stacks;
	}

	@Override
	public long getAmount() {
		return amount;
	}

	@Override
	public EmiIngredient setAmount(long amount) {
		this.amount = amount;
		return this;
	}

	@Override
	public float getChance() {
		return chance;
	}

	@Override
	public EmiIngredient setChance(float chance) {
		this.chance = chance;
		return this;
	}

	@Override
	public void render(GuiGraphicsExtractor draw, int x, int y, float delta, int flags) {
		EmiDrawContext context = EmiDrawContext.wrap(draw);

		if ((flags & RENDER_ICON) != 0) {
			Identifier model = tagKey.getCustomModel();
			Item iconItem = EmiTags.getTagIconItem(model);
			List<EmiTags.TagIconLayer> icon = EmiTags.getTagIcon(model);
			if (iconItem != null) {
				// The tag model only inherits an item or block model, so render that item rather
				// than flattening its model down to a single face
				if (iconStack == null || iconStack.getKey() != iconItem) {
					iconStack = EmiStack.of(iconItem);
				}
				iconStack.render(context.raw(), x, y, delta, -1 ^ RENDER_AMOUNT);
			} else if (icon != null) {
				// 1.21 rendered a baked model here; 26.1 has no standalone model registry, so the
				// tag model is resolved down to flat textures at reload and blitted over the slot
				for (EmiTags.TagIconLayer layer : icon) {
					float u = layer.x() * layer.textureWidth() / 16f;
					int regionWidth = Math.max(1, layer.width() * layer.textureWidth() / 16);
					context.drawTexture(layer.texture(), x + layer.x(), y, layer.width(), 16,
						u, 0, regionWidth, layer.frameHeight(), layer.textureWidth(), layer.textureHeight());
				}
			} else if (stacks.size() > 0) {
				stacks.get(0).render(context.raw(), x, y, delta, -1 ^ RENDER_AMOUNT);
			}
		}
		if ((flags & RENDER_AMOUNT) != 0 && !tagKey.isOf(EmiPort.getFluidRegistry())) {
			String count = "";
			if (amount != 1) {
				count += amount;
			}
			EmiRenderHelper.renderAmount(context, x, y, EmiPort.literal(count));
		}
		if ((flags & RENDER_INGREDIENT) != 0) {
			EmiRender.renderTagIcon(this, context.raw(), x, y);
		}
		if ((flags & RENDER_REMAINDER) != 0) {
			EmiRender.renderRemainderIcon(this, context.raw(), x, y);
		}
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		List<ClientTooltipComponent> list = Lists.newArrayList();
		list.add(new EmiTextTooltipWrapper(this, EmiPort.ordered(tagKey.getTagName())));
		if (EmiUtil.showAdvancedTooltips()) {
			list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal("#" + id, ChatFormatting.DARK_GRAY))));
		}
		if (tagKey.isOf(EmiPort.getFluidRegistry()) && amount > 1) {
			list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiRenderHelper.getAmountText(this, amount))));
		}
		if (EmiConfig.appendModId) {
			String mod = EmiUtil.getModName(id.getNamespace());
			list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(mod, ChatFormatting.BLUE, ChatFormatting.ITALIC))));
		}
		list.add(new TagTooltipComponent(stacks));
		for (EmiStack stack : stacks) {
			if (!stack.getRemainder().isEmpty()) {
				list.add(new RemainderTooltipComponent(this));
				break;
			}
		}
		return list;
	}
}
