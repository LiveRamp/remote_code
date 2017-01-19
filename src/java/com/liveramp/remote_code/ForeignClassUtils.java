package com.liveramp.remote_code;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.commons.lang3.tuple.Pair;
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

  private static void getDependencyClassNames(ClassLoader loader, String classname, ClassPool pool, Set<String> foundClasses) throws Exception {
    foundClasses.add(classname);
    String internal = classNameToInternalName(classname);
    InputStream resourceAsStream = loader.getResourceAsStream(internal);
    try {
      CtClass ctClass = pool.makeClass(resourceAsStream);
      Collection<String> refClasses = ctClass.getRefClasses();
      for (String refClass : refClasses) {
        if (!foundClasses.contains(refClass)) {
          getDependencyClassNames(loader, refClass, pool, foundClasses);
        }
      }
    } catch (IOException e) {
      System.out.println("Can't load" + classname);
    }
  }

  private static Set<String> getDependencyClassNames(ClassLoader loader, String classname, ClassPool pool) throws Exception {
    Set<String> result = Sets.newHashSet();
    getDependencyClassNames(loader, classname, pool, result);
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

    Set<String> deps = getDependencyClassNames(fp, "com.liveramp.field_preparation.workflow.PrepareFields", ClassPool.getDefault());
    System.out.println(deps);
    System.out.println(deps.size());
    System.out.println();

    List<Pair<Integer, String>> orderdDeps = deps.stream()
        .filter(n -> !n.contains("generated"))
        .filter(n -> !n.contains("com.rapleaf.types"))
        .filter(n -> !n.contains("com.liveramp.types"))
        .filter(n -> !n.contains("db_schemas"))
        .filter(n -> !n.contains("cascading_ext"))
        .filter(n -> !n.contains("cascading_tools"))
        .filter(n -> !n.contains("hadoop"))
        .filter(n -> !n.contains("java"))
        .filter(n -> !n.contains("sun"))
        .filter(n -> !n.contains("cascading"))
        .filter(n -> !n.contains("com.rapleaf.formats"))
        .map(s -> {
          try {
            return Pair.of(StreamUtils.getBytes(ac.getResourceAsStream(classNameToInternalName(s))).length, s);
          } catch (Exception e) {
            return Pair.of(0, s);
          }

        }).sorted((o1, o2) -> o2.getKey().compareTo(o1.getKey()))
        .collect(Collectors.toList());


    System.out.println(orderdDeps);
    System.out.println(orderdDeps.stream().map(Pair::getKey).reduce(0, (x, y) -> x + y));

  }
}
