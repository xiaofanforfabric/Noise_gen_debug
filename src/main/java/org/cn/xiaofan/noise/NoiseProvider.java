package org.cn.xiaofan.noise;

/**
 * 可插拔的噪声生成器。
 *
 * <h2>量纲约定（很重要）</h2>
 *
 * <p>返回值必须与<b>原版</b> {@code NoiseChunkGenerator.sampleNoise} 同量纲。
 * 原版那一句是：
 * <pre>
 * return MathHelper.clampedLerp(d / 512.0D, e / 512.0D, (f / 10.0D + 1.0D) / 2.0D);
 * </pre>
 * 其中 {@code d} 是 16 个八度叠加的结果，所以 {@code d / 512} 的量级约为
 * <b>±1 ~ ±40</b>（取决于 {@code y_scale}）。
 *
 * <p>之所以必须对齐，是因为下游 {@code populateNoise} 会拿插值后的值去做：
 * <pre>
 * MathHelper.clamp(an / 200.0D, -1.0D, 1.0D)
 * ao = ao / 2.0D - ao * ao * ao / 24.0D;
 * </pre>
 * 若返回 {@code [-1, 1]} 这种「归一化」值，{@code /200} 之后只有 ±0.005，
 * 地形会<b>几乎没有起伏</b>（表现为超平坦）。
 *
 * @see org.cn.xiaofan.noise.NoiseGenRegistry
 */
public interface NoiseProvider {

	/** 稳定标识符，用于语言键 {@code noise_gen_debug.noisegen.<id>} 与日志。 */
	String id();

	/**
	 * 采样一个方块处的密度贡献。
	 *
	 * <h2>为什么要把分辨率传进来</h2>
	 *
	 * <p>原版 {@code sampleNoise} 收到的是<b>噪声单元</b>坐标，
	 * 每方块前进 {@code 684.412 / horizontalNoiseResolution} 个噪声单位。
	 * 想复刻原版的缩放（从而复刻边境之地的位置），
	 * 就必须知道「一个噪声单元等于几格」。
	 *
	 * @param blockX 方块坐标
	 * @param blockY 方块坐标
	 * @param blockZ 方块坐标
	 * @param horizontalResolution {@code blockX / 噪声单元}（原版 overworld 为 4）
	 * @param verticalResolution   {@code blockY / 噪声单元}（原版 overworld 为 8）
	 * @return 与原版 {@code sampleNoise} 同量纲的密度值
	 */
	double sample(int blockX, int blockY, int blockZ,
			int horizontalResolution, int verticalResolution);

	/**
	 * 诊断：当前坐标下<b>已经失效的八度个数</b>（{@code 0} = 全部正常）。
	 *
	 * <p>各生成器的失效机理不同，"失效"的含义也不同：
	 *
	 * <ul>
	 *   <li>{@link org.cn.xiaofan.noise.provider.OpenSimplex2Provider}
	 *       —— {@code float} 精度耗尽，该八度恒返回 {@code 0}（静默）</li>
	 *   <li>{@link org.cn.xiaofan.noise.provider.LatticeNoiseProvider}
	 *       —— 整数在 {@code 2^31} 饱和，余数无界增长 ⇒ 输出爆炸成天文数字</li>
	 * </ul>
	 *
	 * <p>默认实现认为"没有失效概念"。
	 */
	default int degradedOctaves(int blockX, int blockY, int blockZ,
			int horizontalResolution, int verticalResolution) {
		return 0;
	}
}
