package io.alexjoest.stackupup.mixin.late.ic2classic

import io.alexjoest.stackupup.StackUpUpIds
import io.alexjoest.stackupup.bootstrap.StackUpUpMixinConnector
import io.alexjoest.stackupup.compat.ic2classic.SlotBaseNativeLimit
import io.alexjoest.stackupup.core.InventoryStackLimitHook
import io.alexjoest.stackupup.core.resetInventoryExpansionForTesting
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class Ic2ClassicMixinSourceTest {
    private companion object {
        const val TARGET_CLASS = "ic2.core.inventory.slots.SlotBase"
        const val I_HAS_INVENTORY = "ic2/core/inventory/base/IHasInventory"
        const val WRITER_DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;)V"
        const val SETTER_DESCRIPTOR = "(ILnet/minecraft/item/ItemStack;)V"
        const val RAW_JAR = "IC2Classic-1.12.2-1.5.15.jar"
        const val RAW_SHA256 = "f0b7efa10f12380c196a3c1df31d59c78ed806198e95049e06436ece00a4ee2c"
        const val DEOBF_JAR = "IC2Classic-1.12.2-1.5.15-deobf.jar"
        const val DEOBF_SHA256 = "df99652b76badc165e3e6427abd0ddaeb25bb1e7eca3f842ca2b30adf0955bf4"
        const val RAW_PATH_PROPERTY = "stackupup.ic2classic.rawJar"
        const val DEOBF_PATH_PROPERTY = "stackupup.ic2classic.deobfJar"
        const val RAW_PATH_ENV = "STACKUPUP_IC2CLASSIC_RAW_JAR"
        const val DEOBF_PATH_ENV = "STACKUPUP_IC2CLASSIC_DEOBF_JAR"
    }

    @BeforeEach
    fun resetExpansion() {
        resetInventoryExpansionForTesting()
    }

    @AfterEach
    fun restoreExpansion() {
        resetInventoryExpansionForTesting()
    }

    @Test
    fun classicConfigs_shouldUseClassicTargetWithoutTouchingExperimentalTarget() {
        val lateConfig = read("src/main/resources/mixins.stackupup.late.ic2classic.json")
        val capacityConfig = read("src/main/resources/mixins.stackupup.capacity.late.ic2classic.json")
        val classicMixin = read("src/main/java/io/alexjoest/stackupup/mixin/late/ic2classic/SlotBaseMixin.java")
        val nativeLimitUtility = read("src/main/java/io/alexjoest/stackupup/compat/ic2classic/SlotBaseNativeLimit.java")
        val experimentalMixin = read("src/main/java/io/alexjoest/stackupup/mixin/late/ic2/InvSlotMixin.java")

        assertTrue(lateConfig.contains("io.alexjoest.stackupup.mixin.late.ic2classic"))
        assertTrue(lateConfig.contains("SlotBaseMixin"), "native-64 safety mixin must be in the always-queued config")
        assertFalse(capacityConfig.contains("SlotBaseMixin"), "optional capacity config must remain separate")
        assertTrue(classicMixin.contains("@Pseudo"))
        assertTrue(classicMixin.contains("remap = false"))
        assertTrue(classicMixin.contains("require = 1"))
        assertTrue(classicMixin.contains(StackUpUpIds.IC2_CLASSIC_TARGET_CLASS))
        assertTrue(classicMixin.contains("method = {\"func_75219_a()I\", \"getSlotStackLimit()I\"}"))
        assertTrue(classicMixin.contains("getSlotStackLimit()I"))
        assertTrue(classicMixin.contains("SlotBaseNativeLimit.clamp(original)"))
        assertTrue(nativeLimitUtility.contains("public static int clamp(int original)"))
        assertTrue(nativeLimitUtility.contains("return original > VANILLA_STACK_LIMIT ? VANILLA_STACK_LIMIT : original"))
        assertFalse(classicMixin.contains("func_75215_d"), "不得在无 remainder 返回路径的 writer 上做截断注入")
        assertFalse(classicMixin.contains("setCount"), "不得使用写入后截断或补偿")
        assertFalse(classicMixin.contains(StackUpUpIds.IC2_EXPERIMENTAL_TARGET_CLASS))
        assertTrue(experimentalMixin.contains(StackUpUpIds.IC2_EXPERIMENTAL_TARGET_CLASS))
        assertFalse(experimentalMixin.contains(StackUpUpIds.IC2_CLASSIC_TARGET_CLASS))
    }

    @Test
    fun classicRegistration_shouldUseIc2ModIdForBothProfiles() {
        InventoryStackLimitHook.configureExpansionEnabled(true)
        val queued = queueIc2()

        assertTrue(queued.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertTrue(queued.contains(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertTrue(queued.contains(StackUpUpIds.LATE_IC2_MIXIN_CONFIG))
        assertTrue(queued.contains("mixins.stackupup.capacity.late.ic2.json"))

        val absent = queueIc2(modPresent = false)
        assertFalse(absent.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertFalse(absent.contains(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG))
    }

    @Test
    fun classicCapacity_shouldStayNativeWhenExpansionIsOffOrBlacklisted() {
        val connector = StackUpUpMixinConnector()
        val alwaysQueuedConfig = read("src/main/resources/mixins.stackupup.late.ic2classic.json")
        assertTrue(alwaysQueuedConfig.contains("SlotBaseMixin"))

        InventoryStackLimitHook.configureExpansionEnabled(false)
        val disabledQueued = mutableListOf<String>()
        connector.connectLateWithClassicModules(
            conflicts = emptyList(),
            isModPresent = { it == StackUpUpIds.IC2_MOD_ID },
            queueConfiguration = { disabledQueued += it },
        )
        assertTrue(disabledQueued.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertFalse(disabledQueued.contains(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG))

        resetInventoryExpansionForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        val blacklistedQueued = mutableListOf<String>()
        connector.connectLateWithClassicModules(
            conflicts = emptyList(),
            isModPresent = { it == StackUpUpIds.IC2_MOD_ID },
            blacklistedModIds = setOf(StackUpUpIds.IC2_MOD_ID),
            queueConfiguration = { blacklistedQueued += it },
        )
        assertTrue(blacklistedQueued.contains(StackUpUpIds.LATE_IC2_CLASSIC_MIXIN_CONFIG))
        assertFalse(blacklistedQueued.contains(StackUpUpIds.CAPACITY_LATE_IC2_CLASSIC_MIXIN_CONFIG))
    }

    @Test
    fun nativeSlotLimit_shouldUseCompiledClampAndConserveOfferedCount() {
        assertEquals(16, SlotBaseNativeLimit.clamp(16))
        assertEquals(64, SlotBaseNativeLimit.clamp(64))
        assertEquals(64, SlotBaseNativeLimit.clamp(256))

        val offered = 150
        val accepted = minOf(offered, SlotBaseNativeLimit.clamp(256))
        assertEquals(64, accepted)
        assertEquals(86, offered - accepted)
        assertEquals(offered, accepted + offered - accepted)
    }

    @Test
    fun classicMixin_shouldInspectRawArtifactWhenProvided() {
        assertMixinSource()
        val artifact = rawArtifact()
        assumeTrue(artifact != null, "skip raw IC2 probe: ${RAW_PATH_PROPERTY}/${RAW_PATH_ENV} and local-dev-mods default are absent")
        assertArtifactProbe(requireNotNull(artifact))
    }

    @Test
    fun classicMixin_shouldInspectDeobfArtifactWhenProvided() {
        assertMixinSource()
        val artifact = deobfArtifact()
        assumeTrue(artifact != null, "skip RFG deobf IC2 probe: ${DEOBF_PATH_PROPERTY}/${DEOBF_PATH_ENV} and build default are absent")
        assertArtifactProbe(requireNotNull(artifact))
    }

    private fun assertMixinSource() {
        val source = read("src/main/java/io/alexjoest/stackupup/mixin/late/ic2classic/SlotBaseMixin.java")
        assertEquals(1, Regex("@ModifyReturnValue").findAll(source).count())
        assertTrue(source.contains("method = {\"func_75219_a()I\", \"getSlotStackLimit()I\"}"))
        assertTrue(source.contains("remap = false"))
        assertTrue(source.contains("require = 1"))
    }

    private fun assertArtifactProbe(artifact: Artifact) {
        val original = readJarClass(artifact.path)
        assertEquals(
            1,
            countMethods(original, setOf("func_75219_a", "getSlotStackLimit"), "()I"),
            "${artifact.label} must expose exactly one selected getter",
        )
        assertEquals(
            1,
            countMethods(original, setOf(artifact.selectedGetter), "()I"),
            "${artifact.label} selector must match one getter",
        )
        assertWriterPathEvidence(artifact, original)
    }

    private fun assertWriterPathEvidence(artifact: Artifact, bytes: ByteArray) {
        var writerMethods = 0
        var setterCalls = 0
        var limitCalls = 0
        var clampCalls = 0
        var setterBeforeLimit = false
        var limitBeforeClamp = false
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM5) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (name != artifact.writerMethod || descriptor != WRITER_DESCRIPTOR) {
                        return null
                    }
                    writerMethods++
                    return object : MethodVisitor(Opcodes.ASM5) {
                        override fun visitMethodInsn(opcode: Int, owner: String, methodName: String, methodDescriptor: String, isInterface: Boolean) {
                            when {
                                owner == I_HAS_INVENTORY &&
                                    methodName == "setStackInSlot" &&
                                    methodDescriptor == SETTER_DESCRIPTOR -> {
                                    setterCalls++
                                }
                                owner == I_HAS_INVENTORY &&
                                    methodName == "getMaxStackSize" &&
                                    methodDescriptor == "(I)I" -> {
                                    limitCalls++
                                    setterBeforeLimit = setterCalls > 0
                                }
                                methodName == artifact.setCountMethod && methodDescriptor == "(I)V" -> {
                                    clampCalls++
                                    limitBeforeClamp = limitCalls > 0
                                }
                            }
                        }
                    }
                }
            },
            0,
        )
        assertEquals(1, writerMethods, "${artifact.label} must expose one writer")
        assertEquals(1, setterCalls, "${artifact.label} writer must call setStackInSlot once")
        assertEquals(1, limitCalls, "${artifact.label} writer must read its native max once")
        assertEquals(1, clampCalls, "${artifact.label} writer must retain its native clamp")
        assertTrue(setterBeforeLimit, "${artifact.label} writer must write before its native clamp")
        assertTrue(limitBeforeClamp, "${artifact.label} writer must clamp only after reading its native limit")
    }

    private fun countMethods(bytes: ByteArray, names: Set<String>, descriptor: String? = null): Int {
        var count = 0
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM5) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    methodDescriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name in names && (descriptor == null || descriptor == methodDescriptor)) {
                        count++
                    }
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return count
    }

    private fun rawArtifact(): Artifact? {
        val path = configuredArtifactPath(
            RAW_PATH_PROPERTY,
            RAW_PATH_ENV,
            Paths.get("local-dev-mods", RAW_JAR),
        ) ?: return null
        assertEquals(RAW_SHA256, sha256(path), "Raw IC2 Classic jar must be the audited 1.5.15 artifact")
        return Artifact(
            label = "raw IC2 Classic 1.5.15",
            path = path,
            selectedGetter = "func_75219_a",
            writerMethod = "func_75215_d",
            setCountMethod = "func_190920_e",
        )
    }

    private fun deobfArtifact(): Artifact? {
        val path = configuredArtifactPath(
            DEOBF_PATH_PROPERTY,
            DEOBF_PATH_ENV,
            Paths.get("build", "generated", "local-dev-mods", DEOBF_JAR),
        ) ?: return null
        assertEquals(DEOBF_SHA256, sha256(path), "RFG deobf IC2 Classic jar must be the audited artifact")
        return Artifact(
            label = "RFG deobf IC2 Classic 1.5.15",
            path = path,
            selectedGetter = "getSlotStackLimit",
            writerMethod = "putStack",
            setCountMethod = "setCount",
        )
    }

    private fun configuredArtifactPath(property: String, environment: String, defaultPath: Path): Path? {
        val configured = System.getProperty(property) ?: System.getenv(environment)
        val path = configured?.let(Paths::get) ?: defaultPath
        if (configured != null) {
            require(Files.isRegularFile(path)) {
                "Configured IC2 artifact does not exist: -D$property or $environment=${path.toAbsolutePath()}"
            }
            return path
        }
        return path.takeIf(Files::isRegularFile)
    }

    private fun readJarClass(jar: Path): ByteArray {
        java.util.zip.ZipFile(jar.toFile()).use { zip ->
            return zip.getInputStream(zip.getEntry("${TARGET_CLASS.replace('.', '/')}.class")).use { it.readBytes() }
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class Artifact(val label: String, val path: Path, val selectedGetter: String, val writerMethod: String, val setCountMethod: String)

    private fun queueIc2(modPresent: Boolean = true): List<String> {
        val queued = mutableListOf<String>()
        StackUpUpMixinConnector().connectLateWithClassicModules(
            conflicts = emptyList(),
            isModPresent = { modPresent && it == StackUpUpIds.IC2_MOD_ID },
            queueConfiguration = { queued += it },
        )
        return queued
    }

    private fun read(path: String): String = String(Files.readAllBytes(Paths.get(path)), Charsets.UTF_8)
}
