package com.liveramp.remote_code;

import java.util.Set;

import com.google.common.collect.Sets;

public class LRRemoteCodeConstants {

  public static Set<String> DEFAULT_EXCLUSIONS = Sets.newHashSet(
      "generated",
      "com.rapleaf.types",
      "com.liveramp.types",
      "db_schemas",
      "cascading",
      "java",
      "sun",
      "com.rapleaf.formats"
  );

}
