package org.cn.xiaofan.mixin.border;

import net.minecraft.world.border.WorldBorder;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 读档时忽略存档里保存的世界边界尺寸。
 *
 * <p>根因：世界边界值会随 {@code level.dat} 一起保存。加载世界时
 * {@code MinecraftServer.createWorlds(...)} 会调用
 * {@code worldBorder.load(properties.getWorldBorder())}，而
 * {@code WorldBorder.load} 内部会把存档里的 size 设回去：
 *
 * <pre>
 * // 有插值目标时
 * this.interpolateSize(props.getSize(), props.getTargetSize(), props.getTargetRemainingTime());
 * // 否则
 * this.setSize(props.getSize());
 * </pre>
 *
 * <p>这会**覆盖**我们在 {@code World} 构造时设的 {@code Double.MAX_VALUE}，
 * 于是那面"光幕墙"仍然停留在存档里记录的旧位置（约 ±30000000）。
 *
 * <p>对策：把两次 {@code Properties.getSize()} 都重定向为 {@code Double.MAX_VALUE}。
 */
@Mixin(WorldBorder.class)
public abstract class WorldBorderLoadMixin {

	/** {@code load()} 里第一处 {@code getSize()} —— 传给 {@code interpolateSize}。 */
	@Redirect(
			method = "load",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/border/WorldBorder$Properties;getSize()D",
					ordinal = 0))
	private double noise_gen_debug$overrideSize0(WorldBorder.Properties properties) {
		return NoiseParamsState.isWorldBorderDisabled() ? Double.MAX_VALUE : properties.getSize();
	}

	/** {@code load()} 里第二处 {@code getSize()} —— 传给 {@code setSize}。 */
	@Redirect(
			method = "load",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/border/WorldBorder$Properties;getSize()D",
					ordinal = 1))
	private double noise_gen_debug$overrideSize1(WorldBorder.Properties properties) {
		return NoiseParamsState.isWorldBorderDisabled() ? Double.MAX_VALUE : properties.getSize();
	}

	/**
	 * 插值目标尺寸也要一起处理。
	 *
	 * <p>若存档正好处于「边界正在插值变化」的状态，
	 * {@code interpolateSize(size, targetSize, time)} 会在之后慢慢把边界
	 * 从 size 拉到 targetSize。只改 size 不改 targetSize 会出现边界回缩。
	 */
	@Redirect(
			method = "load",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/border/WorldBorder$Properties;getTargetSize()D"))
	private double noise_gen_debug$overrideTargetSize(WorldBorder.Properties properties) {
		return NoiseParamsState.isWorldBorderDisabled() ? Double.MAX_VALUE : properties.getTargetSize();
	}
}
