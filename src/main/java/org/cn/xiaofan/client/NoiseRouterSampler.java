package org.cn.xiaofan.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseSamplingConfig;
import org.cn.xiaofan.mixin.NoiseChunkGeneratorAccessor;
import org.cn.xiaofan.mixin.NoiseChunkGeneratorInvoker;

/**
 * 在 1.16.5 上复刻高版本原版 F3 的 {@code NoiseRouter} 行。
 *
 * <p>1.18+ 原版格式（参考 Minecraft Wiki）：
 * <pre>
 * NoiseRouter T: T V: V C: C E: E D: D W: W PV: PV PS: PS N: N
 * </pre>
 *
 * <p>1.16.5 **没有** temperature/continentalness/erosion/peaks&valleys 这些
 * 生物群系噪声参数（那时还没引入）。本类尽量映射到语义接近的量：
 * <ul>
 *   <li>{@code D} = {@link Biome#getDepth()} —— 对应 1.18 的 depth</li>
 *   <li>{@code W} = {@link Biome#getScale()} —— 对应 1.18 的 weirdness 角色</li>
 *   <li>{@code T} = {@link Biome#getTemperature()} —— 温度（生物群系属性，非噪声）</li>
 *   <li>{@code PS} = {@code sampleNoise(...)} —— 原始噪声插值值</li>
 *   <li>{@code N} = {@code sampleNoiseColumn(...)[y]} —— 最终密度值</li>
 * </ul>
 *
 * <p>拿不到的字段在输出里省略，不伪造。
 */
public final class NoiseRouterSampler {

	private NoiseRouterSampler() {
	}

	/** 采样结果快照。字段为 {@code null} 表示该版本无法取得。 */
	public static final class Sample {
		/** depth（Biome.getDepth） */
		public float depth;
		/** scale（Biome.getScale），对应 weirdness 角色 */
		public float scale;
		/** temperature（Biome.getTemperature） */
		public float temperature;
		/** 原始噪声插值值（sampleNoise） */
		public double rawNoise;
		/** 最终密度（sampleNoiseColumn[y]） */
		public double density;
		/** 该 y 对应的方块层（density 所在层） */
		public int blockY;
	}

	/**
	 * 在给定坐标采样。
	 *
	 * @param generator 主世界噪声生成器
	 * @param blockX    方块坐标 X
	 * @param blockY    方块坐标 Y
	 * @param blockZ    方块坐标 Z
	 */
	public static Sample sample(NoiseChunkGenerator generator, int blockX, int blockY, int blockZ) {
		Sample s = new Sample();

		NoiseChunkGeneratorAccessor accessor = (NoiseChunkGeneratorAccessor) (Object) generator;
		java.util.function.Supplier<ChunkGeneratorSettings> supplier = accessor.noise_gen_debug$getSettings();
		if (supplier == null) {
			return s;
		}

		ChunkGeneratorSettings settings = supplier.get();
		if (settings == null) {
			return s;
		}

		GenerationShapeConfig shape = settings.getGenerationShapeConfig();
		NoiseSamplingConfig sampling = shape.getSampling();

		// --- Biome 属性（depth / scale / temperature） ---
		BiomeSource biomeSource = generator.getBiomeSource();
		Biome biome = biomeSource.getBiomeForNoiseGen(blockX, blockY, blockZ);
		s.depth = biome.getDepth();
		s.scale = biome.getScale();
		s.temperature = biome.getTemperature(new BlockPos(blockX, blockY, blockZ));

		// --- 原始噪声（sampleNoise） ---
		// 缩放系数与 sampleNoiseColumn 内部保持一致。
		double ae = 684.412D * sampling.getXZScale();
		double af = 684.412D * sampling.getYScale();
		double ag = ae / sampling.getXZFactor();
		double ah = af / sampling.getYFactor();

		// sampleNoise 接收的是「噪声单元坐标」而非方块坐标。
		NoiseChunkGeneratorInvoker inv = (NoiseChunkGeneratorInvoker) (Object) generator;
		int noiseX = Math.floorDiv(blockX, 4);
		int noiseY = Math.floorDiv(blockY, 4);
		int noiseZ = Math.floorDiv(blockZ, 4);

		s.rawNoise = inv.noise_gen_debug$sampleNoise(noiseX, noiseY, noiseZ, ae, af, ag, ah);

		// --- 最终密度（sampleNoiseColumn 的对应层） ---
		int noiseSizeY = inv.noise_gen_debug$getNoiseSizeY();
		int verticalRes = inv.noise_gen_debug$getVerticalNoiseResolution();

		double[] column = new double[noiseSizeY + 1];
		inv.noise_gen_debug$sampleNoiseColumn(column, noiseX, noiseZ);

		int index = MathHelper.clamp(blockY / verticalRes, 0, noiseSizeY);
		s.density = column[index];
		s.blockY = index * verticalRes;

		return s;
	}

	/** 把采样结果格式化成一行，模仿高版本 NoiseRouter 风格。 */
	public static String format(Sample s) {
            return String.format("NoiseRouter D: %.3f W: %.3f T: %.3f PS: %s N: %s",
                            s.depth, s.scale, s.temperature,
                            smart(s.rawNoise), smart(s.density));
    }

    /**
     * 智能格式化数值：小范围用定点，大/小范围用科学计数法。
     *
     * <p>之前统一用 {@code %.4f}，导致极端缩放（如 xzScale = 2^31）下
     * 整数值会打印出几十位数字、溢出屏幕，反而看不清量级。
     * 例如 {@code 1474836480000000000.0000} 直接刷屏。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code |v| < 1e6} → 定点 {@code %.4f}</li>
     *   <li>其他 → 科学计数法 {@code %.4e}，并额外标出是否 NaN / Infinite</li>
     * </ul>
     */
    private static String smart(double v) {
            if (Double.isNaN(v)) {
                    return "NaN";
            }
            if (Double.isInfinite(v)) {
                    return v > 0 ? "+Inf" : "-Inf";
            }
            double abs = Math.abs(v);
            if (abs < 1e6) {
                    return String.format("%.4f", v);
            }
            return String.format("%.4e", v);
    }

    /** 给 F3 用的参数展示：NaN / Inf 用字面量，其余按量级智能选择。 */
    public static String show(double v) {
            return smart(v);
    }
}
