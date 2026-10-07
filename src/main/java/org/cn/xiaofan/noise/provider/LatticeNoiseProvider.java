package org.cn.xiaofan.noise.provider;

import org.cn.xiaofan.noise.NoiseProvider;

/**
 * <b>晶格噪声</b>的通用 provider —— 一次提供 Value / Perlin / ValueCubic 三种核。
 *
 * <h2>为什么要做成一个类</h2>
 *
 * <p>三种核<b>共用同一套东西</b>，只有「格点之间怎么插值」不同：
 *
 * <table border="1">
 *   <tr><th></th><th>格点上的值</th><th>插值权重</th><th>爆炸时剩下什么</th></tr>
 *   <tr><td>{@link Kind#VALUE}</td><td>随机值 {@code [-1,1)}</td>
 *       <td>五次 {@code t³(6t²−15t+10)}</td><td>{@code xs·(v1−v0)}</td></tr>
 *   <tr><td>{@link Kind#PERLIN}</td><td>梯度点积</td>
 *       <td>五次 {@code t³(6t²−15t+10)}</td><td>{@code xs·(−grad·d)}</td></tr>
 *   <tr><td>{@link Kind#VALUE_CUBIC}</td><td>随机值 {@code [-1,1)}</td>
 *       <td>三次 Catmull-Rom</td><td>{@code xs³·p}</td></tr>
 * </table>
 *
 * <p><b>关键</b>：三者都是「<b>有界的格点值</b> × <b>无界的多项式权重</b>」。
 * 坐标一旦越过 {@code 2^31}，格子索引饱和而坐标继续增长 ⇒ 权重变成天文数字
 * ⇒ 输出完全被权重主导 ⇒ <b>只剩格点值的组合方向（符号）</b>。
 *
 * <h2>它们崩出来会一样吗</h2>
 *
 * <p><b>结构一样，图案不同</b>：
 * <ul>
 *   <li><b>结构</b>：爆炸后沿<b>饱和轴</b>完全不变（整数索引卡死），
 *       只在<b>未饱和轴</b>上有变化 ⇒ 三个核都是「沿 X 无限延伸的分层」。
 *       这正是 Minecraft Wiki 说的 <i>"long unchanging tunnels"</i>。</li>
 *   <li><b>图案</b>：未饱和轴上的具体形状由核 + hash 决定 ⇒ 细节不同。</li>
 * </ul>
 *
 * <p>⇒ 所以<b>换核换不出新长相</b>。要换长相得动<b>管线</b>（见 {@link #foldEnabled}）。
 *
 * <h2>爆炸的两个必要条件</h2>
 * <ol>
 *   <li><b>输入是 {@code double}</b> —— 整数才会在 {@code 2^31} 饱和而坐标继续增长。
 *       本类<b>全程 double</b>，绝不转 {@code float}。（FNL 用 float，在 {@code 2^24} 就死了）</li>
 *   <li><b>核无界</b> —— 权重必须能吃到天文数字。三种核都满足；
 *       而 {@link OpenSimplex2Provider} 有 {@code if (a > 0)} 半径截断，永远做不到。</li>
 * </ol>
 */
public final class LatticeNoiseProvider implements NoiseProvider {

	/** 用哪种核。 */
	public enum Kind {
		/** 方格值噪声。 */
		VALUE,
		/** 梯度（Perlin）噪声。 */
		PERLIN,
		/** 方格值噪声 —— 三次（Catmull-Rom）插值。 */
		VALUE_CUBIC,
		/**
		 * Worley（细胞）—— 到最近特征点的距离（F1）。
		 *
		 * <p>注意它的爆炸速度不同：位移是 {@code (格子中心 + jitter) − 坐标}，
		 * 距离取<b>平方</b> ⇒ 量级按余数的 <b>2 次</b>长（格子类核是 5 次）。
		 */
		CELLULAR,
		/**
		 * Worley —— 最近特征点的哈希值。
		 *
		 * <p>关键区别：它在<b>细胞边界上本来就不连续</b>，
		 * 所以爆炸后可能给出<b>块状/多边形</b>而不是分层。
		 */
		CELLULAR_VALUE,
		/**
		 * 域变形 Perlin —— 先用噪声把坐标扭歪，再算 Perlin。
		 *
		 * <p>爆炸值被<b>加到坐标上</b> ⇒ 相邻采样点跳到完全不同的地方
		 * ⇒ <b>空间相关性被摧毁</b> ⇒ 得到高频噪声，而不是「沿轴不变的层」。
		 */
		WARPED_PERLIN,
		/**
		 * 2D Simplex（只用 x/z）.
		 *
		 * <p>密度<b>与 Y 无关</b> ⇒ <b>垂直柱状</b>，与「水平分层」正交。
		 *
		 * <p>但它有 {@code a = 0.5 − x0² − y0²; if (a <= 0) = 0} 的
		 * <b>半径截断</b> ⇒ 和 OpenSimplex2 一样，远处会<b>静默归零</b>。
		 */
		SIMPLEX_2D
	}

