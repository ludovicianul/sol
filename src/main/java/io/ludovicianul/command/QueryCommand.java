package io.ludovicianul.command;

import io.ludovicianul.db.SolDb;
import io.ludovicianul.log.Logger;
import io.quarkus.arc.Unremovable;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

/** Command line interface for running predefined queries on git data. */
@CommandLine.Command(
    name = "query",
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    description = "Runs predefined queries on git data, without using AI")
@Unremovable
public class QueryCommand implements Runnable {

  @CommandLine.Option(
      names = {"--from"},
      description = "From date, format: yyyy-MM-dd")
  String from;

  @CommandLine.Option(
      names = {"--to"},
      description = "To date, format: yyyy-MM-dd")
  String to;

  @CommandLine.Option(
      names = {"--report"},
      converter = CaseInsensitiveReportTypeConverter.class,
      description = "Type of report to generate. Valid values: ${COMPLETION-CANDIDATES}",
      required = true)
  ReportType reportType;

  @Override
  public void run() {
    String query = getQueryToRun(QUERIES.get(reportType));
    try {
      List<Map<String, Object>> result = SolDb.executeQuery(query);
      Logger.print(formatResult(result));
    } catch (SQLException e) {
      Logger.err("Error running query: " + e.getMessage());
    }
  }

  public enum ReportType {
    CHURN,
    RELEASES,
    MERGE_TIME,
    COMMIT_VELOCITY,
    TEST_CHANGES
  }

  public static String formatResult(List<Map<String, Object>> result) {
    StringBuilder sb = new StringBuilder();
    if (!result.isEmpty()) {
      Map<String, Object> firstRow = result.getFirst();
      sb.append(String.join(", ", firstRow.keySet())).append("\n");
    }
    for (Map<String, Object> map : result) {
      sb.append(
              String.join(
                  ", ",
                  map.values().stream()
                      .map(value -> value != null ? value.toString() : "")
                      .toArray(String[]::new)))
          .append("\n");
    }

    return sb.toString().trim();
  }

  public String getQueryToRun(String baseQuery) {
    String fromPlaceholder = getStartOfDayFrom();
    String toPlaceholder = getEndOfDayTo();

    if (from == null && to == null) {
      baseQuery = baseQuery.replace("AND date BETWEEN %s AND %s", "");
    } else if (from == null) {
      baseQuery =
          baseQuery
              .replace("AND date BETWEEN %s AND %s", "AND date <= %s")
              .replaceFirst("%s", toPlaceholder);
    } else if (to == null) {
      baseQuery =
          baseQuery
              .replace("AND date BETWEEN %s AND %s", "AND date >= %s")
              .replaceFirst("%s", fromPlaceholder);
    } else {
      baseQuery = baseQuery.formatted(fromPlaceholder, toPlaceholder);
    }

    return baseQuery;
  }

  private String getEndOfDayTo() {
    return to != null
        ? "'"
            + DateTimeFormatter.ISO_INSTANT.format(
                LocalDate.parse(to).atTime(23, 59, 59).atZone(ZoneId.of("UTC")).toInstant())
            + "'"
        : "NULL";
  }

  private String getStartOfDayFrom() {
    return from != null
        ? "'"
            + DateTimeFormatter.ISO_INSTANT.format(
                LocalDate.parse(from).atStartOfDay(ZoneId.of("UTC")).toInstant())
            + "'"
        : "NULL";
  }

  private static final EnumMap<ReportType, String> QUERIES = new EnumMap<>(ReportType.class);

  private static final String CHURN_QUERY =
      """
      SELECT
          author,
          COUNT(commit_hash) AS num_commits,
          SUM(total_deletions) AS total_deletions,
          SUM(total_additions) AS total_additions
      FROM
          commits
      WHERE
          1 = 1
          AND date BETWEEN %s AND %s
      GROUP BY
          author;
    """;

  private static final String RELEASES_QUERY =
      """
      SELECT
          COUNT(t.tag_name) AS no_of_releases
      FROM
          tags t
      JOIN
          commits c ON t.tag_commit = c.commit_hash
      WHERE
          1 = 1
          AND date BETWEEN %s AND %s;
    """;

  private static final String AVERAGE_MERGE_TIME_QUERY =
      """
      WITH merge_times AS (
          SELECT
              repo_name,
              date AS merge_date,
              LAG(date) OVER (PARTITION BY repo_name ORDER BY date) AS previous_merge_date
          FROM
              commits
          WHERE
              is_merge = 1
              AND date BETWEEN %s AND %s
      ),
      merge_durations AS (
          SELECT
              repo_name,
              JULIANDAY(merge_date) - JULIANDAY(previous_merge_date) AS duration_between_merges
          FROM
              merge_times
          WHERE
              previous_merge_date IS NOT NULL
      )
      SELECT
          repo_name,
          AVG(duration_between_merges) AS avg_time_in_days_between_merges
      FROM
          merge_durations
      GROUP BY
          repo_name;
    """;

  private static final String COMMIT_VELOCITY =
      """
      SELECT
          substr(date, 1, 10) AS day,
          repo_name,
          COUNT(commit_hash) AS commits_per_day
      FROM
          commits
      WHERE
          1 = 1
          AND date BETWEEN %s AND %s
      GROUP BY
          repo_name, day
      ORDER BY
          repo_name, day;
    """;

  private static final String TEST_CHANGES =
      """
    SELECT
        repo_name,
        SUM(total_additions + total_deletions) AS total_changes,
        SUM(total_additions_test + total_deletions_test) AS test_file_changes,
        ROUND((SUM(total_additions_test + total_deletions_test) * 100.0 / SUM(total_additions + total_deletions)), 2) AS test_file_change_percentage
    FROM
        commits
    WHERE
          1 = 1
          AND date BETWEEN %s AND %s
    GROUP BY
        repo_name
    ORDER BY
        test_file_change_percentage DESC;
    """;

  static {
    QUERIES.put(ReportType.CHURN, CHURN_QUERY);
    QUERIES.put(ReportType.RELEASES, RELEASES_QUERY);
    QUERIES.put(ReportType.MERGE_TIME, AVERAGE_MERGE_TIME_QUERY);
    QUERIES.put(ReportType.COMMIT_VELOCITY, COMMIT_VELOCITY);
    QUERIES.put(ReportType.TEST_CHANGES, TEST_CHANGES);
  }
}
