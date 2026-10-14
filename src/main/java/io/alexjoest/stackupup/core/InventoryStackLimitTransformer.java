package io.alexjoest.stackupup.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

/**
 * Narrow compatibility transformer for concrete classes that implement IInventory.
 *
 * <p>Only getInventoryStackLimit()I (and its 1.12 SRG name) is wrapped. Hierarchy metadata is read as
 * bytes; no target class is resolved or initialized, and no Forge or configuration class is touched
 * from this transformer.</p>
 */
public final class InventoryStackLimitTransformer implements IClassTransformer {
    private static final String HOOK_OWNER = "io/alexjoest/stackupup/core/InventoryStackLimitHook";
    private static final String HOOK_NAME = "resolveInventoryStackLimit";
    private static final String HOOK_DESCRIPTOR = "(Ljava/lang/String;I)I";
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
        if (!InventoryStackLimitHook.isExpansionEnabled()) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            InventoryClassHierarchyRepository.ClassMetadata metadata = readMetadata(reader);
            if (metadata == null
                || (metadata.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) != 0
                || !hierarchyRepository.implementsInventory(metadata)) {
                return basicClass;
            }

            Set<String> declaredMethods = findDeclaredMethods(reader);
            Set<String> alreadyTransformed = findAlreadyTransformedMethods(reader);
            Set<String> targetMethods = findTargetMethods(reader);
            targetMethods.removeAll(alreadyTransformed);
            String inheritedGetter = declaredMethods.isEmpty()
                ? hierarchyRepository.inheritedGetterForConcreteClass(metadata)
                : null;
            if (targetMethods.isEmpty() && inheritedGetter == null) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new InventoryMethodVisitor(writer, targetMethods), 0);
            if (inheritedGetter != null) {
                addInheritedGetter(writer, metadata.superName, inheritedGetter);
            }
            return writer.toByteArray();
        } catch (RuntimeException ignored) {
            // A third-party class must never prevent LaunchClassLoader from starting.
            return basicClass;
        }
    }

    private static InventoryClassHierarchyRepository.ClassMetadata readMetadata(ClassReader reader) {
        InventoryClassHierarchyRepository.MetadataVisitor visitor =
            new InventoryClassHierarchyRepository.MetadataVisitor();
        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return visitor.metadata;
    }

    private static Set<String> findDeclaredMethods(ClassReader reader) {
        MethodProbeVisitor visitor = new MethodProbeVisitor(false, true);
        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return visitor.methods;
    }

    private static Set<String> findTargetMethods(ClassReader reader) {
        MethodProbeVisitor visitor = new MethodProbeVisitor(false, false);
        reader.accept(visitor, 0);
        return visitor.methods;
    }

    private static Set<String> findAlreadyTransformedMethods(ClassReader reader) {
        MethodProbeVisitor visitor = new MethodProbeVisitor(true, false);
        reader.accept(visitor, 0);
        return visitor.methods;
    }

    private static boolean isInventoryMethod(String name, String descriptor) {
        return INVENTORY_METHOD_DESCRIPTOR.equals(descriptor)
            && ("getInventoryStackLimit".equals(name) || "func_70297_j_".equals(name));
    }

    private static String methodKey(String name, String descriptor) {
        return name + descriptor;
    }

    private static void addInheritedGetter(ClassWriter writer, String superName, String methodName) {
        MethodVisitor method = writer.visitMethod(
            Opcodes.ACC_PUBLIC,
            methodName,
            INVENTORY_METHOD_DESCRIPTOR,
            null,
            null
        );
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            superName,
            methodName,
            INVENTORY_METHOD_DESCRIPTOR,
            false
        );
        emitRuntimeReceiverHook(method);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitRuntimeReceiverHook(MethodVisitor visitor) {
        visitor.visitVarInsn(Opcodes.ALOAD, 0);
        visitor.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/Object",
            "getClass",
            "()Ljava/lang/Class;",
            false
        );
        visitor.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/Class",
            "getName",
            "()Ljava/lang/String;",
            false
        );
        visitor.visitInsn(Opcodes.SWAP);
        visitor.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            HOOK_OWNER,
            HOOK_NAME,
            HOOK_DESCRIPTOR,
            false
        );
    }

    private static final class MethodProbeVisitor extends ClassVisitor {
        private final boolean findHook;
        private final boolean includeNonConcrete;
        private final Set<String> methods = new HashSet<>();

        private MethodProbeVisitor(boolean findHook, boolean includeNonConcrete) {
            super(Opcodes.ASM5);
            this.findHook = findHook;
            this.includeNonConcrete = includeNonConcrete;
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name, final String descriptor,
                                         String signature, String[] exceptions) {
            if (!isInventoryMethod(name, descriptor)
                || (!includeNonConcrete
                    && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) != 0)) {
                return null;
            }
            if (!findHook) {
                methods.add(methodKey(name, descriptor));
                return null;
            }
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if (findHook && opcode == Opcodes.INVOKESTATIC
                        && HOOK_OWNER.equals(owner)
                        && HOOK_NAME.equals(methodName)
                        && HOOK_DESCRIPTOR.equals(methodDescriptor)) {
                        methods.add(methodKey(name, descriptor));
                    }
                }
            };
        }
    }

    private static final class InventoryMethodVisitor extends ClassVisitor {
        private final Set<String> targetMethods;

        private InventoryMethodVisitor(ClassVisitor delegate, Set<String> targetMethods) {
            super(Opcodes.ASM5, delegate);
            this.targetMethods = targetMethods;
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name, final String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!targetMethods.contains(methodKey(name, descriptor))) {
                return delegate;
            }
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.IRETURN) {
                        emitRuntimeReceiverHook(this);
                    }
                    super.visitInsn(opcode);
                }
            };
        }
    }
}