	/** 与 1.16.5 {@code sampleNoise} 相同的八度数。 */
	public static final int OCTAVES = 16;

	/** 原版 1.16.5 的坐标缩放（overworld 的 {@code coordinate_scale}）。 */
	public static final double COORDINATE_SCALE = 1.0D;

	/** 原版水平/垂直缩放基数。每方块前进 {@code 684.412 / hres ≈ 171.103} 个噪声单位。 */
	public static final double NOISE_SCALE = 684.412D;

	/** 单八度幅度：原版 {@code d / 512} 里的 {@code 684.412 / 512}。 */
	private static final double OCTAVE_AMPLITUDE = NOISE_SCALE / 512.0D;

	/** 整数饱和阈值 —— 原版 {@code (int)} 强转的上限。越过它就会爆炸。 */
	public static final double INT_SATURATION = 2147483647.0D;

	private static final int SEED = 1337;

	private static final int PRIME_X = 501125321;
	private static final int PRIME_Y = 1136930381;
	private static final int PRIME_Z = 1720413743;

	private final Kind kind;

	public LatticeNoiseProvider(Kind kind) {
		this.kind = kind;
	}

	// ==================== 坐标折叠（让长相真正变掉的那一层） ====================

	/** 折叠起点（方块）。默认 = 八度 0 的饱和距离，即原版边境之地的位置。 */
	public static final long FOLD_START = 12550824L;

	/**
	 * 「坐标折叠」开关 —— 把「沿饱和轴无限不变的隧道」换成<b>可控周期的格阵</b>。
	 *
	 * <pre>
	 * |c| ≥ foldStart  →  c = sign(c) · (foldStart + (|c| − foldStart) mod foldLength)
	 * </pre>
	 *
	 * <p>坐标仍在饱和区，但改成以 {@code foldLength} 格为周期摆动 ⇒
	 * 「余数」也跟着周期性变化 ⇒ 输出变成<b>周期 = foldLength 格的图案</b>。
	 *
	 * <p>{@code foldLength = 16} 就是 FarLandsTraveler 的「天空网格」
	 * （它的 {@code repeat_length} 正是 16）。
	 */
	private static boolean foldEnabled;

	/** 折叠周期（方块）。16 = FLT 的天空网格分辨率。 */
	private static long foldLength = 16L;

	public static boolean isFoldEnabled() {
		return foldEnabled;
	}

	public static void setFoldEnabled(boolean v) {
		foldEnabled = v;
	}

	public static void toggleFold() {
		foldEnabled = !foldEnabled;
	}

	public static long getFoldLength() {
		return foldLength;
	}

	public static void setFoldLength(long v) {
		if (v > 0L) {
			foldLength = v;
		}
	}

	/** 把方块坐标折回 {@code [FOLD_START, FOLD_START + foldLength)}。 */
	public static int foldCoord(int c) {
		if (!foldEnabled) {
			return c;
		}
		long a = c < 0L ? -(long) c : (long) c;
		if (a < FOLD_START) {
			return c;
		}
		a = FOLD_START + (a - FOLD_START) % foldLength;
		return (int) (c < 0L ? -a : a);
	}

	// ==================== NoiseProvider ====================

