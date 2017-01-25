package com.liveramp.remote_code.artifactory_loader;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.liveramp.commons.Accessors;

public class ArtifactoryUtil {

  public static void main(String[] args) throws IOException {
    System.out.println(getLatestSnapshotJobjar("com.liveramp", "pipeline_composer", "1.0-SNAPSHOT"));;
  }

  private static final String ARTIFACTORY_URL = "http://library.liveramp.net/artifactory/libs-snapshot-local";
  private static final Pattern SNAPSHOT_ARTIFACT_PATTERN = Pattern.compile("[a-z_A-Z0-9]+-[0-9.]+-([0-9.]+)-[0-9]+.job.jar");

  public static URL getLatestSnapshotJobjar(String org, String artifact, String version) throws IOException {

    String pathString = org.replaceAll("\\.", "/");
    String rootDir = ARTIFACTORY_URL + "/" + pathString + "/" + artifact + "/" + version;

    List<String> versions = Lists.newArrayList();
    for (Element file : Jsoup.connect(rootDir).get().select("pre a")) {
      Matcher snapshotMatch = SNAPSHOT_ARTIFACT_PATTERN.matcher(file.attr("href"));
      if(snapshotMatch.matches()){
        versions.add(snapshotMatch.group(1));
      }
    }

    Collections.sort(versions);

    return new URL(rootDir+"/"+Accessors.last(versions)+".job.jar");
  }

}
