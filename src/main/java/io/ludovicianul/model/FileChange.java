package io.ludovicianul.model;

public record FileChange(
    String repoName,
    String changeType,
    String filePath,
    int additions,
    int deletions,
    boolean isTestFile,
    boolean isBuildFile,
    boolean isDotFile,
    boolean isDocumentationFile) {

  public boolean isAddOrModify() {
    return changeType.equals("A") || changeType.equals("M");
  }
}