	@Override
	public String id() {
		switch (this.kind) {
			case PERLIN:
				return "perlin";
			case VALUE_CUBIC:
				return "valuecubic";
			case CELLULAR:
				return "cellular";
			case CELLULAR_VALUE:
				return "cellularvalue";
			case WARPED_PERLIN:
				return "warped";
			case SIMPLEX_2D:
				return "simplex2d";
			case VALUE:
			default:
				return "value";
		}
	}

	@Override
	public double sample(int blockX, int blockY, int blockZ,
			int horizontalResolution, int verticalResolution) {
		final int fx = foldCoord(blockX);
		final int fz = foldCoord(blockZ);

		// 原版 sampleNoise 收到的是「噪声单元」坐标；全程 double，绝不转 float
		final double ox = (double) fx / horizontalResolution;
		final double oy = (double) blockY / verticalResolution;
		final double oz = (double) fz / horizontalResolution;

		double sum = 0.0D;
		double g = 1.0D;
		for (int i = 0; i < OCTAVES; i++) {
			sum += kernel(SEED + i,
					ox * NOISE_SCALE * COORDINATE_SCALE * g,
					oy * NOISE_SCALE * COORDINATE_SCALE * g,
					oz * NOISE_SCALE * COORDINATE_SCALE * g) * OCTAVE_AMPLITUDE;
			g /= 2.0D;
		}
		return sum;
	}

	/**
	 * 已经「饱和爆炸」的八度个数（{@code 0 ~ OCTAVES}）。
	 *
	 * <p>八度 0 坐标最大 ⇒ <b>最先饱和</b>，饱和距离 = {@code 2^31 / 171.103 = 12,550,824} 格，
	 * 与原版边境之地重合。此后每 2 倍距离再饱和一个。
	 */
	@Override
	public int degradedOctaves(int blockX, int blockY, int blockZ,
			int horizontalResolution, int verticalResolution) {
		final int fx = foldCoord(blockX);
		final int fz = foldCoord(blockZ);
		int n = 0;
		for (int i = 0; i < OCTAVES; i++) {
			final double g = Math.pow(2.0, -i);
			final double s = NOISE_SCALE * COORDINATE_SCALE * g;
			final double m = Math.max(
					Math.max(Math.abs((double) fx / horizontalResolution) * s,
							Math.abs((double) fx / horizontalResolution) * s),
					Math.max(Math.abs((double) blockY / verticalResolution) * s,
							Math.abs((double) fz / horizontalResolution) * s));
			if (m >= INT_SATURATION) {
				n++;
			}
		}
		return n;
	}

	private double kernel(int seed, double x, double y, double z) {
		switch (this.kind) {
			case PERLIN:
				return perlin(seed, x, y, z);
			case VALUE_CUBIC:
				return valueCubic(seed, x, y, z);
			case CELLULAR:
				return cellularF1(seed, x, y, z);
			case CELLULAR_VALUE:
				return cellularValue(seed, x, y, z);
			case WARPED_PERLIN:
				return warpedPerlin(seed, x, y, z);
			case SIMPLEX_2D:
				return simplex2d(seed, x, z);
			case VALUE:
			default:
				return value(seed, x, y, z);
		}
	}

	// ==================== 共用工具 ====================

	/**
	 * 32 位饱和下取整 —— 复刻 Java 的 {@code (int)} 强转。
	 *
	 * <p><b>这就是边境之地的 bug 本体</b>：{@code (int)} 对超出
	 * {@code Integer.MAX_VALUE} 的 double 返回 {@code Integer.MAX_VALUE}（饱和），
	 * 而随后的减法却假设这个整数是精确的 ⇒ 余数无界增长。
	 */
	private static int saturatingFloor(double f) {
		return f >= 0 ? (int) f : (int) f - 1;
	}

	private static int hash(int seed, int x, int y, int z) {
		int h = seed ^ x ^ y ^ z;
		h *= 0x27d4eb2d;
		return h;
	}

	/** 格点上的随机值，落在 {@code [-1, 1)}。 */
	private static double valCoord(int seed, int x, int y, int z) {
		int h = hash(seed, x, y, z);
		h *= h;
		h ^= h << 19;
		return h * (1.0 / 2147483648.0);
	}

