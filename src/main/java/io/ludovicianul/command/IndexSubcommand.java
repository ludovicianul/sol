package io.ludovicianul.command;

import io.ludovicianul.db.SolDb;
import io.ludovicianul.io.ProcessRunner;
import io.ludovicianul.log.Logger;
import io.ludovicianul.model.Branch;
import io.ludovicianul.model.CommitRecord;
import io.ludovicianul.model.FileChange;
import io.ludovicianul.model.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Indexes the git repository and stores the data in the database. It collects commits, branches and
 * tags.
 */
public class IndexSubcommand implements Runnable {

  private final int timeout;
  private String mainBranch;

  public IndexSubcommand(int timeout) {
    this.timeout = timeout;
  }

  @Override
  public void run() {
    mainBranch = getMainBranch();
    initializeDatabase();
    parseCommits();
    parseBranches();
    parseTags();
  }

  private void initializeDatabase() {
    SolDb.initializeDatabase();
    Logger.print("Database initialized");
  }

  private String getMainBranch() {
    String branch =
        ProcessRunner.INSTANCE.getSingleLineProcessOut(timeout, "git branch --show-current");

    return Optional.ofNullable(branch).orElse("main");
  }

  private void parseBranches() {
    parseBranches("merged");
    parseBranches("no-merged");
  }

  private void parseTags() {
    Logger.print("Collecting tags...");

    List<String> tags =
        ProcessRunner.INSTANCE.getMultiLineProcessOut(
            timeout,
            "git for-each-ref --format='%(refname:short),%(objectname),%(creatordate:iso-strict),%(contents)' refs/tags");

    tags.stream()
        .map(tag -> tag.split(","))
        .filter(tag -> tag.length == 4)
        .map(tag -> new Tag(tag[0].trim(), tag[1].trim(), tag[2].trim(), tag[3].trim()))
        .forEach(SolDb::insertTag);

    Logger.print("Finished collecting tags");
  }

  private void parseBranches(String merged) {
    Logger.print("Collecting " + merged + " branches...");

    List<String> branches =
        ProcessRunner.INSTANCE.getMultiLineProcessOut(
            timeout, "git branch -a --%s".formatted(merged));
    Set<String> mergedBranches =
        branches.stream()
            .map(String::trim)
            .filter(branch -> !branch.toLowerCase().endsWith("main"))
            .filter(branch -> !branch.toLowerCase().endsWith("master"))
            .collect(Collectors.toSet());

    mergedBranches.stream()
        .map(
            branch -> {
              String creationDate = getCreationDate(branch);
              String mergeDate = getMergeDate(branch);
              int active = "merged".equals(merged) ? 0 : 1;

              return new Branch(branch, active, creationDate, mergeDate);
            })
        .forEach(SolDb::insertBranch);

    Logger.print("Finished collecting " + merged + " branches");
  }

  private String getMergeDate(String branch) {
    String toSearch = branch.replace("remotes/", "").replace("origin/", "");

    return ProcessRunner.INSTANCE
        .getMultiLineProcessOut(
            timeout, "git log --merges --format=\"%cI %s\" --grep \"" + toSearch + "\"")
        .stream()
        .map(date -> date.split(" ")[0])
        .map(String::trim)
        .findFirst()
        .orElse(null);
  }

  private String getCreationDate(String branch) {
    String branchToGrep = branch.replace("remotes/", "").replace("origin/", "");
    String commandToRun =
        "{ git log --all --grep='%s' --pretty=format:%%cI --reverse | head -n1; git show -s --format=%%cI $(git merge-base %s %s); } | sort | head -n1"
            .formatted(branchToGrep, mainBranch, branch);

    return ProcessRunner.INSTANCE.getSingleLineProcessOut(timeout, commandToRun);
  }

  private void parseCommits() {
    Logger.print("Collecting commits data...");

    List<String> gitLog =
        ProcessRunner.INSTANCE.getMultiLineProcessOut(
            timeout,
            "git log --encoding=UTF-8 --numstat --raw --format=\"commit:%H%nauthor:%an%ndate:%cI %nparents:%P%nmessage:%n%s%n%b%nnumstat:\"");

    List<CommitRecord> commits = parseCommits(gitLog);

    commits.forEach(SolDb::insertCommit);
    Logger.print("Commits indexed successfully");
  }

  public static List<CommitRecord> parseCommits(List<String> lines) {
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
        fileChanges.add(new FileChange(changeType, filePath, 0, 0));
      } else if (line.matches("\\d+\\s+\\d+\\s+.*")) {
        String[] parts = line.split("\\s+");
        int additions = Integer.parseInt(parts[0]);
        int deletions = Integer.parseInt(parts[1]);
        String filePath = parts[2];

        for (FileChange fileChange : fileChanges) {
          if (fileChange.filePath().equals(filePath)) {
            fileChanges.set(
                fileChanges.indexOf(fileChange),
                new FileChange(fileChange.changeType(), filePath, additions, deletions));
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
              commitHash, author, date, message.toString().trim(), fileChanges, parents));
    }

    return commits;
  }
}
