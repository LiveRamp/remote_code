package com.liveramp.remote_code;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;


class ForeignBytecodeClassloader extends ClassLoader {

  private ClassLoader parent;
  private final Map<String, byte[]> classDefinitions;

  public ForeignBytecodeClassloader(
      ClassLoader parent,
      Map<String, byte[]> predefinedClasses) {
    super(parent);
    this.parent = parent;
    this.classDefinitions = predefinedClasses;
  }

  @Override
  public Class<?> loadClass(String s) throws ClassNotFoundException {
    if (classDefinitions.containsKey(s)) {
      byte[] bytes = classDefinitions.get(s);
      return this.defineClass(s, bytes, 0, bytes.length);
    } else {
      return loadFromParent(s);
    }
  }

  private Class loadFromParent(String s) throws ClassNotFoundException {
    return this.loadClass(s, true);
  }

  public Map<String, byte[]> getClassDefinitions() {
    return classDefinitions;
  }

  public ForeignBytecodeClassloader minimize() {

    Pattern pattern = Pattern.compile("\\$.*");
    Function<Map.Entry<String, byte[]>, String> toOuter = e -> pattern.matcher(e.getKey()).replaceAll("");

    //First find every class that we need to load from foreign
    //then store the _outer_ class name of that class
    Set<String> foreignOuterClasses = classDefinitions.entrySet().stream()
        .filter(e -> {
          try {
            String classname = e.getKey();
            String internalName = ForeignClassUtils.classNameToInternalName(classname);
            InputStream resourceAsStream = parent.getResourceAsStream(internalName);
            return resourceAsStream == null ||
                !Arrays.equals(IOUtils.toByteArray(resourceAsStream), e.getValue());

          } catch (IOException e1) {
            throw new RuntimeException(e1);
          }
        }).map(toOuter)
        .collect(Collectors.toSet());


    //Use foreign code for any class whose outer class was stored above (i.e. it, it's outer,
    // or a sibling class needs foreign code)
    Map<String, byte[]> classesWithDifferentBytecode = classDefinitions.entrySet().stream()
        .filter(e -> foreignOuterClasses.contains(toOuter.apply(e)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    return new ForeignBytecodeClassloader(parent, classesWithDifferentBytecode);
  }
}
