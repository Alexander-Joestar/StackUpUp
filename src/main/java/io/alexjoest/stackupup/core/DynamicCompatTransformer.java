package io.alexjoest.stackupup.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.function.Consumer;

/**
 * 当前保留的最小动态兼容层。
 *
 * 这层只负责：
 * 1. 过滤显然无关的基础运行时类
 * 2. 把类名与字节码交给补丁决策函数
 * 3. 把补丁计划应用到字节码
 */
public class DynamicCompatTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedNameIn, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        String internalName = transformedNameIn != null ? transformedNameIn : name;
        if (internalName == null || internalName.isEmpty() || CoremodClassFilter.shouldSkip(internalName)) {
            return basicClass;
        }

        String transformedName = NameConverter.toDotName(internalName);
        List<Consumer<ClassNode>> patches = CompatibilityLimitPatch.planFor(transformedName, basicClass);
        if (patches.isEmpty()) {
            return basicClass;
        }

        return applyPatches(basicClass, patches);
    }

    private byte[] applyPatches(byte[] data, List<Consumer<ClassNode>> patches) {
        ClassReader reader = new ClassReader(data);
        ClassNode originalNode = new ClassNode();
        reader.accept(originalNode, 0);
        for (Consumer<ClassNode> patch : patches) {
            patch.accept(originalNode);
        }
        ClassWriter writer = new ClassWriter(0);
        originalNode.accept(writer);
        return writer.toByteArray();
    }
}
