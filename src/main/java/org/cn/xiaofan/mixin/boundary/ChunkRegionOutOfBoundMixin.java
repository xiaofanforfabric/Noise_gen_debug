package org.cn.xiaofan.mixin.boundary;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让极端坐标下请求越界区块的<b>特征生成</b>安全失败（而不是崩溃或卡死）。
 *
 * <h2>问题背景</h2>
 * 方块坐标接近 {@code 2^31} 时，区块坐标会溢出。实测在
 * {@code CenterX = 134217727} 附近，所有特征生成都会去请求
 * {@code chunkX = -134217728}（溢出的「对称」区块）：
 * <pre>
 * 134217727 * 16 = 2147483632   ✅ 正常
 * 134217728 * 16 = 2147483648 = 2^31  ❌ int 溢出 → -134217728
 * </pre>
 *
 * <h2>为什么不能返回 null</h2>
 * 原版 {@code ChunkRegion.getChunk} 越界时会抛异常；直接返回 {@code null} 也不行，
 * 因为调用链<b>不判空</b>：
 * <pre>
 * ChunkRegion.getBlockState:141
 *     this.getChunk(pos.x &gt;&gt; 4, pos.z &gt;&gt; 4).getBlockState(pos)
 *                                          ^^^^^^^^^^^^^^^^ NPE
 * </pre>
 * 实测崩溃堆栈：{@code FlowerFeature.generate:27} → {@code WorldView.isAir:85}
 * → {@code ChunkRegion.getBlockState:141} → NPE。
 *
 * <h2>做法</h2>
 * 在 {@code getChunk} 返回 {@code null} 时，<b>补一个空的 {@link ProtoChunk}</b>。
 * 这样：
 * <ul>
 *   <li>{@code getBlockState} 拿到的是空区块 → 读出来全是空气</li>
 *   <li>特征生成判断「这里不能放花/湖/泉」→ <b>正常跳过</b></li>
 *   <li>不崩溃、不卡死，只是那片区域没有特征（可接受的降级）</li>
 * </ul>
 *
 * <p>用 {@code @Inject(at = RETURN)} 而不是改 HEAD，是为了<b>不干扰原版逻辑</b>：
 * 正常路径（非越界）完全不碰，只在原版准备抛异常、返回 null 的那种情况下兜底。
 * 注意原版在 {@code !create} 时也会正常返回 null（这是合法路径），
 * 所以这里只对 {@code create == true} 的越界情况补空区块。
 */
@Mixin(ChunkRegion.class)
public class ChunkRegionOutOfBoundMixin {

	private static final Logger LOGGER = LogManager.getLogger("noise_gen_debug/boundary");

	@Shadow
	@Final
	private ChunkPos lowerCorner;

	@Shadow
	@Final
	private ChunkPos upperCorner;

	@Inject(
			method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;",
			at = @At("HEAD"),
			cancellable = true)
	private void noise_gen_debug$safeEmptyChunk(int chunkX, int chunkZ,
			ChunkStatus leastStatus, boolean create,
			CallbackInfoReturnable<Chunk> cir) {
		// create=false 时原版本来就允许返回 null（且不抛异常），维持原样。
		if (!create) {
			return;
		}

		boolean outOfBound = chunkX < this.lowerCorner.x || chunkX > this.upperCorner.x
				|| chunkZ < this.lowerCorner.z || chunkZ > this.upperCorner.z;
		if (!outOfBound) {
			return;
		}

		noise_gen_debug$report(chunkX, chunkZ, leastStatus);

		// 关键：返回一个「空区块」而不是 null。
		// 空 ProtoChunk 里的方块全是空气，特征生成会正常跳过。
		ProtoChunk empty = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.NO_UPGRADE_DATA);
		cir.setReturnValue(empty);
	}

	/** 同一坐标只打一次，避免日志爆炸。 */
	private static final java.util.Set<Long> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static void noise_gen_debug$report(int chunkX, int chunkZ, ChunkStatus leastStatus) {
		long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
		if (!REPORTED.add(key)) {
			return;
		}
		LOGGER.warn("[boundary] 越界区块 ({}, {}) status={} → 返回空区块以降级",
				chunkX, chunkZ, leastStatus);
	}
}
