package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A {@link WebCrawler} that downloads and processes one page at a time.
 */
final class SequentialWebCrawler implements WebCrawler {

  private final Clock clock;
  private final PageParserFactory parserFactory;
  private final Duration timeout;
  private final int popularWordCount;
  private final int maxDepth;
  private final List<Pattern> ignoredUrls;

  @Inject
  SequentialWebCrawler(
          Clock clock,
          PageParserFactory parserFactory,
          @Timeout Duration timeout,
          @PopularWordCount int popularWordCount,
          @MaxDepth int maxDepth,
          @IgnoredUrls List<Pattern> ignoredUrls) {
    this.clock = clock;
    this.parserFactory = parserFactory;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.maxDepth = maxDepth;
    this.ignoredUrls = ignoredUrls;
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);
    Map<String, Integer> counts = new HashMap<>();
    Set<String> visitedUrls = new HashSet<>();
    for (String url : startingUrls) {
      crawlInternal(url, deadline, maxDepth, counts, visitedUrls);
    }

    if (counts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(counts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  private void crawlInternal(String url,
                             Instant deadline,
                             int depth,
                             Map<String, Integer> counts,
                             Set<String> visitedUrls) {
    if (depth == 0 || clock.instant().isAfter(deadline)) return;

    // Check if the URL is already visited or should be ignored
    if (visitedUrls.contains(url)) return;

    for (Pattern pattern : ignoredUrls) {
      if (pattern.matcher(url).matches()) {
        System.out.println("Skipping URL due to ignored pattern: " + url);
        return;  // Exit without marking this URL as visited
      }
    }

    // Mark the URL as visited
    visitedUrls.add(url);

    try {
      // Parse the page
      PageParser.Result result = parserFactory.get(url).parse();

      // If the page is a dead end or error, skip adding word counts
      if (isDeadEndPage(result)) {
        System.out.println("Skipping dead-end or invalid page: " + url);
        return;
      }

      // Merge word counts from the page
      result.getWordCounts().forEach((word, count) ->
              counts.merge(word, count, Integer::sum)
      );

      // Recursively crawl the links found on the page
      for (String link : result.getLinks()) {
        crawlInternal(link, deadline, depth - 1, counts, visitedUrls);
      }
    } catch (Exception e) {
      // Handle parsing failure
      System.err.println("Failed to parse page: " + url);
    }
  }

  // Helper method to detect dead-end pages
  private boolean isDeadEndPage(PageParser.Result result) {
    return result.getWordCounts().containsKey("deadend") ||
            result.getWordCounts().containsKey("404") ||
            result.getWordCounts().containsKey("notfound");
  }

  private CrawlResult buildCrawlResult(Map<String, Integer> counts, Set<String> visitedUrls) {
    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }
}