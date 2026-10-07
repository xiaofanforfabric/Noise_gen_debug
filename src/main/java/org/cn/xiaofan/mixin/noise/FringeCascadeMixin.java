/*
 * 许可例外：本文件派生自 FarLandsTraveler
 * (https://github.com/SmallmanSeries/FarLandsTraveler)，该项目以 LGPL-3.0 发布。
 * 因此本文件按 LGPL-3.0-only 发布，【不适用】本仓库根部的 MIT 条款。
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.cn.xiaofan.mixin.noise;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler;
import net.minecraft.util.math.noise.PerlinNoiseSampler;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cn.xiaofan.client.SkyGridState;
import org.cn.xiaofan.noise.FringeCascade;
import org.cn.xiaofan.noise.FringeNoise;
import org.cn.xiaofan.noise.NoiseGenRegistry;
import org.cn.xiaofan.noise.NoiseProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「边缘之地」基岩噪声级联 —— 天空网格的真正实现。
 *
 * <h2>为什么在 {@code sampleNoise} 上做</h2>
 *
 * <p>{@code NoiseChunkGenerator.sampleNoise(int x, int y, int z, double hs, double vs,
 * double hst, double vst)} 就是 1.18+ 的
 * {@code BlendedNoise.compute(FunctionContext)} 在 1.16.5 里的等价物
 * （FLT 那个类的名字 {@code old_blended_noise_customizable} 正是这么来的）。
 * 这里把它整段接管，理由：
 *
 * <ol>
 *   <li>需要按盒子逐块切换 scale / factor / 偏移 —— 原版只有一组参数，<b>必须重算</b>；</li>
 *   <li>需要按盒子逐块做坐标折叠（{@code repeat}）—— 那是在<b>方块坐标</b>上做的，
 *       而 {@code maintainPrecision} 那一层只能看到已缩放的 double，拿不到方块坐标。</li>
 * </ol>
 *
 * <h2>忠实度说明（必读）</h2>
 *
 * <table border="1">
 *   <tr><th>FLT 参数</th><th>本实现的处理</th></tr>
 *   <tr><td>{@code x_scale / y_scale / z_scale}</td><td>✅ 逐盒生效（z 单独一条通路）</td></tr>
 *   <tr><td>{@code x_factor / y_factor / z_factor}</td><td>✅ 逐盒生效</td></tr>
 *   <tr><td>{@code x_shift / y_shift / z_shift}</td><td>✅ 方块坐标上加，无取整误差</td></tr>
 *   <tr><td>{@code repeat_start/length/count}</td><td>✅ 在方块坐标上折叠（网格来源）</td></tr>
 *   <tr><td>{@code overflowable}</td><td>✅ 跳过 {@code maintainPrecision}</td></tr>
 *   <tr><td>{@code smear_scale_multiplier}</td><td>⚠️ <b>未实现</b>：1.16.5 的
 *       {@code sampleNoise} 里没有这个参数（旧版混合噪声把它编译进了
 *       {@code verticalScale}）；且所有盒子都填 8.0，属固定值。</td></tr>
 *   <tr><td>盒子判定中的 Y 轴</td><td>⚠️ <b>忽略</b>：所有盒子的 Y 范围都是
 *       {@code [-25101648, +25101649)}，覆盖了 MC 全部可能高度。</td></tr>
 * </table>
 */
@Mixin(NoiseChunkGenerator.class)
public abstract class FringeCascadeMixin {

	@Shadow
	@Final
	private int horizontalNoiseResolution;

	@Shadow
	@Final
	private int verticalNoiseResolution;

	@Shadow
	@Final
	private OctavePerlinNoiseSampler lowerInterpolatedNoise;

	@Shadow
	@Final
	private OctavePerlinNoiseSampler upperInterpolatedNoise;

	@Shadow
	@Final
	private OctavePerlinNoiseSampler interpolationNoise;

