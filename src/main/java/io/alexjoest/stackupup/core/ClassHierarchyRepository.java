package io.alexjoest.stackupup.core;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class ClassHierarchyRepository {

    private ClassHierarchyRepository() {}

    private static final class Metadata {
        final String superClass;
        final Set<String> interfaces;

        Metadata(String superClass, Set<String> interfaces) {
            this.superClass = superClass;
            this.interfaces = interfaces;
        }
    }

    private static final ConcurrentHashMap<String, Metadata> cache = new ConcurrentHashMap<>();

    static String superClassOf(String className) {
        return get(className).superClass;
    }

    static Set<String> interfacesOf(String className) {
        return get(className).interfaces;
    }

    private static Metadata get(String className) {
        Metadata existing = cache.get(className);
        if (existing != null) {
            return existing;
        }
        Metadata loaded = loadMetadata(className);
        Metadata previous = cache.putIfAbsent(className, loaded);
        return previous != null ? previous : loaded;
    }

    private static Metadata loadMetadata(String className) {
        String fileName = FMLDeobfuscatingRemapper.INSTANCE.unmap(NameConverter.toSlashName(className));
        fileName = NameConverter.toSlashName(fileName) + ".class";
        InputStream stream = ClassHierarchyRepository.class.getClassLoader().getResourceAsStream(fileName);
        if (stream == null) {
            return new Metadata(null, Collections.<String>emptySet());
        }

        try {
            HierarchySignatureCollector collector = new HierarchySignatureCollector();
            ClassReader reader = new ClassReader(stream);
            reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return collector.toMetadata();
        } catch (Exception e) {
            return new Metadata(null, Collections.<String>emptySet());
        } finally {
            try { stream.close(); } catch (Exception ignored) {}
        }
    }

    private static class HierarchySignatureCollector extends ClassVisitor {
        private String rawSuperClass;
        private String[] rawInterfaces = new String[0];

        HierarchySignatureCollector() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            rawSuperClass = superName;
            rawInterfaces = interfaces;
        }

        Metadata toMetadata() {
            String superClass = null;
            if (rawSuperClass != null) {
                superClass = NameConverter.toDotName(FMLDeobfuscatingRemapper.INSTANCE.map(rawSuperClass));
            }
            LinkedHashSet<String> interfaces = new LinkedHashSet<>(rawInterfaces.length);
            for (String rawInterface : rawInterfaces) {
                interfaces.add(NameConverter.toDotName(FMLDeobfuscatingRemapper.INSTANCE.map(rawInterface)));
            }
            return new Metadata(superClass, interfaces);
        }
    }
}
