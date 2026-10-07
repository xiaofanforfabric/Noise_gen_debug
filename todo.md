# noise_gen_debug 任务计划

目标：对单个存档的噪声生成参数进行调整（MC 1.16.5 / Fabric）

## 决策（已确认）
- 注入点：`NoiseChunkGenerator` 构造期替换 `ChunkGeneratorSettings`（`@ModifyVariable`）
- GUI：纯原版 `Screen`，自绘控件，不加 Cloth Config / Mod Menu 依赖
- 工具链：Loom `0.7-SNAPSHOT` + Gradle `7.6`（当前 1.18-SNAPSHOT 需 JVM 25，本机只有 17）

## 步骤
- [ ] 1. 降级工具链（gradle.properties / gradle-wrapper.properties / build.gradle 插件 ID）
- [ ] 2. 跑通 `genSources`，拿到 1.16.5 真实映射源码
- [ ] 3. javap/源码核对噪声类真实签名与描述符
- [ ] 4. 实现噪声参数数据模型 + 存档级存储
- [ ] 5. 实现构造期 settings 替换 mixin
- [ ] 6. 实现创建世界页面的原版 Screen GUI
- [ ] 7. 构建验证
