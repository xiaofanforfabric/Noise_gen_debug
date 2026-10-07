/*
 * 许可例外：本文件派生自 FarLandsTraveler
 * (https://github.com/SmallmanSeries/FarLandsTraveler)，该项目以 LGPL-3.0 发布。
 * 因此本文件按 LGPL-3.0-only 发布，【不适用】本仓库根部的 MIT 条款。
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package org.cn.xiaofan.noise;

/**
 * 「边缘之地」基岩噪声级联（Fringe Lands base-3D-noise cascade）。
 *
 * <p><b>本文件由 {@code tools/gen_fringe_cascade.py} 从 FarLandsTraveler 的
 * {@code base_3d_noise_far_lands.json} 自动生成，请勿手改。</b>
 *
 * <h2>结构</h2>
 *
 * <p>原版是一个二叉判定树：{@code range_choice(box_select, when_in_range, when_out_of_range)}。
 * 因为 {@code box_select} 只依赖 (x, z)，整棵树可以退化成对 (x, z) 的纯函数。
 * 这里把树展平成节点表，用循环求值（避免递归深度问题，也便于校验）。
 *
 * <p>节点编码（每节点 7 个 int）：
 * <pre>
 *   type 0（常数）： [0, 值×1000, -, -, -, -, -]
 *   type 1（盒子）： [1, minX, maxX, minZ, maxZ, 在盒内的子节点, 不在盒内的子节点]
 *   type 2（噪声）： [2, 规格下标, -, -, -, -, -]
 * </pre>
 *
 * <p>Y 轴被忽略：本文件里所有盒子的 Y 范围都是
 * {@code [-25101648, +25101649)}，覆盖了 MC 全部可能的方块高度。
 *
 * @see <a href="https://github.com/SmallmanSeries/FarLandsTraveler">FarLandsTraveler</a>
 */
public final class FringeCascade {

	private FringeCascade() {
	}

	/** 节点总数。 */
	public static final int NODE_COUNT = 55;

	/** 噪声规格数量。 */
	public static final int SPEC_COUNT = 26;

	/** 根节点下标。 */
	public static final int ROOT = 0;

	// type, a, b, c, d, e, f —— 见类注释
	private static final int[][] NODES = {
			new int[] {1, -14900000, 14900000, -14900000, 14900000, 26, 1}, new int[] {1, 0, 16777216, -12550824, 12550824, 25, 2}, new int[] {1, -16777216, 0, -12550824, 12550824, 24, 3},
			new int[] {1, -12550824, 12550824, 0, 16777216, 23, 4}, new int[] {1, -12550824, 12550824, -16777216, 0, 22, 5}, new int[] {1, 14900000, 48454432, 12550824, 14900001, 21, 6},
			new int[] {1, 12550824, 14900001, 14900000, 48454432, 20, 7}, new int[] {1, -14900001, -12550824, 14900000, 48454432, 19, 8}, new int[] {1, -48454432, -14900000, 12550824, 14900001, 18, 9},
			new int[] {1, 14900000, 48454432, -14900001, -12550824, 17, 10}, new int[] {1, 12550824, 14900001, -48454432, -14900000, 16, 11}, new int[] {1, -14900001, -12550824, -48454432, -14900000, 15, 12},
			new int[] {1, -48454432, -14900000, -14900001, -12550824, 14, 13}, new int[] {0, -1, 0, 0, 0, 0, 0}, new int[] {2, 0, 0, 0, 0, 0, 0},
			new int[] {2, 1, 0, 0, 0, 0, 0}, new int[] {2, 2, 0, 0, 0, 0, 0}, new int[] {2, 3, 0, 0, 0, 0, 0},
			new int[] {2, 4, 0, 0, 0, 0, 0}, new int[] {2, 5, 0, 0, 0, 0, 0}, new int[] {2, 6, 0, 0, 0, 0, 0},
			new int[] {2, 7, 0, 0, 0, 0, 0}, new int[] {2, 8, 0, 0, 0, 0, 0}, new int[] {2, 9, 0, 0, 0, 0, 0},
			new int[] {2, 10, 0, 0, 0, 0, 0}, new int[] {2, 11, 0, 0, 0, 0, 0}, new int[] {1, -13400000, 13400000, -13400000, 13400000, 32, 27},
			new int[] {1, -14900000, 14900000, -12550824, 12550824, 31, 28}, new int[] {1, -12550824, 12550824, -14900000, 14900000, 30, 29}, new int[] {2, 12, 0, 0, 0, 0, 0},
			new int[] {2, 13, 0, 0, 0, 0, 0}, new int[] {2, 14, 0, 0, 0, 0, 0}, new int[] {1, -13002848, 13002848, -13002848, 13002848, 52, 33},
			new int[] {1, -13005488, 13005488, -13005488, 13005488, 51, 34}, new int[] {1, -13008208, 13008208, -13008208, 13008208, 50, 35}, new int[] {1, -13010560, 13010560, -13010560, 13010560, 49, 36},
			new int[] {1, -13012848, 13012848, -13012848, 13012848, 48, 37}, new int[] {1, -13014512, 13014512, -13014512, 13014512, 47, 38}, new int[] {1, -13015872, 13015872, -13015872, 13015872, 46, 39},
			new int[] {1, -13016784, 13016784, -13016784, 13016784, 45, 40}, new int[] {1, -13017520, 13017520, -13017520, 13017520, 44, 41}, new int[] {1, -13017984, 13017984, -13017984, 13017984, 43, 42},
			new int[] {2, 15, 0, 0, 0, 0, 0}, new int[] {2, 16, 0, 0, 0, 0, 0}, new int[] {2, 17, 0, 0, 0, 0, 0},
			new int[] {2, 18, 0, 0, 0, 0, 0}, new int[] {2, 19, 0, 0, 0, 0, 0}, new int[] {2, 20, 0, 0, 0, 0, 0},
			new int[] {2, 21, 0, 0, 0, 0, 0}, new int[] {2, 22, 0, 0, 0, 0, 0}, new int[] {2, 23, 0, 0, 0, 0, 0},
			new int[] {2, 24, 0, 0, 0, 0, 0}, new int[] {1, -12551417, -12550908, -12550941, -12550560, 54, 53}, new int[] {2, 25, 0, 0, 0, 0, 0},
			new int[] {0, -1, 0, 0, 0, 0, 0}
	};

