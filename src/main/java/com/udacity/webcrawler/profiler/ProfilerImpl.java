package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime startTime;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.startTime = ZonedDateTime.now(clock);
  }

  private boolean containsProfiledMethod(Class<?> klass) {
    for (Method method : klass.getDeclaredMethods()) {
      if (method.isAnnotationPresent(Profiled.class)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public <T> T wrap(Class<T> klass, T delegate) {
    Objects.requireNonNull(klass);
    if (!containsProfiledMethod(klass)) {
      throw new IllegalArgumentException("Class must contain at least one @Profiled method.");
    }

    return (T) Proxy.newProxyInstance(
            klass.getClassLoader(),
            new Class<?>[]{klass},
            (proxy, method, args) -> {
              Instant startTime = clock.instant();
              try {
                // Invoke the actual method
                return method.invoke(delegate, args);
              } catch (InvocationTargetException e) {
                // Re-throw the original exception (cause) thrown by the method
                throw e.getCause();
              } finally {
                // After method execution or exception, record the profiling data
                Instant endTime = clock.instant();
                if (method.isAnnotationPresent(Profiled.class)) {
                  state.record(delegate.getClass(), method, Duration.between(startTime, endTime));
                }
              }
            });
  }

  @Override
  public void writeData(Path path) {
    Objects.requireNonNull(path);

    // Use a try-with-resources block to ensure the file is written and closed properly
    try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      writeData(writer);  // Reuse the existing method to write to the Appendable
    } catch (IOException e) {
      throw new RuntimeException("Failed to write profiling data to file", e);
    }
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}
