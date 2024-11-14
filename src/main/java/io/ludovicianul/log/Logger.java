package io.ludovicianul.log;

/** Logger class for logging debug information. */
public class Logger {
  private static boolean debug;

  public static boolean isDebug() {
    return debug;
  }

  public static void setDebug(boolean debug) {
    Logger.debug = debug;
  }

  public static void print(String message) {
    System.out.println(message);
  }

  public static void err(String message) {
    System.err.println(message);
  }

  public static void debug(String message) {
    if (debug) {
      System.out.println(message);
    }
  }

  public static void printNewLine() {
    System.out.println();
  }
}
