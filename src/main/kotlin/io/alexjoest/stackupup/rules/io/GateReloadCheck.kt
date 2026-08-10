package io.alexjoest.stackupup.rules.io

/**
 * 判断 state 写入是否值得触发 reload。
 *
 * reload 的唯一收益是让 gate 求值结果变化生效：若被写入的 state 没有被任何 gate
 * 表达式引用，或引用了但求值结果不变，reload 只会重建一次缓存，可以跳过。
 * setState 频率低（手动命令或整合包阶段切换），这里直接现场扫描源文件，不引入缓存。
 *
 * gate 收集规则与 [MarkdownRuleSource.collectSectionInputs] 保持一致：只收集
 * `# rules` 章节内 level > 1、标题非 `always`、解析成功的 gate 表达式。
 */
internal object GateReloadCheck {
    /**
     * 用旧/新两套 state 集合分别求值全部 gate，任一结果变化返回 true。
     *
     * @param fileLines 写入后（或待写入）的 markdown 文件内容
     * @param oldStates 写入前的完整 state 集合（含文档声明与其他外部写入）
     * @param newStates 写入后的完整 state 集合
     */
    fun needsReload(fileLines: List<String>, oldStates: Map<String, Boolean>, newStates: Map<String, Boolean>): Boolean {
        val gates = collectGates(fileLines)
        if (gates.isEmpty()) {
            return false
        }
        val loadedMods = RuleGateContext.fromLoadedMods().loadedMods
        val oldContext = RuleGateContext(loadedMods, oldStates)
        val newContext = RuleGateContext(loadedMods, newStates)
        return gates.any { it.evaluate(oldContext) != it.evaluate(newContext) }
    }

    private fun collectGates(lines: List<String>): List<RuleGateExpression> {
        val document = MarkdownContainerScanner.scan(lines)
        val gates = ArrayList<RuleGateExpression>()
        for ((_, _, blocks) in document.ruleSections) {
            for (block in blocks) {
                if (block !is MarkdownBlock.Heading || block.level <= 1 || block.title == "always") {
                    continue
                }
                val parsed = MarkdownGateParser.parse(block.title)
                if (parsed is MarkdownGateParseResult.Success) {
                    gates += parsed.expression
                }
            }
        }
        return gates
    }
}
