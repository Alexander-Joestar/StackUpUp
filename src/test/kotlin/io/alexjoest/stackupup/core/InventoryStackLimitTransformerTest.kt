package io.alexjoest.stackupup.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        InventoryStackLimitHook.configureExpansionEnabled(false)

        assertArrayEquals(original, transformer.transform("example.DisabledInventory", null, original))
    }

    @Test
    fun unpreparedPolicy_shouldLeaveClassBytesUnchanged() {
        val original = inventoryClassBytes(
            className = "example.UnpreparedInventory",
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        InventoryStackLimitHook.resetForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)

        assertArrayEquals(original, transformer.transform("example.UnpreparedInventory", null, original))
    }

    @Test
    fun nativeAndInternalInventoryClasses_shouldRemainUnchanged() {
        val names = listOf(
            "net.minecraft.entity.player.InventoryPlayer",
            "net.minecraft.inventory.InventoryLargeChest",
            "io.alexjoest.stackupup.core.InternalInventory",
        )
        for (name in names) {
            val original = inventoryClassBytes(
                className = name,
                methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
                interfaces = arrayOf(INVENTORY_INTERFACE),
            )

            assertArrayEquals(original, transformer.transform(name, name, original))
        }

        val abstractInventory = inventoryClassBytes(
            className = "example.AbstractInventory",
            access = Opcodes.ACC_ABSTRACT,
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )
        val inventoryInterface = inventoryClassBytes(
            className = "example.InventoryInterface",
            interfaceClass = true,
            methods = listOf(
                returningBipushMethod(
                    "getInventoryStackLimit",
                    64,
                    access = Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT,
                ),
            ),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        assertArrayEquals(abstractInventory, transformer.transform(null, null, abstractInventory))
        assertArrayEquals(inventoryInterface, transformer.transform(null, null, inventoryInterface))
    }

    @Test
    fun directConcreteInventory_shouldPatchOnlyExactCapacityDescriptorsAndEvery64Literal() {
        val original = inventoryClassBytes(
            className = "example.DirectInventory",
            methods = listOf(
                methodSpec("getInventoryStackLimit") { method ->
                    method.visitIntInsn(Opcodes.BIPUSH, 64)
                    method.visitIntInsn(Opcodes.BIPUSH, 64)
                    method.visitInsn(Opcodes.IADD)
                    method.visitInsn(Opcodes.IRETURN)
                },
                returningBipushMethod("func_70297_j_", 64),
                returningBipushMethod("getInventoryStackLimit", 64, descriptor = "(I)I"),
                returningBipushMethod("unrelatedMethod", 64),
            ),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        val transformed = transformer.transform("example.DirectInventory", "example.DirectInventory", original)

        assertEquals(listOf(64, 64), methodLiterals(transformed, "getInventoryStackLimit", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformed, "getInventoryStackLimit", "()I").bipush)
        assertEquals(listOf(64), methodLiterals(transformed, "func_70297_j_", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformed, "func_70297_j_", "()I").bipush)
        assertEquals(listOf(64), methodLiterals(transformed, "getInventoryStackLimit", "(I)I").bipush)
        assertEquals(listOf(64), methodLiterals(transformed, "unrelatedMethod", "()I").bipush)
        assertEquals(3, hookInvocationCount(transformed))
    }

    @Test
    fun non64ConstantsAndDynamicCapacityExpressions_shouldRemainUnchanged() {
        val original = inventoryClassBytes(
            className = "example.ComputedInventory",
            methods = listOf(
                methodSpec("getInventoryStackLimit") { method ->
                    method.visitIntInsn(Opcodes.BIPUSH, 32)
                    method.visitIntInsn(Opcodes.BIPUSH, 32)
                    method.visitInsn(Opcodes.IADD)
                    method.visitLdcInsn(64)
                    method.visitInsn(Opcodes.IADD)
                    method.visitInsn(Opcodes.IRETURN)
                },
                methodSpec("func_70297_j_") { method ->
                    method.visitIntInsn(Opcodes.SIPUSH, 64)
                    method.visitInsn(Opcodes.IRETURN)
                },
            ),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        assertArrayEquals(original, transformer.transform("example.ComputedInventory", null, original))
    }

    @Test
    fun blacklistedConcreteInventory_shouldRemainUnchanged() {
        val original = inventoryClassBytes(
            className = "bad.mod.BlacklistedInventory",
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        InventoryStackLimitHook.prepare(256, arrayOf("bad.mod"))

        assertArrayEquals(original, transformer.transform("bad.mod.BlacklistedInventory", null, original))
    }

    @Test
    fun applyingTransformerTwice_shouldNotDuplicateHelperCalls() {
        val original = inventoryClassBytes(
            className = "example.RepeatedInventory",
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        val transformed = transformer.transform(null, null, original)
        val repeated = transformer.transform(null, null, transformed)

        assertArrayEquals(transformed, repeated)
        assertEquals(listOf(64), methodLiterals(repeated, "getInventoryStackLimit", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(repeated, "getInventoryStackLimit", "()I").bipush)
        assertEquals(1, hookInvocationCount(repeated))
    }

    @Test
    fun inheritedConcreteGetter_shouldUseDynamicReceiverHelper() {
        val parentName = "example.InventoryParent"
        val childName = "example.InventoryChild"
        val parent = inventoryClassBytes(
            className = parentName,
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )
        val child = inventoryClassBytes(childName, superName = parentName.replace('.', '/'))
        val metadata = HashMap<String, ByteArray>()
        metadata[parentName.replace('.', '/')] = parent
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        val transformedParent = customTransformer.transform(parentName, parentName, parent)
        val transformedChild = customTransformer.transform(childName, childName, child)

        assertEquals(listOf(64), methodLiterals(transformedParent, "getInventoryStackLimit", "()I").sipush)
        assertEquals(1, hookInvocationCount(transformedParent))
        assertArrayEquals(child, transformedChild)
        assertFalse(hasDeclaredMethod(transformedChild, "getInventoryStackLimit", "()I"))
        assertEquals(0, hookInvocationCount(transformedChild))
    }

    @Test
    fun inheritedGetterFromAbstractParent_shouldRemainNaturalFallback() {
        val parentName = "example.AbstractInventoryParent"
        val childName = "example.InventoryChild"
        val parent = makeAbstract(
            inventoryClassBytes(
                className = parentName,
                methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
                interfaces = arrayOf(INVENTORY_INTERFACE),
            ),
        )
        val child = inventoryClassBytes(childName, superName = parentName.replace('.', '/'))
        val metadata = HashMap<String, ByteArray>()
        metadata[parentName.replace('.', '/')] = parent
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        assertArrayEquals(child, customTransformer.transform(childName, childName, child))
        assertArrayEquals(parent, customTransformer.transform(parentName, parentName, parent))
    }

    @Test
    fun inventorySubinterface_shouldStillIdentifyConcreteGetter() {
        val subinterfaceName = "example.SidedInventory"
        val childName = "example.SidedContainer"
        val metadata = HashMap<String, ByteArray>()
        metadata[subinterfaceName.replace('.', '/')] = inventoryClassBytes(
            className = subinterfaceName,
            interfaceClass = true,
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )
        val child = inventoryClassBytes(
            className = childName,
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(subinterfaceName.replace('.', '/')),
        )
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        val transformed = customTransformer.transform(childName, childName, child)

        assertEquals(listOf(64), methodLiterals(transformed, "getInventoryStackLimit", "()I").sipush)
        assertEquals(1, hookInvocationCount(transformed))
    }

    @Test
    fun itemHandlerGetter_shouldPatchOnlyGetSlotLimitWithTheIntDescriptor() {
        val original = inventoryClassBytes(
            className = "example.Handler",
            methods = listOf(
                returningBipushMethod("getSlotLimit", 64, descriptor = "(I)I"),
                returningBipushMethod("getSlotLimit", 64),
                returningBipushMethod("getSlotLimit", 64, descriptor = "(II)I"),
                returningBipushMethod("unrelatedMethod", 64),
            ),
            interfaces = arrayOf(ITEM_HANDLER_INTERFACE),
        )

        val transformed = transformer.transform("example.Handler", "example.Handler", original)

        assertEquals(listOf(64), methodLiterals(transformed, "getSlotLimit", "(I)I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformed, "getSlotLimit", "(I)I").bipush)
        assertEquals(listOf(64), methodLiterals(transformed, "getSlotLimit", "()I").bipush)
        assertEquals(listOf(64), methodLiterals(transformed, "getSlotLimit", "(II)I").bipush)
        assertEquals(listOf(64), methodLiterals(transformed, "unrelatedMethod", "()I").bipush)
        assertEquals(1, hookInvocationCount(transformed))
    }

    @Test
    fun inheritedItemHandlerSubinterface_shouldIdentifyConcreteGetter() {
        val subinterfaceName = "example.ExtendedItemHandler"
        val childName = "example.ChildHandler"
        val metadata = HashMap<String, ByteArray>()
        metadata[subinterfaceName.replace('.', '/')] = inventoryClassBytes(
            className = subinterfaceName,
            interfaceClass = true,
            interfaces = arrayOf(ITEM_HANDLER_INTERFACE),
        )
        val child = inventoryClassBytes(
            className = childName,
            methods = listOf(returningBipushMethod("getSlotLimit", 64, descriptor = "(I)I")),
            interfaces = arrayOf(subinterfaceName.replace('.', '/')),
        )
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        val transformed = customTransformer.transform(childName, childName, child)

        assertEquals(listOf(64), methodLiterals(transformed, "getSlotLimit", "(I)I").sipush)
        assertEquals(1, hookInvocationCount(transformed))
    }

    @Test
    fun slotSubclasses_shouldPatchBothMethodFamiliesAndSrgAliases() {
        val parentName = "example.SlotBase"
        val childName = "example.SlotChild"
        val parent = inventoryClassBytes(parentName, superName = SLOT_CLASS)
        val child = inventoryClassBytes(
            className = childName,
            superName = parentName.replace('.', '/'),
            methods = listOf(
                returningBipushMethod("getItemStackLimit", 64, descriptor = ITEM_STACK_DESCRIPTOR),
                returningBipushMethod("func_178170_b", 64, descriptor = ITEM_STACK_DESCRIPTOR),
                returningBipushMethod("getSlotStackLimit", 64),
                returningBipushMethod("func_75219_a", 64),
                returningBipushMethod("getItemStackLimit", 64, descriptor = "(I)I"),
                returningBipushMethod("getSlotStackLimit", 64, descriptor = "(I)I"),
                returningBipushMethod("unrelatedMethod", 64),
            ),
        )
        val metadata = HashMap<String, ByteArray>()
        metadata[parentName.replace('.', '/')] = parent
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(metadata)),
        )

        val transformed = customTransformer.transform(childName, childName, child)
        val supportedMethods = listOf(
            "getItemStackLimit" to ITEM_STACK_DESCRIPTOR,
            "func_178170_b" to ITEM_STACK_DESCRIPTOR,
            "getSlotStackLimit" to "()I",
            "func_75219_a" to "()I",
        )

        supportedMethods.forEach { (name, descriptor) ->
            assertEquals(listOf(64), methodLiterals(transformed, name, descriptor).sipush)
            assertEquals(emptyList<Int>(), methodLiterals(transformed, name, descriptor).bipush)
        }
        assertEquals(listOf(64), methodLiterals(transformed, "getItemStackLimit", "(I)I").bipush)
        assertEquals(listOf(64), methodLiterals(transformed, "getSlotStackLimit", "(I)I").bipush)
        assertEquals(listOf(64), methodLiterals(transformed, "unrelatedMethod", "()I").bipush)
        assertEquals(4, hookInvocationCount(transformed))
    }

    @Test
    fun missingHierarchyMetadata_shouldSkipUnknownInventoryLikeClasses() {
        val child = inventoryClassBytes(
            className = "example.MissingParent",
            superName = "missing/Parent",
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
        )
        val handler = inventoryClassBytes(
            className = "example.UnknownHandler",
            methods = listOf(returningBipushMethod("getSlotLimit", 64, descriptor = "(I)I")),
            interfaces = arrayOf("example/UnknownHandlerInterface"),
        )
        val slot = inventoryClassBytes(
            className = "example.UnknownSlotChild",
            superName = "example/UnknownSlotParent",
            methods = listOf(returningBipushMethod("getSlotStackLimit", 64)),
        )
        val customTransformer = InventoryStackLimitTransformer(
            InventoryClassHierarchyRepository(MapMetadataSource(emptyMap())),
        )

        assertArrayEquals(child, customTransformer.transform(null, null, child))
        assertArrayEquals(handler, customTransformer.transform(null, null, handler))
        assertArrayEquals(slot, customTransformer.transform(null, null, slot))
    }

    @Test
    fun malformedClassBytes_shouldKeepOriginalBytes() {
        val original = byteArrayOf(0, 1, 2, 3, 4)

        assertArrayEquals(original, transformer.transform("example.MalformedInventory", null, original))
    }

    @Test
    fun largeCompatibilityLimit_shouldUseReceiverHelperWithoutEmbeddingTheLimit() {
        InventoryStackLimitHook.prepare(40000, null)
        val original = inventoryClassBytes(
            className = "example.LargeInventory",
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        val transformed = transformer.transform(null, null, original)
        val literals = methodLiterals(transformed, "getInventoryStackLimit", "()I")

        assertEquals(listOf(64), literals.sipush)
        assertEquals(emptyList<Int>(), literals.ldc)
        assertEquals(1, hookInvocationCount(transformed))
    }

    private fun inventoryClassBytes(
        className: String,
        interfaceClass: Boolean = false,
        access: Int = 0,
        superName: String = "java/lang/Object",
        interfaces: Array<String> = emptyArray(),
        methods: List<MethodSpec> = emptyList(),
    ): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        val classAccess = Opcodes.ACC_PUBLIC or access or if (interfaceClass) Opcodes.ACC_INTERFACE else 0
        writer.visit(
            Opcodes.V1_8,
            classAccess,
            className.replace('.', '/'),
            null,
            superName,
            interfaces,
        )
        methods.forEach { spec ->
            val method = writer.visitMethod(spec.access, spec.name, spec.descriptor, null, null)
            if ((spec.access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE)) == 0) {
                method.visitCode()
                spec.body.invoke(method)
                method.visitMaxs(0, 0)
            }
            method.visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun returningBipushMethod(name: String, value: Int, access: Int = Opcodes.ACC_PUBLIC, descriptor: String = "()I"): MethodSpec =
        methodSpec(name, descriptor, access) { method ->
            method.visitIntInsn(Opcodes.BIPUSH, value)
            method.visitInsn(Opcodes.IRETURN)
        }

    private fun methodSpec(name: String, descriptor: String = "()I", access: Int = Opcodes.ACC_PUBLIC, body: (MethodVisitor) -> Unit): MethodSpec =
        MethodSpec(access, name, descriptor, body)

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

    private fun methodLiterals(bytes: ByteArray, methodName: String, descriptor: String): LiteralSummary {
        val bipush = mutableListOf<Int>()
        val sipush = mutableListOf<Int>()
        val ldc = mutableListOf<Int>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM5) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    methodDescriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name != methodName || methodDescriptor != descriptor) {
                        return null
                    }
                    return object : MethodVisitor(Opcodes.ASM5) {
                        override fun visitIntInsn(opcode: Int, operand: Int) {
                            when (opcode) {
                                Opcodes.BIPUSH -> bipush += operand
                                Opcodes.SIPUSH -> sipush += operand
                            }
                        }

                        override fun visitLdcInsn(value: Any) {
                            if (value is Int) {
                                ldc += value
                            }
                        }
                    }
                }
            },
            0,
        )
        return LiteralSummary(bipush, sipush, ldc)
    }

    private fun hasDeclaredMethod(bytes: ByteArray, methodName: String, descriptor: String): Boolean {
        var found = false
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM5) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    methodDescriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name == methodName && methodDescriptor == descriptor) {
                        found = true
                    }
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
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

    private data class MethodSpec(val access: Int, val name: String, val descriptor: String, val body: (MethodVisitor) -> Unit)

    private data class LiteralSummary(val bipush: List<Int>, val sipush: List<Int>, val ldc: List<Int>)

    private class MapMetadataSource(private val metadata: Map<String, ByteArray>) : InventoryClassHierarchyRepository.MetadataSource {
        override fun read(internalName: String): ByteArray? = metadata[internalName]
    }

    private companion object {
        const val INVENTORY_INTERFACE = "net/minecraft/inventory/IInventory"
        const val ITEM_HANDLER_INTERFACE = "net/minecraftforge/items/IItemHandler"
        const val SLOT_CLASS = "net/minecraft/inventory/Slot"
        const val ITEM_STACK_DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;)I"
    }
}
