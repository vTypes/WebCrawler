package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Pattern;

final class ParallelWebCrawler implements WebCrawler {

  // Your existing fields
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final PageParserFactory parserFactory;
  private final int maxDepth;
  private final List<Pattern> ignoredUrls;

  @Inject
  ParallelWebCrawler(Clock clock,
                     @Timeout Duration timeout,
                     @PopularWordCount int popularWordCount,
                     @TargetParallelism int threadCount,
                     PageParserFactory parserFactory,
                     @MaxDepth int maxDepth,
                     @IgnoredUrls List<Pattern> ignoredUrls) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.parserFactory = parserFactory;
    this.maxDepth = maxDepth;
    this.ignoredUrls = ignoredUrls;
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = clock.instant().plus(timeout);

    // Thread-safe collections for tracking visited URLs and word counts
    Map<String, Integer> wordCounts = new ConcurrentHashMap<>();
    Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    try {
      // Submit the initial task to the ForkJoinPool
      pool.invoke(new CrawlTask(startingUrls, deadline, maxDepth, wordCounts, visitedUrls));
    } finally {
      // Shutdown and ensure clean termination of the pool
      pool.shutdown();
      try {
        if (!pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
          System.err.println("ForkJoinPool did not terminate cleanly.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    if (wordCounts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(wordCounts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(wordCounts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  // Custom recursive action for parallel crawling
  private class CrawlTask extends RecursiveAction {
    private final List<String> urls;
    private final Instant deadline;
    private final int depth;
    private final Map<String, Integer> wordCounts;
    private final Set<String> visitedUrls;

    CrawlTask(List<String> urls, Instant deadline, int depth,
              Map<String, Integer> wordCounts, Set<String> visitedUrls) {
      this.urls = urls;
      this.deadline = deadline;
      this.depth = depth;
      this.wordCounts = wordCounts;
      this.visitedUrls = visitedUrls;
    }

    @Override
    protected void compute() {
      if (depth == 0 || clock.instant().isAfter(deadline)) return;

      List<CrawlTask> subtasks = new ArrayList<>();

      for (String url : urls) {
        // Check if the URL should be ignored first
        if (ignoredUrls.stream().anyMatch(p -> p.matcher(url).matches())) {
          System.out.println("Skipping URL due to ignored pattern: " + url);
          continue;
        }

        // Auto-check if the URL was already visited and add if not
        if (!visitedUrls.add(url)) {
          continue;
        }

        // Proceed with parsing and crawling
        PageParser.Result result;
        try {
          result = parserFactory.get(url).parse();

          // Handle dead-end or error pages
          if (isDeadEndPage(result)) {
            System.out.println("Skipping dead-end or invalid page: " + url);
            continue;
          }

          // Merge word counts
          result.getWordCounts().forEach((word, count) ->
                  wordCounts.merge(word, count, Integer::sum)
          );

          // Create subtasks for found links
          if (!result.getLinks().isEmpty()) {
            subtasks.add(new CrawlTask(result.getLinks(), deadline, depth - 1, wordCounts, visitedUrls));
          }
        } catch (Exception e) {
          System.err.println("Failed to parse page: " + url);
        }
      }

      // Execute the subtasks in parallel
      invokeAll(subtasks);
    }

    // Helper method to detect dead-end pages
    private boolean isDeadEndPage(PageParser.Result result) {
      return result.getWordCounts().containsKey("deadend") ||
              result.getWordCounts().containsKey("404") ||
              result.getWordCounts().containsKey("notfound");
    }
  }
}