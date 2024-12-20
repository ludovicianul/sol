package io.ludovicianul.command;

import io.ludovicianul.db.SolDb;
import io.ludovicianul.io.ProcessRunner;
import io.ludovicianul.log.Logger;
import io.ludovicianul.model.Branch;
import io.ludovicianul.model.CommitRecord;
import io.ludovicianul.model.FileChange;
import io.ludovicianul.model.Tag;
import io.ludovicianul.service.FileTypeService;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Indexes the git repository and stores the data in the database. It collects commits, branches and
 * tags.
 */
public class IndexSubcommand implements Runnable {

  private final int timeout;
  private List<CommitRecord> commits;
  private Map<String, String> branchAndCreationDates;

  private final FileTypeService fileTypeService;

  public IndexSubcommand(int timeout) {
    this.timeout = timeout;
    this.fileTypeService = new FileTypeService();
  }

  @Override
  public void run() {
    initializeDatabase();
    List<String> directories =
        getDirectories().stream().filter(directory -> !directory.equals(".sol")).toList();
    for (String directory : directories) {
      if (isGitDirectory(directory)) {
        parseCommits(directory);
        parseBranches(directory);
        parseTags(directory);
      }
    }
  }

  private List<String> getDirectories() {
    String currentDirectory = ".";

    if (isGitDirectory(currentDirectory)) {
      return List.of(Path.of(currentDirectory).toFile().getName());
    }
    return Arrays.stream(
            Objects.requireNonNull(Path.of(currentDirectory).toFile().listFiles(File::isDirectory)))
        .map(File::getName)
        .collect(Collectors.toList());
  }

  private boolean isGitDirectory(String directory) {
    return new File(directory, ".git").exists();
  }

  private void initializeDatabase() {
    SolDb.initializeDatabase();
    Logger.print("Database initialized");
  }

  private void parseBranches(String directory) {
    branchAndCreationDates = getBranchAndCreationDates(directory);
    parseBranches(directory, "merged");
    parseBranches(directory, "no-merged");
  }

  private Map<String, String> getBranchAndCreationDates(String directory) {
    Logger.print("Getting branch creation dates...");
    return ProcessRunner.INSTANCE
        .getMultiLineProcessOut(
            timeout,
            "cd "
                + directory
                + " && git for-each-ref --format='%(refname:short),%(creatordate:iso-strict)' refs/heads/ refs/remotes/")
        .stream()
        .map(String::trim)
        .map(branch -> branch.replace("origin/", ""))
        .distinct()
        .map(branch -> branch.split(","))
        .filter(branch -> branch.length == 2)
        .collect(
            Collectors.toMap(
                branch -> branch[0].trim(),
                branch -> branch[1].trim(),
                (existing, replacement) -> existing));
  }

  private void parseTags(String directory) {
    Logger.print("Collecting tags...");

    List<String> tags =
        ProcessRunner.INSTANCE.getMultiLineProcessOut(
            timeout,
            "cd "
                + directory
                + " && git for-each-ref --format='%(refname:short),%(objectname),%(object),%(creatordate:iso-strict),%(contents)' refs/tags");

    tags.stream()
        .map(tag -> tag.split(","))
        .filter(tag -> tag.length == 5)
        .map(
            tag ->
                new Tag(
                    directory,
                    tag[0].trim(),
                    tag[2].trim().isEmpty() ? tag[1].trim() : tag[2].trim(),
                    tag[3].trim(),
                    tag[4].trim()))
        .forEach(SolDb::insertTag);

    Logger.print("Finished collecting tags");
  }

  private void parseBranches(String directory, String merged) {
    Logger.print("Collecting " + merged + " branches...");
    boolean isMerged = "merged".equals(merged);

    List<String> branches =
        ProcessRunner.INSTANCE.getMultiLineProcessOut(
            timeout, "cd %s && git branch -a --%s".formatted(directory, merged));
    Set<String> mergedBranches =
        branches.stream()
            .map(String::trim)
            .filter(Branch::isNotMaster)
            .map(Branch::removeRemoteOriginPrefix)
            .collect(Collectors.toSet());
    mergedBranches.stream()
        .map(branch -> createBranch(directory, branch, isMerged))
        .forEach(SolDb::insertBranch);

    Logger.print("Finished collecting " + merged + " branches");
  }

