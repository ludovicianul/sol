package io.ludovicianul;

import io.ludovicianul.command.IndexSubcommand;
import io.ludovicianul.log.Logger;
import io.ludovicianul.model.QueryResult;
import io.ludovicianul.service.SqlQueryService;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import java.io.File;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "sol",
    description = "Statistics Over git Logs",
    header =
        "%n@|bold,fg(208) sol - Statistics Over git Logs.|@%nGet meaningful insights around code and people behaviour from git activity.%n",
    mixinStandardHelpOptions = true,
    version = "@|bold,fg(208) sol 1.0.4|@",
    subcommands = {AutoComplete.GenerateCompletion.class})
@TopCommand
public class SolCommand implements Runnable {

  @CommandLine.Option(
      names = {"-q", "--question"},
      description = "Question to ask Sol")
  String question;

  @CommandLine.Option(
      names = {"-s", "--ai"},
      description = "The AI service to use. Default: OpenAI")
  AiSystem aiService = AiSystem.OPENAI;

  @CommandLine.Option(
      names = {"-m", "--model"},
      description =
          "The AI model to use. Default: gpt-4o-mini for OpenAI, claude-3-sonnet for Anthropic, llama3.2 for Ollama")
  String aiModel;

  @CommandLine.Option(
      names = {"-D", "--debug"},
      description = "Print debug info")
  boolean debug;

  @CommandLine.Option(
      names = {"-T", "--timeout"},
      description = "Timeout when executing Git commands")
  int timeout = 10;

  @CommandLine.Option(
      names = {"-X", "--maxTokensToAnalyze"},
      description = "Max tokens to use when interpreting the results. Default: 10000")
  int maxTokens = 10000;

  @CommandLine.Option(
      names = {"-i", "--index"},
      description = "Index current git repo")
  boolean index;

  @CommandLine.Option(
      names = {"-u", "--baseUrl"},
      description = "Base url when using Ollama. Default: http://localhost:11434")
  private String baseUrl = "http://localhost:11434";

  @CommandLine.Option(
      names = {"-o", "--output"},
      description =
          "The output type. Default: AI. RAW will print the raw details without interpretation. AI will use the AI service to interpret the results.")
  ResultType outputType = ResultType.AI;

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  SqlQueryService sqlQueryService;

  @Override
  public void run() {
    Logger.setDebug(debug);
    Logger.printNewLine();

    if (index) {
      new IndexSubcommand(timeout).run();
      return;
    }

    validateEnvironmentVariables();
    checkDbIsAvailable();

    sqlQueryService = new SqlQueryService(aiService, aiModel, baseUrl, maxTokens);
    QueryResult queryResult = sqlQueryService.askQuestion("The user question is: " + question);

    displayResult(queryResult);
  }

  private void displayResult(QueryResult queryResult) {
    if (queryResult.isNoAnswer() || outputType == ResultType.RAW) {
      Logger.print(queryResult.answers());
      return;
    }

    Logger.print(sqlQueryService.analyzeWithAi(queryResult));
  }

  private void checkDbIsAvailable() {
    File db = new File(".sol/commits.db");
    if (!db.exists()) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "Git activity is not indexed. Please run 'sol --index'");
    }
  }

  private void validateEnvironmentVariables() {
    String openAiKey = System.getenv("OPENAI_API_KEY");
    String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

    if (openAiKey == null && aiService == AiSystem.OPENAI) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "OPENAI_API_KEY environment variable is missing");
    }

    if (anthropicKey == null && aiService == AiSystem.ANTHROPIC) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "ANTHROPIC_API_KEY environment variable is missing");
    }
  }

  public enum AiSystem {
    OPENAI,
    ANTHROPIC,
    OLLAMA
  }

  public enum ResultType {
    RAW,
    AI
  }
}
