package io.alexjoest.stackupup.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode

/**
 * 关闭态字节码门（T12.4）：**全部为结构检查，不做行为验证**。
 *
 * 断言默认关闭态下守恒审计热路径的字节码形态：
 * - `Ae2ItemHandlerInsertLimiter.insertWithAudit`：无 `System.getProperty` 调用、无日志构造、
 *   事件对象分配位于 simulate/开关两个条件分支之后（关闭态直通 `insertItem` 返回，不构造事件）；
 * - `Ae2ItemHandlerInsertLimiter` 全类零次 `System.getProperty`（单次读取在 ConservationAuditor 类加载时）；
 * - `ConservationAuditor`：系统属性恰好读取一次（`<clinit>`，类加载时），`enabled()` 只读缓存字段，
 *   日志器只在 `<clinit>` 构造一次，默认关闭按字符串 `"true"` 比较；
 * - `ConservationReportWriter`：报告路径属性在类加载初始化路径中恰好读取一次，`write()` 不读配置（T12.5）；
 * - `DevAutomationServerDriver.probeTarget`：先单次判定开关再构造事件，不读系统属性。
 *
 * 结构检查通过只证明字节码形态，不证明运行行为；行为验证由 [ConservationAuditorTest] 与
 * `io.alexjoest.stackupup.core.Ae2ItemHandlerInsertLimiterConservationAuditTest` 承担。
 */
class ConservationAuditorBytecodeTest {
    @Test
    fun `insertWithAudit_closedState_shouldHaveNoConfigReadNoLoggerNoUnguardedEventAllocation`() {
        val clazz = classNode("io.alexjoest.stackupup.core.Ae2ItemHandlerInsertLimiter")
        val method = clazz.methods.single { it.name == "insertWithAudit" }
        val insns = method.insns()
        val text = method.insnText()

        // 红线：每次插入不读系统属性（单次读取在 ConservationAuditor 类加载时）
        assertTrue(
            text.none { it == "INVOKE java/lang/System.getProperty" },
            "关闭态热路径不得读取系统属性，实际指令: $text",
        )
        // 红线：关闭态无日志构造
        assertTrue(
            text.none { it.contains("LogManager") || it.contains("log4j") },
            "热路径不得构造日志器，实际指令: $text",
        )
        // 关闭态无统计分支：不得触碰告警记录集合
        assertTrue(
            text.none { it.contains("CopyOnWriteArrayList") || it.contains("recordedWarnings") },
            "热路径不得出现统计记录分支，实际指令: $text",
        )

        // 事件构造必须被开关分支守护：关闭态先经 simulate/开关判定直通 insertItem 返回
        val newIndex = insns.indexOfFirst {
            it is TypeInsnNode && it.opcode == Opcodes.NEW && it.desc == "io/alexjoest/stackupup/audit/ConservationEvent"
        }
        assertTrue(newIndex >= 0, "开启分支必须保留事件构造")
        val jumpsBeforeNew = insns.take(newIndex).count { it.opcode in CONDITIONAL_JUMP_OPCODES }
        assertEquals(2, jumpsBeforeNew, "事件构造前应只有 simulate 与开关两个条件分支（关闭态无统计分支）")
        val directInsert = insns.indexOfFirst {
            it is MethodInsnNode && it.owner == "net/minecraftforge/items/IItemHandler" && it.name == "insertItem"
        }
        assertTrue(directInsert >= 0 && directInsert < newIndex, "关闭态直通 insertItem 调用必须位于事件构造之前")
        val switchRead = insns.indexOfFirst {
            it is MethodInsnNode && it.owner == "io/alexjoest/stackupup/audit/ConservationAuditor" && it.name == "enabled"
        }
        assertTrue(switchRead >= 0 && switchRead < newIndex, "开关判定必须位于事件构造之前（关闭态直通返回）")
        val auditCall = insns.indexOfFirst {
            it is MethodInsnNode && it.owner == "io/alexjoest/stackupup/audit/ConservationAuditor" && it.name == "audit"
        }
        assertTrue(auditCall > newIndex, "审计调用必须位于事件构造之后（开启分支内）")
    }

    @Test
    fun `limiter_shouldNotReadEnablePropertyAnywhere`() {
        val clazz = classNode("io.alexjoest.stackupup.core.Ae2ItemHandlerInsertLimiter")
        val reads = clazz.methods.flatMap { method ->
            method.instructions.iterator().asSequence()
                .filterIsInstance<MethodInsnNode>()
                .filter { it.owner == "java/lang/System" && it.name == "getProperty" }
                .map { method.name }
        }.toList()

        assertEquals(emptyList<String>(), reads, "限流器全类不得读取系统属性（单次读取在 ConservationAuditor 类加载时）")
    }

    @Test
    fun `auditor_shouldReadEnablePropertyExactlyOnceAtClassLoad`() {
        val clazz = classNode("io.alexjoest.stackupup.audit.ConservationAuditor")
        val reads = clazz.methods.flatMap { method ->
            method.instructions.iterator().asSequence()
                .filterIsInstance<MethodInsnNode>()
                .filter { it.owner == "java/lang/System" && it.name == "getProperty" }
                .map { method.name }
        }.toList()

        assertEquals(listOf("<clinit>"), reads, "系统属性必须且只能在类加载（<clinit>）时读取一次")
    }

