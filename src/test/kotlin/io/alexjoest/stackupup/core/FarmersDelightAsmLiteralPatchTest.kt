package io.alexjoest.stackupup.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Farmer's Delight Legacy 容量扩展的行为验证。
 *
 * 该 Mod 四台机器的 `getInventoryStackLimit()` 源码都是字面量 `return 64;`（编译后 BIPUSH 64），
 * 因此容量扩展走 [InventoryStackLimitTransformer] 的窄范围字面量替换，不再需要
 * `FarmersDelightInventoryLimitMixin`（已删除）。这里按真实类名生成等价字节码（`IInventory` 实现
 * + `BIPUSH 64; IRETURN`），经真实 transformer 转换后在独立 ClassLoader 中加载并真正调用 getter，
 * 断言返回值本身，而不是只断言字节码形状。
 */
class FarmersDelightAsmLiteralPatchTest {
    private val transformer = InventoryStackLimitTransformer()

    @BeforeEach
    fun enableExpansion() {
        InventoryStackLimitHook.resetForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(true)
        InventoryStackLimitHook.prepare(EXPANDED_LIMIT, null)
    }

    @AfterEach
    fun resetHook() {
        InventoryStackLimitHook.resetForTesting()
    }

    @Test
    fun everyFarmersDelightTileGetter_shouldReturnExpandedLimitAfterTransform() {
        for (className in FARMERS_DELIGHT_TILES) {
            val original = tileClassBytes(className)
            val transformed = transformer.transform(className, className, original)

            assertFalse(transformed.contentEquals(original), "$className 应被字面量替换命中")
            assertEquals(
                EXPANDED_LIMIT,
                invokeInventoryStackLimit(className, transformed),
                "$className 的 getInventoryStackLimit() 必须返回扩展上限而不是 64",
            )
            assertEquals(
                emptyList<Int>(),
                literalOperands(transformed, Opcodes.BIPUSH),
                "$className 的 getInventoryStackLimit() 不应残留 BIPUSH 64",
            )
            assertEquals(listOf(VANILLA_LIMIT), literalOperands(transformed, Opcodes.SIPUSH))
            assertEquals(1, hookInvocationCount(transformed), "$className 每次调用只应经过一次 hook")
        }
    }

    @Test
    fun untransformedFarmersDelightGetter_shouldStillReturnVanilla64() {
        for (className in FARMERS_DELIGHT_TILES) {
            assertEquals(
                VANILLA_LIMIT,
                invokeInventoryStackLimit(className, tileClassBytes(className)),
                "$className 的对照组（未转换字节码）必须仍是 64，否则本测试无法区分补丁效果",
            )
        }
    }

    @Test
    fun blacklistedFarmersDelightPackage_shouldKeepVanillaLiteral() {
        InventoryStackLimitHook.prepare(EXPANDED_LIMIT, arrayOf(FARMERS_DELIGHT_PACKAGE))

        for (className in FARMERS_DELIGHT_TILES) {
            val original = tileClassBytes(className)

            assertArrayEquals(original, transformer.transform(className, className, original))
            assertEquals(VANILLA_LIMIT, invokeInventoryStackLimit(className, original))
        }
    }

    @Test
    fun disabledExpansion_shouldKeepVanillaLiteral() {
        InventoryStackLimitHook.resetForTesting()
        InventoryStackLimitHook.configureExpansionEnabled(false)

        for (className in FARMERS_DELIGHT_TILES) {
            val original = tileClassBytes(className)

            assertArrayEquals(original, transformer.transform(className, className, original))
            assertEquals(VANILLA_LIMIT, invokeInventoryStackLimit(className, original))
        }
    }

    private fun tileClassBytes(className: String): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
            className.replace('.', '/'),
            null,
            "java/lang/Object",
            arrayOf(INVENTORY_INTERFACE),
        )

        val constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructor.visitCode()
        constructor.visitVarInsn(Opcodes.ALOAD, 0)
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        constructor.visitInsn(Opcodes.RETURN)
        constructor.visitMaxs(0, 0)
        constructor.visitEnd()

        val getter = writer.visitMethod(Opcodes.ACC_PUBLIC, INVENTORY_LIMIT_METHOD, "()I", null, null)
        getter.visitCode()
        getter.visitIntInsn(Opcodes.BIPUSH, VANILLA_LIMIT)
        getter.visitInsn(Opcodes.IRETURN)
        getter.visitMaxs(0, 0)
        getter.visitEnd()

        writer.visitEnd()
        return writer.toByteArray()
    }

    /**
     * 以真实 receiver 调用 getter：转换后的字节码把常量替换为
     * `InventoryStackLimitHook.resolveInventoryStackLimit(getClass(), 64)`，
     * 因此返回值取决于运行时 receiver 的类名与当前策略。
     */
    private fun invokeInventoryStackLimit(className: String, bytes: ByteArray): Int {
        val receiverClass = BytesClassLoader(className, bytes).define()
        val receiver = receiverClass.getDeclaredConstructor().newInstance()

        return receiverClass.getMethod(INVENTORY_LIMIT_METHOD).invoke(receiver) as Int
    }

    private fun literalOperands(bytes: ByteArray, opcode: Int): List<Int> {
        val operands = mutableListOf<Int>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM5) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (name != INVENTORY_LIMIT_METHOD || descriptor != "()I") {
                        return null
                    }
                    return object : MethodVisitor(Opcodes.ASM5) {
                        override fun visitIntInsn(op: Int, operand: Int) {
                            if (op == opcode) {
                                operands += operand
                            }
                        }
                    }
                }
            },
            0,
        )
        return operands
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

    /**
     * 直接 `defineClass`，不走 `loadClass`，因此不存在父优先问题：即使测试 classpath 上有同名的真实
     * Farmer's Delight 类，也一定用测试自己生成的字节。
     */
    private class BytesClassLoader(private val className: String, private val bytes: ByteArray) :
        ClassLoader(
            FarmersDelightAsmLiteralPatchTest::class.java.classLoader,
        ) {
        fun define(): Class<*> = defineClass(className, bytes, 0, bytes.size)
    }

    private companion object {
        const val VANILLA_LIMIT = 64
        const val EXPANDED_LIMIT = 256
        const val INVENTORY_INTERFACE = "net/minecraft/inventory/IInventory"
        const val INVENTORY_LIMIT_METHOD = "getInventoryStackLimit"
        const val FARMERS_DELIGHT_PACKAGE = "com.wdcftgg.farmersdelightlegacy"
        val FARMERS_DELIGHT_TILES = listOf(
            "com.wdcftgg.farmersdelightlegacy.common.tile.TileEntityBasket",
            "com.wdcftgg.farmersdelightlegacy.common.tile.TileEntityCabinet",
            "com.wdcftgg.farmersdelightlegacy.common.tile.TileEntityCookingPot",
            "com.wdcftgg.farmersdelightlegacy.common.tile.TileEntitySkillet",
        )
    }
}
