package io.ludovicianul.model;

/** Represents a tag in the git repository. */
public record Tag(String repoName, String name, String commitId, String date, String message) {}
