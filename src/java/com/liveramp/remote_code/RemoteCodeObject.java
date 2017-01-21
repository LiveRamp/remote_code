package com.liveramp.remote_code;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;

import javassist.ClassPool;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

public class RemoteCodeObject<T extends Serializable> implements Serializable {

  private final Map<String, byte[]> classDefinitions;
  private final String classname;
  private final byte[] serializedObject;
  private transient T obj;

  public RemoteCodeObject(Map<String, byte[]> classDefinitions, String classname, byte[] serializedObject) {
    this.classDefinitions = classDefinitions;
    this.classname = classname;
    this.serializedObject = serializedObject;
  }


  public T deserialize() throws IOException {
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
    return obj;
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

    public RCOInterceptor(RemoteCodeObject rco) {
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

  public static <T extends Serializable> RemoteCodeObject<T> toRemoteCodeObject(T obj) throws Exception {
    return toRemoteCodeObject(obj, getLocalLoader());
  }

  public static <T extends Serializable> RemoteCodeObject<T> toRemoteCodeObject(String classname, ClassLoader loader, Object... args) throws Exception {
    T obj = (T)loader.loadClass(classname)
        .getConstructor(ForeignClassUtils.getClasses(args)).newInstance(args);
    return toRemoteCodeObject(obj, loader);
  }

  public static <T extends Serializable> RemoteCodeObject<T> toRemoteCodeObject(T obj, ClassLoader loader) throws Exception {

    String className = obj.getClass().getCanonicalName();
    Map<String, byte[]> dependencyBytecode = ForeignClassUtils.getDependencyBytecode(
        loader,
        className,
        ClassPool.getDefault(),
        LRRemoteCodeConstants.DEFAULT_EXCLUSIONS);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(obj);
    oos.close();
    byte[] serializedObj = baos.toByteArray();

    return new RemoteCodeObject<T>(dependencyBytecode, className, serializedObj);
  }
}
