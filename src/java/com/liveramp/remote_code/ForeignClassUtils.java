package com.liveramp.remote_code;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.log4j.lf5.util.StreamUtils;
import org.jetbrains.annotations.NotNull;

import com.liveramp.importer.generated.ImportRecordID;
import com.liveramp.importer.generated.OnlineImportRecord;
import com.liveramp.java_support.functional.Fn;
import com.liveramp.types.anonymous_records.AnonymousRecord;

public class ForeignClassUtils {

  public static <T> ForeignClassFactory<T> loadWithRedefs(
      Class<T> clazz,
      String replacingJar) throws Exception {
    return loadWithRedefs(clazz, replacingJar, new Object[0]);
  }

  public static <T> ForeignClassFactory<T> loadWithRedefs(
      Class<T> clazz,
      String replacingJar,
      Collection<Object> constructorArgs) throws Exception {
    return loadWithRedefs(clazz, replacingJar, constructorArgs.toArray());
  }


  public static <T> ForeignClassFactory<T> loadWithRedefs(
      Class<T> clazz,
      String replacingJar,
      Object... constructorArgs) throws Exception {
    return loadWithRedefs(clazz.getCanonicalName(), clazz, replacingJar, constructorArgs);
  }

  public static <T> ForeignClassFactory<T> loadWithRedefs(
      String className,
      Class<T> interfaceClass,
      String replacingJar,
      Object... constructorArgs) throws Exception {

    File file = new File(replacingJar);

    if (!file.exists()) {
      throw new IllegalArgumentException("Could not find jar at " + replacingJar);
    }

    URL jarURL = file.toURI().toURL();

    ClassLoader currentClassloader = Thread.currentThread().getContextClassLoader();
    URLClassLoader alternate = new URLClassLoader(new URL[]{jarURL}, null);

    SelectiveHandoffClassLoader foreignLoader = new SelectiveHandoffClassLoader(
        currentClassloader,
        alternate,
        Maps.<String, byte[]>newHashMap());

    //Gets necessary class definitions in byte form for all deps
    loadHierarchy(foreignLoader, alternate, className, ClassPool.getDefault());

    return new ForeignClassFactoryImpl<T>(foreignLoader.getClassDefinitions(), className, constructorArgs);
  }

  private static void loadHierarchy(SelectiveHandoffClassLoader loader, ClassLoader alternate, String classname, ClassPool pool) throws Exception {
    loader.loadClass(classname);
    String internal = classNameToInternalName(classname);
    InputStream resourceAsStream = alternate.getResourceAsStream(internal);
    CtClass ctClass = pool.makeClass(resourceAsStream);
    Collection<String> refClasses = ctClass.getRefClasses();
    if (!loader.getKnownParentClasses().contains(classname)) { //Only keep going if we're still loading from foreign jar
      for (String refClass : refClasses) {
        if (!loader.getClassDefinitions().containsKey(refClass)) {
          loadHierarchy(loader, alternate, refClass, pool);
        }
      }
    }
  }

  private static Class<?>[] getClasses(Object[] constructorArgs) {
    Class<?>[] constructorArgTypes = new Class[constructorArgs.length];
    for (int i = 0; i < constructorArgs.length; i++) {
      constructorArgTypes[i] = constructorArgs[i].getClass();
    }
    return constructorArgTypes;
  }


  private static Class<?> load(ClassLoader loader, String canonicalName) throws ClassNotFoundException {
    try {
      return loader.loadClass(canonicalName);
    } catch (ClassNotFoundException e) {
      return loader.loadClass(makeInner(canonicalName));
    }
  }

  private static String makeInner(String canonicalName) {
    return replaceLast(canonicalName, ".", "$");
  }

  private static String replaceLast(String string, String substring, String replacement) {
    int index = string.lastIndexOf(substring);
    if (index == -1) {
      return string;
    }
    return string.substring(0, index) + replacement
        + string.substring(index + substring.length());
  }

  private static class SelectiveHandoffClassLoader extends ClassLoader {

    private final ClassLoader alternate;
    private final Map<String, byte[]> classDefinitions;
    private final Set<String> knownParentClasses;


