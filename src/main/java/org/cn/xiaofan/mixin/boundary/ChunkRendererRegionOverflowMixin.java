package org.cn.xiaofan.mixin.boundary;

import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 防止极端坐标下区块渲染因 int 溢出而崩溃。
 *
 * <p>原版 {@code ChunkRendererRegion.create} 里有：
 * <pre>
 * int i = startPos.getX() - chunkRadius &gt;&gt; 4;
 * int k = endPos.getX()   + chunkRadius &gt;&gt; 4;
 * WorldChunk[][] chunks = new WorldChunk[k - i + 1][l - j + 1];
 * </pre>
 * 当坐标接近 {@code 2^31}（约 21.4 亿）时，{@code k - i + 1} 会 int 溢出成负值，
 * 于是 {@code new WorldChunk[负数]} 抛出 {@link NegativeArraySizeException}，
 * **客户端直接崩溃**（不是卡死）。
 *
 * <p>对策：按原版公式实算一遍，**只在真的会溢出时**返回 {@code null}。
 *
 * <p>注意<b>不要用猜测的阈值</b>：早期版本用 {@code 2^30} 做阈值，把
 * {@code x = 1073741808}（原版完全正常）也拦掉了，表现为「那片区域渲染器停止工作」。
 * 真实溢出点在 {@code 2^31} 附近。
 *
 * <p>为什么返回 null 是安全的：本方法签名本来就是 {@code @Nullable}，调用方
 * {@code ChunkBuilder.RebuildTask} 也显式接受 {@code @Nullable ChunkRendererRegion}，
 * 且内部还会主动把 region 置 null 表示「不渲染」。所以返回 null 只是让
 * **这个区块不被渲染**，游戏继续运行。
 */
@Mixin(ChunkRendererRegion.class)
public class ChunkRendererRegionOverflowMixin {

	@Inject(method = "create", at = @At("HEAD"), cancellable = true)
	private static void noise_gen_debug$guardOverflow(World world, BlockPos startPos, BlockPos endPos,
			int chunkRadius, CallbackInfoReturnable<ChunkRendererRegion> cir) {
		if (noise_gen_debug$willOverflow(startPos, endPos, chunkRadius)) {
			cir.setReturnValue(null);
		}
	}

	/**
	 * 精确判断原版那几行运算是否会溢出。
	 *
	 * <p>原版逻辑（调用方固定传 {@code startPos = origin-1}、{@code endPos = origin+16}、
	 * {@code chunkRadius = 1}）：
	 * <pre>
	 * int i = (startPos.x - radius) &gt;&gt; 4;
	 * int k = (endPos.x   + radius) &gt;&gt; 4;
	 * new WorldChunk[k - i + 1][...]
	 * </pre>
	 *
	 * <p>所以真正会出事的是：
	 * <ol>
	 *   <li>{@code startPos - radius} 或 {@code endPos + radius} 本身越界（极罕见）</li>
	 *   <li>{@code k - i + 1} 为负数 → {@code new WorldChunk[负数]}</li>
	 * </ol>
	 *
	 * <p><b>不要去猜阈值。</b>之前用 {@code 2^30} 做阈值把
	 * {@code x = 1073741808}（原本完全正常）也拦掉了，导致那片区域不渲染。
	 * 这里改为按原版公式<b>实算一遍</b>，只在该溢出时才拦。
	 */
	private static boolean noise_gen_debug$willOverflow(BlockPos startPos, BlockPos endPos, int chunkRadius) {
		return noise_gen_debug$axisOverflows(startPos.getX(), endPos.getX(), chunkRadius)
				|| noise_gen_debug$axisOverflows(startPos.getZ(), endPos.getZ(), chunkRadius);
	}

	/** 单轴复刻原版计算，检测中间结果是否翻号。 */
	private static boolean noise_gen_debug$axisOverflows(int start, int end, int radius) {
		// 第 1 步：加减 radius 本身就可能溢出。
		long startMinus = (long) start - radius;
		long endPlus = (long) end + radius;
		if (startMinus < Integer.MIN_VALUE || startMinus > Integer.MAX_VALUE
				|| endPlus < Integer.MIN_VALUE || endPlus > Integer.MAX_VALUE) {
			return true;
		}

		// 第 2 步：复刻 >> 4 与 k - i + 1。
		long i = ((int) startMinus) >> 4;
		long k = ((int) endPlus) >> 4;
		long size = k - i + 1;

		// 数组长度必须为正且不能大到离谱（超过 int 上限即溢出）。
		return size <= 0 || size > Integer.MAX_VALUE;
	}
}
