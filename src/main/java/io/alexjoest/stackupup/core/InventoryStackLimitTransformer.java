package io.alexjoest.stackupup.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Narrow class-load transformer for concrete third-party IInventory implementations.
 *
 * <p>Only a literal BIPUSH 64 in the exact inventory getter families is replaced. Hierarchy
 * metadata is read from bytes; no target class is resolved or initialized.</p>
 */
public final class InventoryStackLimitTransformer implements IClassTransformer {
    private static final int VANILLA_STACK_LIMIT = 64;
    private static final String INVENTORY_METHOD_DESCRIPTOR = "()I";
    private final InventoryClassHierarchyRepository hierarchyRepository;

    public InventoryStackLimitTransformer() {
        this(new InventoryClassHierarchyRepository());
    }

    InventoryStackLimitTransformer(InventoryClassHierarchyRepository hierarchyRepository) {
        this.hierarchyRepository = hierarchyRepository;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        InventoryStackLimitHook.ClassLoadPolicy policy = InventoryStackLimitHook.classLoadPolicy();
        if (!policy.isExpansionEnabled() || !policy.isPrepared()) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            InventoryClassHierarchyRepository.ClassMetadata metadata = readMetadata(reader);
            if (metadata == null
                || metadata.name == null
                || metadata.name.startsWith("net/minecraft/")
                || (metadata.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) != 0
                || !policy.shouldPatch(metadata.name)
                || !hierarchyRepository.implementsInventory(metadata)) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            LiteralPatchVisitor visitor = new LiteralPatchVisitor(writer, policy.getCompatibilityLimit());
            reader.accept(visitor, 0);
            return visitor.patched ? writer.toByteArray() : basicClass;
        } catch (RuntimeException ignored) {
            // Preserve the original class when ASM or hierarchy processing cannot handle it.
            return basicClass;
        }
    }

    private static InventoryClassHierarchyRepository.ClassMetadata readMetadata(ClassReader reader) {
        InventoryClassHierarchyRepository.MetadataVisitor visitor =
            new InventoryClassHierarchyRepository.MetadataVisitor();
        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return visitor.metadata;
    }

    private static boolean isInventoryMethod(String name, String descriptor) {
        return INVENTORY_METHOD_DESCRIPTOR.equals(descriptor)
            && ("getInventoryStackLimit".equals(name) || "func_70297_j_".equals(name));
    }

    private static void emitInteger(MethodVisitor visitor, int value) {
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            visitor.visitLdcInsn(value);
        }
    }

    private static final class LiteralPatchVisitor extends ClassVisitor {
        private final int compatibilityLimit;
        private boolean patched;

        private LiteralPatchVisitor(ClassVisitor delegate, int compatibilityLimit) {
            super(Opcodes.ASM5, delegate);
            this.compatibilityLimit = compatibilityLimit;
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name, final String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!isInventoryMethod(name, descriptor)
                || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) != 0) {
                return delegate;
            }
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visitIntInsn(int opcode, int operand) {
                    if (opcode == Opcodes.BIPUSH
                        && operand == VANILLA_STACK_LIMIT
                        && compatibilityLimit != VANILLA_STACK_LIMIT) {
                        emitInteger(this, compatibilityLimit);
                        patched = true;
                    } else {
                        super.visitIntInsn(opcode, operand);
                    }
                }
            };
        }
    }
}