	// ==================== 临时诊断（调好后删） ====================
	//
	// 在 latest.log 里打前缀 [NGD-Fringe] 即可查。
	// 目的：一次区分「没进级联 / 进了但节点不对 / 节点对了但值不对」。
	private static final Logger NGD_LOG = LogManager.getLogger("noise_gen_debug/fringe");
	private static int ngdCallCount;
	private static int ngdFoldLogs;
	private static int ngdComputeLogs;
	private static int ngdDegenerateLogs;

	/** 折叠是否真的在起作用（两轴都超过 repeatStart）。 */
	private static boolean ngdFoldActive(int spec, int blockX, int blockZ) {
		if (!FringeCascade.repeating(spec)) {
			return false;
		}
		final int rs = FringeCascade.rStart(spec);
		return Math.abs(blockX) >= rs && Math.abs(blockZ) >= rs;
	}

	// ---- 噪声生成器：八度失效日志（每个「失效层数」只打一条） ----

	private static final Set<String> ngdDeadOctaveLogged = new HashSet<String>();

	/**
	 * 「八度逐层失效」是这些非常规生成器的共同距离现象：
	 * 八度 0 坐标最大 ⇒ 最先失效，此后每 2 倍距离再失效一个。
	 * 不同生成器的失效含义不同（静默归零 / 天文数字爆炸），见
	 * {@link NoiseProvider#degradedOctaves}。
	 */
	private static void noise_gen_debug$logOctaveDeath(NoiseProvider provider,
			int blockX, int blockY, int blockZ, int hres, int vres) {
		final int d = provider.degradedOctaves(blockX, blockY, blockZ, hres, vres);
		if (d > 0 && ngdDeadOctaveLogged.add(provider.id() + "#" + d)) {
			NGD_LOG.info("[NGD-NoiseGen] provider={} blocks=({},{},{}) 失效八度={} "
					+ "(八度 0 坐标最大、最先失效；每 2 倍距离再失效一个)",
					provider.id(), blockX, blockY, blockZ, d);
		}
	}