	/** 梯度点积。{@code xd/yd/zd} 是相对格点的位移（饱和后会是天文数字）。 */
	private static double gradCoord(int seed, int x, int y, int z,
			double xd, double yd, double zd) {
		int h = hash(seed, x, y, z);
		h ^= h >> 15;
		h &= 63 << 2;
		return xd * GRADIENTS_3D[h]
				+ yd * GRADIENTS_3D[h | 1]
				+ zd * GRADIENTS_3D[h | 2];
	}

	/** 五次平滑权重 {@code t³(6t²−15t+10)} —— 与原版 wiki 里那个式子是同一个。 */
	private static double quintic(double t) {
		return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
	}

	private static double lerp(double a, double b, double t) {
		return a + t * (b - a);
	}

	/** Catmull-Rom 三次插值。 */
	private static double cubicLerp(double a, double b, double c, double d, double t) {
		final double p = (d - c) - (a - b);
		return t * t * t * p + t * t * ((a - b) - p) + t * (c - a) + b;
	}

	// ==================== 三种核 ====================

	/** Value noise（3D）：格点随机值 + 五次插值。 */
	private static double value(int seed, double x, double y, double z) {
		final int x0 = saturatingFloor(x);
		final int y0 = saturatingFloor(y);
		final int z0 = saturatingFloor(z);
		final int x1 = x0 + 1;
		final int y1 = y0 + 1;
		final int z1 = z0 + 1;

		// ⚠️ 爆炸点：整数饱和后 x - x0 不再落在 [0,1)，会一直涨
		final double xs = quintic(x - x0);
		final double ys = quintic(y - y0);
		final double zs = quintic(z - z0);

		final double x00 = lerp(valCoord(seed, x0, y0, z0), valCoord(seed, x1, y0, z0), xs);
		final double x10 = lerp(valCoord(seed, x0, y1, z0), valCoord(seed, x1, y1, z0), xs);
		final double x01 = lerp(valCoord(seed, x0, y0, z1), valCoord(seed, x1, y0, z1), xs);
		final double x11 = lerp(valCoord(seed, x0, y1, z1), valCoord(seed, x1, y1, z1), xs);

		return lerp(lerp(x00, x10, ys), lerp(x01, x11, ys), zs);
	}

	/** Perlin noise（3D）：梯度点积 + 五次插值。 */
	private static double perlin(int seed, double x, double y, double z) {
		final int ix = saturatingFloor(x);
		final int iy = saturatingFloor(y);
		final int iz = saturatingFloor(z);

		final double xd0 = x - ix;
		final double yd0 = y - iy;
		final double zd0 = z - iz;
		final double xd1 = xd0 - 1.0;
		final double yd1 = yd0 - 1.0;
		final double zd1 = zd0 - 1.0;

		final double xs = quintic(xd0);
		final double ys = quintic(yd0);
		final double zs = quintic(zd0);

		final int x0 = ix * PRIME_X;
		final int y0 = iy * PRIME_Y;
		final int z0 = iz * PRIME_Z;
		final int x1 = x0 + PRIME_X;
		final int y1 = y0 + PRIME_Y;
		final int z1 = z0 + PRIME_Z;

		final double xf00 = lerp(gradCoord(seed, x0, y0, z0, xd0, yd0, zd0),
				gradCoord(seed, x1, y0, z0, xd1, yd0, zd0), xs);
		final double xf10 = lerp(gradCoord(seed, x0, y1, z0, xd0, yd1, zd0),
				gradCoord(seed, x1, y1, z0, xd1, yd1, zd0), xs);
		final double xf01 = lerp(gradCoord(seed, x0, y0, z1, xd0, yd0, zd1),
				gradCoord(seed, x1, y0, z1, xd1, yd0, zd1), xs);
		final double xf11 = lerp(gradCoord(seed, x0, y1, z1, xd0, yd1, zd1),
				gradCoord(seed, x1, y1, z1, xd1, yd1, zd1), xs);

		return lerp(lerp(xf00, xf10, ys), lerp(xf01, xf11, ys), zs)
				* 0.964921414852142333984375D;
	}

