package io.alexjoest.stackupup.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.HashMap

class InventoryStackLimitTransformerTest {
    private val transformer = InventoryStackLimitTransformer()

    @BeforeEach
    fun enableExpansionForTest() {
        InventoryStackLimitHook.resetForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(256, null)
    }

    @AfterEach
    fun resetHook() {
        InventoryStackLimitHook.resetForTesting()
    }

    @Test
    fun nullBasicClass_shouldReturnNull() {
        assertNull(transformer.transform(null, "example.Inventory", null))
    }

    @Test
    fun disabledExpansion_shouldLeaveClassBytesUnchanged() {
        val original = inventoryClassBytes(
            className = "example.DisabledInventory",
            withGetter = true,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
        )

        InventoryStackLimitHook.configureExpansionEnabled(false)

        assertArrayEquals(original, transformer.transform("example.DisabledInventory", null, original))
    }

    @Test
    fun nativeInventories_useOnlyExplicitCoreMixinPolicy() {
        for (name in listOf("net.minecraft.entity.player.InventoryPlayer", "net.minecraft.inventory.InventoryLargeChest")) {
            val original = inventoryClassBytes(name, withGetter = true, interfaces = arrayOf("net/minecraft/inventory/IInventory"))
            assertArrayEquals(original, transformer.transform(name, name, original))
        }
    }

    @Test
    fun directConcreteInventory_shouldWrapOnlyInventoryGetter() {
        val original = inventoryClassBytes(
            className = "example.DirectInventory",
            withGetter = true,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
        )

        val transformed = transformer.transform(
            "example.DirectInventory",
            "example.DirectInventory",
            original,
        )

        assertTrue(hasHookInvocation(transformed))
        assertEquals(1, hookInvocationCount(transformed))
    }

    @Test
    fun interfaceAndAbstractInventory_shouldRemainUnchanged() {
        val interfaceBytes = inventoryClassBytes(
            className = "example.InventoryInterface",
            interfaceClass = true,
            withGetter = true,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
        )
        val abstractBytes = inventoryClassBytes(
            className = "example.AbstractInventory",
            withGetter = true,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
        )
            .let { makeAbstract(it) }

        assertArrayEquals(interfaceBytes, transformer.transform(null, null, interfaceBytes))
        assertArrayEquals(abstractBytes, transformer.transform(null, null, abstractBytes))
    }

    @Test
    fun classWithoutTargetMethod_shouldRemainUnchanged() {
        val original = inventoryClassBytes(
            className = "example.NoGetter",
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
        )
        assertArrayEquals(original, transformer.transform(null, null, original))
    }

    @Test
    fun applyingTransformerTwice_shouldNotDuplicateHook() {
        val original = inventoryClassBytes(
            className = "example.RepeatedInventory",
            withGetter = true,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
        )
        val transformed = transformer.transform(null, null, original)
        val repeated = transformer.transform(null, null, transformed)

        assertArrayEquals(transformed, repeated)
        assertEquals(1, hookInvocationCount(repeated))
    }

    @Test
    fun inheritedConcreteGetter_shouldUseRuntimeReceiverForBlacklist() {
        val parentName = "example.InventoryParent"
        val childName = "bad.mod.InventoryChild"
        val metadata = HashMap<String, ByteArray>()
        metadata[parentName.replace('.', '/')] = inventoryClassBytes(
            className = parentName,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
        ).let { makeAbstract(it) }
        val child = inventoryClassBytes(childName, superName = parentName.replace('.', '/'), withGetter = true)
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        val transformed = customTransformer.transform(childName, childName, child)

        assertEquals(1, hookInvocationCount(transformed))
        assertTrue(hasRuntimeReceiverLookup(transformed))

        InventoryStackLimitHook.prepare(256, arrayOf("bad.mod"))
        assertEquals(64, InventoryStackLimitHook.resolveInventoryStackLimit(childName, 64))
        assertEquals(256, InventoryStackLimitHook.resolveInventoryStackLimit(parentName, 64))
    }

    @Test
    fun inheritedConcreteGetter_fromAbstractInventoryParent_shouldGenerateOverride() {
        val parentName = "example.AbstractInventoryParent"
        val childName = "example.InventoryChild"
        val metadata = HashMap<String, ByteArray>()
        metadata[parentName.replace('.', '/')] = inventoryClassBytes(
            className = parentName,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
            withGetter = true,
        ).let { makeAbstract(it) }
        val child = inventoryClassBytes(childName, superName = parentName.replace('.', '/'))
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        val transformed = customTransformer.transform(childName, childName, child)

        assertEquals(1, hookInvocationCount(transformed))
        assertTrue(hasRuntimeReceiverLookup(transformed))
        assertTrue(hasSuperGetterCall(transformed, parentName.replace('.', '/')))
        assertArrayEquals(transformed, customTransformer.transform(childName, childName, transformed))
    }

