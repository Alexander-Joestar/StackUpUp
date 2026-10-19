package io.alexjoest.stackupup.core;

import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Reads class hierarchy metadata without resolving or initializing target classes. */
final class InventoryClassHierarchyRepository {
    private static final String INVENTORY_INTERFACE = "net/minecraft/inventory/IInventory";
    private static final String ITEM_HANDLER_INTERFACE = "net/minecraftforge/items/IItemHandler";
    private static final String SLOT_CLASS = "net/minecraft/inventory/Slot";
    private final MetadataSource source;

    InventoryClassHierarchyRepository() {
        this(new ClassLoaderMetadataSource(InventoryClassHierarchyRepository.class.getClassLoader()));
    }

    InventoryClassHierarchyRepository(MetadataSource source) {
        this.source = source;
    }

    boolean isStackLimitTarget(ClassMetadata root) {
        return implementsInventory(root) || implementsItemHandler(root) || extendsSlot(root);
    }

    boolean implementsInventory(ClassMetadata root) {
        return implementsInterface(root, INVENTORY_INTERFACE);
    }

    private boolean implementsItemHandler(ClassMetadata root) {
        return implementsInterface(root, ITEM_HANDLER_INTERFACE);
    }

    private boolean extendsSlot(ClassMetadata root) {
        if (root == null) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        String current = root.superName;
        while (current != null && visited.add(current)) {
            if (SLOT_CLASS.equals(current)) {
                return true;
            }
            ClassMetadata metadata = read(current);
            if (metadata == null) {
                return false;
            }
            current = metadata.superName;
        }
        return false;
    }

    private boolean implementsInterface(ClassMetadata root, String targetInterface) {
        if (root == null) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        if (root.name != null) {
            visited.add(root.name);
        }
        return implementsInterface(root.interfaces, root.superName, targetInterface, visited);
    }

    private boolean implementsInterface(String[] interfaces, String superName, String targetInterface, Set<String> visited) {
        if (interfaces != null) {
            for (String interfaceName : interfaces) {
                if (targetInterface.equals(interfaceName)
                    || implementsInterface(interfaceName, targetInterface, visited)) {
                    return true;
                }
            }
        }
        return superName != null && implementsInterface(superName, targetInterface, visited);
    }

    private boolean implementsInterface(String className, String targetInterface, Set<String> visited) {
        if (targetInterface.equals(className)) {
            return true;
        }
        if (!visited.add(className)) {
            return false;
        }
        ClassMetadata metadata = read(className);
        return metadata != null
            && implementsInterface(metadata.interfaces, metadata.superName, targetInterface, visited);
    }

    private ClassMetadata read(String internalName) {
        byte[] bytes;
        try {
            bytes = source.read(internalName);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        if (bytes == null) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(bytes);
            MetadataVisitor visitor = new MetadataVisitor();
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor.metadata;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    interface MetadataSource {
        byte[] read(String internalName) throws IOException;
    }

    private static final class ClassLoaderMetadataSource implements MetadataSource {
        private final ClassLoader loader;

        private ClassLoaderMetadataSource(ClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public byte[] read(String internalName) throws IOException {
            String resourceName = FMLDeobfuscatingRemapper.INSTANCE.unmap(internalName) + ".class";
            InputStream stream = loader == null
                ? ClassLoader.getSystemResourceAsStream(resourceName)
                : loader.getResourceAsStream(resourceName);
            if (stream == null) {
                return null;
            }
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        output.write(buffer, 0, count);
                    }
                }
                return output.toByteArray();
            }
        }
    }

    static final class ClassMetadata {
        final String name;
        final int access;
        final String superName;
        final String[] interfaces;

        ClassMetadata(String name, int access, String superName, String[] interfaces) {
            this.name = name;
            this.access = access;
            this.superName = superName;
            this.interfaces = interfaces == null ? new String[0] : interfaces;
        }
    }

    static final class MetadataVisitor extends ClassVisitor {
        ClassMetadata metadata;

        MetadataVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            String[] mappedInterfaces = interfaces == null ? new String[0] : new String[interfaces.length];
            if (interfaces != null) {
                for (int index = 0; index < interfaces.length; index++) {
                    mappedInterfaces[index] = mapName(interfaces[index]);
                }
            }
            metadata = new ClassMetadata(
                mapName(name),
                access,
                mapName(superName),
                mappedInterfaces
            );
        }

        private static String mapName(String internalName) {
            return internalName == null ? null : FMLDeobfuscatingRemapper.INSTANCE.map(internalName);
        }
    }
}
