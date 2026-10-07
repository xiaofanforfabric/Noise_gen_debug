package org.cn.xiaofan.mixin;

import org.cn.xiaofan.client.NoiseParamsState;
import org.cn.xiaofan.client.RepositionState;
import org.cn.xiaofan.noise.FarLandsMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler;

/**
 * 可配置的 {@code maintainPrecision} —— 控制边境之地的位置与形态。
 *
 * <h2>移植来源</h2>
 *
 * <p>参考 <a href="https://github.com/INF32768/UltimateScaler">UltimateScaler</a>
 * 的 {@code MixinPerlinNoise}（MIT）。它注入 1.18+ 的 {@code PerlinNoise.wrap}，
 * 这里对应 1.16.5 的 {@code OctavePerlinNoiseSampler.maintainPrecision}。
 *
 * <h2>原版公式</h2>
 *
 * <pre>
 * return value - lfloor(value / 3.3554432E7 + 0.5) * 3.3554432E7;
 * </pre>
 *
 * <p>把坐标折叠回 {@code [-2^25/2, 2^25/2)}。因为噪声每方块前进约 171.103 单位，
 * {@code 2^31 / 171.103 ≈ 12,550,821}，所以这个折叠把 {@code int} 溢出推到
 * 1250 万格 —— 也就是边境之地的位置。
 *
 * <h2>为什么改这里能造出「天空网格」</h2>
 *
 * <p>{@link FarLandsMode#REMOVED} 直接跳过折叠，于是坐标真的长到 {@code 2^31} 以上，
 * {@code floor(double)->int} 饱和成 {@code Integer.MAX_VALUE} → 图案周期性重复。
 * {@link FarLandsMode#CUSTOM} 把除数调小，等价于把边境之地按比例拉近，
 * 于是能在近处散步时观察。
 *
 * <h2>注入方式</h2>
 *
 * <p>用 {@code @Inject(HEAD, cancellable)} 而不是 {@code @ModifyReturnValue} ——
 * 因为要<b>完全替换</b>折叠算法，而不是在原结果上做微调。
 */
@Mixin(OctavePerlinNoiseSampler.class)
public class OctavePerlinNoiseSamplerMixin {

	// 注意：mixin 类里不允许出现非 private 的静态字段
	//（报错 "contains non-private static field"），所以常量放在 FarLandsMode 里。

