package org.cn.xiaofan.mixin.border;

import net.minecraft.entity.player.PlayerEntity;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 放开玩家位置的 xz 夹取范围。
 *
 * <p>原版 {@code PlayerEntity.tick()} 会把玩家坐标夹在 ±{@code 2.9999999E7}。
 * 这里改成 double 全范围，否则走到约 3000 万格就被硬性夹住。
 *
 * <p>参考 geniiii/FarLands 的实现（MIT）。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityPositionMixin {

	@ModifyConstant(constant = @Constant(doubleValue = -2.9999999E7D), method = "tick")
	private static double noise_gen_debug$clampNegative(double original) {
		return NoiseParamsState.isWorldBorderDisabled() ? -Double.MAX_VALUE : original;
	}

	@ModifyConstant(constant = @Constant(doubleValue = 2.9999999E7D), method = "tick")
	private static double noise_gen_debug$clampPositive(double original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Double.MAX_VALUE : original;
	}
}
