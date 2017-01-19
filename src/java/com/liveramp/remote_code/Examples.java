package com.liveramp.remote_code;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

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

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    oos.writeObject(factory);

    oos.close();

    byte[] bytes = baos.toByteArray();
    System.out.println(bytes.length);

    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

    ObjectInputStream ois = new ObjectInputStream(bais);

    Object o = ois.readObject();

    Fn<OnlineImportRecord, AnonymousRecord> f = ((ForeignClassFactory<Fn<OnlineImportRecord, AnonymousRecord>>)o).createUsingStoredArgs();
    AnonymousRecord result2 = f.apply(record);

    System.out.println(result2);
  }

}
