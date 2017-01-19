package com.liveramp.remote_code;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.log4j.lf5.util.StreamUtils;

class SelectiveHandoffClassLoader extends ClassLoader {

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
        String internalName = ForeignClassUtils.classNameToInternalName(s);

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
