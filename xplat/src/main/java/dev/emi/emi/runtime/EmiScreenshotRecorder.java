package dev.emi.emi.runtime;

import java.io.File;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.emi.emi.EmiPort;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.mixin.accessor.GameRendererAccessor;
import dev.emi.emi.mixin.accessor.MinecraftAccessor;

public class EmiScreenshotRecorder {
	private static final String SCREENSHOTS_DIRNAME = "screenshots";
	// A render target of width * height * scale^2 pixels is allocated per screenshot, so the
	// configured scale is bounded rather than trusted
	private static final int MIN_SCALE = 1;
	private static final int MAX_SCALE = 8;

	public static void saveScreenshot(String path, int width, int height, Consumer<GuiGraphicsExtractor> renderer) {
		if (!RenderSystem.isOnRenderThread()) {
			Minecraft.getInstance().execute(() -> saveScreenshotInner(path, width, height, renderer));
		} else {
			saveScreenshotInner(path, width, height, renderer);
		}
	}

	/**
	 * Renders one standalone GUI frame containing only the recipe, into an offscreen target, and
	 * reads it back.
	 * <p>
	 * 1.21 could simply bind a framebuffer and draw immediately. 26.1 extracts the GUI into a
	 * {@link GuiRenderState} first and only {@link GuiRenderer} can turn that state into pixels,
	 * so the recipe is extracted into vanilla's own render state (which sits empty between frames,
	 * and which {@code GuiRenderer.render} resets again when it is done) and vanilla's
	 * {@code GuiRenderer} is run over it. That renderer always draws into
	 * {@code Minecraft.getMainRenderTarget} and takes its projection and scissor rectangles from
	 * the window render state, so both are pointed at the screenshot target for the duration of
	 * the render and restored afterwards. See D-R6 in the port decision log.
	 */
	private static void saveScreenshotInner(String path, int width, int height, Consumer<GuiGraphicsExtractor> renderer) {
		Minecraft client = Minecraft.getInstance();

		int scale;
		if (EmiConfig.recipeScreenshotScale < 1) {
			scale = EmiPort.getGuiScale(client);
		} else {
			scale = EmiConfig.recipeScreenshotScale;
		}
		scale = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);

		GameRenderer gameRenderer = client.gameRenderer;
		if (gameRenderer == null) {
			EmiLog.error("Cannot take a recipe screenshot before the game renderer exists");
			return;
		}
		GuiRenderer guiRenderer = ((GameRendererAccessor) gameRenderer).emi$getGuiRenderer();
		FogRenderer fogRenderer = ((GameRendererAccessor) gameRenderer).emi$getFogRenderer();
		GameRenderState gameState = gameRenderer.getGameRenderState();
		if (guiRenderer == null || fogRenderer == null || gameState == null) {
			EmiLog.error("Cannot take a recipe screenshot, vanilla's GUI renderer is unavailable");
			return;
		}
		GuiRenderState state = gameState.guiRenderState;
		WindowRenderState window = gameState.windowRenderState;

		RenderTarget framebuffer;
		try {
			framebuffer = new TextureTarget("EMI Screenshot", width * scale, height * scale, true);
		} catch (Throwable t) {
			EmiLog.error("Could not allocate a render target for the recipe screenshot", t);
			return;
		}
		GpuTexture colorTexture = framebuffer.getColorTexture();
		if (colorTexture == null) {
			framebuffer.destroyBuffers();
			EmiLog.error("Could not allocate a render target for the recipe screenshot");
			return;
		}

