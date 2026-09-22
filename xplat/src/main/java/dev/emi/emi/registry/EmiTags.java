package dev.emi.emi.registry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiRegistryAdapter;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.data.TagExclusions;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.runtime.EmiHidden;
import dev.emi.emi.runtime.EmiReloadLog;
import dev.emi.emi.runtime.EmiTagKey;
import dev.emi.emi.util.InheritanceMap;

public class EmiTags {
	public static final InheritanceMap<EmiRegistryAdapter<?>> ADAPTERS_BY_CLASS = new InheritanceMap<>(Maps.newHashMap());
	public static final Map<Registry<?>, EmiRegistryAdapter<?>> ADAPTERS_BY_REGISTRY = Maps.newHashMap();
	public static final Identifier HIDDEN_FROM_RECIPE_VIEWERS = EmiPort.id("c", "hidden_from_recipe_viewers");
	public static final Map<TagKey<?>, Identifier> MODELED_TAGS = Maps.newHashMap();
	private static final Map<Identifier, List<TagIconLayer>> TAG_ICONS = Maps.newHashMap();
	private static final Map<Set<?>, List<EmiTagKey<?>>> CACHED_TAGS = Maps.newHashMap();
	private static final Map<EmiTagKey<?>, List<?>> TAG_VALUES = Maps.newHashMap();
	private static final Map<Identifier, List<EmiTagKey<?>>> SORTED_TAGS = Maps.newHashMap();
	public static final List<EmiTagKey<?>> TAGS = Lists.newArrayList();
	public static TagExclusions exclusions = new TagExclusions();

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> List<EmiStack> getValues(EmiTagKey<T> key) {
		if (TAG_VALUES.containsKey(key)) {
			EmiRegistryAdapter adapter = ADAPTERS_BY_REGISTRY.get(key.registry());
			if (adapter != null) {
				List<T> values = (List<T>) TAG_VALUES.getOrDefault(key, List.of());
				return values.stream().map(t -> adapter.of(t, EmiPort.emptyExtraData(), 1)).toList();
			}
		}
		return List.of();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> List<EmiStack> getRawValues(EmiTagKey<T> key) {
		if (key.isOf(EmiPort.getBlockRegistry())) {
			return key.stream().map(e -> EmiStack.of((Block) e)).toList();
		}
		EmiRegistryAdapter adapter = ADAPTERS_BY_REGISTRY.get(key.registry());
		if (adapter != null) {
			return key.stream().map(t -> adapter.of(t, EmiPort.emptyExtraData(), 1)).toList();
		}
		return List.of();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> EmiIngredient getIngredient(Class<T> clazz, List<EmiStack> stacks, long amount) {
		Map<T, EmiStack> map = Maps.newHashMap();
		for (EmiStack stack : stacks) {
			if (!stack.isEmpty()) {
				EmiStack existing = map.getOrDefault(stack.getKey(), null);
				if (existing != null && !stack.equals(existing)) {
					return new ListEmiIngredient(stacks, amount);
				}
				map.put((T) stack.getKey(), stack);
			}
		}
		if (map.size() == 0) {
			return EmiStack.EMPTY;
		} else if (map.size() == 1) {
			return map.values().stream().toList().get(0).copy().setAmount(amount);
		}
		EmiRegistryAdapter<T> adapter = (EmiRegistryAdapter<T>) ADAPTERS_BY_CLASS.get(clazz);
		if (adapter == null) {
			return new ListEmiIngredient(stacks, amount);
		}
		Registry<T> registry = adapter.getRegistry();
		List<EmiTagKey<T>> keys = (List<EmiTagKey<T>>) (List) CACHED_TAGS.get(map.keySet());

		if (keys != null) {
			for (EmiTagKey<T> key : keys) {
				List<T> values = key.getList();
				values.forEach(map::remove);
			}
		} else {
			keys = Lists.newArrayList();
			Set<T> original = new HashSet<>(map.keySet());
			for (EmiTagKey<T> key : getTags(registry)) {
				List<T> values = key.getList();
				if (values.size() < 2) {
					continue;
				}
				if (map.keySet().containsAll(values)) {
					values.forEach(map::remove);
					keys.add(key);
				}
				if (map.isEmpty()) {
					break;
				}
			}
			CACHED_TAGS.put((Set) original, (List) keys);
		}

		if (keys == null || keys.isEmpty()) {
			return new ListEmiIngredient(stacks.stream().toList(), amount);
		} else if (map.isEmpty()) {
			if (keys.size() == 1) {
				return tagIngredient(keys.get(0), amount);
			} else {
				return new ListEmiIngredient(keys.stream().map(k -> tagIngredient(k, 1)).toList(), amount);
			}
		} else {
			return new ListEmiIngredient(List.of(map.values().stream().map(i -> i.copy().setAmount(1)).toList(),
					keys.stream().map(k -> tagIngredient(k, 1)).toList())
				.stream().flatMap(a -> a.stream()).toList(), amount);
		}
	}

	private static EmiIngredient tagIngredient(EmiTagKey<?> key, long amount) {
		List<?> list = TAG_VALUES.get(key);
		if (list == null || list.isEmpty()) {
			return EmiStack.EMPTY;
		} else if (list.size() == 1) {
			return new TagEmiIngredient(key, amount).getEmiStacks().get(0).copy().setAmount(amount);
		} else {
			return new TagEmiIngredient(key, amount);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T> List<EmiTagKey<T>> getTags(Registry<T> registry) {
		return (List<EmiTagKey<T>>) (List) SORTED_TAGS.getOrDefault(registry.key().identifier(), List.of());
	}

	/**
	 * A single texture of a tag icon, drawn as the [x, x + width) horizontal slice of a 16x16
	 * texture, at that same slice of the 16x16 slot. A plain single texture icon is one layer
	 * covering the full width.
	 */
	public record TagIconLayer(Identifier texture, int x, int width) {
	}

	/**
	 * The icon to draw for a tag model, or null if the model could not be reduced to flat
	 * textures, in which case callers fall back to rendering the tag's first stack.
	 */
	public static List<TagIconLayer> getTagIcon(Identifier modelId) {
		return modelId == null ? null : TAG_ICONS.get(modelId);
	}

	public static void registerTagModels(ResourceManager manager, Consumer<Identifier> consumer) {
		EmiTags.MODELED_TAGS.clear();
		EmiTags.TAG_ICONS.clear();
		for (Identifier id : EmiPort.findResources(manager, "models/tag", s -> s.endsWith(".json"))) {
			String path = id.getPath();
			path = path.substring(11, path.length() - 5);
			String[] parts = path.split("/");
			if (parts.length > 1) {
				TagKey<?> key = TagKey.create(ResourceKey.createRegistryKey(EmiPort.id("minecraft", parts[0])), EmiPort.id(id.getNamespace(), path.substring(1 + parts[0].length())));
				Identifier mid = EmiPort.id(id.getNamespace(), "tag/" + path);
				EmiTags.MODELED_TAGS.put(key, mid);
				List<TagIconLayer> icon = resolveTagIcon(manager, mid);
				if (icon != null) {
					EmiTags.TAG_ICONS.put(mid, icon);
				}
				consumer.accept(mid);
			}
		}
		/*
		Disable legacy tag models in 1.21+ due to modeling complications
		for (Identifier id : EmiPort.findResources(manager, "models/item/tags", s -> s.endsWith(".json"))) {
			String path = id.getPath();
			path = path.substring(0, path.length() - 5);
			String[] parts = path.substring(17).split("/");
			if (id.getNamespace().equals("emi") && parts.length > 1) {
				Identifier mid = new ModelIdentifier(id.getNamespace(), path.substring(12), "inventory");
				EmiTags.MODELED_TAGS.put(TagKey.of(EmiPort.getItemRegistry().getKey(), EmiPort.id(parts[0], path.substring(18 + parts[0].length()))), mid);
				consumer.accept(mid);
			}
		}
		*/
	}
	
	public static void reload() {
		reloadTagModels();
		EmiTagKey.reload();
		TAGS.clear();
		SORTED_TAGS.clear();
		TAG_VALUES.clear();
		CACHED_TAGS.clear();
		for (Registry<?> registry : ADAPTERS_BY_REGISTRY.keySet()) {
			reloadTags(registry);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> void reloadTags(Registry<T> registry) {
		Set<T> hidden = EmiTagKey.of(registry, HIDDEN_FROM_RECIPE_VIEWERS).getSet();
		Identifier rid = registry.key().identifier();
		List<EmiTagKey<T>> tags = EmiTagKey.fromRegistry(registry)
			.filter(key -> !exclusions.contains(rid, key.id()) && !hidden.containsAll(key.getList()))
			.toList();
		logUntranslatedTags(tags);
		tags = consolodateTags(tags);
		for (EmiTagKey<T> key : tags) {
			List<T> values = key.stream().filter(s -> !EmiHidden.isDisabled(stackFromKey(key, s))).toList();
			if (values.isEmpty()) {
				TAG_VALUES.put(key, key.getList());
			} else {
				TAG_VALUES.put(key, values);
			}
		}
		EmiTags.TAGS.addAll(tags.stream().sorted((a, b) -> a.id().toString().compareTo(b.id().toString())).toList());
		tags = tags.stream()
			.sorted((a, b) -> Long.compare(b.stream().count(), a.stream().count()))
			.toList();
		EmiTags.SORTED_TAGS.put(registry.key().identifier(), (List) tags);
	}

	@SuppressWarnings("unchecked")
	private static <T> EmiStack stackFromKey(EmiTagKey<T> key, T t) {
		EmiRegistryAdapter<T> adapter = (EmiRegistryAdapter<T>) ADAPTERS_BY_REGISTRY.get(key.registry());
		if (adapter != null) {
			return adapter.of(t, EmiPort.emptyExtraData(), 1);
		}
		throw new UnsupportedOperationException("Unsupported tag registry " + key);
	}

	private static <T> void logUntranslatedTags(List<EmiTagKey<T>> tags) {
		if (EmiConfig.logUntranslatedTags) {
			List<String> untranslated = Lists.newArrayList();
			for (EmiTagKey<T> tag : tags) {
				if (!tag.hasTranslation()) {
					untranslated.add(tag.id().toString());
				}
			}
			if (!untranslated.isEmpty()) {
				for (String tag : untranslated.stream().sorted().toList()) {
					EmiReloadLog.warn("Untranslated tag #" + tag);
				}
				EmiReloadLog.info(" Tag warning can be disabled in the config, EMI docs describe how to add a translation or exclude tags.");
			}
		}
	}

	private static <T> List<EmiTagKey<T>> consolodateTags(List<EmiTagKey<T>> tags) {
		Map<Set<T>, EmiTagKey<T>> map = Maps.newHashMap();
		for (int i = 0; i < tags.size(); i++) {
			EmiTagKey<T> key = tags.get(i);
			Set<T> values = key.getSet();
			EmiTagKey<T> original = map.get(values);
			if (original != null) {
				map.put(values, betterTag(key, original));
			} else {
				map.put(values, key);
			}
		}
		return map.values().stream().toList();
	}

	private static<T> EmiTagKey<T> betterTag(EmiTagKey<T> a, EmiTagKey<T> b) {
		if (a.hasTranslation() != b.hasTranslation()) {
			return a.hasTranslation() ? a : b;
		}
		if (a.hasCustomModel() != b.hasCustomModel()) {
			return a.hasCustomModel() ? a : b;
		}
		String an = a.id().getNamespace();
		String bn = b.id().getNamespace();
		if (!an.equals(bn)) {
			if (an.equals("minecraft")) {
				return a;
			} else if (bn.equals("minecraft")) {
				return b;
			} else if (an.equals("c")) {
				return a;
			} else if (bn.equals("c")) {
				return b;
			} else if (an.equals("fabric")) {
				return EmiAgnos.isModLoaded("forge") ? b : a;
			} else if (bn.equals("fabric")) {
				return EmiAgnos.isModLoaded("forge") ? a : b;
			} else if (an.equals("forge")) {
				return EmiAgnos.isModLoaded("forge") ? a : b;
			} else if (bn.equals("forge")) {
				return EmiAgnos.isModLoaded("forge") ? b : a;
			}
		}
		return a.id().toString().length() <= b.id().toString().length() ? a : b;
	}

	/**
	 * Discovers models/tag/&#42;&#42;.json in every namespace and records, for each, the tag it
	 * decorates and the textures to draw for it.
	 * <p>
	 * 1.21 handed the model ids to the loader's model registry and rendered the resulting baked
	 * model. 26.1 has no such registry for standalone models, so the model json is resolved here
	 * down to flat textures instead; see D-R1 in the port decision log.
	 */
	private static void reloadTagModels() {
		try {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			ResourceManager manager = client.getResourceManager();
			if (manager == null) {
				return;
			}
			registerTagModels(manager, id -> {});
		} catch (Throwable t) {
			EmiTags.MODELED_TAGS.clear();
			EmiTags.TAG_ICONS.clear();
			EmiReloadLog.warn("Error discovering tag models", t);
		}
	}

	private static final int MODEL_PARENT_LIMIT = 16;
	// Texture keys worth drawing, best first. layer0 covers item/generated, the rest cover the
	// common block model parents (cube_all, cube_column, slab, stairs, cross...).
	private static final String[] ICON_TEXTURE_KEYS = {"layer0", "all", "south", "side", "texture", "end", "top", "cross", "front", "particle"};

	private static List<TagIconLayer> resolveTagIcon(ResourceManager manager, Identifier modelId) {
		try {
			Map<String, String> textures = new HashMap<>();
			Identifier current = modelId;
			for (int i = 0; i < MODEL_PARENT_LIMIT; i++) {
				JsonObject model = readModel(manager, current);
				if (model == null) {
					// The tag model itself has to exist, but a parent that does not is just the
					// end of the chain: item/generated and friends inherit from builtin/generated,
					// which has no json of its own
					if (i == 0) {
						return null;
					}
					break;
				}
				if (model.get("textures") instanceof JsonObject declared) {
					for (Map.Entry<String, JsonElement> entry : declared.entrySet()) {
						// A child's declaration wins over anything its parents declare
						textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
					}
				}
				JsonElement parent = model.get("parent");
				if (parent == null) {
					break;
				}
				Identifier parentId = Identifier.parse(parent.getAsString());
				List<TagIconLayer> split = splitTagIcon(parentId, textures);
				if (split != null) {
					return split;
				}
				current = parentId;
			}
			String texture = null;
			for (String key : ICON_TEXTURE_KEYS) {
				if (textures.containsKey(key)) {
					texture = resolveTexture(textures, textures.get(key));
					if (texture != null) {
						break;
					}
				}
			}
			if (texture == null) {
				return null;
			}
			return List.of(new TagIconLayer(texturePath(texture), 0, 16));
		} catch (Exception e) {
			EmiReloadLog.warn("Error reading tag model " + modelId, e);
			return null;
		}
	}

	/**
	 * EMI's own multi texture models, which split the slot into vertical strips. The strips match
	 * the element bounds in the model json; a model element's south face defaults to the uv of its
	 * own x range, so each strip shows that same slice of its texture.
	 */
	private static List<TagIconLayer> splitTagIcon(Identifier parentId, Map<String, String> textures) {
		if (!parentId.getNamespace().equals("emi")) {
			return null;
		}
		String[] keys;
		int[] bounds;
		switch (parentId.getPath()) {
			case "item/half_item" -> {
				keys = new String[] {"first", "second"};
				bounds = new int[] {0, 8, 16};
			}
			case "item/third_item" -> {
				keys = new String[] {"first", "second", "third"};
				bounds = new int[] {0, 6, 10, 16};
			}
			case "item/quarter_item" -> {
				keys = new String[] {"first", "second", "third", "fourth"};
				bounds = new int[] {0, 4, 8, 12, 16};
			}
			default -> {
				return null;
			}
		}
		List<TagIconLayer> layers = Lists.newArrayList();
		for (int i = 0; i < keys.length; i++) {
			String texture = resolveTexture(textures, textures.get(keys[i]));
			if (texture == null) {
				return null;
			}
			layers.add(new TagIconLayer(texturePath(texture), bounds[i], bounds[i + 1] - bounds[i]));
		}
		return layers;
	}

	private static JsonObject readModel(ResourceManager manager, Identifier modelId) {
		Identifier path = EmiPort.id(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
		Optional<Resource> resource = manager.getResource(path);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream stream = EmiPort.getInputStream(resource.get())) {
			if (stream == null) {
				return null;
			}
			JsonElement element = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			return element instanceof JsonObject object ? object : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Follows #key texture references until an actual texture identifier is reached.
	 */
	private static String resolveTexture(Map<String, String> textures, String value) {
		for (int i = 0; value != null && value.startsWith("#") && i < MODEL_PARENT_LIMIT; i++) {
			value = textures.get(value.substring(1));
		}
		return value != null && !value.startsWith("#") ? value : null;
	}

	private static Identifier texturePath(String texture) {
		Identifier id = Identifier.parse(texture);
		return EmiPort.id(id.getNamespace(), "textures/" + id.getPath() + ".png");
	}
}
