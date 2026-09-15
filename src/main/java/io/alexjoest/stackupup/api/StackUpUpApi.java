package io.alexjoest.stackupup.api;

import io.alexjoest.stackupup.Constants;
import io.alexjoest.stackupup.RuleRuntimeCoordinator;
import io.alexjoest.stackupup.StackLimitHooks;
import io.alexjoest.stackupup.StackUpUp;
import io.alexjoest.stackupup.StackUpUpConfig;
import io.alexjoest.stackupup.rules.io.RuleSourceLocator;
import io.alexjoest.stackupup.rules.io.RuleStateService;
import net.minecraft.item.ItemStack;

/**
 * 对外公开门面：供 CraftTweaker 等外部脚本读取动态堆叠上限与写入规则状态。
 *
 * 只做转发与 null 保护，不持有运行时状态、不新增 manager，也不暴露内部实现。
 */
public final class StackUpUpApi {
    private static final RuleStateService STATE_SERVICE =
        new RuleStateService(RuleSourceLocator.INSTANCE::resolveWorldMarkdownFile);

    private StackUpUpApi() {
    }

    /** 返回物品当前动态堆叠上限；无规则命中时为物品原始上限。 */
    public static int getLimit(ItemStack stack) {
        return getLimit(stack, Constants.VANILLA_STACK_LIMIT);
    }

    /** 返回物品当前动态堆叠上限；stack 为 null 时返回 fallback。 */
    public static int getLimit(ItemStack stack, int fallback) {
        if (stack == null) {
            return fallback;
        }
        return StackLimitHooks.applyDynamicStackLimit(stack, fallback);
    }

    /** 读取状态三态：null 表示状态存储不可用，false 表示键缺失，true 表示键已有值。 */
    public static synchronized Boolean getState(String name) {
        return STATE_SERVICE.getState(name);
    }

    /**
     * 写入状态，返回底层状态文件是否真被改写；存储不可用时返回 false。
     *
     * 完全委托 {@code StackUpUp.setState}，由它负责 gate 翻转判断与条件式 reload，
     * 避免门面复制一份会漂移的 gate 逻辑。
     */
    public static synchronized boolean setState(String name, boolean value) {
        return StackUpUp.setState(name, value);
    }

    /** 重载规则文件，返回本次重载报告是否不含 errors。 */
    public static boolean reload() {
        return RuleRuntimeCoordinator.INSTANCE
            .reload(StackUpUpConfig.general.enableDslRules)
            .getErrors()
            .isEmpty();
    }
}
