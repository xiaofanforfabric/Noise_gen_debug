package org.cn.xiaofan.noise.provider;

import org.cn.xiaofan.noise.NoiseProvider;

/**
 * 纯 <b>OpenSimplex2（3D）</b> 噪声生成器，按<b>原版 1.16.5 的缩放约定</b>接入。
 *
 * <h2>来源与许可</h2>
 *
 * <p>算法整段移植自 <a href="https://github.com/Auburn/FastNoiseLite">FastNoiseLite</a>
 * 的 Java 版 {@code SingleOpenSimplex2}（Jordan Peck，OpenSimplex 系算法由
 * <a href="https://github.com/KdotJPG/OpenSimplex2">K.jpg</a> 设计），<b>MIT 许可</b>。
 * 数值上<b>未做任何改动</b> —— 全部保留 {@code float}，因为 float 的精度极限
 * 正是要研究的对象。
 *
 * <h2>已对齐原版的缩放（关键）</h2>
 *
 * <p>原版 {@code sampleNoise} 里，第 i 个八度的坐标是
 * {@code (blockX / hres) * 684.412 * coordinateScale * 2^-i}，
 * 即<b>每方块前进 {@code 684.412 / hres ≈ 171.103} 个噪声单位</b>（overworld）。
 * 于是：
 *
 * <pre>
 * 原版边境之地 = 2^31 / 171.103 ≈ 12,550,821   ← int 饱和
 * </pre>
 *
 * <p>本实现用同样的缩放，但没有 double —— fastRound 里的 {@code (int)} 只有在
 * {@code 2^31} 才饱和，而 float 的「旋转步相消」在 {@code 2^23} 就先把位移向量打成 0。
 * 两者相差 {@code 2^31 / 2^23 = 256} 倍。所以：
 *
 * <pre>
 * OpenSimplex2 的「边境之地」 = 12,550,821 / 256 ≈ 49,026
 * </pre>
 *
 * <h2>「八度逐层死亡」—— 本生成器的独有距离现象</h2>
 *
 * <p>16 个八度按 {@code 2^-i} 递减缩放，第 i 个八度的坐标是第 i-1 个的一半。
 * 每个八度各自独立地撞上 float 极限，于是<b>死亡是分层的</b>：
 *
 * <table border="1">
 *   <tr><th>距离（格）</th><th>死掉的八度</th><th>表现</th></tr>
 *   <tr><td>49,026</td><td>i=0（最细，特征 0.006 格）</td><td>失去亚方块细节</td></tr>
 *   <tr><td>98,053</td><td>i=1</td><td>细节再剥一层</td></tr>
 *   <tr><td>…</td><td>…</td><td>每 2 倍距离死一个</td></tr>
 *   <tr><td>1.6e9</td><td>i=15（最粗，特征 191 格）</td><td>地形彻底冻结</td></tr>
 * </table>
 *
 * <p>不会像 Perlin 那样「啪一下变混沌墙」，而是<b>细节一层层剥落</b>。
 * 这是原版没有的现象。
 *
 * @see #deadOctaves(int, int, int, int, int)
 */
public final class OpenSimplex2Provider implements NoiseProvider {

	private static final int SEED = 1337;

	/** 与 1.16.5 {@code sampleNoise} 相同的八度数。 */
	public static final int OCTAVES = 16;

	/** 原版 1.16.5 的坐标缩放（overworld 的 {@code coordinate_scale}）。 */
	public static final double COORDINATE_SCALE = 1.0D;

	/** 原版水平/垂直缩放基数（1.16.5 的 {@code horizontalScale / verticalScale}）。 */
	public static final double NOISE_SCALE = 684.412D;

	/** 单八度幅度：原版 {@code d / 512} 里的 {@code 684.412 / 512}。 */
	private static final double OCTAVE_AMPLITUDE = NOISE_SCALE / 512.0D;

	/**
	 * float 精度耗尽阈值（噪声坐标）。
	 *
	 * <p>实测：噪声坐标超过约 {@code 6.885e6} 后，本实现恒返回 {@code 0.0}
	 * （旋转步的灾难性相消把 {@code x0/y0/z0} 打成 0 ⇒ 位移向量全零 ⇒ 点积为 0）。
	 * 这里取 {@code 2^23 = 8,388,608} 作为保守刻度。
	 */
	public static final double FLOAT_DEATH_LIMIT = 8388608.0D;

