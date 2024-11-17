package io.ludovicianul.model;

public record FileChange(
    String changeType,
    String filePath,
    int additions,
    int deletions,
    boolean isTestFile,
    boolean isBuildFile,
    boolean isDotFile,
    boolean isDocumentationFile) {}
