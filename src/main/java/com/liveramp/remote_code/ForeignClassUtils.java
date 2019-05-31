package com.liveramp.remote_code;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javassist.ClassPool;
import javassist.CtClass;
import org.apache.commons.io.IOUtils;

public class ForeignClassUtils {


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
    operateOnDependencies(loader, classname, pool, new HashSet<>(), consumer);
  }

  public static Map<String, byte[]> getDependencyBytecode(ClassLoader loader, String classname, ClassPool pool, List<Pattern> packagesToExclude) throws Exception {
    Map<String, byte[]> result = new HashMap<>();
    operateOnDependencies(loader, classname, pool, s -> {
      try {
        if (packagesToExclude.stream().noneMatch(pattern -> pattern.asPredicate().test(s))) {
          result.put(s, IOUtils.toByteArray(loader.getResourceAsStream(classNameToInternalName(s))));
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

  public static String classNameToInternalName(String s) {
    return s.replace('.', '/') + ".class";
  }

}