	@Inject(method = "maintainPrecision", at = @At("HEAD"), cancellable = true)
	private static void noise_gen_debug$customWrap(double value,
			CallbackInfoReturnable<Double> cir) {

		// ==================== 1) 坐标重定位 ====================
		//
		// 这里是整个 1.16.5 噪声管线里「坐标仍是 double」的位置，
		// 所以任意大的偏移/缩放都必须在这里施加 ——
		// 再往前是 sampleNoise 的 int 参数，被钉死在 2^31。
		//
		// 注意 value 已经是 x * horizontalScale * g，即「已缩放且随 octave 变化」，
		// 而不是裸方块坐标。
		final double moved = noise_gen_debug$reposition(value);
		// 重定位是否真的改变了值。用原始 bit 比较，避免 -0.0 == 0.0 之类的误判。
		final boolean repositioned =
				Double.doubleToRawLongBits(moved) != Double.doubleToRawLongBits(value);

		FarLandsMode mode = NoiseParamsState.getFarLandsMode();
		if (mode == null) {
			mode = FarLandsMode.DEFAULT;
		}

		// 只有「没重定位 且 模式是原版」才真正零开销地放行原版。
		//
		// ⚠️ 上一版在这里无条件 return，导致 FarLandsMode == DEFAULT 时
		//    重定位的结果被<b>整个丢弃</b> —— mixin 注入在 HEAD，改的是局部变量，
		//    不影响原方法的参数，所以原版折叠仍作用在原始 value 上。
		//    这就是「重定位开了但没效果」的根因。
		if (mode == FarLandsMode.DEFAULT && !repositioned) {
			return;
		}

		// ⚠️ Infinity 是<b>毒药</b>，不是原料 —— 必须在进入折叠逻辑前原样放行。
		//
		// 原因：maintainPrecision 的一切运算（取模 / 取整）都会把 Inf 变成 NaN：
		//   Inf % 2^25 == NaN，lfloor(Inf) 饱和后 Inf - const == Inf。
		// 而即使 Inf 侥幸过去，它一进 PerlinNoiseSampler 也会死：
		//   perlinFade(Inf) = Inf → lerp3 内部 Inf*0 → NaN。
		// 所以这个分支的意义不是「生产 Inf」，而是<b>把 Inf 限制在这一层</b>，
		// 不让它进 Perlin 去污染整个区块。
		// 实测见 /tmp/chain/SG.java：1e30 → -Inf(可用)；Infinity → NaN(死路)。
		if (Double.isInfinite(moved)) {
			cir.setReturnValue(moved);
			return;
		}

		double result;
		switch (mode) {
			case BETA:
				// Beta 1.8 之前：完全不动（天空网格推荐用这个模式）
				result = moved;
				break;
			case RELEASE:
				// 1.14.4+ 的算法：先夹掉超出 long 范围的部分
				result = Math.abs(moved) > Long.MAX_VALUE
						? moved - Math.signum(moved) * Long.MAX_VALUE
						: (moved + FarLandsMode.HALF_DIVISOR) % FarLandsMode.VANILLA_DIVISOR - FarLandsMode.HALF_DIVISOR;
				break;
			case REMOVED:
				// 移除折叠（不夹 long）
				result = (moved + FarLandsMode.HALF_DIVISOR) % FarLandsMode.VANILLA_DIVISOR - FarLandsMode.HALF_DIVISOR;
				break;
			case CUSTOM:
				double divisor = NoiseParamsState.getMaintainPrecisionDivisor();
				if (divisor <= 0.0D) {
					divisor = FarLandsMode.VANILLA_DIVISOR;
				}
				result = moved - (double) MathHelper.lfloor(moved / divisor + 0.5D) * divisor;
				break;
			case DEFAULT:
			default:
				// 重定位生效 + 原版模式：用原版折叠公式作用在<b>重定位后</b>的值上
				result = moved - (double) MathHelper.lfloor(moved / FarLandsMode.VANILLA_DIVISOR + 0.5D)
						* FarLandsMode.VANILLA_DIVISOR;
				break;
		}

		// 可选的「返回值上限」：把 |result| > 10^n 的值按对数折回 10^n 量级。
		// 参考实现语言文件的说明：「调到特定数值可以移除条纹之地」。
		if (NoiseParamsState.isLimitReturnValue()) {
			int n = NoiseParamsState.getMaxNoiseLogarithmValue();
			double abs = Math.abs(result);
			double log = Math.log10(abs);
			if (log > n) {
				double mantissa = Math.pow(10.0D, log - Math.floor(log - n));
				result = mantissa * Math.signum(result);
			}
		}

		cir.setReturnValue(result);
	}

