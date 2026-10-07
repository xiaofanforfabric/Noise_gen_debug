package org.cn.xiaofan.mixin.border;

import net.minecraft.entity.Entity;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 放开 {@link Entity#updatePosition(double, double, double)} 里的坐标夹取。
 *
 * <p><b>这是"被拉回 30000000"的真正元凶。</b>
 *
 * <p>{@code Entity.updatePosition} 每次实体移动都会被调用，里面有：
 * <pre>
 * double x = MathHelper.clamp(x, -3.0E7, 3.0E7);
 * double z = MathHelper.clamp(z, -3.0E7, 3.0E7);
 * </pre>
 * 玩家继承自 {@code Entity}，所以一旦超过 ±3000 万，**每走一步都会被强行夹回来**，
 * 表现为"被拉住"而不是"被墙挡住"。
 *
 * <p>{@code PlayerEntity.tick} 里还有另一处 ±{@code 2.9999999E7} 的夹取
 * （见 {@link PlayerEntityPositionMixin}），但这个方法权限更高、调用更频繁。
 *
 * <p>4 个常量（x/z 各一对）都要替换，用 {@code @ModifyConstant} 按值匹配即可
 * 一次覆盖全部出现。
 */
@Mixin(Entity.class)
public abstract class EntityPositionMixin {

	@ModifyConstant(constant = @Constant(doubleValue = -3.0E7D), method = "updatePosition")
	private double noise_gen_debug$clampNegative(double original) {
		return NoiseParamsState.isWorldBorderDisabled() ? -Double.MAX_VALUE : original;
	}

	@ModifyConstant(constant = @Constant(doubleValue = 3.0E7D), method = "updatePosition")
	private double noise_gen_debug$clampPositive(double original) {
		return NoiseParamsState.isWorldBorderDisabled() ? Double.MAX_VALUE : original;
	}
}