    @Test
    fun inheritedFinalGetter_fromAbstractInventoryParent_shouldRemainUnchanged() {
        val parentName = "example.AbstractInventoryParent"
        val childName = "example.InventoryChild"
        val metadata = HashMap<String, ByteArray>()
        metadata[parentName.replace('.', '/')] = inventoryClassBytes(
            className = parentName,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
            withGetter = true,
            getterFinal = true,
        ).let { makeAbstract(it) }
        val child = inventoryClassBytes(childName, superName = parentName.replace('.', '/'))
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        assertArrayEquals(child, customTransformer.transform(childName, childName, child))
    }

    @Test
    fun inheritedGetter_shouldNotBeGeneratedAgainBelowConcreteInventoryAncestor() {
        val parentName = "example.AbstractInventoryParent"
        val middleName = "example.ConcreteInventoryMiddle"
        val childName = "example.InventoryGrandchild"
        val metadata = HashMap<String, ByteArray>()
        metadata[parentName.replace('.', '/')] = inventoryClassBytes(
            className = parentName,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
            withGetter = true,
        ).let { makeAbstract(it) }
        metadata[middleName.replace('.', '/')] = inventoryClassBytes(
            className = middleName,
            superName = parentName.replace('.', '/'),
        )
        val child = inventoryClassBytes(childName, superName = middleName.replace('.', '/'))
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        val transformedMiddle = customTransformer.transform(middleName, middleName, metadata[middleName.replace('.', '/')]!!)
        assertEquals(1, hookInvocationCount(transformedMiddle))
        assertArrayEquals(child, customTransformer.transform(childName, childName, child))
    }

    @Test
    fun inventorySubinterface_shouldWrapGetter() {
        val subinterfaceName = "example.SidedInventory"
        val metadata = HashMap<String, ByteArray>()
        metadata[subinterfaceName.replace('.', '/')] = inventoryClassBytes(
            className = subinterfaceName,
            interfaceClass = true,
            interfaces = arrayOf("net/minecraft/inventory/IInventory"),
        )
        val child = inventoryClassBytes(
            className = "example.SidedContainer",
            interfaces = arrayOf(subinterfaceName.replace('.', '/')),
            withGetter = true,
        )
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        val transformed = customTransformer.transform(null, null, child)

        assertEquals(1, hookInvocationCount(transformed))
    }

