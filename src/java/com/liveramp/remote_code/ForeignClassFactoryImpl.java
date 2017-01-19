package com.liveramp.remote_code;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

class ForeignClassFactoryImpl<T> implements ForeignClassFactory<T> {

  private final Map<String, byte[]> classDefs;
  private final String className;
  private final Object[] constructorArgs;

  public ForeignClassFactoryImpl(Map<String, byte[]> classDefs, String className, Object[] constructorArgs) {
    this.classDefs = classDefs;
    this.className = className;
    this.constructorArgs = constructorArgs;
  }

  public static Class<?> load(ClassLoader loader, String canonicalName) throws ClassNotFoundException {
    try {
      return loader.loadClass(canonicalName);
    } catch (ClassNotFoundException e) {
      return loader.loadClass(makeInner(canonicalName));
    }
  }

  public static String makeInner(String canonicalName) {
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
      Object reloadedObject = load.getConstructor(ForeignClassUtils.getClasses(args)).newInstance(args);
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
