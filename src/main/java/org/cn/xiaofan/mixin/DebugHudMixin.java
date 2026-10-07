package org.cn.xiaofan.mixin;

import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseSamplingConfig;
import net.minecraft.world.gen.chunk.SlideConfig;
import org.cn.xiaofan.client.NoiseParamsState;
import org.cn.xiaofan.client.NoiseRouterSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 往 F3 调试屏（右栏）追加噪声生成器信息。
 *
 * <p>原版 F3 不显示任何 worldgen 数据，这里补上：采样参数、slide 配置，
 * 以及本模组的 FARLANDS 折叠开关状态。
 *
 * <p>本模组针对单人存档：客户端与集成服务器同 JVM，所以能直接从
 * {@code ServerWorld} 拿到真实的 {@link NoiseChunkGenerator} 与其 settings。
 */
@Mixin(DebugHud.class)
public class DebugHudMixin {

	@Shadow
	@Final
	private MinecraftClient client;

	@Inject(method = "getRightText", at = @At("RETURN"))
	private void noise_gen_debug$appendNoiseInfo(CallbackInfoReturnable<List<String>> cir) {
		List<String> list = cir.getReturnValue();

		list.add("");
		list.add(Formatting.UNDERLINE + "Noise Generator (noise_gen_debug)");

boolean foldingDisabled = NoiseParamsState.getFarLandsMode()
								== org.cn.xiaofan.noise.FarLandsMode.REMOVED;
				list.add("Precision folding: "
								+ (foldingDisabled ? Formatting.RED + "DISABLED" : Formatting.GREEN + "enabled")
								+ Formatting.GRAY + " [" + NoiseParamsState.getFarLandsMode().name() + "]");

		ChunkGeneratorSettings settings = this.noise_gen_debug$getServerSettings();
		if (settings == null) {
			list.add(Formatting.GRAY + "generator: unavailable (not a noise generator?)");
			return;
		}

		GenerationShapeConfig shape = settings.getGenerationShapeConfig();
		NoiseSamplingConfig sampling = shape.getSampling();
		SlideConfig top = shape.getTopSlide();
		SlideConfig bottom = shape.getBottomSlide();

		list.add(String.format("height=%d sizeH=%d sizeV=%d",
				shape.getHeight(), shape.getSizeHorizontal(), shape.getSizeVertical()));
		list.add(String.format("density f=%.5f o=%.5f",
				shape.getDensityFactor(), shape.getDensityOffset()));
		list.add(String.format("sampling xz=%s y=%s fxz=%.1f fy=%.1f",
				NoiseRouterSampler.show(sampling.getXZScale()),
				NoiseRouterSampler.show(sampling.getYScale()),
				sampling.getXZFactor(), sampling.getYFactor()));
		list.add(String.format("topSlide    t=%d s=%d o=%d",
				top.getTarget(), top.getSize(), top.getOffset()));
		list.add(String.format("bottomSlide t=%d s=%d o=%d",
				bottom.getTarget(), bottom.getSize(), bottom.getOffset()));
		list.add(String.format("seaLevel=%d simplex=%s amplified=%s",
				settings.getSeaLevel(),
				shape.hasSimplexSurfaceNoise(),
				shape.isAmplified()));

		// 仿高版本原版 NoiseRouter 行：当前玩家所在点的噪声值。
		this.noise_gen_debug$appendNoiseRouter(list);
	}

	/** 追加「Player Noise」行 —— 1.16.5 上能算出的噪声量。 */
	private void noise_gen_debug$appendNoiseRouter(List<String> list) {
		NoiseChunkGenerator generator = this.noise_gen_debug$getServerGenerator();
		if (generator == null) {
			return;
		}

		BlockPos pos = this.client.player != null
				? this.client.player.getBlockPos()
				: new BlockPos(0, 64, 0);

		NoiseRouterSampler.Sample sample = NoiseRouterSampler.sample(
				generator, pos.getX(), pos.getY(), pos.getZ());
		list.add(Formatting.YELLOW + "Player Noise " + NoiseRouterSampler.format(sample));

		// float 精度降级的验证统计：Inf / NaN / 总采样数。
		long[] stats = org.cn.xiaofan.client.NoisePrecisionStats.snapshot();
		list.add(String.format("FloatPrec %s  Inf=%d NaN=%d total=%d",
				NoiseParamsState.isFloatPrecisionEnabled() ? "32" : "64",
				stats[0], stats[1], stats[2]));
	}

	/** 单人存档下从集成服务器取真实生效的 settings。 */
	private ChunkGeneratorSettings noise_gen_debug$getServerSettings() {
		NoiseChunkGenerator generator = this.noise_gen_debug$getServerGenerator();
		if (generator == null) {
			return null;
		}

		NoiseChunkGeneratorAccessor accessor = (NoiseChunkGeneratorAccessor) (Object) generator;
		java.util.function.Supplier<ChunkGeneratorSettings> supplier = accessor.noise_gen_debug$getSettings();
		return supplier == null ? null : supplier.get();
	}

	/** 从集成服务器取主世界噪声生成器；不是噪声生成器则返回 null。 */
	private NoiseChunkGenerator noise_gen_debug$getServerGenerator() {
		if (this.client.world == null) {
			return null;
		}

		IntegratedServer server = this.client.getServer();
		if (server == null) {
			return null;
		}

		ServerWorld serverWorld = server.getWorld(this.client.world.getRegistryKey());
		if (serverWorld == null) {
			return null;
		}

		ChunkGenerator generator = serverWorld.getChunkManager().getChunkGenerator();
		return generator instanceof NoiseChunkGenerator ? (NoiseChunkGenerator) generator : null;
	}
}