	// ==================== NoiseProvider ====================

	@Override
	public String id() {
		return "opensimplex2";
	}

	@Override
	public double sample(int blockX, int blockY, int blockZ,
			int horizontalResolution, int verticalResolution) {
		// 原版 sampleNoise 收到的是「噪声单元」坐标
		final double ox = (double) blockX / horizontalResolution;
		final double oy = (double) blockY / verticalResolution;
		final double oz = (double) blockZ / horizontalResolution;

		double sum = 0.0D;
		double g = 1.0D;
		for (int i = 0; i < OCTAVES; i++) {
			sum += octave(i, ox, oy, oz, g);
			g /= 2.0D;   // 与原版一致：第 i+1 个八度坐标是第 i 个的一半
		}
		return sum;
	}

	/** 第 {@code i} 个八度：原版 {@code (block/分辨率) * 缩放 * 2^-i}，再落进 float32。 */
	private static double octave(int i, double ox, double oy, double oz, double g) {
		final float x = (float) (ox * NOISE_SCALE * COORDINATE_SCALE * g);
		final float y = (float) (oy * NOISE_SCALE * COORDINATE_SCALE * g);
		final float z = (float) (oz * NOISE_SCALE * COORDINATE_SCALE * g);

		// 复刻 FastNoiseLite 的 TransformType3D.DefaultOpenSimplex2（旋转，非偏斜）
		final float r3 = 2.0F / 3.0F;
		final float r = (x + y + z) * r3;

		return singleOpenSimplex2(SEED + i, r - x, r - y, r - z) * OCTAVE_AMPLITUDE;
	}

	// ==================== 八度死亡探针 ====================

	/**
	 * 该八度在当前坐标下是否已经「死掉」。
	 *
	 * <p>判据：缩放后的噪声坐标超过 {@link #FLOAT_DEATH_LIMIT}。
	 * 是高精度的解析近似（真实边界还依赖具体的取整落点）。
	 */
	public static boolean isOctaveDead(int octave, int blockX, int blockY, int blockZ,
			int horizontalResolution, int verticalResolution) {
		final double g = Math.pow(2.0, -octave);
		final double ax = Math.abs((double) blockX / horizontalResolution)
				* NOISE_SCALE * COORDINATE_SCALE * g;
		final double ay = Math.abs((double) blockY / verticalResolution)
				* NOISE_SCALE * COORDINATE_SCALE * g;
		final double az = Math.abs((double) blockZ / horizontalResolution)
				* NOISE_SCALE * COORDINATE_SCALE * g;
		return Math.max(ax, Math.max(ay, az)) >= FLOAT_DEATH_LIMIT;
	}

	/**
	 * 已经死掉的八度个数（{@code 0 ~ OCTAVES}）。
	 *
	 * <p>八度 0 坐标最大 ⇒ <b>最先死</b>；八度 15 最后死。
	 * 每 2 倍距离死一个，共 16 级阶梯。
	 */
	@Override
	public int degradedOctaves(int blockX, int blockY, int blockZ,
			int horizontalResolution, int verticalResolution) {
		int dead = 0;
		for (int i = 0; i < OCTAVES; i++) {
			if (isOctaveDead(i, blockX, blockY, blockZ, horizontalResolution,
					verticalResolution)) {
				dead++;
			}
		}
		return dead;
	}

	// ==================== 以下为 FastNoiseLite 原样移植 ====================

	private static final int PRIME_X = 501125321;
	private static final int PRIME_Y = 1136930381;
	private static final int PRIME_Z = 1720413743;

	private static int fastRound(float f) {
		return f >= 0 ? (int) (f + 0.5F) : (int) (f - 0.5F);
	}

	private static int hash(int seed, int xPrimed, int yPrimed, int zPrimed) {
		int hash = seed ^ xPrimed ^ yPrimed ^ zPrimed;
		hash *= 0x27d4eb2d;
		return hash;
	}

