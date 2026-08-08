package io.alexjoest.stackupup.audit

/**
 * 守恒审计事件：一次真实投喂的只读快照。
 *
 * 守恒公式（只读审计，非补偿）：`storedDelta + remainderCount == offered`。
 * `simulate=true` 的投喂不产守恒事件、不改变状态；`balanced` 只按公式计算，不区分 simulate。
 */
data class ConservationEvent(
    /** 调用点标识，如 "DevAutomationServerDriver#probeTarget"。 */
    val callSite: String,
    /** 目标 IItemHandler 的实际类名，告警用于定位真实写入面。 */
    val handlerClassName: String,
    /** 投喂槽位。 */
    val slot: Int,
    /** 是否 simulate 投喂；true 时审计器不产事件。 */
    val simulate: Boolean,
    /** 本次投喂数量。 */
    val offered: Int,
    /** 投喂前槽内数量。 */
    val before: Int,
    /** 投喂后槽内数量。 */
    val after: Int,
    /** 投喂返回的余量数量。 */
    val remainderCount: Int,
) {
    /** 落库增量 = 投喂后 - 投喂前。 */
    val storedDelta: Int = after - before

    /** 守恒判定：(storedDelta + remainderCount) == offered。 */
    val balanced: Boolean = (storedDelta + remainderCount) == offered
}