		RenderTarget mainTarget = client.getMainRenderTarget();
		int windowWidth = window.width;
		int windowHeight = window.height;
		int windowGuiScale = window.guiScale;
		// Changing the gui scale invalidates the GUI item atlas, so it is only touched when the
		// screenshot actually wants a different one
		boolean guiScaleChanged = windowGuiScale != scale;
		GpuBufferSlice backupProj = RenderSystem.getProjectionMatrixBuffer();
		ProjectionType backupProjType = RenderSystem.getProjectionType();
		boolean previousUiLightmap = ((GameRendererAccessor) gameRenderer).emi$getUseUiLightmap();
		boolean rendered = false;
		try {
			state.reset();
			renderer.accept(new GuiGraphicsExtractor(client, state, 0, 0));

			((MinecraftAccessor) client).emi$setMainRenderTarget(framebuffer);
			window.width = framebuffer.width;
			window.height = framebuffer.height;
			if (guiScaleChanged) {
				window.guiScale = scale;
			}

			// GuiRenderer never clears, and unlike the main target this one is cleared to a fully
			// transparent black so that the recipe keeps its transparent background
			RenderSystem.getDevice().createCommandEncoder()
				.clearColorAndDepthTextures(colorTexture, 0, framebuffer.getDepthTexture(), 1.0);

			// GuiRenderer draws with whatever lightmap GameRenderer.lightmap() hands out, which is
			// the world's unless the flat UI one is selected. GameRenderer.render sets both of
			// these around its own GUI pass; outside it, items would be lit by the player's
			// surroundings instead
			gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
			((GameRendererAccessor) gameRenderer).emi$setUseUiLightmap(true);

			guiRenderer.render(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
			guiRenderer.endFrame();
			rendered = true;
		} catch (Throwable t) {
			EmiLog.error("Failed to render recipe screenshot", t);
		} finally {
			((GameRendererAccessor) gameRenderer).emi$setUseUiLightmap(previousUiLightmap);
			((MinecraftAccessor) client).emi$setMainRenderTarget(mainTarget);
			window.width = windowWidth;
			window.height = windowHeight;
			if (guiScaleChanged) {
				window.guiScale = windowGuiScale;
			}
			RenderSystem.setProjectionMatrix(backupProj, backupProjType);
			state.reset();
		}

		if (rendered) {
			saveScreenshotInner(client.gameDirectory, path, framebuffer,
				message -> client.execute(() -> client.gui.getChat().addClientSystemMessage(message)));
		}
		// The readback above only issues the copy; it reads from the buffer it filled, never from
		// the texture again, so the target can go away immediately
		framebuffer.destroyBuffers();
	}

	private static void saveScreenshotInner(File gameDirectory, String suggestedPath, RenderTarget framebuffer, Consumer<Component> messageReceiver) {
		takeScreenshot(framebuffer, nativeImage -> {
			File screenshots = new File(gameDirectory, SCREENSHOTS_DIRNAME);
			screenshots.mkdir();

			String filename = getScreenshotFilename(screenshots, suggestedPath);
			File file = new File(screenshots, filename);

			File parent = file.getParentFile();
			parent.mkdirs();

			Util.ioPool().execute(() -> {
				try {
					nativeImage.writeToFile(file);

					Component text = EmiPort.literal(filename,
						Style.EMPTY.withUnderlined(true).withClickEvent(new ClickEvent.OpenFile(file.getAbsoluteFile())));
					messageReceiver.accept(EmiPort.translatable("screenshot.success", text));
				} catch (Throwable e) {
					EmiLog.error("Failed to write screenshot", e);
					messageReceiver.accept(EmiPort.translatable("screenshot.failure", e.getMessage()));
				} finally {
					nativeImage.close();
				}
			});
		});
	}

	private static void takeScreenshot(RenderTarget framebuffer, Consumer<NativeImage> consumer) {
		int i = framebuffer.width;
		int j = framebuffer.height;
		GpuTexture gputexture = framebuffer.getColorTexture();
		if (gputexture == null) {
			return;
		}
		int usage = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
		// The readback is run as a fenced task, by which point the render target this texture
		// belongs to has been destroyed, so nothing about it may be looked up from inside it
		int pixelSize = gputexture.getFormat().pixelSize();
		GpuBuffer gpubuffer = RenderSystem.getDevice()
			.createBuffer(() -> "EMI Screenshot buffer", usage, i * j * pixelSize);
		CommandEncoder commandencoder = RenderSystem.getDevice().createCommandEncoder();
		commandencoder.copyTextureToBuffer(gputexture, gpubuffer, 0, () -> {
			try (GpuBuffer.MappedView mappedview = commandencoder.mapBuffer(gpubuffer, true, false)) {
				NativeImage nativeimage = new NativeImage(i, j, false);

				for (int i1 = 0; i1 < j; i1++) {
					for (int j1 = 0; j1 < i; j1++) {
						int k1 = mappedview.data().getInt((j1 + i1 * i) * pixelSize);
						// Unlike vanilla's screenshot of the main framebuffer, this target is
						// cleared to (0, 0, 0, 0), so the alpha read back is meaningful and is
						// kept to preserve the recipe's transparent background
						nativeimage.setPixelABGR(j1, j - i1 - 1, k1);
					}
				}

				consumer.accept(nativeimage);
			}

			gpubuffer.close();
		}, 0);
	}

	private static String getScreenshotFilename(File directory, String path) {
		int i = 1;
		while ((new File(directory, path + (i == 1 ? "" : "_" + i) + ".png")).exists()) {
			++i;
		}
		return path + (i == 1 ? "" : "_" + i) + ".png";
	}
}