	/** ValueCubic noise（3D）：格点随机值 + 三次（Catmull-Rom）插值，4×4×4 格点。 */
	private static double valueCubic(int seed, double x, double y, double z) {
		final int f1 = saturatingFloor(x);
		final int g1 = saturatingFloor(y);
		final int h1 = saturatingFloor(z);

		final double xs = x - f1;
		final double ys = y - g1;
		final double zs = z - h1;

		final int[] xc = offsets(f1, PRIME_X);
		final int[] yc = offsets(g1, PRIME_Y);
		final int[] zc = offsets(h1, PRIME_Z);

		final double[] zSlice = new double[4];
		for (int zi = 0; zi < 4; zi++) {
			final double[] yRow = new double[4];
			for (int yi = 0; yi < 4; yi++) {
				yRow[yi] = cubicLerp(
						valCoord(seed, xc[0], yc[yi], zc[zi]),
						valCoord(seed, xc[1], yc[yi], zc[zi]),
						valCoord(seed, xc[2], yc[yi], zc[zi]),
						valCoord(seed, xc[3], yc[yi], zc[zi]), xs);
			}
			zSlice[zi] = cubicLerp(yRow[0], yRow[1], yRow[2], yRow[3], ys);
		}
		return cubicLerp(zSlice[0], zSlice[1], zSlice[2], zSlice[3], zs)
				* (1.0 / 3.375D);
	}

	/** {@code {i-1, i, i+1, i+2}} 乘以 prime。 */
	private static int[] offsets(int i, int prime) {
		final int base = i * prime;
		return new int[] { base - prime, base, base + prime, base + (prime << 1) };
	}

	/** 32 位饱和四舍五入（Worley 用）。 */
	private static int saturatingRound(double f) {
		return f >= 0 ? (int) (f + 0.5) : (int) (f - 0.5);
	}

	/** Worley 的特征点抖动幅度。 */
	private static final double CELLULAR_JITTER = 0.43701595D;

	/** 把哈希拆成 [-0.5, 0.5) 的三个抖动分量。 */
	private static double jitter(int h, int shift) {
		return ((h >> shift) & 0xFF) / 255.0 - 0.5;
	}