	@Inject(method = "sampleNoise", at = @At("HEAD"), cancellable = true)
	private void noise_gen_debug$fringeCascade(int x, int y, int z,
			double horizontalScale, double verticalScale,
			double horizontalStretch, double verticalStretch,
			CallbackInfoReturnable<Double> cir) {
		// sampleNoise 收到的是「噪声单元」坐标（1 单元 = horizontalNoiseResolution 方块）
		final int blockX = x * this.horizontalNoiseResolution;
		final int blockZ = z * this.horizontalNoiseResolution;

		// ① 可插拔的噪声生成器优先 —— GUI「噪声生成器」按钮循环切换。
		//    active() 为 null 即「原版」，此时只是一次判空，零开销。
		final NoiseProvider provider = NoiseGenRegistry.active();
		if (provider != null) {
			final int blockY = y * this.verticalNoiseResolution;
			cir.setReturnValue(provider.sample(blockX, blockY, blockZ,
					this.horizontalNoiseResolution, this.verticalNoiseResolution));
			noise_gen_debug$logOctaveDeath(provider, blockX, blockY, blockZ,
					this.horizontalNoiseResolution, this.verticalNoiseResolution);
			return;
		}

		// ② 「边缘之地」级联 —— 天空网格的真正来源。
		if (!SkyGridState.isFringeCascade()) {
			return; // 交给原版
		}

		final int node = FringeCascade.resolveNode(blockX, blockZ);
		if (FringeCascade.isConst(node)) {
			if (ngdCallCount++ % 2000 == 0) {
				NGD_LOG.info("[NGD-Fringe] CONST blocks=({},{},{}) value={}",
						blockX, y * this.verticalNoiseResolution, blockZ,
						FringeCascade.constValue(node));
			}
			cir.setReturnValue(FringeCascade.constValue(node));
			return;
		}

		final int spec = FringeCascade.specOf(node);
		final int blockY = y * this.verticalNoiseResolution;

		if (ngdFoldActive(spec, blockX, blockZ) && ngdFoldLogs < 40) {
			ngdFoldLogs++;
			NGD_LOG.info("[NGD-Fringe] FOLD blocks=({},{},{}) spec#{} scale=({},{}) "
					+ "shift=({},{}) repeat=({},{},{}) skyGrid={} forceClamp={}",
					blockX, blockY, blockZ, spec,
					FringeCascade.xScale(spec), FringeCascade.zScale(spec),
					FringeCascade.xShift(spec), FringeCascade.zShift(spec),
					FringeCascade.rStart(spec), FringeCascade.rLen(spec),
					FringeCascade.rCount(spec),
					SkyGridState.isEnabled(), SkyGridState.isForceClamp());
		} else if (ngdCallCount++ % 2000 == 0) {
			NGD_LOG.info("[NGD-Fringe] SPEC blocks=({},{},{}) spec#{} scale=({},{}) "
					+ "shift=({},{})",
					blockX, blockY, blockZ, spec,
					FringeCascade.xScale(spec), FringeCascade.zScale(spec),
					FringeCascade.xShift(spec), FringeCascade.zShift(spec));
		}

		// ⚠️ 这里【不要】加 FLT 那个 ±100 的 range_choice 阈值。
		//
		// FarLandsTraveler 里级联有两处用法，容易搞混：
		//
		// <ul>
		//   <li>{@code far_lands_generation_check.json}：
		//       {@code add(0.078125, base_3d_noise_far_lands)}
		//       —— <b>base 3D 噪声就是这个，无阈值</b>，
		//       对应 1.16.5 的 {@code sampleNoise}，也就是本方法；</li>
		//   <li>{@code noise_settings/far_lands.json}：
		//       {@code range_choice(input = base_3d_noise_far_lands, min = -100, max = 100, ...)}
		//       —— 那是给 {@code preliminary_surface_level} 等<b>别的</b>密度函数用的。</li>
		// </ul>
		//
		// 曾经在这里加过阈值，结果<b>把天空网格杀死了</b>：
		// 网格区用的规格是 {@code x_scale = z_scale = 0.25}（正常尺度），
		// 噪声值只有 ±50 量级，落进 {@code [-100, 100)} → 放行原版
		// → {@code repeat} 折叠结果被整个丢弃。
		cir.setReturnValue(noise_gen_debug$compute(blockX, blockY, blockZ, spec));
	}

