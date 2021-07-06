package fr.i360matt.runnableOverNetwork;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicClassLoader extends SecureClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final ConcurrentHashMap<String, byte[]> rawClasses;
    private final ConcurrentHashMap<String, Object> rawInstances;
    private final CodeSource codeSource;

    public DynamicClassLoader(boolean isolated) {
        super(isolated ? null : DynamicClassLoader.class.getClassLoader());
        this.rawClasses = new ConcurrentHashMap<>();
        this.rawInstances = new ConcurrentHashMap<>();
        this.codeSource = new CodeSource(null, new CodeSigner[0]);
    }

    public void putClass(String className, byte[] classData) {
        this.rawClasses.put(className, classData);
    }

    public boolean hasClass(String className) {
        return this.rawClasses.containsKey(className);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] cl;
        if (name.endsWith(".class") && (cl = rawClasses.get(
                name.substring(0, name.length() - 6).replace('/', '.'))) != null) {
            return new ByteArrayInputStream(cl);
        }
        return super.getResourceAsStream(name);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        name = name.replace('/', '.');
        byte[] cl;
        if ((cl = rawClasses.get(name.replace('/', '.'))) != null) {
            int index = name.lastIndexOf('.');
            if (index != -1) {
                String pkg = name.substring(0, index);
                if (this.getPackage(pkg) == null) {
                    this.definePackage(pkg, null, null, null, null, null, null, null);
                }
            }
            return defineClass(name, cl, 0, cl.length, this.codeSource);
        }
        throw new ClassNotFoundException(name);
    }

    public Object loadInstance(String name) throws ReflectiveOperationException {
        return this.rawInstances.computeIfAbsent(name, n -> {
            try {
                return findClass(n).newInstance();
            } catch (ReflectiveOperationException e) {
                IOHelper.sneakyThrow(e);
                return null;
            }
        });
    }
}
