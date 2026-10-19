package io.alexjoest.stackupup.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Structural class-load transformer for inventory, item-handler, and slot capacity getters.
 *
 * <p>Every class that implements {@code IInventory} or {@code IItemHandler}, or extends
 * {@code Slot}, and declares a supported capacity method with a body is a target regardless of
 * namespace and regardless of whether the class or the method is concrete: interface {@code default}
 * methods and concrete methods on abstract classes are rewritten too, which is what lets the
 * implementations and subclasses that never override the getter inherit the expanded capacity
 * (for vanilla, {@code EntityMinecartContainer} is abstract and its concrete
 * {@code EntityMinecartChest} / {@code EntityMinecartHopper} declare no getter of their own). Only
 * BIPUSH 64 constants in the supported capacity methods are replaced, so classes that delegate or
 * compute their limit are untouched. Declarations without a body to rewrite stay untouched even
 * inside a target class: an interface or abstract getter ({@code ACC_ABSTRACT}), a {@code native}
 * method, and a {@code static} method (no {@code this} to emit the receiver hook with) are skipped
 * per method, not per class. Class names whose capacity is owned by an explicit configuration path
 * are excluded, as is this mod's own package. Hierarchy metadata is read from bytes; no target class
 * is resolved or initialized.</p>
 *
 * <p>The hook call is emitted unconditionally and {@link InventoryStackLimitHook} resolves the value
 * when the getter runs, so a class transformed before the startup policy is prepared still resolves
 * the vanilla 64 until the policy exists instead of staying unpatched for the whole session.</p>
 *
 * <p>This transformer widens the read side only; it widens no write path and cannot make an
 * inventory store more than its own writer accepts. {@code InventoryWriteLimitMixin} wraps just
 * {@code TileEntityLockableLoot#setInventorySlotContents}, so it covers the vanilla subclasses that
 * inherit that setter ({@code TileEntityChest}, {@code TileEntityDispenser},
 * {@code TileEntityShulkerBox}) and not {@code TileEntityFurnace} or
 * {@code TileEntityBrewingStand} (they extend {@code TileEntityLockable}), not
 * {@code TileEntityHopper} (it overrides the setter), and not {@code EntityMinecartContainer}
 * (abstract, own setter). Those classes either clamp their own writes to the same getter they
 * advertise ({@code TileEntityFurnace}, {@code TileEntityHopper}, {@code EntityMinecartContainer})
 * or do not clamp at all ({@code TileEntityBrewingStand}), so advertised and stored capacity stay
 * consistent, but none of them receives an item-aware write rule. {@code EntityMinecartChest} and
 * {@code EntityMinecartHopper} inherit their getter from the abstract
 * {@code EntityMinecartContainer}, so the inherited declaration is patched here and both now
 * advertise the compatibility limit; their inherited setter clamps against that same getter, so the
 * raise is consistent rather than advertised-only.</p>
 *
 * <p>Blacklist scope differs by check. The class-load check ({@code policy.isBlacklisted}) sees only
 * the name of the class being rewritten, so it decides whether a declaring class is patched at all:
 * an interface {@code default} method can be excluded only by blacklisting the package that declares
 * the interface, never by blacklisting an implementing mod. The hook call then resolves at getter
 * time from the actual receiver class, so a blacklisted implementing package still receives the
 * original 64 from the patched declaration. The patched interface keeps the hook call and stays a
 * no-op for those receivers.</p>
 */
public final class InventoryStackLimitTransformer implements IClassTransformer {
    private static final int VANILLA_STACK_LIMIT = 64;
    private static final String CORE_PACKAGE_PREFIX = "io/alexjoest/stackupup/";
    private static final Set<String> CONFIG_MANAGED_CAPACITY_CLASSES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            // compat.vanilla.craftingSlotLimit owns these two through VanillaInventoryLimitMixin;
            // patching the literal here would take precedence and bypass that configuration.
            "net/minecraft/inventory/InventoryCrafting",
            "net/minecraft/inventory/InventoryCraftResult"
        ))
    );
    private static final String INVENTORY_METHOD_DESCRIPTOR = "()I";
    private static final String ITEM_HANDLER_METHOD_DESCRIPTOR = "(I)I";
    private static final String SLOT_ITEM_STACK_METHOD_DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;)I";
    private static final String HELPER_OWNER = "io/alexjoest/stackupup/core/InventoryStackLimitHook";
    private static final String HELPER_METHOD = "resolveInventoryStackLimit";
    private static final String HELPER_DESCRIPTOR = "(Ljava/lang/Class;I)I";
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
        if (!policy.isExpansionEnabled()) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            InventoryClassHierarchyRepository.ClassMetadata metadata = readMetadata(reader);
            if (metadata == null
                || metadata.name == null
                || isExcludedClass(metadata.name)
                || isExcludedClass(name)
                || isExcludedClass(transformedName)
                || policy.isBlacklisted(metadata.name)
                || !hierarchyRepository.isStackLimitTarget(metadata)) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            LiteralPatchVisitor visitor = new LiteralPatchVisitor(writer);
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

    private static boolean isExcludedClass(String className) {
        if (className == null) {
            return false;
        }
        String internalName = className.replace('.', '/');
        return internalName.startsWith(CORE_PACKAGE_PREFIX)
            || "io/alexjoest/stackupup".equals(internalName)
            || isConfigManagedCapacityClass(internalName);
    }

    private static boolean isConfigManagedCapacityClass(String internalName) {
        return internalName != null && CONFIG_MANAGED_CAPACITY_CLASSES.contains(internalName);
    }

    private static boolean isInventoryMethod(String name, String descriptor) {
        return INVENTORY_METHOD_DESCRIPTOR.equals(descriptor)
            && ("getInventoryStackLimit".equals(name) || "func_70297_j_".equals(name));
    }

    private static boolean isItemHandlerMethod(String name, String descriptor) {
        return "getSlotLimit".equals(name) && ITEM_HANDLER_METHOD_DESCRIPTOR.equals(descriptor);
    }

    private static boolean isSlotMethod(String name, String descriptor) {
        if (("getSlotStackLimit".equals(name) || "func_75219_a".equals(name))
            && INVENTORY_METHOD_DESCRIPTOR.equals(descriptor)) {
            return true;
        }
        if (!"getItemStackLimit".equals(name) && !"func_178170_b".equals(name)) {
            return false;
        }
        return SLOT_ITEM_STACK_METHOD_DESCRIPTOR.equals(FMLDeobfuscatingRemapper.INSTANCE.mapDesc(descriptor));
    }

    private static boolean isCapacityMethod(String name, String descriptor) {
        return isInventoryMethod(name, descriptor)
            || isItemHandlerMethod(name, descriptor)
            || isSlotMethod(name, descriptor);
    }

    private static final class LiteralPatchVisitor extends ClassVisitor {
        private boolean patched;

        private LiteralPatchVisitor(ClassVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name, final String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!isCapacityMethod(name, descriptor)
                || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) != 0) {
                return delegate;
            }
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visitIntInsn(int opcode, int operand) {
                    if (opcode == Opcodes.BIPUSH && operand == VANILLA_STACK_LIMIT) {
                        visitVarInsn(Opcodes.ALOAD, 0);
                        visitMethodInsn(
                            Opcodes.INVOKEVIRTUAL,
                            "java/lang/Object",
                            "getClass",
                            "()Ljava/lang/Class;",
                            false
                        );
                        visitIntInsn(Opcodes.SIPUSH, VANILLA_STACK_LIMIT);
                        visitMethodInsn(Opcodes.INVOKESTATIC, HELPER_OWNER, HELPER_METHOD, HELPER_DESCRIPTOR, false);
                        patched = true;
                    } else {
                        super.visitIntInsn(opcode, operand);
                    }
                }
            };
        }
    }
}
