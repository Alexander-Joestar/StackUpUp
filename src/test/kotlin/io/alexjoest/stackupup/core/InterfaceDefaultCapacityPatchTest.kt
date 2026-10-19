package io.alexjoest.stackupup.core

import org.junit.jupiter.api.AfterEach
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
 * 接口 default 方法与抽象基类具体方法的容量补丁行为验证。
 *
 * <p>补丁体是 `ALOAD 0; INVOKEVIRTUAL java/lang/Object.getClass(); SIPUSH 64; INVOKESTATIC hook`。
 * 该序列在接口 default 方法里合法：JVMS §4.6/§4.10.1.2 中 default 方法的局部变量 0 就是 `this`
 * （声明类型即该接口），而任何接口类型都可赋值给 `java/lang/Object`，所以 `invokevirtual
 * java/lang/Object.getClass` 的接收者类型检查通过；default 方法本身要求 class file ≥ 52，本补丁不引入
 * 更高版本要求。这里不只依赖规范推断：转换后的字节码会被真正 defineClass（触发校验器）并在实现类实例上
 * 调用 getter，返回扩展上限才算通过。</p>
 */
class InterfaceDefaultCapacityPatchTest {
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
    fun interfaceDefaultGetterPatch_shouldVerifyAndResolveThroughTheImplementingReceiver() {
        val original = interfaceBytes(INTERFACE_NAME, withDefaultGetter = true)
        val transformed = transformer.transform(INTERFACE_NAME, INTERFACE_NAME, original)

        assertFalse(original.contentEquals(transformed), "接口 default 方法体应被字面量替换命中")
        assertEquals(1, hookInvocationCount(transformed))
        assertEquals(
            EXPANDED_LIMIT,
            invokeGetter(transformed, INTERFACE_NAME, IMPLEMENTATION_NAME),
            "转换后的接口 default 方法必须通过校验并返回扩展上限",
        )
        assertEquals(
            VANILLA_LIMIT,
            invokeGetter(original, INTERFACE_NAME, IMPLEMENTATION_NAME),
            "未转换的对照接口必须仍是 64，否则本测试无法区分补丁效果",
        )
    }

    @Test
    fun abstractClassGetterPatch_shouldVerifyAndResolveThroughTheConcreteSubclass() {
        val original = abstractClassBytes(ABSTRACT_CLASS_NAME)
        val transformed = transformer.transform(ABSTRACT_CLASS_NAME, ABSTRACT_CLASS_NAME, original)

        assertFalse(original.contentEquals(transformed), "抽象基类的具体 getter 应被字面量替换命中")
        assertEquals(
            EXPANDED_LIMIT,
            invokeGetter(transformed, ABSTRACT_CLASS_NAME, SUBCLASS_NAME),
            "抽象基类的具体 getter 改写后，未声明 getter 的子类必须继承扩展上限",
        )
        assertEquals(
            VANILLA_LIMIT,
            invokeGetter(original, ABSTRACT_CLASS_NAME, SUBCLASS_NAME),
            "未转换的对照基类必须仍是 64",
        )
    }

    @Test
    fun blacklistedReceiverPackage_shouldKeepVanilla64WhileTheInterfacePatchStays() {
        // 类加载阶段的 isBlacklisted 只看被改写的类自身：接口声明在 example.interfaces，
        // 因此 example.impl 被拉黑不会阻止接口被改写；运行期 hook 再按实际 receiver 判定，
        // 于是被拉黑包的实现仍拿到原值 64。
        InventoryStackLimitHook.prepare(EXPANDED_LIMIT, arrayOf(BLACKLISTED_RECEIVER_PACKAGE))

        val original = interfaceBytes(INTERFACE_NAME, withDefaultGetter = true)
        val transformed = transformer.transform(INTERFACE_NAME, INTERFACE_NAME, original)

        assertFalse(original.contentEquals(transformed), "接口自身未被拉黑，方法体仍应被改写")
        assertEquals(
            VANILLA_LIMIT,
            invokeGetter(transformed, INTERFACE_NAME, IMPLEMENTATION_NAME),
            "receiver 落在黑名单包时 hook 必须返回原值",
        )
    }

    private fun invokeGetter(interfaceOrBase: ByteArray, interfaceOrBaseName: String, implementationName: String): Int {
        val loader = BytesClassLoader(
            linkedMapOf(
                interfaceOrBaseName.replace('.', '/') to interfaceOrBase,
                implementationName.replace('.', '/') to implementationBytes(implementationName, interfaceOrBaseName),
            ),
        )
        val receiverClass = loader.define(implementationName)
        val receiver = receiverClass.getDeclaredConstructor().newInstance()

        return receiverClass.getMethod(INVENTORY_LIMIT_METHOD).invoke(receiver) as Int
    }

    private fun interfaceBytes(className: String, withDefaultGetter: Boolean): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT,
            className.replace('.', '/'),
            null,
            "java/lang/Object",
            arrayOf(INVENTORY_INTERFACE),
        )
        if (withDefaultGetter) {
            val getter = writer.visitMethod(Opcodes.ACC_PUBLIC, INVENTORY_LIMIT_METHOD, "()I", null, null)
            getter.visitCode()
            getter.visitIntInsn(Opcodes.BIPUSH, VANILLA_LIMIT)
            getter.visitInsn(Opcodes.IRETURN)
            getter.visitMaxs(0, 0)
            getter.visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun abstractClassBytes(className: String): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER or Opcodes.ACC_ABSTRACT,
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

    /** 实现类/子类都不声明 getter，容量只能来自被改写的接口 default 方法或抽象基类方法体。 */
    private fun implementationBytes(className: String, interfaceOrBaseName: String): ByteArray {
        val isInterface = interfaceOrBaseName == INTERFACE_NAME
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(
            Opcodes.V1_8,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
            className.replace('.', '/'),
            null,
            if (isInterface) "java/lang/Object" else interfaceOrBaseName.replace('.', '/'),
            if (isInterface) arrayOf(interfaceOrBaseName.replace('.', '/')) else null,
        )
        val constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        constructor.visitCode()
        constructor.visitVarInsn(Opcodes.ALOAD, 0)
        constructor.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            if (isInterface) "java/lang/Object" else interfaceOrBaseName.replace('.', '/'),
            "<init>",
            "()V",
            false,
        )
        constructor.visitInsn(Opcodes.RETURN)
        constructor.visitMaxs(0, 0)
        constructor.visitEnd()
        writer.visitEnd()
        return writer.toByteArray()
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

    private class BytesClassLoader(private val sources: Map<String, ByteArray>) :
        ClassLoader(InterfaceDefaultCapacityPatchTest::class.java.classLoader) {
        fun define(className: String): Class<*> = loadClass(className)

        override fun findClass(name: String): Class<*> {
            val bytes = requireNotNull(sources[name.replace('.', '/')]) { "missing generated bytes for $name" }
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    private companion object {
        const val VANILLA_LIMIT = 64
        const val EXPANDED_LIMIT = 256
        const val INVENTORY_INTERFACE = "net/minecraft/inventory/IInventory"
        const val INVENTORY_LIMIT_METHOD = "getInventoryStackLimit"
        const val INTERFACE_NAME = "example.interfaces.SidedInventory"
        const val IMPLEMENTATION_NAME = "example.impl.SidedChest"
        const val ABSTRACT_CLASS_NAME = "example.base.AbstractContainer"
        const val SUBCLASS_NAME = "example.impl.Chest"
        const val BLACKLISTED_RECEIVER_PACKAGE = "example.impl"
    }
}
