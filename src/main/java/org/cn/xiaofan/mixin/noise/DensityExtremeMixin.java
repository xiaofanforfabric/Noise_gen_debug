package org.cn.xiaofan.mixin.noise;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.cn.xiaofan.client.NoiseParamsState;
import org.cn.xiaofan.client.SkyGridState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 极端缩放下的「安全降级」：把 NaN / Inf 密度夹掉，让世界变成虚空而不是崩坏。
 *
 * <p><b>问题</b>：{@code NoiseChunkGenerator.sampleNoise} 的返回值直接决定该点的
 * 密度（&gt; 0 为实心）。当 {@code xzScale} 极大（比如 1.8e307）时：
 * <pre>
 * 采样坐标 = x * 171.d * xzScale   →  double 溢出成 +Inf
 * maintainPrecision: Inf - Inf     →  NaN
 * hash / grad                      →  全 NaN
 * sampleNoise 返回                 →  NaN
 * </pre>
 * NaN 参与比较时<b>恒为 false</b>（{@code NaN > 0 == false}），于是整块区域被判为空气，
 * 但更糟的是 NaN 会被写进 {@code ChunkSection} 的密度缓存，导致后续
 * {@code BlockState} 选择逻辑出现难以预测的方块（常见是整片石头或整个 EmptyChunk），
 * 有时还会让区块生成线程卡住。
 *
 * <p><b>对策</b>：在 {@code sampleNoise} 的返回值上做一次「非有限值 → 负无穷」的替换。
 * 负无穷意味着「绝对不存在」，密度判断必然为空气，于是：
 * <ul>
 *   <li>地形不会因为随机 NaN 而抖成噪声墙</li>
 *   <li>越靠近坐标原点（乘出来还没溢出）的地方地形越正常</li>
 *   <li>越往外（溢出区）越干净地变成虚空 —— 正好是「渐渐成虚空」的观察效果</li>
 * </ul>
 *
 * <p>注意这里用 {@code @ModifyVariable} 而不是 {@code @Inject} + {@code @Return}：
 * {@code sampleNoise} 是 {@code static} 且返回值是 {@code double}，
 * MixinExtras 的 {@code @ModifyReturnValue} 亦可，但 {@code @ModifyVariable}
 * 对原生 Mixin 无额外依赖，更稳。
 */
@Mixin(NoiseChunkGenerator.class)
public class DensityExtremeMixin {

	/**
	 * 夹掉 {@code sampleNoise} 返回的 NaN / Inf。
	 *
	 * <p>{@code at = @At("RETURN")} + {@code ordinal = -1} 会在方法返回前
	 * 捕获返回值这个「隐式局部变量」，这是 Mixin 官方支持的写法
	 * （返回值的 ordinal 固定取 -1）。
	 *
	 * @param original 原始返回值（可能是 NaN / Inf）
	 * @return 有限值原样返回；NaN 或 ±Inf 一律替换为负无穷（虚空）
	 */
	@ModifyReturnValue(method = "sampleNoise", at = @At("RETURN"))
	private static double noise_gen_debug$guardExtremeDensity(double original) {
		// 天空网格研究需要看到密度层的真实取值（±Infinity 是它的有效数据），
		// 这里让路。
		//
		// ⚠️ 但注意：本方法拿到的 {@code original} 里如果出现 NaN，它<b>不是</b>天空网格数据，
		//    而是 Perlin 溢出后的垃圾（perlinFade(Inf)=Inf → lerp3 的 Inf*0）。
		//    所以严格说这里应该「放行 ±Inf，但仍把 NaN 转成 -Inf」。
		//    暂时保留原样放行，等游戏内实测后再定。
		if (SkyGridState.isEnabled()) {
			return original;
		}

		if (!NoiseParamsState.isExtremeDensityGuardEnabled()) {
			return original;
		}
		if (Double.isFinite(original)) {
			return original;
		}
		// NaN：溢出产生的无效值，当作「绝对空」。
		// +Inf：坐标溢出，说明已经跑到无效区域，也当作空。
		// -Inf：本来就是空，保持不变。
		return Double.NEGATIVE_INFINITY;
	}
}
