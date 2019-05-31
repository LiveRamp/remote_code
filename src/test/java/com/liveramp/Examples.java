package com.liveramp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.function.Function;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.Assert;

import com.liveramp.remote_code.RemoteCodeObject;

public class Examples extends TestCase {
  public Examples() {
    super("examples");
  }

  /**
   * @return the suite of tests being tested
   */
  public static Test suite() {
    return new TestSuite(Examples.class);
  }


  interface MyIntegerFunction extends Function<Integer, Integer>, Serializable {

  }

  public void testRemoteCodeObject() throws Exception {

    MyIntegerFunction codeToTransmit = (input) -> input * 2;
    RemoteCodeObject<MyIntegerFunction> remoteCodeObject = RemoteCodeObject.toRemoteCodeObject(codeToTransmit);

    byte[] serialized = SerializationUtils.serialize(remoteCodeObject);

    RemoteCodeObject<MyIntegerFunction> serverSideRCO = SerializationUtils.deserialize(serialized);
    MyIntegerFunction codeWeTransmitted = serverSideRCO.toProxy(MyIntegerFunction.class);

    Assert.assertEquals(Integer.valueOf(4), codeWeTransmitted.apply(2));
  }

  public void testRemoteCodeObjectWithExclusions() throws Exception {

    MyIntegerFunction codeToTransmit = (input) -> input * 2;
    RemoteCodeObject<MyIntegerFunction> remoteCodeObject = RemoteCodeObject.builder(codeToTransmit)
        .addExclusion("com.liveramp.universally.shared.*")
        .build();

    byte[] serialized = SerializationUtils.serialize(remoteCodeObject);

    RemoteCodeObject<MyIntegerFunction> serverSideRCO = SerializationUtils.deserialize(serialized);
    MyIntegerFunction codeWeTransmitted = serverSideRCO.toProxy(MyIntegerFunction.class);

    Assert.assertEquals(Integer.valueOf(4), codeWeTransmitted.apply(2));
  }

}
