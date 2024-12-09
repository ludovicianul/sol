package io.ludovicianul.model;

/** Represents a branch in the git repository. I might extend it to consider abandoned branches. */
public record Branch(
    String repoName, String name, int active, String creationDate, String mergeDate) {

  public static boolean isNotMaster(String branch) {
    return !branch.toLowerCase().endsWith("main") && !branch.toLowerCase().endsWith("master");
  }

  public static String removeRemoteOriginPrefix(String branch) {
    return branch.replace("remotes/", "").replace("origin/", "");
  }
}
