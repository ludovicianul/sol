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
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Interacts with the sqlite db. */
@Singleton
@Unremovable
public class SolDb {

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
                        total_additions INTEGER DEFAULT 0,
                        total_deletions INTEGER DEFAULT 0,
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
                            tag_date TEXT,
                            tag_commit TEXT,
                            tag_message TEXT,
                            FOREIGN KEY(tag_commit) REFERENCES commits(commit_id)
                          );
              """;

      List<String> indexes =
          List.of(
              "CREATE INDEX idx_commits_author ON commits(author);",
              "CREATE INDEX idx_commits_date ON commits(date);",
              "CREATE INDEX idx_file_changes_file_path ON file_changes(file_path);",
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
    String insertTagSQL =
        "INSERT INTO tags (tag_name, tag_date, tag_commit, tag_message) VALUES (?, ?, ?, ?)";

    try (Connection conn = DriverManager.getConnection(DB_URL);
        PreparedStatement tagStmt = conn.prepareStatement(insertTagSQL)) {

      tagStmt.setString(1, tag.name());
      tagStmt.setString(2, tag.date());
      tagStmt.setString(3, tag.commitId());
      tagStmt.setString(4, tag.message());
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

      branchStmt.setString(1, branch.branchDate());
      branchStmt.setInt(2, branch.active());
      branchStmt.setString(3, branch.creationDate());
      branchStmt.setString(4, branch.mergeDate());
      branchStmt.executeUpdate();
    } catch (SQLException e) {
      System.err.println("There was an issue inserting branches: " + e.getMessage());
    }
  }

  /**
   * Inserts a commit record into the database.
   *
   * @param commit the commit record to insert
   */
  public static void insertCommit(CommitRecord commit) {
    String insertCommitSQL =
        "INSERT INTO commits (commit_id, author, date, timezone, total_additions, total_deletions, message) VALUES (?, ?, ?, ?, ?, ?, ?)";
    String insertFileChangeSQL =
        "INSERT INTO file_changes (commit_hash, author, change_type, file_path, additions, deletions) VALUES (?, ?, ?, ?, ?, ?)";
    String insertIntoCommitParentsSQL =
        "INSERT INTO commit_parents (commit_id, parent_id) VALUES (?, ?)";

    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      conn.setAutoCommit(false);

      try (PreparedStatement commitStmt = conn.prepareStatement(insertCommitSQL);
          PreparedStatement fileChangeStmt = conn.prepareStatement(insertFileChangeSQL);
          PreparedStatement parentStmt = conn.prepareStatement(insertIntoCommitParentsSQL)) {
        int totalAdd =
            commit.fileChanges().stream()
                .filter(f -> f.changeType().equals("A"))
                .mapToInt(FileChange::additions)
                .sum();
        int totalDel =
            commit.fileChanges().stream()
                .filter(f -> f.changeType().equals("D"))
                .mapToInt(FileChange::deletions)
                .sum();

        String zone =
            OffsetDateTime.parse(commit.date())
                .getOffset()
                .getDisplayName(TextStyle.SHORT, Locale.getDefault());

        commitStmt.setString(1, commit.commitHash());
        commitStmt.setString(2, commit.author());
        commitStmt.setString(3, commit.date());
        commitStmt.setString(4, zone);

        commitStmt.setInt(5, totalAdd);
        commitStmt.setInt(6, totalDel);
        commitStmt.setString(7, commit.message());
        commitStmt.executeUpdate();

        for (FileChange fileChange : commit.fileChanges()) {
          fileChangeStmt.setString(1, commit.commitHash());
          fileChangeStmt.setString(2, commit.author());
          fileChangeStmt.setString(3, fileChange.changeType());
          fileChangeStmt.setString(4, fileChange.filePath());
          fileChangeStmt.setInt(5, fileChange.additions());
          fileChangeStmt.setInt(6, fileChange.deletions());
          fileChangeStmt.addBatch();
        }
        fileChangeStmt.executeBatch();

        for (String parent : commit.parents()) {
          parentStmt.setString(1, commit.commitHash());
          parentStmt.setString(2, parent);
          parentStmt.addBatch();
        }
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
}
