package io.ludovicianul.command;

import io.ludovicianul.SolCommand;
import java.util.Arrays;
import picocli.CommandLine;

/**
 * Case insensitive converter for the AI system command line argument.
 */
public class CaseInsensitiveAiSystemConverter
    implements CommandLine.ITypeConverter<SolCommand.AiSystem> {

  @Override
  public SolCommand.AiSystem convert(String value) {
    for (SolCommand.AiSystem constant : SolCommand.AiSystem.values()) {
      if (constant.name().equalsIgnoreCase(value)) {
        return constant;
      }
    }
    throw new IllegalArgumentException(
        "Allowed values: " + Arrays.toString(SolCommand.AiSystem.values()));
  }
}
