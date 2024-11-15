package io.ludovicianul.model;

import java.util.List;

public record CommitRecord(
    String commitHash,
    String author,
    String date,
    String message,
    List<FileChange> fileChanges,
    List<String> parents) {}
