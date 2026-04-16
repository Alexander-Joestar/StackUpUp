package pl.asie.stackup.core

import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.io.ByteStreams
import net.minecraft.launchwrapper.IClassTransformer
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import pl.asie.stackup.StackUpConfig
import pl.asie.stackup.StackUpCore
import java.io.InputStream
import java.util.function.Consumer

private fun toDotName(name: String): String {
    val builder = StringBuilder(name.length)
    for (c in name) {
        builder.append(if (c == '/') '.' else c)
    }
    return builder.toString()
}

private fun toSlashName(name: String): String {
    val builder = StringBuilder(name.length)
    for (c in name) {
        builder.append(if (c == '.') '/' else c)
    }
    return builder.toString()
}

private fun hasPrefix(text: String, prefix: String): Boolean {
    if (text.length < prefix.length) {
        return false
    }
    for (i in prefix.indices) {
        if (text[i] != prefix[i]) {
            return false
        }
    }
    return true
}

// 这里是 coremod 启动期的热路径，尽量只用基础字符串操作，避免触发 kotlin.text 的额外类加载。
class StackUpTransformer : IClassTransformer {
    fun hasClass(s: String): Boolean {
        return try {
            Class.forName(s)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    override fun transform(name: String?, transformedNameIn: String, basicClass: ByteArray): ByteArray {
        if (transformedNameIn.length >= 7 &&
            transformedNameIn[0] == 'k' &&
            transformedNameIn[1] == 'o' &&
            transformedNameIn[2] == 't' &&
            transformedNameIn[3] == 'l' &&
            transformedNameIn[4] == 'i' &&
            transformedNameIn[5] == 'n' &&
            transformedNameIn[6] == '/'
        ) {
            return basicClass
        }

        val transformedName = toDotName(transformedNameIn)
        var consumer: Consumer<ClassNode> = Consumer { }
        var changed = false

        if (StackUpClassTracker.isImplements(transformedName, "net.minecraft.inventory.IInventory")) {
            consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("getInventoryStackLimit", "func_70297_j_"))
            changed = true
        }

        if (StackUpClassTracker.isImplements(transformedName, "net.minecraftforge.items.IItemHandler")) {
            consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("getSlotLimit"))
            changed = true
        }

        if (StackUpClassTracker.isExtends(transformedName, "net.minecraft.inventory.Slot")) {
            consumer = consumer.andThen(
                MaxStackConstantPatch.patchMaxLimit(
                    "getItemStackLimit",
                    "func_178170_b",
                    "getSlotStackLimit",
                    "func_75219_a"
                )
            )
            changed = true
        }

        when {
            StackUpConfig.coremodPatchRefinedStorage &&
                hasPrefix(transformedName, "com.raoulvdberge.refinedstorage.apiimpl.network.grid.handler.ItemGridHandler") -> {
                consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("onExtract"))
                changed = true
            }

            StackUpConfig.coremodPatchMantle && transformedName == "slimeknights.mantle.tileentity.TileInventory" -> {
                consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("<init>"))
                changed = true
            }