	/**
	 * Worley —— 到最近特征点的<b>平方</b>距离（F1）。
	 *
	 * <p>爆炸时位移 {@code (格子中心 + jitter) − 坐标} 无界增长，
	 * 取平方 ⇒ 量级按余数的 <b>2 次</b>增长（对比格子类核的 5 次）。
	 */
	private static double cellularF1(int seed, double x, double y, double z) {
		final int xr = saturatingRound(x);
		final int yr = saturatingRound(y);
		final int zr = saturatingRound(z);
		double best = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					final int cx = xr + dx;
					final int cy = yr + dy;
					final int cz = zr + dz;
					final int h = hash(seed, cx, cy, cz);
					final double vx = cx + jitter(h, 0) * CELLULAR_JITTER - x;
					final double vy = cy + jitter(h, 8) * CELLULAR_JITTER - y;
					final double vz = cz + jitter(h, 16) * CELLULAR_JITTER - z;
					final double d = vx * vx + vy * vy + vz * vz;
					if (d < best) {
						best = d;
					}
				}
			}
		}
		return best - 1.0;
	}

	/**
	 * Worley —— 最近特征点的哈希值。
	 *
	 * <p><b>在细胞边界上不连续</b>：这正是它可能与「分层」不同的地方。
	 */
	private static double cellularValue(int seed, double x, double y, double z) {
		final int xr = saturatingRound(x);
		final int yr = saturatingRound(y);
		final int zr = saturatingRound(z);
		double best = Double.MAX_VALUE;
		int bestHash = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					final int cx = xr + dx;
					final int cy = yr + dy;
					final int cz = zr + dz;
					final int h = hash(seed, cx, cy, cz);
					final double vx = cx + jitter(h, 0) * CELLULAR_JITTER - x;
					final double vy = cy + jitter(h, 8) * CELLULAR_JITTER - y;
					final double vz = cz + jitter(h, 16) * CELLULAR_JITTER - z;
					final double d = vx * vx + vy * vy + vz * vz;
					if (d < best) {
						best = d;
						bestHash = h;
					}
				}
			}
		}
		return bestHash * (1.0 / 2147483648.0);
	}

	/** 域变形的幅度（噪声单位）。 */
	private static final double WARP_AMPLITUDE = 4.0D;

	/**
	 * 域变形 Perlin：{@code coord' = coord + amp · 偏移噪声}，然后在 {@code coord'} 上算 Perlin。
	 *
	 * <p>爆炸时偏移量是天文数字 ⇒ 相邻采样点被甩到完全不同的位置
	 * ⇒ <b>空间相关性被摧毁</b> ⇒ 高频，而非「沿轴不变的层」。
	 */
	private static double warpedPerlin(int seed, double x, double y, double z) {
		final double wx = x + WARP_AMPLITUDE * perlin(seed + 101, x, y, z);
		final double wy = y + WARP_AMPLITUDE * perlin(seed + 202, x, y, z);
		final double wz = z + WARP_AMPLITUDE * perlin(seed + 303, x, y, z);
		return perlin(seed, wx, wy, wz);
	}

	private static final double SQRT3 = 1.7320508075688772935274463415059D;
	private static final double F2 = 0.5D * (SQRT3 - 1.0D);
	private static final double G2 = (3.0D - SQRT3) / 6.0D;

	/**
	 * 2D Simplex（只用 x/z，忽略 y）—— 移植自 FastNoiseLite 的 {@code SingleSimplex}。
	 *
	 * <p><b>与 Y 无关</b> ⇒ 密度在垂直方向恒定 ⇒ <b>柱状</b>。
	 *
	 * <p>但它带 {@code a = 0.5 − x0² − z0²; if (a <= 0) 0} 的<b>半径截断</b>：
	 * 坐标一大会直接丢弃所有贡献 ⇒ 远处<b>静默归零</b>（和 OpenSimplex2 同理）。
	 * 所以它演示的是「垂直柱状」，不是「爆炸」。
	 *
	 * <p>梯度表用的是 3D 表的 (x, z) 两个分量 —— 与原库的 2D 表不同，
	 * 但「半径截断 + 梯度点积」这个决定远端行为的机制完全一致。
	 */
	private static double simplex2d(int seed, double x, double z) {
		// 偏斜（原库把这一步放在调用方）
		final double s = (x + z) * F2;
		final double xf = x + s;
		final double zf = z + s;

		final int i = saturatingFloor(xf);
		final int j = saturatingFloor(zf);
		final double xi = xf - i;
		final double zi = zf - j;

		final double t = (xi + zi) * G2;
		final double x0 = xi - t;
		final double z0 = zi - t;

		final int ip = i * PRIME_X;
		final int jp = j * PRIME_Y;

		double n0 = 0;
		double a = 0.5D - x0 * x0 - z0 * z0;
		if (a > 0) {
			n0 = (a * a) * (a * a) * gradCoord(seed, ip, jp, 0, x0, 0.0, z0);
		}

		double n2 = 0;
		double c = 2.0D * (1.0D - 2.0D * G2) * (1.0D / G2 - 2.0D) * t
				+ (-2.0D * (1.0D - 2.0D * G2) * (1.0D - 2.0D * G2) + a);
		if (c > 0) {
			final double x2 = x0 + (2.0D * G2 - 1.0D);
			final double z2 = z0 + (2.0D * G2 - 1.0D);
			n2 = (c * c) * (c * c) * gradCoord(seed, ip + PRIME_X, jp + PRIME_Y, 0,
					x2, 0.0, z2);
		}

		double n1 = 0;
		if (z0 > x0) {
			final double x1 = x0 + G2;
			final double z1 = z0 + (G2 - 1.0D);
			double b = 0.5D - x1 * x1 - z1 * z1;
			if (b > 0) {
				n1 = (b * b) * (b * b) * gradCoord(seed, ip, jp + PRIME_Y, 0, x1, 0.0, z1);
			}
		} else {
			final double x1 = x0 + (G2 - 1.0D);
			final double z1 = z0 + G2;
			double b = 0.5D - x1 * x1 - z1 * z1;
			if (b > 0) {
				n1 = (b * b) * (b * b) * gradCoord(seed, ip + PRIME_X, jp, 0, x1, 0.0, z1);
			}
		}

		return (n0 + n1 + n2) * 99.83685446303647D;
	}

	private static final double[] GRADIENTS_3D = {
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
