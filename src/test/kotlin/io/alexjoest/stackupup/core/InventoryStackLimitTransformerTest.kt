package io.alexjoest.stackupup.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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
    fun unpreparedPolicyAtLoadTime_shouldStillEmitHookCallThatResolvesAtCallTime() {
        val original = inventoryClassBytes(
            className = "example.EarlyLoadedInventory",
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        InventoryStackLimitHook.resetForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)

        val transformed = transformer.transform("example.EarlyLoadedInventory", null, original)

        assertFalse(original.contentEquals(transformed))
        assertEquals(listOf(64), methodLiterals(transformed, "getInventoryStackLimit", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformed, "getInventoryStackLimit", "()I").bipush)
        assertEquals(1, hookInvocationCount(transformed))
        assertEquals(
            64,
            InventoryStackLimitHook.resolveInventoryStackLimit(InventoryStackLimitTransformer::class.java, 64),
        )

        InventoryStackLimitHook.prepare(256, null)

        assertEquals(
            256,
            InventoryStackLimitHook.resolveInventoryStackLimit(InventoryStackLimitTransformer::class.java, 64),
        )
    }

    @Test
    fun vanillaNamespaceConcreteInventory_shouldBePatchedByStructure() {
        val names = listOf(
            "net.minecraft.client.player.inventory.ContainerLocalMenu",
            "net.minecraft.entity.player.InventoryPlayer",
            "net.minecraft.inventory.InventoryBasic",
        )
        for (name in names) {
            val original = inventoryClassBytes(
                className = name,
                methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
                interfaces = arrayOf(INVENTORY_INTERFACE),
            )

            val transformed = transformer.transform(name, name, original)

            assertFalse(original.contentEquals(transformed), "expected a structural patch for $name")
            assertEquals(listOf(64), methodLiterals(transformed, "getInventoryStackLimit", "()I").sipush)
            assertEquals(emptyList<Int>(), methodLiterals(transformed, "getInventoryStackLimit", "()I").bipush)
            assertEquals(1, hookInvocationCount(transformed))
        }
    }

    @Test
    fun configManagedCraftingInventories_shouldRemainUnchanged() {
        val names = listOf(
            "net.minecraft.inventory.InventoryCrafting",
            "net.minecraft.inventory.InventoryCraftResult",
        )
        for (name in names) {
            val original = inventoryClassBytes(
                className = name,
                methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
                interfaces = arrayOf(INVENTORY_INTERFACE),
            )

            val transformed = transformer.transform(name, name, original)

            assertArrayEquals(original, transformed)
            assertEquals(0, hookInvocationCount(transformed))
        }
    }

    @Test
    fun vanillaDelegatingAndFieldBackedGetters_shouldRemainUnchanged() {
        val largeChest = inventoryClassBytes(
            className = "net.minecraft.inventory.InventoryLargeChest",
            methods = listOf(
                methodSpec("getInventoryStackLimit") { method ->
                    method.visitVarInsn(Opcodes.ALOAD, 0)
                    method.visitFieldInsn(
                        Opcodes.GETFIELD,
                        "net/minecraft/inventory/InventoryLargeChest",
                        "upperChest",
                        "Lnet/minecraft/inventory/IInventory;",
                    )
                    method.visitMethodInsn(
                        Opcodes.INVOKEINTERFACE,
                        "net/minecraft/inventory/IInventory",
                        "getInventoryStackLimit",
                        "()I",
                        true,
                    )
                    method.visitInsn(Opcodes.IRETURN)
                },
            ),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )
        val fieldBacked = inventoryClassBytes(
            className = "net.minecraft.inventory.FieldBackedInventory",
            methods = listOf(
                methodSpec("getInventoryStackLimit") { method ->
                    method.visitVarInsn(Opcodes.ALOAD, 0)
                    method.visitFieldInsn(
                        Opcodes.GETFIELD,
                        "net/minecraft/inventory/FieldBackedInventory",
                        "slotLimit",
                        "I",
                    )
                    method.visitInsn(Opcodes.IRETURN)
                },
            ),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )
        val non64Constant = inventoryClassBytes(
            className = "net.minecraft.inventory.FixedLimitInventory",
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 32)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        assertArrayEquals(largeChest, transformer.transform(null, null, largeChest))
        assertArrayEquals(fieldBacked, transformer.transform(null, null, fieldBacked))
        assertArrayEquals(non64Constant, transformer.transform(null, null, non64Constant))
    }

    @Test
    fun internalPackageInventories_shouldRemainUnchanged() {
        val internalInventory = inventoryClassBytes(
            className = "io.alexjoest.stackupup.core.InternalInventory",
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        assertArrayEquals(internalInventory, transformer.transform(null, null, internalInventory))
    }

    @Test
    fun interfaceDefaultGetter_shouldBePatchedInItsOwnBody() {
        val inventoryInterface = inventoryClassBytes(
            className = "example.SidedInventory",
            interfaceClass = true,
            access = Opcodes.ACC_ABSTRACT,
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        val transformed = transformer.transform("example.SidedInventory", null, inventoryInterface)

        assertFalse(inventoryInterface.contentEquals(transformed), "接口 default 方法体应被字面量替换命中")
        assertEquals(listOf(64), methodLiterals(transformed, "getInventoryStackLimit", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformed, "getInventoryStackLimit", "()I").bipush)
        assertEquals(1, hookInvocationCount(transformed))
    }

    @Test
    fun abstractClassConcreteGetter_shouldBePatchedInItsOwnBody() {
        val abstractInventory = inventoryClassBytes(
            className = "example.AbstractInventory",
            access = Opcodes.ACC_ABSTRACT,
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        val transformed = transformer.transform(null, null, abstractInventory)

        assertFalse(abstractInventory.contentEquals(transformed), "抽象基类的具体 getter 应被字面量替换命中")
        assertEquals(listOf(64), methodLiterals(transformed, "getInventoryStackLimit", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformed, "getInventoryStackLimit", "()I").bipush)
        assertEquals(1, hookInvocationCount(transformed))
    }

    @Test
    fun abstractGetterDeclarations_shouldRemainUnchangedWithoutThrowing() {
        // abstract 声明没有 Code 属性，ClassReader 不产生任何指令回调，所以 ACC_ABSTRACT 位按构造是惰性的：
        // 从掩码里删掉它也不会让本用例变红。本用例守的是「类级 target 判定命中、声明无方法体时不抛异常且字节不变」。
        val inventoryInterface = inventoryClassBytes(
            className = "example.AbstractGetterInterface",
            interfaceClass = true,
            access = Opcodes.ACC_ABSTRACT,
            methods = listOf(
                methodSpec("getInventoryStackLimit", access = Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT) { },
            ),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )
        val abstractClass = inventoryClassBytes(
            className = "example.AbstractGetterClass",
            access = Opcodes.ACC_ABSTRACT,
            methods = listOf(
                methodSpec("func_70297_j_", access = Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT) { },
            ),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        assertArrayEquals(inventoryInterface, transformer.transform(null, null, inventoryInterface))
        assertArrayEquals(abstractClass, transformer.transform(null, null, abstractClass))
    }

    @Test
    fun staticAndNativeCapacityMethods_shouldRemainUnchangedInsideATargetClass() {
        // 方法级掩码里真正被检测的是 ACC_STATIC：删掉它，static getter 的 bipush 会消失、sipush 出现。
        // native 声明没有 Code 属性，按构造惰性，不构成 ACC_NATIVE 位的回归保护；
        // 同一类的具体实例方法仍要被改写，才能证明是方法级而不是类级跳过。
        val original = inventoryClassBytes(
            className = "example.MixedInventory",
            methods = listOf(
                returningBipushMethod("getInventoryStackLimit", 64, access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC),
                methodSpec("func_70297_j_", access = Opcodes.ACC_PUBLIC or Opcodes.ACC_NATIVE) { },
                returningBipushMethod("getSlotStackLimit", 64),
            ),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        val transformed = transformer.transform(null, null, original)

        assertEquals(
            listOf(64),
            methodLiterals(transformed, "getInventoryStackLimit", "()I").bipush,
            "static 方法没有 receiver，不得注入 hook",
        )
        assertEquals(emptyList<Int>(), methodLiterals(transformed, "getInventoryStackLimit", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformed, "func_70297_j_", "()I").sipush)
        assertEquals(
            listOf(64),
            methodLiterals(transformed, "getSlotStackLimit", "()I").sipush,
            "同一类里的具体实例方法仍应被改写",
        )
        assertEquals(1, hookInvocationCount(transformed))
    }

    @Test
    fun disabledExpansion_shouldLeaveInterfaceAndAbstractTargetsUnchanged() {
        val inventoryInterface = inventoryClassBytes(
            className = "example.DisabledInterface",
            interfaceClass = true,
            access = Opcodes.ACC_ABSTRACT,
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )
        val abstractInventory = inventoryClassBytes(
            className = "example.DisabledAbstractInventory",
            access = Opcodes.ACC_ABSTRACT,
            methods = listOf(returningBipushMethod("getInventoryStackLimit", 64)),
            interfaces = arrayOf(INVENTORY_INTERFACE),
        )

        InventoryStackLimitHook.configureExpansionEnabled(false)

        val transformedInterface = transformer.transform(null, null, inventoryInterface)
        val transformedAbstract = transformer.transform(null, null, abstractInventory)

        assertArrayEquals(inventoryInterface, transformedInterface)
        assertArrayEquals(abstractInventory, transformedAbstract)
        assertEquals(0, hookInvocationCount(transformedInterface), "OFF 时返回字节不得携带 hook 调用")
        assertEquals(0, hookInvocationCount(transformedAbstract), "OFF 时返回字节不得携带 hook 调用")
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
    fun inheritedGetterFromAbstractParent_shouldBePatchedOnceAndInheritedByChildren() {
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

        val transformedParent = customTransformer.transform(parentName, parentName, parent)

        // 抽象基类的具体 getter 现在会被改写；子类自己没有声明 getter，经继承获得同一份被改写的代码。
        assertFalse(parent.contentEquals(transformedParent), "抽象基类的具体 getter 应被字面量替换命中")
        assertEquals(listOf(64), methodLiterals(transformedParent, "getInventoryStackLimit", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformedParent, "getInventoryStackLimit", "()I").bipush)
        assertEquals(1, hookInvocationCount(transformedParent))
        assertArrayEquals(child, customTransformer.transform(childName, childName, child))
        assertFalse(hasDeclaredMethod(child, "getInventoryStackLimit", "()I"))
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
    fun abstractInventoryBaseGetter_shouldCarryCapacityWithoutACapacityMixin() {
        // Mystical Agriculture 的 TileEssenceReprocessor 形状（javap -p -c MysticalAgriculture-1.7.5 实证）：
        // public abstract class，implements ISidedInventory（→ IInventory），getter 是 SRG 名
        // func_70297_j_() 的 `bipush 64; ireturn`，六个具体子类都不声明 getter。这类目标同时满足
        // isStackLimitTarget 与「getter 体里有字面 64」，所以它的容量 mixin 已被删除，由本 transformer 独占。
        // 本用例接管原先由 MysticalAgricultureInventoryLimitMixin 结构测试持有的行为断言：
        // 不改写声明类而是改写子类，确认扩展值经继承到达未声明 getter 的具体机器。
        val baseName = "com.blakebr0.mysticalagriculture.tileentity.reprocessor.TileEssenceReprocessor"
        val machineName = "com.blakebr0.mysticalagriculture.tileentity.reprocessor.TileInferiumReprocessor"
        val base = makeAbstract(
            inventoryClassBytes(
                className = baseName,
                interfaces = arrayOf(INVENTORY_INTERFACE),
                methods = listOf(
                    defaultConstructor(),
                    returningBipushMethod("func_70297_j_", 64),
                ),
            ),
        )
        val machine = subclassBytes(machineName, baseName)

        val transformedBase = transformer.transform(baseName, baseName, base)

        assertFalse(base.contentEquals(transformedBase), "抽象基类的具体 getter 应被字面量替换命中")
        assertEquals(listOf(64), methodLiterals(transformedBase, "func_70297_j_", "()I").sipush)
        assertEquals(emptyList<Int>(), methodLiterals(transformedBase, "func_70297_j_", "()I").bipush)
        assertEquals(1, hookInvocationCount(transformedBase))

        // 行为验证：子类自己没有 getter，实例调用必须落到被改写的基类方法体并解析为兼容上限。
        val loader = BytesClassLoader(
            linkedMapOf(
                baseName.replace('.', '/') to transformedBase,
                machineName.replace('.', '/') to machine,
            ),
        )
        val machineClass = loader.loadClass(machineName)
        assertSame(
            loader,
            machineClass.classLoader,
            "生成类必须来自测试自己的字节（子优先），不得被 classpath 上的同名真实类替换",
        )
        val instance = machineClass.getDeclaredConstructor().newInstance()
        assertEquals(COMPATIBILITY_LIMIT, machineClass.getMethod("func_70297_j_").invoke(instance))

        val controlLoader = BytesClassLoader(
            linkedMapOf(
                baseName.replace('.', '/') to base,
                machineName.replace('.', '/') to machine,
            ),
        )
        val controlClass = controlLoader.loadClass(machineName)
        assertSame(controlLoader, controlClass.classLoader, "对照组的生成类也必须来自测试自己的字节")
        val controlInstance = controlClass.getDeclaredConstructor().newInstance()
        assertEquals(64, controlInstance.javaClass.getMethod("func_70297_j_").invoke(controlInstance), "未改写的对照基类必须仍是 64")
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

    /** 具体子类，只有默认构造器，不声明任何容量 getter。 */
    private fun subclassBytes(className: String, superName: String): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, className.replace('.', '/'), null, superName.replace('.', '/'), null)
        val constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructor.visitCode()
        constructor.visitVarInsn(Opcodes.ALOAD, 0)
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, superName.replace('.', '/'), "<init>", "()V", false)
        constructor.visitInsn(Opcodes.RETURN)
        constructor.visitMaxs(0, 0)
        constructor.visitEnd()
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun defaultConstructor(): MethodSpec = methodSpec("<init>", "()V") { method ->
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        method.visitInsn(Opcodes.RETURN)
    }

    /**
     * 子优先加载器：`sources` 里有的名字一律用测试自己生成的字节定义，其余委派父加载器。
     * `ClassLoader.loadClass` 默认父优先，若测试 classpath 上存在同名真实类（例如 `local-dev-mods`
     * 里的 MysticalAgriculture），生成字节会被跳过、反射拿到的就不是被转换过的类。
     */
    private class BytesClassLoader(private val sources: Map<String, ByteArray>) :
        ClassLoader(InventoryStackLimitTransformerTest::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name) ?: generatedClass(name, resolve) ?: super.loadClass(name, resolve)
        }

        private fun generatedClass(name: String, resolve: Boolean): Class<*>? {
            val bytes = sources[name.replace('.', '/')] ?: return null
            val defined = defineClass(name, bytes, 0, bytes.size)
            if (resolve) {
                resolveClass(defined)
            }
            return defined
        }
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
        const val COMPATIBILITY_LIMIT = 256
    }
}