            StackUpConfig.coremodPatchIc2 && transformedName == "ic2.core.block.invslot.InvSlot" -> {
                consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("<init>"))
                changed = true
            }

            StackUpConfig.coremodPatchAppliedEnergistics2 && transformedName == "appeng.tile.inventory.AppEngInternalInventory" -> {
                consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("<init>"))
                changed = true
            }

            StackUpConfig.coremodPatchAppliedEnergistics2 && transformedName == "appeng.tile.inventory.AppEngInternalAEInventory" -> {
                consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("<init>"))
                changed = true
            }

            StackUpConfig.coremodPatchActuallyAdditions && transformedName == "de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase" -> {
                consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("getMaxStackSize"))
                changed = true
            }

            transformedName == "net.minecraft.client.renderer.entity.RenderEntityItem" -> {
                consumer = consumer.andThen(
                    Consumer { node ->
                        spliceClasses(
                            node,
                            "pl.asie.stackup.core.RenderEntityItemSplice",
                            "getModelCount",
                            "func_177078_a"
                        )
                        RenderEntityItemPatch.patchDistanceConstant(node)
                    }
                )
                changed = true
            }

            transformedName == "net.minecraft.inventory.InventoryHelper" -> {
                consumer = consumer.andThen(
                    Consumer { node ->
                        spliceClasses(
                            node,
                            "pl.asie.stackup.core.InventoryHelperPerformanceSplice",
                            "spawnItemStack",
                            "func_180173_a"
                        )
                    }
                )
                changed = true
            }

            transformedName == "net.minecraft.util.ServerRecipeBookHelper" -> {
                consumer = consumer.andThen(MaxStackConstantPatch.patchMaxLimit("func_194324_a"))
                changed = true
            }

            transformedName == "net.minecraft.network.PacketBuffer" -> {
                consumer = consumer.andThen(
                    Consumer { node ->
                        spliceClasses(
                            node,
                            "pl.asie.stackup.core.PacketBufferWriterSplice",
                            "readItemStack",
                            "func_150791_c",
                            "writeItemStack",
                            "func_150788_a"
                        )
                    }
                )
                changed = true
            }

            transformedName == "net.minecraft.client.renderer.RenderItem" -> {
                consumer = consumer.andThen(Consumer { node -> RenderItemPatch.patchDrawItemCount(node) })
                changed = true
            }

            transformedName == "net.minecraft.network.NetHandlerPlayServer" -> {
                consumer = consumer.andThen(Consumer { node -> NetHandlerPlayServerPatch.patchCreativeInventory(node) })
                changed = true
            }

            transformedName == "net.minecraftforge.common.util.PacketUtil" -> {
                consumer = consumer.andThen(
                    Consumer { node ->
                        spliceClasses(node, "pl.asie.stackup.core.PacketUtilWriterSplice", "writeItemStackFromClientToServer")
                    }
                )
                changed = true
            }

            transformedName == "net.minecraft.item.ItemStack" -> {
                consumer = consumer.andThen(Consumer { node -> ItemStackPatch.patchCountGetSet(node) })
                changed = true
            }
        }

        return if (changed) processNode(basicClass, consumer) else basicClass
    }

    companion object {
        @JvmStatic
        fun processNode(data: ByteArray, classNodeConsumer: Consumer<ClassNode>): ByteArray {
            val reader = ClassReader(data)
            val nodeOrig = ClassNode()
            reader.accept(nodeOrig, 0)
            classNodeConsumer.accept(nodeOrig)
            val writer = ClassWriter(0)
            nodeOrig.accept(writer)
            return writer.toByteArray()
        }

        @JvmStatic
        fun spliceClasses(data: ByteArray, className: String, vararg methods: String): ByteArray {
            val reader = ClassReader(data)
            val nodeOrig = ClassNode()
            reader.accept(nodeOrig, 0)
            val nodeNew = spliceClasses(nodeOrig, className, *methods)
            val writer = ClassWriter(0)
            nodeNew.accept(writer)
            return writer.toByteArray()
        }

        @JvmStatic
        fun spliceClasses(data: ClassNode, className: String, vararg methods: String): ClassNode {
            val stream: InputStream = StackUpCore::class.java.classLoader.getResourceAsStream(toSlashName(className) + ".class")
                ?: throw RuntimeException("Class $className not found! This is a FoamFix bug!")

            try {
                return spliceClasses(data, ByteStreams.toByteArray(stream), className, *methods)
            } catch (e: java.io.IOException) {
                throw RuntimeException(e)
            } finally {
                stream.close()
            }
        }

        @JvmStatic
        fun spliceClasses(nodeData: ClassNode, dataSplice: ByteArray?, className: String, vararg methods: String): ClassNode {
            if (dataSplice == null) {
                throw RuntimeException("Class $className not found! This is a FoamFix bug!")
            }

            val methodSet = Sets.newHashSet(*methods)
            val methodList = Lists.newArrayList(*methods)

            val readerSplice = ClassReader(dataSplice)
            val className2 = toSlashName(className)
            val targetClassName2 = nodeData.name
            val targetClassName = toDotName(targetClassName2)
            val remapper: Remapper = object : Remapper() {
                override fun map(name: String): String {
                    return if (className2 == name) targetClassName2 else name
                }
            }

            val nodeSplice = ClassNode()
            readerSplice.accept(ClassRemapper(nodeSplice, remapper), ClassReader.EXPAND_FRAMES)
            for (s in nodeSplice.interfaces) {
                val interfaceName = s as String
                if (methodSet.contains(interfaceName)) {
                    nodeData.interfaces.add(interfaceName)
                    println("Added INTERFACE: $interfaceName")
                }
            }

            for (i in nodeSplice.methods.indices) {
                val mn = nodeSplice.methods[i]
                if (methodSet.contains(mn.name)) {
                    var added = false

                    for (j in nodeData.methods.indices) {
                        if (nodeData.methods[j].name == mn.name && nodeData.methods[j].desc == mn.desc) {
                            val oldMn = nodeData.methods[j]
                            println("Spliced in METHOD: $targetClassName.${mn.name}")
                            nodeData.methods[j] = mn
                            if (nodeData.superName != null && nodeData.name == nodeSplice.superName) {
                                val nodeList = mn.instructions.toArray()
                                for (node in nodeList) {
                                    if (node is MethodInsnNode && node.getOpcode() == Opcodes.INVOKESPECIAL) {
                                        if (targetClassName2 == node.owner) {
                                            node.owner = nodeData.superName
                                        }
                                    }
                                }
                            }

                            val oldIndex = methodList.indexOf(oldMn.name)
                            val pairedIndex = oldIndex - (oldIndex % 2)
                            oldMn.name = methodList[pairedIndex] + "_stackup_old"
                            nodeData.methods.add(oldMn)
                            added = true
                            break
                        }
                    }

                    if (!added) {
                        println("Added METHOD: $targetClassName.${mn.name}")
                        nodeData.methods.add(mn)
                    }
                }
            }

            for (i in nodeSplice.fields.indices) {
                val fn: FieldNode = nodeSplice.fields[i]
                if (methodSet.contains(fn.name)) {
                    var added = false

                    for (j in nodeData.fields.indices) {
                        if (nodeData.fields[j].name == fn.name && nodeData.fields[j].desc == fn.desc) {
                            println("Spliced in FIELD: $targetClassName.${fn.name}")
                            nodeData.fields[j] = fn
                            added = true
                            break
                        }
                    }

                    if (!added) {
                        println("Added FIELD: $targetClassName.${fn.name}")
                        nodeData.fields.add(fn)
                    }
                }
            }

            return nodeData
        }
    }
}