    public SelectiveHandoffClassLoader(
        ClassLoader parent,
        ClassLoader alternate,
        Map<String, byte[]> predefinedClasses) {
      super(parent);
      this.alternate = alternate;
      this.classDefinitions = predefinedClasses;
      this.knownParentClasses = Sets.newHashSet();
    }

    @Override
    public Class<?> loadClass(String s) throws ClassNotFoundException {
      if (classDefinitions.containsKey(s)) {
        byte[] bytes = classDefinitions.get(s);
        return this.defineClass(s, bytes, 0, bytes.length);
      }
      if (knownParentClasses.contains(s)) {
        return this.loadClass(s, false);
      }

      if (alternate != null) {
        try {
          String internalName = classNameToInternalName(s);

          InputStream alternateClassStream = alternate.getResourceAsStream(internalName);
          InputStream standardClassStream = this.getResourceAsStream(internalName);

          if (standardClassStream != null) {
            return loadFromParent(s);
          } else if (alternateClassStream != null) {
            return loadAlternate(s, internalName);
          } else {
            throw new RuntimeException("Class not found " + s + " internal name: " + internalName);
          }
        } catch (ClassNotFoundException e) {
          throw e;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else {
        return loadFromParent(s);

      }
    }

    private Class loadFromParent(String s) throws ClassNotFoundException {
      knownParentClasses.add(s);
      return this.loadClass(s, true);
    }

    public Set<String> getKnownParentClasses() {
      return knownParentClasses;
    }

    public Class<?> loadAlternate(String s, String internalName) throws IOException, ClassNotFoundException {
      byte[] bytes = StreamUtils.getBytes(alternate.getResourceAsStream(internalName));
      classDefinitions.put(s, bytes);
      return alternate.loadClass(s);
    }

    public Map<String, byte[]> getClassDefinitions() {
      return classDefinitions;
    }
  }

  @NotNull
  private static String classNameToInternalName(String s) {
    return s.replace('.', '/') + ".class";
  }

  private static class ForeignClassFactoryImpl<T> implements ForeignClassFactory<T> {

    private final Map<String, byte[]> classDefs;
    private final String className;
    private final Object[] constructorArgs;

    public ForeignClassFactoryImpl(Map<String, byte[]> classDefs, String className, Object[] constructorArgs) {
      this.classDefs = classDefs;
      this.className = className;
      this.constructorArgs = constructorArgs;
    }

    @Override
    public T createUsingStoredArgs() {
      return createNewObject(constructorArgs);
    }

    public T createNewObject(Object... args) {
      SelectiveHandoffClassLoader loader =
          new SelectiveHandoffClassLoader(ClassLoader.getSystemClassLoader(),
              null,
              classDefs);
      try {
        Class<?> load = load(loader, className);
        Object reloadedObject = load.getConstructor(getClasses(args)).newInstance(args);
        return (T)reloadedObject;
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      } catch (InstantiationException e) {
        throw new RuntimeException(e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }


  }

  public static void main(String[] args) throws Exception {

    ForeignClassFactory<Fn<OnlineImportRecord, AnonymousRecord>> factory = loadWithRedefs(
        "com.liveramp.audience_compiler.converters.OnlineImportRecordToAnonymousRecord",
        (Class<Fn<OnlineImportRecord, AnonymousRecord>>)(Class)Fn.class,
        "/Users/pwestling/dev/audience_compiler/build/audience_compiler.job.jar",
        123L);

    Fn<OnlineImportRecord, AnonymousRecord> fn = factory.createUsingStoredArgs();

    OnlineImportRecord record = new OnlineImportRecord(
        new ImportRecordID(1L, 1, 1L),
        Maps.newHashMap(),
        Lists.newArrayList(),
        Lists.newArrayList());
    AnonymousRecord result = fn.apply(record);

    System.out.println(result);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    oos.writeObject(factory);

    oos.close();

    byte[] bytes = baos.toByteArray();
    System.out.println(bytes.length);

    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

    ObjectInputStream ois = new ObjectInputStream(bais);

    Object o = ois.readObject();

    Fn<OnlineImportRecord, AnonymousRecord> f = ((ForeignClassFactory<Fn<OnlineImportRecord, AnonymousRecord>>)o).createUsingStoredArgs();
    AnonymousRecord result2 = f.apply(record);

    System.out.println(result2);
  }

}
