package io.ludovicianul.command;

import java.util.Arrays;
import picocli.CommandLine;

/**
 * Case insensitive converter for the report type command line argument.
 */
public class CaseInsensitiveReportTypeConverter
    implements CommandLine.ITypeConverter<QueryCommand.ReportType> {

  @Override
  public QueryCommand.ReportType convert(String value) {
    for (QueryCommand.ReportType constant : QueryCommand.ReportType.values()) {
      if (constant.name().equalsIgnoreCase(value)) {
        return constant;
      }
    }
    throw new IllegalArgumentException(
        "Allowed values: " + Arrays.toString(QueryCommand.ReportType.values()));
  }
}
