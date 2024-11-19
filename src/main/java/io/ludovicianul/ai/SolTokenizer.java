package io.ludovicianul.ai;

import dev.langchain4j.model.openai.OpenAiTokenizer;
import io.ludovicianul.log.Logger;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tokenizer for the AI models. */
public class SolTokenizer {
  private static final String MARKDOWN_CODE = "(?s)```[^`]*?\\n([\\s\\S]*?)```";
  private static final Pattern KEEP_TEXT_INSIDE_BRACKETS = Pattern.compile("\\[[^\\]]+\\]");

  private final OpenAiTokenizer tokenizer;
  private final int maxTokens;

  private SolTokenizer(String model, int maxTokens) {
    tokenizer = new OpenAiTokenizer(model);
    this.maxTokens = maxTokens;
  }

  public static SolTokenizer createTokenizer(String model, int maxTokens) {
    return new SolTokenizer(model, maxTokens);
  }

  public String limitTokens(String text) {
    try {
      int tokenCount = tokenizer.estimateTokenCountInText(text);
      Logger.debug("Total tokens to be sent: " + tokenCount);

      if (tokenCount <= maxTokens) {
        return text;
      }
      List<Integer> tokenIds = tokenizer.encode(text);
      List<Integer> limitedTokens = tokenIds.subList(0, Math.min(5000, tokenIds.size()));
      return tokenizer.decode(limitedTokens);
    } catch (Exception e) {
      Logger.err("Error while limiting tokens: " + e.getMessage());
      return text;
    }
  }

  public String clean(String markdownText) {
    String cleanedText = removeCodeBlocks(markdownText);
    cleanedText = extractTextInsideBrackets(cleanedText);

    return cleanedText;
  }

  public String removeCodeBlocks(String markdownText) {
    if (markdownText == null) {
      return "";
    }
    String cleanedText = markdownText.replaceAll(MARKDOWN_CODE, "$1");

    cleanedText = cleanedText.trim().replace("\n", "");
    return cleanedText;
  }

  public static String extractTextInsideBrackets(String input) {
    if (input == null) {
      return "";
    }

    StringBuilder result = new StringBuilder();

    Matcher matcher = KEEP_TEXT_INSIDE_BRACKETS.matcher(input);

    while (matcher.find()) {
      result.append(matcher.group()).append(" ");
    }

    return result.toString().trim();
  }
}
