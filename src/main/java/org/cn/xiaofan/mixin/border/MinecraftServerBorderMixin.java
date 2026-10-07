package org.cn.xiaofan.mixin.border;

import net.minecraft.server.MinecraftServer;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 世界边界半径上限提升。
 *
 * <p>原版 {@code getMaxWorldBorderRadius()} 返回写死的 {@code 29999984}，
 * 导致 {@code /worldborder set} 无法超过约 3000 万格，走不到 FARLANDS。
 *
 * <p>参考 geniiii/FarLands 的实现（MIT）。
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerBorderMixin {

	@ModifyConstant(constant = @Constant(intValue = 29999984), method = "getMaxWorldBorderRadius")
	private int noise_gen_debug$maxRadius(int original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Integer.MAX_VALUE : original;
	}
}
