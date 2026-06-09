package io.alexjoest.stackupup.rules.io

import io.alexjoest.stackupup.rules.LocalizedMessage
import io.alexjoest.stackupup.rules.RuleMessageKey
import io.alexjoest.stackupup.rules.RuleMessages
import io.alexjoest.stackupup.rules.compile.CompiledRule
import io.alexjoest.stackupup.rules.compile.RuleSnapshot
import java.io.File

/**
 * 从 Markdown 文档中的规则代码块加载 DSL 规则。
 */
internal object MarkdownRuleSource {
    /**
     * 读取单个 Markdown 规则文件。
     */
    fun fromFile(file: File, gateContext: RuleGateContext = RuleGateContext.EMPTY): RuleLoadResult {
        RuleFileTemplate.ensureExists(file)
        return fromLines(file.readLines(Charsets.UTF_8), file.name, gateContext)
    }

    /**
     * 读取多个 Markdown 规则文件，并为每个文件独立合并本文件 state。
     */
    fun fromFiles(files: List<File>, gateContext: RuleGateContext = RuleGateContext.EMPTY): RuleLoadResult {
        return fromParsedFiles(files.mapNotNull { file ->
            if (!file.exists()) {
                null
            } else {
                ParsedMarkdownFile(
                    sourceName = file.name,
                    document = MarkdownStateParser.parse(file.readLines(Charsets.UTF_8)),
                )
            }
        }, gateContext)
    }

    /**
     * 使用已解析的 Markdown state 文档加载规则，避免同一文件重复解析。
     */
    fun fromParsedFiles(
        files: List<ParsedMarkdownFile>,
        gateContext: RuleGateContext = RuleGateContext.EMPTY,
    ): RuleLoadResult {
        val allRules = ArrayList<CompiledRule>()
        val allErrors = ArrayList<LocalizedMessage>()
        for (file in files) {
            val effectiveContext = gateContext.copy(states = gateContext.states + file.document.states)
            val result = collectInputs(file.document.lines, file.sourceName, effectiveContext)
            allRules += result.snapshot.rules
            allErrors += result.errors
        }
        return RuleLoadResult(
            snapshot = RuleSnapshot(System.nanoTime(), allRules),
            errors = allErrors,
        )
    }

    /**
     * 从内存中的 Markdown 行加载规则。
     */
    fun fromLines(lines: List<String>, sourceName: String = "markdown", gateContext: RuleGateContext = RuleGateContext.EMPTY): RuleLoadResult {
        val effectiveContext = gateContext.copy(states = gateContext.states + MarkdownStateParser.parse(lines).states)
        return collectInputs(lines, sourceName, effectiveContext)
    }

    private fun collectInputs(lines: List<String>, sourceName: String, gateContext: RuleGateContext): RuleLoadResult {
        val document = MarkdownContainerScanner.scan(lines)
        val inputs = ArrayList<RuleLineLoader.RuleLineInput>()
        val gateErrors = ArrayList<LocalizedMessage>()
        for (section in document.ruleSections) {
            collectSectionInputs(section, gateContext, sourceName, inputs, gateErrors)
        }
        val dslResult = RuleLineLoader.load(
            RuleConditionalPreprocessor.filter(inputs, gateContext),
        )
        return dslResult.copy(errors = dslResult.errors + gateErrors)
    }

    private fun collectSectionInputs(
        section: MarkdownSection,
        gateContext: RuleGateContext,
        sourceName: String,
        target: MutableList<RuleLineLoader.RuleLineInput>,
        gateErrors: MutableList<LocalizedMessage>,
    ) {
        val gateStack = ArrayList<MarkdownGateFrame>()
        for (block in section.blocks) {
            when (block) {
                is MarkdownBlock.Heading -> {
                    if (block.level <= 1) {
                        continue
                    }
                    while (gateStack.size >= block.level - 1) {
                        gateStack.removeAt(gateStack.lastIndex)
                    }
                    if (block.title == "always") {
                        gateStack += MarkdownGateFrame(null)
                        continue
                    }
                    val parsed = MarkdownGateParser.parse(block.title)
                    if (parsed is MarkdownGateParseResult.Failure) {
                        gateErrors += RuleMessages.message(
                            RuleMessageKey.GATE_PARSE_ERROR,
                            sourceName,
                            block.lineNumber,
                            parsed.message,
                        )
                    }
                    gateStack += MarkdownGateFrame(parsed)
                }
                is MarkdownBlock.FencedCodeBlock -> {
                    if (!block.isRuleBlock) {
                        continue
                    }
                    emitRuleBlock(block, gateStack, gateContext, sourceName, target)
                }
                else -> Unit
            }
        }
    }

    private fun emitRuleBlock(
        block: MarkdownBlock.FencedCodeBlock,
        gateStack: List<MarkdownGateFrame>,
        gateContext: RuleGateContext,
        sourceName: String,
        target: MutableList<RuleLineLoader.RuleLineInput>,
    ) {
        var active = true
        for (frame in gateStack) {
            val parsed = frame.parsed
            active = active && (parsed == null || (parsed is MarkdownGateParseResult.Success && gateContext.matches(parsed.expression)))
        }
        if (!active) {
            return
        }

        for ((index, line) in block.lines.withIndex()) {
            target += RuleLineLoader.RuleLineInput(
                content = line,
                lineNumber = block.startLine + index + 1,
                sourceName = sourceName,
            )
        }
    }

    private data class MarkdownGateFrame(val parsed: MarkdownGateParseResult?)
}

/**
 * 已解析的 Markdown 规则文件，用于在 reload 流程中复用 state 解析结果。
 */
internal data class ParsedMarkdownFile(
    val sourceName: String,
    val document: MarkdownStateDocument,
)