	/**
	 * 逐规格重算原版 {@code sampleNoise} 的整段算法。
	 *
	 * <p>与原版的差异只在「坐标怎么来的」和「每 octave 的 scale」；循环体结构、
	 * {@code /512}、{@code /10}、{@code clampedLerp} 全部照抄。
	 *
	 * @return 密度贡献值
	 */
	private double noise_gen_debug$compute(int blockX, int blockY, int blockZ, int spec) {
		final int hres = this.horizontalNoiseResolution;
		final int vres = this.verticalNoiseResolution;
		final boolean ov = FringeCascade.overflowable(spec);

		// ---- ① 循环折叠（在方块坐标上；FLT 是先折叠、后加偏移）----
		final int rStart = FringeCascade.rStart(spec);
		final int rLen = FringeCascade.rLen(spec);
		final int rCount = FringeCascade.rCount(spec);
		// FLT 源码对 x 用 >=，对 y/z 用 >，这里照抄。
		final int fx = FringeNoise.fold(blockX, rStart, rLen, rCount, true);
		final int fz = FringeNoise.fold(blockZ, rStart, rLen, rCount, false);
		// Y 不折叠：所有规格的 repeat_start 都是 1300 万，远超世界高度。

		// ---- ② 加偏移并换算到噪声单元 ----
		final double ox = (fx + FringeCascade.xShift(spec)) / (double) hres;
		final double oz = (fz + FringeCascade.zShift(spec)) / (double) hres;
		final double oy = (blockY + FringeCascade.yShift(spec)) / (double) vres;

		// ---- ③ 逐规格的 scale / factor ----
		final double hsX = 684.412D * FringeCascade.xScale(spec);
		final double hsZ = 684.412D * FringeCascade.zScale(spec);
		final double vs = 684.412D * FringeCascade.yScale(spec);
		final double hstX = hsX / FringeCascade.xFactor(spec);
		final double hstZ = hsZ / FringeCascade.zFactor(spec);
		final double vst = vs / FringeCascade.yFactor(spec);

		// ---- ④ 原版 sampleNoise 的主体 ----
		double d = 0.0D;
		double e = 0.0D;
		double f = 0.0D;
		double g = 1.0D;

		for (int i = 0; i < 16; i++) {
			final double rawX = ox * hsX * g;
			final double rawY = oy * vs * g;
			final double rawZ = oz * hsZ * g;
			final double px = FringeNoise.wrap(rawX, ov);
			final double py = FringeNoise.wrap(rawY, ov);
			final double pz = FringeNoise.wrap(rawZ, ov);
			final double l = vs * g;

			final PerlinNoiseSampler lower = this.lowerInterpolatedNoise.getOctave(i);
			if (lower != null) {
				// 原版第 5 个实参是 `(double)y * l`，即<b>未</b>折叠的 y 坐标（与 j 同值）
				d += lower.sample(px, py, pz, l, rawY) / g;
			}

			final PerlinNoiseSampler upper = this.upperInterpolatedNoise.getOctave(i);
			if (upper != null) {
				e += upper.sample(px, py, pz, l, rawY) / g;
			}

			if (i < 8) {
				final PerlinNoiseSampler interp = this.interpolationNoise.getOctave(i);
				if (interp != null) {
					f += interp.sample(
							FringeNoise.wrap(ox * hstX * g, ov),
							FringeNoise.wrap(oy * vst * g, ov),
							FringeNoise.wrap(oz * hstZ * g, ov),
							vst * g, oy * vst * g) / g;
				}
			}

			g /= 2.0D;
		}

		final double result = MathHelper.clampedLerp(d / 512.0D, e / 512.0D,
				(f / 10.0D + 1.0D) / 2.0D);

		// 退化规格（如 x_scale = 6.25e41，坐标会溢出成 ±Inf）也值得记录 ——
		// 「天空之桥 / 天空网格」的原料全在这里。
		final boolean degenerate = !FringeCascade.repeating(spec)
				&& (Math.abs(FringeCascade.xScale(spec)) > 1.0E10D
						|| Math.abs(FringeCascade.zScale(spec)) > 1.0E10D);
		if (ngdFoldActive(spec, blockX, blockZ) && ngdComputeLogs < 40) {
			ngdComputeLogs++;
			NGD_LOG.info("[NGD-Fringe] COMPUTE blocks=({},{},{}) fold=({},{}) "
					+ "noiseUnit=({},{}) raw0=({},{}) d={} e={} f={} result={}",
					blockX, blockY, blockZ, fx, fz, ox, oz,
					ox * hsX, oz * hsZ, d, e, f, result);
		} else if (degenerate && ngdDegenerateLogs < 30) {
			ngdDegenerateLogs++;
			NGD_LOG.info("[NGD-Fringe] DEGENERATE blocks=({},{},{}) spec#{} "
					+ "scale=({},{}) shift=({},{}) noiseUnit=({},{}) raw0=({},{}) "
					+ "d={} e={} f={} result={} finite={}",
					blockX, blockY, blockZ, spec,
					FringeCascade.xScale(spec), FringeCascade.zScale(spec),
					FringeCascade.xShift(spec), FringeCascade.zShift(spec),
					ox, oz, ox * hsX, oz * hsZ, d, e, f, result,
					Double.isFinite(result));
		}

		return result;
	}
}
