package com.liveramp.remote_code;

import java.io.Serializable;

public interface ForeignClassFactory<T> extends Serializable {
  T createUsingStoredArgs();

  T createNewObject(Object... args);
}
