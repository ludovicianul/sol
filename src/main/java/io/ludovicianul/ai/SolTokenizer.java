package io.ludovicianul.ai;

import dev.langchain4j.model.openai.OpenAiTokenizer;
import io.ludovicianul.log.Logger;
import java.util.List;

/** Tokenizer for the AI models. */
public class SolTokenizer {
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
    int tokenCount = tokenizer.estimateTokenCountInText(text);
    Logger.debug("Total tokens to be sent: " + tokenCount);

    if (tokenCount <= maxTokens) {
      return text;
    }
    List<Integer> tokenIds = tokenizer.encode(text);
    List<Integer> limitedTokens = tokenIds.subList(0, Math.min(5000, tokenIds.size()));
    return tokenizer.decode(limitedTokens);
  }
}
