package io.ludovicianul.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import io.ludovicianul.SolCommand;
import io.ludovicianul.ai.SqlGeneratorAi;
import io.ludovicianul.db.SolDb;
import io.ludovicianul.log.Logger;
import io.ludovicianul.model.QueryResult;
import io.quarkiverse.langchain4j.ollama.OllamaChatLanguageModel;
import io.quarkiverse.langchain4j.ollama.Options;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SqlQueryService {
  private final SqlGeneratorAi sqlGenerator;

  public SqlQueryService(
      SolCommand.AiSystem modelType, String userSuppliedModel, String ollamaUrl) {
    String openAiKey = System.getenv("OPENAI_API_KEY");
    String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

    String modelName =
        switch (modelType) {
          case OPENAI -> Optional.ofNullable(userSuppliedModel).orElse("gpt-4o-mini");
          case ANTHROPIC -> Optional.ofNullable(userSuppliedModel).orElse("claude-3-sonnet");
          case OLLAMA -> Optional.ofNullable(userSuppliedModel).orElse("llama3.2");
        };

    ChatLanguageModel aiModel =
        switch (modelType) {
          case OPENAI ->
              OpenAiChatModel.builder()
                  .apiKey(openAiKey)
                  .modelName(modelName)
                  .maxTokens(5000)
                  .temperature(0.0)
                  .build();
          case ANTHROPIC ->
              AnthropicChatModel.builder()
                  .apiKey(anthropicKey)
                  .modelName(modelName)
                  .maxTokens(5000)
                  .temperature(0.0)
                  .build();
          case OLLAMA ->
              OllamaChatLanguageModel.builder()
                  .baseUrl(ollamaUrl)
                  .model(modelName)
                  .timeout(Duration.ofSeconds(30))
                  .options(
                      Options.builder().temperature(0.0).numCtx(20000).numPredict(20000).build())
                  .build();
        };

    Logger.debug("Ai system: " + modelType + ", model: " + modelName);

    this.sqlGenerator = AiServices.create(SqlGeneratorAi.class, aiModel);
  }

  public String analyzeWithAi(QueryResult result) {
    return sqlGenerator.beautifyResult(result.piped());
  }

  public QueryResult askQuestion(String userQuestion) {
    String sql = sqlGenerator.generateSqlQuery(userQuestion);

    Logger.debug("Generated SQL query: " + sql + "\n");

    List<String> queries = new Gson().fromJson(sql, new TypeToken<List<String>>() {}.getType());
    StringBuilder finalResult = new StringBuilder();
    int resultLength = 0;

    for (String query : queries) {
      List<Map<String, Object>> queryResult = executeQuery(query);

      resultLength += queryResult.size();
      String secondResultAsString =
          queryResult.stream()
              .map(
                  item ->
                      item.entrySet().stream()
                          .map(entry -> entry.getKey() + ": " + entry.getValue())
                          .collect(Collectors.joining(",")))
              .collect(Collectors.joining(";"));
      finalResult.append(secondResultAsString).append("\n");
    }

    if (resultLength == 0) {
      return QueryResult.empty();
    }

    return new QueryResult(sql, finalResult.toString(), userQuestion);
  }

  private List<Map<String, Object>> executeQuery(String query) {
    List<Map<String, Object>> queryResult = List.of();
    long t0 = System.currentTimeMillis();
    try {
      queryResult = SolDb.executeQuery(query);
    } catch (SQLException e) {
      Logger.debug("Error while executing first query: %s".formatted(e.getMessage()));

      String errorMessage = "Error while executing query: %s".formatted(e.getMessage());
      String newQuery =
          sqlGenerator.reviewSqlQuery("SQL query: " + query + "\nError Message: " + errorMessage);

      Logger.debug("Revised query: " + newQuery);
      try {
        queryResult = SolDb.executeQuery(newQuery);
      } catch (SQLException e2) {
        Logger.debug("Error while executing revised query: " + e2.getMessage());
      }
    } finally {
      long t1 = System.currentTimeMillis();
      Logger.debug("Query execution time: " + (t1 - t0) + " ms");
    }
    return queryResult;
  }
}
