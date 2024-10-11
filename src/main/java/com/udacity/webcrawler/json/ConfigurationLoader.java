package com.udacity.webcrawler.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A static utility class that loads a JSON configuration file.
 */
public final class ConfigurationLoader {

  private final Path path;

  /**
   * Create a {@link ConfigurationLoader} that loads configuration from the given {@link Path}.
   */
  public ConfigurationLoader(Path path) {
    this.path = Objects.requireNonNull(path);
  }

  /**
   * Loads configuration from this {@link ConfigurationLoader}'s path
   *
   * @return the loaded {@link CrawlerConfiguration}.
   */
  public CrawlerConfiguration load() {
    try (Reader reader = Files.newBufferedReader(path)) {
      // Use the read method to parse the configuration from the reader
      return read(reader);
    } catch (IOException e) {
      // Handle potential IO exceptions
      throw new RuntimeException("Failed to load crawler configuration from file", e);
    }
    //return new CrawlerConfiguration.Builder().build();
  }

  /**
   * Loads crawler configuration from the given reader.
   *
   * @param reader a Reader pointing to a JSON string that contains crawler configuration.
   * @return a crawler configuration
   */
  public static CrawlerConfiguration read(Reader reader) {
    // This is here to get rid of the unused variable warning.
    Objects.requireNonNull(reader);
    // TODO: Fill in this method
    try {
      // Create an ObjectMapper instance
      ObjectMapper objectMapper = new ObjectMapper();

      // Disable AUTO_CLOSE_SOURCE to avoid closing the reader prematurely
      objectMapper.disable(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE);

      // Deserialize JSON into a CrawlerConfiguration object
      return objectMapper.readValue(reader, CrawlerConfiguration.class);
    } catch (IOException e) {
      // Handle potential IO exceptions
      throw new RuntimeException("Failed to read crawler configuration from JSON", e);
    }
    //return new CrawlerConfiguration.Builder().build();
  }
}