    @Test
    fun missingHierarchyMetadata_shouldSkipSafely() {
        val child = inventoryClassBytes(
            className = "example.MissingParent",
            superName = "missing/Parent",
            withGetter = true,
        )
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(emptyMap())),
        )

        assertArrayEquals(child, customTransformer.transform(null, null, child))
    }

    @Test
    fun hookUsesPreparedCompatibilityLimitAndPackageBlacklist() {
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(256, null)
        assertEquals(256, InventoryStackLimitHook.resolveInventoryStackLimit("example.Inventory", 64))

        InventoryStackLimitHook.prepare(256, arrayOf("bad.mod"))

        assertEquals(256, InventoryStackLimitHook.resolveInventoryStackLimit("example.Inventory", 64))
        assertEquals(64, InventoryStackLimitHook.resolveInventoryStackLimit("bad.mod.Container", 64))
        assertEquals(64, InventoryStackLimitHook.resolveInventoryStackLimit("bad.mod\$Inner", 64))
        assertEquals(256, InventoryStackLimitHook.resolveInventoryStackLimit("bad.module.Container", 64))
        assertEquals(256, InventoryStackLimitHook.resolveInventoryStackLimit("other.Container", 64))
    }

    @Test
    fun hook_shouldPreserveNonVanillaOriginalValues() {
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(256, null)

        for (original in intArrayOf(0, 1, 16, 64, 256)) {
            val resolved = InventoryStackLimitHook.resolveInventoryStackLimit("example.Inventory", original)
            assertEquals(if (original == 64) 256 else original, resolved)
        }
    }

    @Test
    fun selectedBlacklist_shouldNotDisableOtherInventories() {
        InventoryStackLimitHook.prepare(256, arrayOf("bad.mod"))

        assertEquals(64, InventoryStackLimitHook.resolveInventoryStackLimit("bad.mod.Inventory", 64))
        assertEquals(256, InventoryStackLimitHook.resolveInventoryStackLimit("example.Inventory", 64))
        assertTrue(InventoryStackLimitHook.isExpansionEnabled())
    }

    @Test
    fun defaultOffDecision_shouldNotBeEnabledBySetter() {
        InventoryStackLimitHook.resetForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(false)
        InventoryStackLimitHook.prepare(256, null)

        InventoryStackLimitHook.configureExpansionEnabled(true)

        assertEquals(64, InventoryStackLimitHook.resolveInventoryStackLimit("example.Inventory", 64))
        assertFalse(InventoryStackLimitHook.isExpansionEnabled())
    }

    @Test
    fun unpreparedHook_shouldKeepVanillaLimitEvenWhenExpansionFlagIsSet() {
        InventoryStackLimitHook.resetForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)

        assertEquals(64, InventoryStackLimitHook.resolveInventoryStackLimit("example.Inventory", 64))
    }

    @Test
    fun hookAcceptsSlashNamesAndNullBlacklist() {
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(128, null)
        assertEquals(128, InventoryStackLimitHook.resolveInventoryStackLimit("example/Inventory", 64))
        assertTrue(InventoryStackLimitHook.isPrepared())
    }

    private fun inventoryClassBytes(
        className: String,
        interfaceClass: Boolean = false,
        withGetter: Boolean = false,
        getterFinal: Boolean = false,
        superName: String = "java/lang/Object",
        interfaces: Array<String> = emptyArray(),
    ): ByteArray {
        val writer = ClassWriter(0)
        val access = Opcodes.ACC_PUBLIC or if (interfaceClass) Opcodes.ACC_INTERFACE else 0
        writer.visit(
            Opcodes.V1_8,
            access,
            className.replace('.', '/'),
            null,
            superName,
            interfaces,
        )
        if (withGetter) {
            val getterAccess = Opcodes.ACC_PUBLIC or if (getterFinal) Opcodes.ACC_FINAL else 0
            val method = writer.visitMethod(getterAccess, "getInventoryStackLimit", "()I", null, null)
            method.visitCode()
            method.visitIntInsn(Opcodes.BIPUSH, 64)
            method.visitInsn(Opcodes.IRETURN)
            method.visitMaxs(1, 1)
            method.visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun makeAbstract(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(0)
        reader.accept(
            object : ClassVisitor(Opcodes.ASM5, writer) {
                override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                    super.visit(version, access or Opcodes.ACC_ABSTRACT, name, signature, superName, interfaces)
                }
            },
            0,
        )
        return writer.toByteArray()
    }

    private fun hasHookInvocation(bytes: ByteArray): Boolean = hookInvocationCount(bytes) > 0

    private fun hasRuntimeReceiverLookup(bytes: ByteArray): Boolean {
        var found = false
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM5) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM5) {
                        override fun visitMethodInsn(opcode: Int, owner: String, methodName: String, methodDescriptor: String, isInterface: Boolean) {
                            if (opcode == Opcodes.INVOKEVIRTUAL &&
                                owner == "java/lang/Object" &&
                                methodName == "getClass" &&
                                methodDescriptor == "()Ljava/lang/Class;"
                            ) {
                                found = true
                            }
                        }
                    }
            },
            0,
        )
        return found
    }

    private fun hasSuperGetterCall(bytes: ByteArray, ownerName: String): Boolean {
        var found = false
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM5) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM5) {
                        override fun visitMethodInsn(opcode: Int, owner: String, methodName: String, methodDescriptor: String, isInterface: Boolean) {
                            if (opcode == Opcodes.INVOKESPECIAL &&
                                owner == ownerName &&
                                methodName == "getInventoryStackLimit" &&
                                methodDescriptor == "()I"
                            ) {
                                found = true
                            }
                        }
                    }
            },
            0,
        )
        return found
    }

    private fun hookInvocationCount(bytes: ByteArray): Int {
        var count = 0
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM5) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM5) {
                        override fun visitMethodInsn(opcode: Int, owner: String, methodName: String, methodDescriptor: String, isInterface: Boolean) {
                            if (opcode == Opcodes.INVOKESTATIC &&
                                owner == "io/alexjoest/stackupup/core/InventoryStackLimitHook" &&
                                methodName == "resolveInventoryStackLimit" &&
                                methodDescriptor == "(Ljava/lang/Class;I)I"
                            ) {
                                count++
                            }
                        }
                    }
            },
            0,
        )
        return count
    }

    private class MapMetadataSource(private val metadata: Map<String, ByteArray>) : InventoryClassHierarchyRepository.MetadataSource {
        override fun read(internalName: String): ByteArray? = metadata[internalName]
    }

    private class Inner
}