	private static float gradCoord(int seed, int xPrimed, int yPrimed, int zPrimed,
			float xd, float yd, float zd) {
		int hash = hash(seed, xPrimed, yPrimed, zPrimed);
		hash ^= hash >> 15;
		hash &= 63 << 2;

		float xg = GRADIENTS_3D[hash];
		float yg = GRADIENTS_3D[hash | 1];
		float zg = GRADIENTS_3D[hash | 2];

		return xd * xg + yd * yg + zd * zg;
	}

	/** FastNoiseLite 的 {@code SingleOpenSimplex2}（3D），逐字照抄。 */
	private static float singleOpenSimplex2(int seed, float x, float y, float z) {
		// 3D OpenSimplex2 case uses two offset rotated cube grids.
		int i = fastRound(x);
		int j = fastRound(y);
		int k = fastRound(z);
		float x0 = x - i;
		float y0 = y - j;
		float z0 = z - k;

		int xNSign = (int) (-1.0F - x0) | 1;
		int yNSign = (int) (-1.0F - y0) | 1;
		int zNSign = (int) (-1.0F - z0) | 1;

		float ax0 = xNSign * -x0;
		float ay0 = yNSign * -y0;
		float az0 = zNSign * -z0;

		i *= PRIME_X;
		j *= PRIME_Y;
		k *= PRIME_Z;

		float value = 0;
		float a = (0.6F - x0 * x0) - (y0 * y0 + z0 * z0);

		for (int l = 0; ; l++) {
			if (a > 0) {
				value += (a * a) * (a * a) * gradCoord(seed, i, j, k, x0, y0, z0);
			}

			if (ax0 >= ay0 && ax0 >= az0) {
				float b = a + ax0 + ax0;
				if (b > 1) {
					b -= 1;
					value += (b * b) * (b * b) * gradCoord(seed, i - xNSign * PRIME_X,
							j, k, x0 + xNSign, y0, z0);
				}
			} else if (ay0 > ax0 && ay0 >= az0) {
				float b = a + ay0 + ay0;
				if (b > 1) {
					b -= 1;
					value += (b * b) * (b * b) * gradCoord(seed, i, j - yNSign * PRIME_Y,
							k, x0, y0 + yNSign, z0);
				}
			} else {
				float b = a + az0 + az0;
				if (b > 1) {
					b -= 1;
					value += (b * b) * (b * b) * gradCoord(seed, i, j, k - zNSign * PRIME_Z,
							x0, y0, z0 + zNSign);
				}
			}

			if (l == 1) {
				break;
			}

			ax0 = 0.5F - ax0;
			ay0 = 0.5F - ay0;
			az0 = 0.5F - az0;

			x0 = xNSign * ax0;
			y0 = yNSign * ay0;
			z0 = zNSign * az0;

			a += (0.75F - ax0) - (ay0 + az0);

			i += (xNSign >> 1) & PRIME_X;
			j += (yNSign >> 1) & PRIME_Y;
			k += (zNSign >> 1) & PRIME_Z;

			xNSign = -xNSign;
			yNSign = -yNSign;
			zNSign = -zNSign;

			seed = ~seed;
		}

		return value * 32.69428253173828125F;
	}

	private static final float[] GRADIENTS_3D = {
			0, 1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0,
			1, 0, 1, 0, -1, 0, 1, 0, 1, 0, -1, 0, -1, 0, -1, 0,
			1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0, 0,
			0, 1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0,
			1, 0, 1, 0, -1, 0, 1, 0, 1, 0, -1, 0, -1, 0, -1, 0,
			1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0, 0,
			0, 1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0,
			1, 0, 1, 0, -1, 0, 1, 0, 1, 0, -1, 0, -1, 0, -1, 0,
			1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0, 0,
			0, 1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0,
			1, 0, 1, 0, -1, 0, 1, 0, 1, 0, -1, 0, -1, 0, -1, 0,
			1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0, 0,
			0, 1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0,
			1, 0, 1, 0, -1, 0, 1, 0, 1, 0, -1, 0, -1, 0, -1, 0,
			1, 1, 0, 0, -1, 1, 0, 0, 1, -1, 0, 0, -1, -1, 0, 0,
			1, 1, 0, 0, 0, -1, 1, 0, -1, 1, 0, 0, 0, -1, -1, 0
	};
}
