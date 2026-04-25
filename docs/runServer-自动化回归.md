# runServer 自动化回归

## 目标

当前项目默认以 `runServer` 自动化回归作为主验证入口。

原因：

1. 不依赖渲染与 GUI，结果更稳定
2. 直接验证 `ItemStack` / `metadata` / 矿辞 / 插槽上限
3. 更适合作为 Mixin / ASM 收缩时的主回归基线

## 主入口

```powershell
.\gradlew.bat runServerAutoTestMatrix
```

IntelliJ 导入 Gradle 后，也可以直接运行：

```text
2a. Run Server AutoTest Matrix
```

## 当前覆盖

### GT / metadata 样例

1. `gregtech:meta_ingot@324`
2. `gregtech:meta_plate@324`
3. `gregtech:meta_dust@324`
4. `gregtech:meta_item_1@516`

### 外部兼容探针

1. `refinedstorage_grid_extract`
2. `refinedstorage_portable_grid_extract`
3. `refinedstorage_storage_monitor_extract`
4. `cyclopscore_simple_inventory_limit`
5. `colossalchests_inventory_limit`
6. `combined_inv_wrapper_limit`
7. `inv_wrapper_limit`
8. `ranged_wrapper_limit`
9. `sided_inv_wrapper_limit`
10. `slot_item_handler_limit`

## 验证维度

1. 规则解析上限
2. 真实 `ItemStack.maxStackSize`
3. 插槽上限
4. 插入大于 `64` 数量后的存入与剩余
5. 外部模组的提取 / 库存限制路径

## 相关实现

1. `DevAutomationServerDriver`
2. `DevTargetRuntimeResolver`
3. `DevCompatProbeRunner`
4. `DevInventoryCompatProbes`
5. `DevRefinedStorageCompatProbes`
6. `DevWrapperCompatProbes`

## 开发期模组建议

推荐把开发期联调 jar 放到：

```text
local-dev-mods/
```

说明：

1. `run/mods/*.jar` 更适合临时运行验证
2. `local-dev-mods/*.jar` 更适合长期开发联调

## 当前判断

1. `runClient` 仍然有价值，主要用于 GUI / tooltip / 客户端交互验证
2. 但默认主回归应优先看 `runServerAutoTestMatrix`
3. 若 server matrix 先失败，不应先去怀疑客户端显示链
