package com.liveramp.remote_code;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;

public class AlternateLoaderObjectInputStream extends ObjectInputStream {
  private ClassLoader loader;

  public AlternateLoaderObjectInputStream(InputStream out, ClassLoader loader) throws IOException {
    super(out);
    this.loader = loader;
  }

  @Override
  protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
    return Class.forName(desc.getName(), true, loader);
  }
}
