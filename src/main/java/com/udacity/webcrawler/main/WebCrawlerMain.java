package com.udacity.webcrawler.main;

import com.google.inject.Guice;
import com.udacity.webcrawler.WebCrawler;
import com.udacity.webcrawler.WebCrawlerModule;
import com.udacity.webcrawler.json.ConfigurationLoader;
import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.json.CrawlResultWriter;
import com.udacity.webcrawler.json.CrawlerConfiguration;
import com.udacity.webcrawler.profiler.Profiler;
import com.udacity.webcrawler.profiler.ProfilerModule;

import javax.inject.Inject;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class WebCrawlerMain {

  private final CrawlerConfiguration config;

  @Inject
  private WebCrawlerMain(CrawlerConfiguration config) {
    this.config = Objects.requireNonNull(config);
  }

  @Inject
  private WebCrawler crawler;

  @Inject
  private Profiler profiler;

  private void run() throws IOException {
    CrawlResult result = crawler.crawl(config.getStartPages());
    CrawlResultWriter resultWriter = new CrawlResultWriter(result);

    if (config.getResultPath() != null && !config.getResultPath().isEmpty()) {
      Path resultPath = Path.of(config.getResultPath());
      resultWriter.write(resultPath);
    } else {
      try (Writer writer = new OutputStreamWriter(System.out)) {
        resultWriter.write(writer);
      }
    }

    if (config.getProfileOutputPath() != null && !config.getProfileOutputPath().isEmpty()) {
      Path profilePath = Path.of(config.getProfileOutputPath());
      profiler.writeData(profilePath);
    } else {
      try (Writer writer = new OutputStreamWriter(System.out)) {
        profiler.writeData(writer);
      }
    }
  }

    public static void main(String[] args) throws Exception {
      if (args.length != 1) {
        System.out.println("Usage: WebCrawlerMain [config-file]");
        return;
      }

      CrawlerConfiguration config = new ConfigurationLoader(Path.of(args[0])).load();
      WebCrawlerMain webCrawlerMain = Guice.createInjector(new WebCrawlerModule(config), new ProfilerModule())
              .getInstance(WebCrawlerMain.class);
      webCrawlerMain.run();
    }
}
