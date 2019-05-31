package com.liveramp.remote_code;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javassist.ClassPool;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.apache.commons.lang3.SerializationUtils;

public class RemoteCodeObject<T extends Serializable> implements Serializable {

  private final Map<String, byte[]> classDefinitions;
  private final byte[] serializedObject;
  private transient T obj;

  public RemoteCodeObject(Map<String, byte[]> classDefinitions, byte[] serializedObject) {
    this.classDefinitions = classDefinitions;
    this.serializedObject = serializedObject;
  }

  private void deserialize() throws IOException {
    if (obj == null) {
      ForeignBytecodeClassloader loader =
          new ForeignBytecodeClassloader(getLocalLoader(), classDefinitions);
      loader = loader.minimize();

      ByteArrayInputStream bais = new ByteArrayInputStream(serializedObject);
      AlternateLoaderObjectInputStream ois = new AlternateLoaderObjectInputStream(bais, loader);
      try {
        obj = (T)ois.readObject();
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public T toProxy(Class<? super T> tClass) throws ObjectStreamException {
    Class parent = tClass.isInterface() ? Object.class : tClass;
    Class[] interfaces = tClass.isInterface() ? new Class[]{tClass, Serializable.class} : new Class[]{Serializable.class};
    return (T)Enhancer.create(parent, interfaces, new RCOInterceptor(this));
  }

  public T getObj() {
    return obj;
  }

  private static class RCOInterceptor implements MethodInterceptor, Serializable {

    private final RemoteCodeObject rco;

    RCOInterceptor(RemoteCodeObject rco) {
      this.rco = rco;
    }

    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
      if (rco.getObj() == null) {
        rco.deserialize();
      }
      return methodProxy.invoke(rco.getObj(), objects);
    }
  }


  private static ClassLoader getLocalLoader() {
    return Thread.currentThread().getContextClassLoader();
  }

  public static <T extends Serializable> Builder<T> builder(T obj) {
    return new Builder<>(obj);
  }

  public static <T extends Serializable> RemoteCodeObject<T> toRemoteCodeObject(T obj) throws Exception {
    return builder(obj).build();
  }

  public static <T extends Serializable> RemoteCodeObject<T> toRemoteCodeObject(T obj, ClassLoader loader, List<Pattern> exclusions) throws Exception {

    String className = obj.getClass().getCanonicalName();
    Map<String, byte[]> dependencyBytecode = ForeignClassUtils.getDependencyBytecode(
        loader,
        className,
        ClassPool.getDefault(),
        exclusions);

    byte[] serializedObj = SerializationUtils.serialize(obj);

    return new RemoteCodeObject<>(dependencyBytecode, serializedObj);
  }

  public static class Builder<T extends Serializable> {
    List<Pattern> exclusions = new ArrayList<>();
    ClassLoader classLoader = getLocalLoader();
    T obj;

    public Builder(T object) {
      // default exclusions - use
      // toRemoteCodeObject(T obj, ClassLoader loader, List<Pattern> exclusions) for full control
      exclusions.add(Pattern.compile("sun.*"));
      exclusions.add(Pattern.compile("java.*"));
      obj = object;
    }

    public Builder<T> addExclusion(String regex) {
      exclusions.add(Pattern.compile(regex));
      return this;
    }

    public Builder<T> addExclusion(Pattern regex) {
      exclusions.add(regex);
      return this;
    }

    public Builder<T> setClassLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    public RemoteCodeObject<T> build() throws Exception {
      return RemoteCodeObject.toRemoteCodeObject(obj, classLoader, exclusions);
    }
  }
}
