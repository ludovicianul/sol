package io.ludovicianul.io;

import io.ludovicianul.log.Logger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public enum ProcessRunner {
  INSTANCE;
  private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

  public List<String> getMultiLineProcessOut(int timeout, String command) {
    ProcessBuilder processBuilder = createProcess("/bin/sh", "-c", command);

    try {
      return getProcessOut(processBuilder.start(), timeout);
    } catch (Exception e) {
      System.err.printf("Error while running command %s: %s%n", command, e.getMessage());
    }
    return List.of();
  }

  public String getSingleLineProcessOut(int timeout, String command) {
    List<String> listOutput = this.getMultiLineProcessOut(timeout, command);
    return listOutput.isEmpty() ? null : listOutput.getFirst();
  }

  public ProcessBuilder createProcess(String... args) {
    ProcessBuilder builder = new ProcessBuilder(args);
    builder.redirectErrorStream(true);
    builder.redirectError(java.lang.ProcessBuilder.Redirect.PIPE);
    builder.environment().put("NO_COLOR", "1");
    builder.environment().put("QUARKUS_BANNER_ENABLED", "false");

    builder.command().removeIf(String::isBlank);

    return builder;
  }

  public void executeProcess(Process process, int timeout) {
    this.runInThread(() -> getProcessOut(process, null, null), timeout);
  }

  private List<String> runInThread(Supplier<List<String>> supplier, int timeout) {
    final List<String> result = new ArrayList<>();

    Future<?> future =
        EXECUTOR_SERVICE.submit(
            () -> {
              result.addAll(supplier.get());
            });
    try {
      future.get(timeout, TimeUnit.SECONDS);
    } catch (TimeoutException | InterruptedException | ExecutionException e) {
      if (e instanceof ExecutionException) {
        throw new RuntimeException(e);
      }
      Logger.print("Timeout!");
    }
    return result;
  }

  public List<String> getProcessOut(Process process, int timeout) {
    return this.runInThread(() -> getProcessOut(process, null, null), timeout);
  }

  public List<String> getProcessOut(
      Process process, Consumer<String> consumer, Predicate<String> consumeIf) {
    List<String> builder = new ArrayList<>();

    try (java.io.BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {

        Logger.debug(line);

        if (consumer == null) {
          builder.add(line);
        } else if (consumeIf.test(line)) {
          consumer.accept(line);
        }
      }
      int result = process.waitFor();
      if (result != 0) {
        throw new ErrorExecutionException();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return builder;
  }
}
