package org.cn.xiaofan.mixin.border;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.WorldView;
import org.cn.xiaofan.client.NoiseParamsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 去掉「边界外一律全亮」的光幕。
 *
 * <p><b>这才是你看到的那层"光幕墙"的真正来源。</b>
 *
 * <p>原版 {@code WorldView.getLightLevel(BlockPos, int)} 有一段硬编码的边界判断：
 * <pre>
 * return pos.getX() &gt;= -30000000 &amp;&amp; pos.getZ() &gt;= -30000000
 *     &amp;&amp; pos.getX() &lt; 30000000 &amp;&amp; pos.getZ() &lt; 30000000
 *     ? this.getBaseLightLevel(pos, ambientDarkness)
 *     : 15;   // 超出边界 → 一律当作"亮度 15"（全亮）
 * </pre>
 *
 * <p>于是坐标一旦超过 ±30000000，光照全部拉满，视觉上就是一层刺眼的白色幕布。
 * 把这段判断整个删掉，恒为真实光照即可。
 *
 * <p>注意这是 {@code @Overwrite}（原版把它写在 interface 的 default 方法里，
 * 没有别的注入点可以干净地绕过这个三元判断）。参考 geniiii/FarLands（MIT）。
 */
@Mixin(WorldView.class)
public interface WorldViewLightMixin extends BlockRenderView {

	/**
	 * @author
	 * @reason
	 */
	@Overwrite
	default int getLightLevel(BlockPos pos, int ambientDarkness) {
		// 开关关闭时保持原版行为（边界外一律全亮），避免影响正常世界。
		if (!NoiseParamsState.isWorldBorderDisabled()) {
			return pos.getX() >= -30000000 && pos.getZ() >= -30000000
					&& pos.getX() < 30000000 && pos.getZ() < 30000000
					? this.getBaseLightLevel(pos, ambientDarkness)
					: 15;
		}

		return this.getBaseLightLevel(pos, ambientDarkness);
	}
}