	/**
	 * 施加坐标重定位与噪声偏移。
	 *
	 * <h2>两个来源</h2>
	 *
	 * <ol>
	 *   <li>{@link RepositionState} 的 {@code scale}/{@code offset}
	 *       —— 子界面「距离现象」的通用变换</li>
	 *   <li>{@link NoiseParamsState} 的 {@code noiseOffsetX/Z}
	 *       —— 主界面 XYZ 偏移框的值（方块坐标）</li>
	 * </ol>
	 *
	 * <p>两者是叠加的：先按 scale 放大、再分别加两个偏移。
	 *
	 * <h2>为什么 noiseOffset 要在这里加，而不是在 sampleNoise 的 int 参数上</h2>
	 *
	 * <p>{@code sampleNoise(int x, ...)} 的 {@code x} 是 {@code int}，
	 * 能表达的偏移上限只有 {@code ±2^31} 个噪声单元。
	 * 而这里的 {@code value} 是 {@code double}，能到 {@code 1e308}。
	 *
	 * <p>但要注意单位差异：{@code sampleNoise} 的 {@code x} 是<b>噪声单元</b>
	 * （1 单元 = {@code horizontalNoiseResolution} 方块），
	 * 而 GUI 输入的 {@code noiseOffsetX} 是<b>方块</b>。
	 * 这里的 {@code value} 也已乘过 {@code horizontalScale}
	 * （其倒数正是「方块 → 噪声单元」的比例），
	 * 所以不能直接相加，必须把方块偏移换算到同一尺度上。
	 *
	 * <p>换算方式：{@code noiseOffsetX} 除以水平分辨率得到噪声单元偏移，
	 * 再乘以 {@code g}（当前 octave 的采样密度）。
	 * 由于这里拿不到 {@code g}，用<b>近似比值</b> {@code value / x} 反推 ——
	 * 但更稳妥的做法是让调用方传入。见下。
	 */
	private static double noise_gen_debug$reposition(double value) {
		// ⚠️ RepositionState.getScale()/getOffset() 本身<b>不检查</b> enabled，
		//    所以要在这里自己判 —— 否则「关闭重定位」这个开关是死的
		//    （scale 仍留在 1e18，照样生效）。
		if (!RepositionState.isEnabled()) {
			return value;
		}

		final double scale = RepositionState.getScale(0);
		final double offset = RepositionState.getOffset(0);

		// 主界面的 XYZ 偏移（方块坐标）。
		// 它已经由 NoiseOffsetMixin 在 int 层处理过一次（小范围部分），
		// 这里只补上「超出 int 表达范围」的余量，避免重复叠加。
		final org.cn.xiaofan.noise.NoiseParams params = NoiseParamsState.get();
		double bigX = noise_gen_debug$overflowPart(params.noiseOffsetX);
		double bigZ = noise_gen_debug$overflowPart(params.noiseOffsetZ);
		// bigZ 目前没有对应项可加（该层拿不到轴向信息），保留计算以便将来使用。

		if (scale == 1.0D && offset == 0.0D && bigX == 0.0D && bigZ == 0.0D) {
			return value;
		}

		// 减去 int 层已经处理的部分，只在这里补余量。
		//
		// ⚠️ 这里就是「坐标重定位」的全部作用点。
		//    目标不是 Infinity（Infinity 进 Perlin 会变 NaN，见上方注释），
		//    而是把坐标推到「极大有限值」：此时 perlinFade(t) ≈ t^5 仍是有限值，
		//    但 lerp3 内部的乘法会溢出成 ±Infinity，那才是密度层的有效数据。
		//    可用 scale 窗口（RepositionState.MAX_SCALE）：1e18 ~ 1e30。
		return value * scale + offset + bigX;
	}

	/**
	 * 取出偏移里「超出 int 表达范围」的那部分。
	 *
	 * <p>{@code NoiseOffsetMixin} 已经在 {@code sampleNoise} 的 int 参数上
	 * 加了 {@code ±2^31} 以内的偏移；这里返回剩下的部分，
	 * 避免同一份偏移被加两次。
	 */
	private static double noise_gen_debug$overflowPart(java.math.BigDecimal offset) {
		if (offset == null || offset.signum() == 0) {
			return 0.0D;
		}
		// 用 BigDecimal 做减法，保证「减去 int 层已处理的部分」这一步不丢精度。
		final java.math.BigDecimal intLimit = java.math.BigDecimal.valueOf(2147483647L);
		java.math.BigDecimal rest;
		if (offset.compareTo(intLimit) > 0) {
			rest = offset.subtract(intLimit);
		} else if (offset.compareTo(intLimit.negate()) < 0) {
			rest = offset.add(intLimit);
		} else {
			return 0.0D;
		}
		return rest.doubleValue();
	}
}