	private static final double[] S_xScale = {2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 0.25D, 0.25D, 6.25e+41D, 6.25e+41D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D};
	private static final double[] S_yScale = {0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D, 0.125D};
	private static final double[] S_zScale = {2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 2.5e+21D, 1.5625e+42D, 1.5625e+42D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D, 0.25D};
	private static final double[] S_xFactor = {80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 1.067658984D, 80D, 1.067658984D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D};
	private static final double[] S_yFactor = {160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D, 160D};
	private static final double[] S_zFactor = {80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 1.067658984D, 1.067658984D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D, 80D};
	private static final double[] S_smear = {8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D, 8D};
	private static final double[] S_xShift = {14899999D, 12550807D, -12550807D, -14899999D, 14899999D, 12550807D, -12550807D, -14899999D, 0D, 0D, 11630008D, -11630008D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D};
	private static final double[] S_yShift = {0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D};
	private static final double[] S_zShift = {12550759D, 14899999D, 14899999D, 12550759D, -12550759D, -14899999D, -14899999D, -12550759D, 13435680D, -13435680D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D};
	private static final int[] S_overflowable = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
	private static final int[] S_rStart = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 13017984, 13017520, 13016784, 13015872, 13014512, 13012848, 13010560, 13008208, 13005488, 13002848, 13000000};
	private static final int[] S_rLen = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16};
	private static final int[] S_rCount = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 172647, 29, 23, 19, 17, 13, 11, 7, 5, 3, 2};

	/**
	 * 对 (方块X, 方块Z) 求值。
	 *
	 * @return 非负 = 应使用该下标的噪声规格；
	 *         {@link #CONST_NODE} = 该处是常数，取值见 {@link #constValue(int)}
	 */
	public static int resolveNode(int blockX, int blockZ) {
		int n = ROOT;
		for (int guard = 0; guard < 256; guard++) {
			int[] nd = NODES[n];
			if (nd[0] == 1) {
				boolean inside = blockX >= nd[1] && blockX < nd[2]
						&& blockZ >= nd[3] && blockZ < nd[4];
				n = inside ? nd[5] : nd[6];
				continue;
			}
			return n;   // 0 = 常数节点，2 = 噪声节点
		}
		throw new IllegalStateException("FringeCascade: node walk did not terminate");
	}

	/** 节点是否为常数。 */
	public static boolean isConst(int node) {
		return NODES[node][0] == 0;
	}

	/** 常数节点的取值。 */
	public static double constValue(int node) {
		return NODES[node][1] / 1000.0D;
	}

	/** 噪声节点对应的规格下标。 */
	public static int specOf(int node) {
		return NODES[node][1];
	}

	public static double xScale(int spec) {
		return S_xScale[spec];
	}

	public static double yScale(int spec) {
		return S_yScale[spec];
	}

	public static double zScale(int spec) {
		return S_zScale[spec];
	}

	public static double xFactor(int spec) {
		return S_xFactor[spec];
	}

	public static double yFactor(int spec) {
		return S_yFactor[spec];
	}

	public static double zFactor(int spec) {
		return S_zFactor[spec];
	}

	public static double smear(int spec) {
		return S_smear[spec];
	}

	public static double xShift(int spec) {
		return S_xShift[spec];
	}

	public static double yShift(int spec) {
		return S_yShift[spec];
	}

	public static double zShift(int spec) {
		return S_zShift[spec];
	}

	public static boolean overflowable(int spec) {
		return S_overflowable[spec] != 0;
	}

	public static int rStart(int spec) {
		return S_rStart[spec];
	}

	public static int rLen(int spec) {
		return S_rLen[spec];
	}

	public static int rCount(int spec) {
		return S_rCount[spec];
	}

	/** 该规格是否启用循环（三个 repeat 字段都为正值）。 */
	public static boolean repeating(int spec) {
		return rStart(spec) > 0 && rLen(spec) > 0 && rCount(spec) > 0;
	}
}
