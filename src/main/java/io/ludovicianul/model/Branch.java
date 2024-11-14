package io.ludovicianul.model;

/** Represents a branch in the git repository. I might extend it to consider abandoned branches. */
public record Branch(String branchDate, int active, String creationDate, String mergeDate) {}