  private Branch createBranch(String directory, String branch, boolean isMerged) {
    String creationDate = branchAndCreationDates.get(branch);
    String mergeDate = isMerged ? getMergeDate(branch) : null;
    int active = isMerged ? 0 : 1;

    return new Branch(directory, branch, active, creationDate, mergeDate);
  }

  private String getMergeDate(String branch) {
    return commits.stream()
        .filter(commit -> commit.messageContains(branch))
        .findFirst()
        .map(CommitRecord::date)
        .orElse(null);
  }

  private void parseCommits(String directory) {
    Logger.print("Collecting commits data...");

    List<String> gitLog =
        ProcessRunner.INSTANCE.getMultiLineProcessOut(
            timeout,
            "cd "
                + directory
                + " && git log --all --encoding=UTF-8 --numstat --raw --format=\"commit:%H%nauthor:%an%ndate:%cI %nparents:%P%nmessage:%n%s%n%b%nnumstat:\"");

    commits = parseCommits(directory, gitLog);

    SolDb.insertCommits(commits);
    Logger.print("Commits indexed successfully");
  }

  public List<CommitRecord> parseCommits(String directory, List<String> lines) {
    List<CommitRecord> commits = new ArrayList<>();
    String commitHash = null;
    String author = null;
    String date = null;
    StringBuilder message = new StringBuilder();
    List<FileChange> fileChanges = new ArrayList<>();
    List<String> parents = new ArrayList<>();

    for (String line : lines) {
      if (line.startsWith("commit:")) {
        if (commitHash != null) {
          commits.add(
              new CommitRecord(
                  directory,
                  commitHash,
                  author,
                  date,
                  message.toString().trim(),
                  new ArrayList<>(fileChanges),
                  new ArrayList<>(parents)));
          fileChanges.clear();
          parents.clear();
        }
        commitHash = line.substring(7).trim();
        message.setLength(0);
      } else if (line.startsWith("author:")) {
        author = line.substring(7).trim();
      } else if (line.startsWith("date:")) {
        date = line.substring(5).trim();
      } else if (line.startsWith("message:")) {
        message.setLength(0);
      } else if (line.trim().isEmpty() || line.startsWith("numstat:")) {
        // do nothing
      } else if (line.startsWith("parents:")) {
        String[] parts = line.substring(8).trim().split("\\s+");
        parents.addAll(Arrays.asList(parts));
      } else if (line.matches("^:\\d{6} \\d{6}.*")) {
        String[] parts = line.split("\\s+");
        String changeType = parts[4];
        String filePath = parts[5];
        fileChanges.add(
            new FileChange(
                directory,
                changeType,
                filePath,
                0,
                0,
                fileTypeService.isTestFile(filePath),
                fileTypeService.isBuildFile(filePath),
                fileTypeService.isDotFile(filePath),
                fileTypeService.isDocumentationFile(filePath)));
      } else if (line.matches("\\d+\\s+\\d+\\s+.*")) {
        String[] parts = line.split("\\s+");
        int additions = Integer.parseInt(parts[0]);
        int deletions = Integer.parseInt(parts[1]);
        String filePath = parts[2];

        for (FileChange fileChange : fileChanges) {
          if (fileChange.filePath().equals(filePath)) {
            fileChanges.set(
                fileChanges.indexOf(fileChange),
                new FileChange(
                    directory,
                    fileChange.changeType(),
                    filePath,
                    additions,
                    deletions,
                    fileChange.isTestFile(),
                    fileChange.isBuildFile(),
                    fileChange.isDotFile(),
                    fileChange.isDocumentationFile()));
            break;
          }
        }
      } else {
        message.append(line).append("\n");
      }
    }
    if (commitHash != null) {
      commits.add(
          new CommitRecord(
              directory,
              commitHash,
              author,
              date,
              message.toString().trim(),
              fileChanges,
              parents));
    }

    return commits;
  }
}
