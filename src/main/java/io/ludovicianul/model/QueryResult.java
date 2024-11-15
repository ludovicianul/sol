package io.ludovicianul.model;

public record QueryResult(String queries, String answers, String question) {
  private static final String NO_RESULT = "No data could be retrieved for the given question";

  public String piped() {
    return queries + "|" + answers + "|" + question;
  }

  public static QueryResult empty() {
    return new QueryResult("", NO_RESULT, "");
  }

  public boolean isNoAnswer() {
    return answers.equals(NO_RESULT);
  }
}
