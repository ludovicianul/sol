package io.ludovicianul.db;

import io.ludovicianul.model.Branch;
import io.ludovicianul.model.CommitRecord;
import io.ludovicianul.model.FileChange;
import io.ludovicianul.model.Tag;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Singleton;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Interacts with the sqlite db. */
@Singleton
@Unremovable
public class SolDb {
  private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
  private static final String DB_URL = "jdbc:sqlite:.sol/commits.db";

  public static void initializeDatabase() {
    createSolIfNoExists();
    removeSolDbIfExists();

    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      String createCommitsTable =
          """
                    CREATE TABLE IF NOT EXISTS commits (
                        commit_id TEXT PRIMARY KEY,
                        author TEXT,
                        date TEXT,
                        timezone TEXT,
                        is_merge INTEGER,
                        total_additions INTEGER DEFAULT 0,
                        total_deletions INTEGER DEFAULT 0,
                        total_additions_test INTEGER DEFAULT 0,
                        total_deletions_test INTEGER DEFAULT 0,
                        total_additions_dot INTEGER DEFAULT 0,
                        total_deletions_dot INTEGER DEFAULT 0,
                        total_additions_build INTEGER DEFAULT 0,
                        total_deletions_build INTEGER DEFAULT 0,
                        message TEXT
                    );
                    """;

      String createFileChangesTable =
          """
                    CREATE TABLE IF NOT EXISTS file_changes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        commit_hash TEXT,
                        author TEXT,
                        change_type TEXT,
                        file_path TEXT,
                        additions INTEGER,
                        deletions INTEGER,
                        is_test_file INTEGER,
                        is_build_file INTEGER,
                        is_dot_file INTEGER,
                        is_documentation_file INTEGER,
                        FOREIGN KEY(commit_hash) REFERENCES commits(commit_id)
                    );
                    """;

      String commitParentsTable =
          """
                    CREATE TABLE IF NOT EXISTS commit_parents (
                        commit_id TEXT,
                        parent_id TEXT,
                        FOREIGN KEY(commit_id) REFERENCES commits(commit_id),
                        FOREIGN KEY(parent_id) REFERENCES commits(commit_id)
                    );
                    """;

      String branchesTable =
          """
              CREATE TABLE IF NOT EXISTS branches (
                  branch_name TEXT PRIMARY KEY,
                  is_active INTEGER,
                  creation_date TEXT,
                  merge_date TEXT
              );
              """;

      String tagsTable =
          """
                        CREATE TABLE IF NOT EXISTS tags (
                            tag_name TEXT PRIMARY KEY,
                            tag_commit TEXT,
                            tag_message TEXT,
                            FOREIGN KEY(tag_commit) REFERENCES commits(commit_id)
                          );
              """;

      List<String> indexes =
          List.of(
              "CREATE INDEX idx_commits_author ON commits(author);",
              "CREATE INDEX idx_commits_date ON commits(date);",
              "CREATE INDEX idx_commits_author_date ON commits(author, date);",
              "CREATE INDEX idx_commit_date_merge on commits(date, is_merge);",
              "CREATE INDEX idx_commit_merge_date on commits(is_merge, date);",
              "CREATE INDEX idx_commit_date_id on commits(date, commit_id);",
              "CREATE INDEX idx_file_changes_file_path ON file_changes(file_path);",
              "CREATE INDEX idx_file_changes_commit_file ON file_changes(commit_hash, file_path);",
              "CREATE INDEX idx_file_changes_group_order ON file_changes(file_path, commit_hash);",
              "CREATE INDEX idx_file_changes_commit_hash ON file_changes(commit_hash);",
              "CREATE INDEX idx_is_test_file ON file_changes(is_test_file);",
              "CREATE INDEX idx_is_build_file ON file_changes(is_build_file);",
              "CREATE INDEX idx_is_dot_file ON file_changes(is_dot_file);",
              "CREATE INDEX idx_tag_name on tags(tag_name);",
              "CREATE INDEX idx_is_documentation_file ON file_changes(is_documentation_file);",
              "CREATE INDEX idx_file_changes_performance ON file_changes(commit_hash, file_path, is_test_file, is_build_file, is_dot_file, is_documentation_file);",
              "CREATE INDEX idx_file_changes_hash_add_del ON file_changes(commit_hash, additions, deletions);",
              "CREATE INDEX idx_commit_parents_commit_id ON commit_parents(commit_id);",
              "CREATE INDEX idx_commit_parents_parent_id ON commit_parents(parent_id);");

      try (Statement stmt = conn.createStatement()) {
        stmt.execute(createCommitsTable);
        stmt.execute(createFileChangesTable);
        stmt.execute(commitParentsTable);
        stmt.execute(branchesTable);
        stmt.execute(tagsTable);

        indexes.forEach(
            sql -> {
              try {
                stmt.execute(sql);
              } catch (SQLException e) {
                System.err.println("Error while creating indexes: " + e.getMessage());
              }
            });
      }
    } catch (SQLException e) {
      System.err.println("There was an issue creating commits.db: " + e.getMessage());
    }
  }

  private static void removeSolDbIfExists() {
    File file = new File(".sol/commits.db");
    if (file.exists()) {
      file.delete();
    }
  }

  private static void createSolIfNoExists() {
    File file = new File(".sol");
    if (!file.exists()) {
      file.mkdir();
    }
  }

  public static void insertTag(Tag tag) {
    String insertTagSQL = "INSERT INTO tags (tag_name, tag_commit, tag_message) VALUES (?, ?, ?)";

    try (Connection conn = DriverManager.getConnection(DB_URL);
        PreparedStatement tagStmt = conn.prepareStatement(insertTagSQL)) {

      tagStmt.setString(1, tag.name());
      tagStmt.setString(2, tag.commitId());
      tagStmt.setString(3, tag.message());
      tagStmt.executeUpdate();
    } catch (SQLException e) {
      System.err.println("There was an issue inserting tags: " + e.getMessage());
    }
  }

  public static void insertBranch(Branch branch) {
    String insertBranchSQL =
        "INSERT INTO branches (branch_name, is_active, creation_date, merge_date) VALUES (?, ?, ?, ?)";

    try (Connection conn = DriverManager.getConnection(DB_URL);
        PreparedStatement branchStmt = conn.prepareStatement(insertBranchSQL)) {

      branchStmt.setString(1, branch.name());
      branchStmt.setInt(2, branch.active());
      branchStmt.setString(3, convertDateToUtc(branch.creationDate()));
      branchStmt.setString(4, convertDateToUtc(branch.mergeDate()));
      branchStmt.executeUpdate();
    } catch (SQLException e) {
      System.err.println("There was an issue inserting branches: " + e.getMessage());
    }
  }

  /**
   * Inserts a commit record into the database.
   *
   * @param commits the list of commit records to insert
   */
  public static void insertCommits(List<CommitRecord> commits) {
    String insertCommitSQL =
        "INSERT INTO commits (commit_id, author, date, timezone, is_merge, total_additions, "
            + "total_deletions, message, total_additions_test, total_deletions_test, "
            + "total_additions_build, total_deletions_build, "
            + "total_additions_dot, total_deletions_dot) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    String insertFileChangeSQL =
        "INSERT INTO file_changes (commit_hash, author, change_type, file_path, additions, deletions, is_test_file, is_build_file, is_dot_file, is_documentation_file) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    String insertIntoCommitParentsSQL =
        "INSERT INTO commit_parents (commit_id, parent_id) VALUES (?, ?)";

    final int BATCH_SIZE = 1000;
    int batchCount = 0;

    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      conn.setAutoCommit(false);

      try (PreparedStatement commitStmt = conn.prepareStatement(insertCommitSQL);
          PreparedStatement fileChangeStmt = conn.prepareStatement(insertFileChangeSQL);
          PreparedStatement parentStmt = conn.prepareStatement(insertIntoCommitParentsSQL)) {

        for (CommitRecord commit : commits) {
          // Prepare commit batch
          int totalAdd = count(commit, x -> true, FileChange::additions);
          int totalDel = count(commit, x -> true, FileChange::deletions);

          int totalDelTestFiles = count(commit, FileChange::isTestFile, FileChange::deletions);
          int totalAddTestFiles = count(commit, FileChange::isTestFile, FileChange::additions);
          int totalDelDotFiles = count(commit, FileChange::isDotFile, FileChange::deletions);
          int totalAddDotFiles = count(commit, FileChange::isDotFile, FileChange::additions);
          int totalDelBuildFiles = count(commit, FileChange::isBuildFile, FileChange::deletions);
          int totalAddBuildFiles = count(commit, FileChange::isBuildFile, FileChange::additions);

          String zone =
              OffsetDateTime.parse(commit.date())
                  .getOffset()
                  .getDisplayName(TextStyle.SHORT, Locale.getDefault());

          commitStmt.setString(1, commit.commitHash());
          commitStmt.setString(2, commit.author());
          commitStmt.setString(3, convertDateToUtc(commit.date()));
          commitStmt.setString(4, zone);
          commitStmt.setInt(5, commit.parents().size() > 1 ? 1 : 0);
          commitStmt.setInt(6, totalAdd);
          commitStmt.setInt(7, totalDel);
          commitStmt.setString(8, commit.message());
          commitStmt.setInt(9, totalAddTestFiles);
          commitStmt.setInt(10, totalDelTestFiles);
          commitStmt.setInt(11, totalAddDotFiles);
          commitStmt.setInt(12, totalDelDotFiles);
          commitStmt.setInt(13, totalAddBuildFiles);
          commitStmt.setInt(14, totalDelBuildFiles);

          commitStmt.addBatch();

          // Prepare file changes batch
          for (FileChange fileChange : commit.fileChanges()) {
            fileChangeStmt.setString(1, commit.commitHash());
            fileChangeStmt.setString(2, commit.author());
            fileChangeStmt.setString(3, fileChange.changeType());
            fileChangeStmt.setString(4, fileChange.filePath());
            fileChangeStmt.setInt(5, fileChange.additions());
            fileChangeStmt.setInt(6, fileChange.deletions());
            fileChangeStmt.setInt(7, fileChange.isTestFile() ? 1 : 0);
            fileChangeStmt.setInt(8, fileChange.isBuildFile() ? 1 : 0);
            fileChangeStmt.setInt(9, fileChange.isDotFile() ? 1 : 0);
            fileChangeStmt.setInt(10, fileChange.isDocumentationFile() ? 1 : 0);
            fileChangeStmt.addBatch();
          }

          // Prepare commit parents batch
          for (String parent : commit.parents()) {
            parentStmt.setString(1, commit.commitHash());
            parentStmt.setString(2, parent);
            parentStmt.addBatch();
          }

          batchCount++;

          // Execute and commit every BATCH_SIZE records
          if (batchCount % BATCH_SIZE == 0) {
            commitStmt.executeBatch();
            fileChangeStmt.executeBatch();
            parentStmt.executeBatch();
            conn.commit();
          }
        }

        // Execute batches
        commitStmt.executeBatch();
        fileChangeStmt.executeBatch();
        parentStmt.executeBatch();

        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        System.err.println("There was an issue inserting commits: " + e.getMessage());
      }
    } catch (SQLException e) {
      System.err.println("There was an issue connecting to commits.db: " + e.getMessage());
    }
  }

  private static int count(
      CommitRecord commit, Predicate<FileChange> predicate, ToIntFunction<FileChange> sumFunction) {
    return commit.fileChanges().stream()
        .filter(FileChange::isAddOrModify)
        .filter(predicate)
        .mapToInt(sumFunction)
        .sum();
  }

  /**
   * Executes a query and returns the results. The query is trusted by default as it will run
   * against the local commits.db.
   *
   * @param query the query to execute
   * @return the results of the query
   * @throws SQLException if there is an issue executing the query
   */
  public static List<Map<String, Object>> executeQuery(String query) throws SQLException {
    List<Map<String, Object>> results = new ArrayList<>();

    try (Connection conn = DriverManager.getConnection(DB_URL);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query)) {

      int columnCount = rs.getMetaData().getColumnCount();

      while (rs.next()) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
          String columnName = rs.getMetaData().getColumnName(i);
          Object value = rs.getObject(i);
          row.put(columnName, value);
        }
        results.add(row);
      }
    }
    return results;
  }

  private static String convertDateToUtc(String date) {
    if (date == null) {
      return null;
    }
    return ISO_FORMATTER.format(
        OffsetDateTime.parse(date).toZonedDateTime().withZoneSameInstant(ZoneId.of("UTC")));
  }
}