    @Test
    fun `reportWriter_shouldReadReportPathPropertyOnlyDuringInitialization`() {
        val clazz = classNode("io.alexjoest.stackupup.audit.ConservationReportWriter")
        val reads = clazz.methods.flatMap { method ->
            method.instructions.iterator().asSequence()
                .filterIsInstance<MethodInsnNode>()
                .filter { it.owner == "java/lang/System" && it.name == "getProperty" }
                .map { method.name }
        }.toList()

        assertEquals(1, reads.size, "报告路径必须且只能在类加载时读取一次，实际: $reads")
        assertTrue(
            reads.all { it == "<clinit>" || it == "<init>" || it == "resolveReportFile" },
            "报告路径读取必须位于初始化路径内（write 热路径不得读配置），实际: $reads",
        )
        val write = clazz.methods.single { it.name == "write" }
        assertTrue(
            write.insnText().none { it == "INVOKE java/lang/System.getProperty" },
            "write() 不得读取系统属性，实际指令: ${write.insnText()}",
        )
    }

    @Test
    fun `auditor_shouldConstructLoggerOnlyOnceAtClassLoad`() {
        val clazz = classNode("io.alexjoest.stackupup.audit.ConservationAuditor")
        val getLogger = clazz.methods.flatMap { method ->
            method.instructions.iterator().asSequence()
                .filterIsInstance<MethodInsnNode>()
                .filter { it.owner == "org/apache/logging/log4j/LogManager" && it.name == "getLogger" }
                .map { method.name }
        }.toList()

        assertEquals(listOf("<clinit>"), getLogger, "日志器必须且只能在类加载时构造一次")
    }

    @Test
    fun `auditor_shouldCompareDefaultOffAgainstTrueLiteralAtClassLoad`() {
        val clazz = classNode("io.alexjoest.stackupup.audit.ConservationAuditor")
        val clinitText = clazz.methods.single { it.name == "<clinit>" }.insnText()

        assertTrue(
            "INVOKE java/lang/System.getProperty" in clinitText,
            "<clinit> 必须包含唯一一次系统属性读取，实际指令: $clinitText",
        )
        assertTrue(
            "LDC true" in clinitText,
            "开关初值必须按字符串 \"true\" 比较（未设置属性时默认关闭），实际指令: $clinitText",
        )
    }

    @Test
    fun `enabledMethod_shouldOnlyReadCachedFieldWithoutConfigAccess`() {
        val clazz = classNode("io.alexjoest.stackupup.audit.ConservationAuditor")
        val enabled = clazz.methods.single { it.name == "enabled" }
        val text = enabled.insnText()

        assertTrue(text.none { it.startsWith("INVOKE") }, "enabled() 必须只读缓存字段，不得发起任何调用，实际指令: $text")
        assertTrue(
            "FIELD io/alexjoest/stackupup/audit/ConservationAuditor.enabledState" in text,
            "enabled() 必须读取缓存开关字段，实际指令: $text",
        )
    }

    @Test
    fun `auditMethod_shouldNotReadConfigOrConstructLogger`() {
        val clazz = classNode("io.alexjoest.stackupup.audit.ConservationAuditor")
        val audit = clazz.methods.single { it.name == "audit" }
        val text = audit.insnText()

        assertTrue(
            text.none { it == "INVOKE java/lang/System.getProperty" },
            "audit() 不得读取系统属性（只读缓存开关），实际指令: $text",
        )
        assertTrue(text.none { it.contains("LogManager") }, "audit() 不得构造日志器（logger 只在类加载时构造）")
    }

    @Test
    fun `probeTarget_shouldGateEventAllocationBehindSingleSwitchDecision`() {
        val clazz = classNode("io.alexjoest.stackupup.dev.DevAutomationServerDriver")
        val probe = clazz.methods.single { it.name == "probeTarget" }
        val text = probe.insnText()

        assertTrue(
            text.none { it == "INVOKE java/lang/System.getProperty" },
            "探针不得读取系统属性（经 ConservationAuditor.enabled 单次判定），实际指令: $text",
        )
        val newIndex = text.indexOf("NEW io/alexjoest/stackupup/audit/ConservationEvent")
        assertTrue(newIndex >= 0, "开启分支必须保留事件构造")
        val switchDecision = text.indexOf("INVOKE io/alexjoest/stackupup/audit/ConservationAuditor.enabled")
        assertTrue(switchDecision >= 0 && switchDecision < newIndex, "探针必须先单次判定开关，再构造事件")
    }

    private fun classNode(binaryName: String): ClassNode {
        val resourcePath = binaryName.replace('.', '/') + ".class"
        val bytes = requireNotNull(
            ConservationAuditorBytecodeTest::class.java.classLoader.getResourceAsStream(resourcePath),
        ) { "无法读取类字节码: $binaryName" }.use { it.readBytes() }
        return ClassNode().also { ClassReader(bytes).accept(it, 0) }
    }

    private fun MethodNode.insns(): Array<AbstractInsnNode> = instructions.toArray()

    /** 指令的紧凑文本序列（只保留调用、类型、字段与常量），便于断言指令顺序。 */
    private fun MethodNode.insnText(): List<String> = instructions.iterator().asSequence().map { insn ->
        when (insn) {
            is MethodInsnNode -> "INVOKE ${insn.owner}.${insn.name}"
            is TypeInsnNode -> if (insn.opcode == Opcodes.NEW) "NEW ${insn.desc}" else "TYPE ${insn.desc}"
            is FieldInsnNode -> "FIELD ${insn.owner}.${insn.name}"
            is LdcInsnNode -> "LDC ${insn.cst}"
            else -> "OP ${insn.opcode}"
        }
    }.toList()

    companion object {
        /** 条件分支 opcode（IFEQ..IF_ACMPNE 与 IFNULL/IFNONNULL）。 */
        private val CONDITIONAL_JUMP_OPCODES: Set<Int> =
            (Opcodes.IFEQ..Opcodes.IF_ACMPNE).toSet() + Opcodes.IFNULL + Opcodes.IFNONNULL
    }
}
