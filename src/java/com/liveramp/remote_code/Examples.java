package com.liveramp.remote_code;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLClassLoader;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.liveramp.importer.generated.ImportRecordID;
import com.liveramp.importer.generated.OnlineImportRecord;
import com.liveramp.java_support.functional.Fn;
import com.liveramp.types.anonymous_records.AnonymousRecord;

public class Examples {

  public static void example1() throws Exception {

    ForeignClassFactory<Fn<OnlineImportRecord, AnonymousRecord>> factory = ForeignClassUtils.loadWithRedefs(
        "com.liveramp.audience_compiler.converters.OnlineImportRecordToAnonymousRecord",
        (Class<Fn<OnlineImportRecord, AnonymousRecord>>)(Class)Fn.class,
        "/Users/pwestling/dev/audience_compiler/build/audience_compiler.job.jar",
        123L);

    Fn<OnlineImportRecord, AnonymousRecord> fn = factory.createUsingStoredArgs();

    OnlineImportRecord record = new OnlineImportRecord(
        new ImportRecordID(1L, 1, 1L),
        Maps.newHashMap(),
        Lists.newArrayList(),
        Lists.newArrayList());
    AnonymousRecord result = fn.apply(record);

    System.out.println(result);

    Object o = passThroughSerialization(factory);

    Fn<OnlineImportRecord, AnonymousRecord> f = ((ForeignClassFactory<Fn<OnlineImportRecord, AnonymousRecord>>)o).createUsingStoredArgs();
    AnonymousRecord result2 = f.apply(record);

    System.out.println(result2);
  }

  public static void example2(String[] args) throws Exception {

    String acJar = "/Users/pwestling/dev/audience_compiler/build/audience_compiler.job.jar";
    URLClassLoader ac = new URLClassLoader(new URL[]{new File(acJar).toURI().toURL()}, null);
    String cn = "com.liveramp.audience_compiler.converters.OnlineImportRecordToAnonymousRecord";

    RemoteCodeObject<Fn<OnlineImportRecord, AnonymousRecord>> rco =
        RemoteCodeObject.toRemoteCodeObject(cn, ac, 123L);

    Fn<OnlineImportRecord, AnonymousRecord> fn = rco.deserialize();

    OnlineImportRecord record = new OnlineImportRecord(
        new ImportRecordID(1L, 1, 1L),
        Maps.newHashMap(),
        Lists.newArrayList(),
        Lists.newArrayList());
    AnonymousRecord result = fn.apply(record);

    System.out.println(result);

    Fn<OnlineImportRecord, AnonymousRecord> proxy = rco.toProxy(Fn.class);
    result = proxy.apply(record);
    System.out.println(result);

    proxy = Examples.passThroughSerialization(proxy);
    result = proxy.apply(record);
    System.out.println(result);

    try {
      fn = Examples.passThroughSerialization(fn);

      throw new RuntimeException("Something magical happened and this worked when it shouldn't have");
    } catch (ClassNotFoundException e) {
      //doesnt work because bytecode isnt also passed
    }

  }

  public static <T> T passThroughSerialization(T obj) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    oos.writeObject(obj);

    oos.close();

    byte[] bytes = baos.toByteArray();
    System.out.println(bytes.length);

    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

    ObjectInputStream ois = new ObjectInputStream(bais);

    return (T)ois.readObject();
  }

}
