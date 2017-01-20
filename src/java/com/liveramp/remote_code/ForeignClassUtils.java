package com.liveramp.remote_code;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.lf5.util.StreamUtils;
import org.jetbrains.annotations.NotNull;

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

  public static void loadHierarchy(SelectiveHandoffClassLoader loader, ClassLoader alternate, String classname, ClassPool pool) throws Exception {
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

  public static void operateOnDependencies(ClassLoader loader, String classname, ClassPool pool, Set<String> foundClasses, Consumer<String> consumer) throws Exception {
    consumer.accept(classname);
    foundClasses.add(classname);
    String internal = classNameToInternalName(classname);
    InputStream resourceAsStream = loader.getResourceAsStream(internal);
    try {
      CtClass ctClass = pool.makeClass(resourceAsStream);
      Collection<String> refClasses = ctClass.getRefClasses();
      for (String refClass : refClasses) {
        if (!foundClasses.contains(refClass)) {
          operateOnDependencies(loader, refClass, pool, foundClasses, consumer);
        }
      }
    } catch (IOException e) {
      //ignore
    }
  }

  public static void operateOnDependencies(ClassLoader loader, String classname, ClassPool pool, Consumer<String> consumer) throws Exception {
    operateOnDependencies(loader, classname, pool, Sets.newHashSet(), consumer);
  }

  public static Set<String> getDependencyClasses(ClassLoader loader, String classname, ClassPool pool) throws Exception {
    Set<String> result = Sets.newHashSet();
    operateOnDependencies(loader, classname, pool, result::add);
    return result;
  }

  public static Map<String, byte[]> getDependencyBytecode(ClassLoader loader, String classname, ClassPool pool, Set<String> packagesToExclude) throws Exception {
    Map<String, byte[]> result = Maps.newHashMap();
    operateOnDependencies(loader, classname, pool, s -> {
      try {
        if (packagesToExclude.stream().noneMatch(s::contains)) {
          result.put(s, StreamUtils.getBytes(loader.getResourceAsStream(classNameToInternalName(s))));
        }
      } catch (IOException | NullPointerException e) {
        //class is not in this jar - this is actually generally fine
      }
    });
    return result;
  }

  public static Class<?>[] getClasses(Object[] constructorArgs) {
    Class<?>[] constructorArgTypes = new Class[constructorArgs.length];
    for (int i = 0; i < constructorArgs.length; i++) {
      constructorArgTypes[i] = constructorArgs[i].getClass();
    }
    return constructorArgTypes;
  }

  @NotNull
  public static String classNameToInternalName(String s) {
    return s.replace('.', '/') + ".class";
  }

  public static void main(String[] args) throws Exception {

    String jsJar = "/Users/pwestling/dev/java_support/build/java_support.job.jar";
    URLClassLoader js = new URLClassLoader(new URL[]{new File(jsJar).toURI().toURL()}, null);

    String acJar = "/Users/pwestling/dev/audience_compiler/build/audience_compiler.job.jar";
    URLClassLoader ac = new URLClassLoader(new URL[]{new File(acJar).toURI().toURL()}, null);

    String fpJar = "/Users/pwestling/dev/field_preparation/field_preparation_executor/build/field_preparation_executor.job.jar";
    URLClassLoader fp = new URLClassLoader(new URL[]{new File(fpJar).toURI().toURL()}, null);

    Map<String, byte[]> deps = getDependencyBytecode(fp, "com.liveramp.field_preparation.workflow.PrepareFields", ClassPool.getDefault(),
        Sets.newHashSet(
            "generated",
            "com.rapleaf.types",
            "com.liveramp.types",
            "db_schemas",
            "cascading",
            "java",
            "sun",
            "com.rapleaf.formats"
        ));
    System.out.println(deps.size());


    System.out.println(deps.values().stream().map(b -> b.length).reduce(0, (x, y) -> x + y));

  }
}
