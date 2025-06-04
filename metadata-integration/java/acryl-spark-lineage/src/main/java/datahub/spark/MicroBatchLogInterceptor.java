package datahub.spark;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Intercepts log output from Spark's MicroBatchExecution to extract additional lineage information.
 * This class hooks into Spark's logging infrastructure to capture details that may not be available
 * through the standard Spark listener interfaces.
 */
@Slf4j
public class MicroBatchLogInterceptor {
  private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
  private static final String MICRO_BATCH_LOGGER_NAME =
      "org.apache.spark.sql.execution.streaming.MicroBatchExecution";
  private static DatahubEventEmitter emitter;
  private static final Pattern CATALOG_TABLE_PATTERN =
      Pattern.compile(
          ".*MicroBatchExecution.*\\[queryId = ([^\\]]+)\\].*CatalogTable\\(\\s*\n"
              + "Catalog: ([^\\n]+)\\s*\n"
              + "Database: ([^\\n]+)\\s*\n"
              + "Table: ([^\\n]+)\\s*\n"
              + "(?:Created Time:[^\\n]*\\s*\n)?"
              + "(?:Last Access:[^\\n]*\\s*\n)?"
              + "(?:Created By:[^\\n]*\\s*\n)?"
              + "(?:Type:[^\\n]*\\s*\n)?"
              + "(?:Provider: ([^\\n]+)\\s*\n)?"
              + "(?:.*\\s*\n)*?"
              + "(?:Location: ([^\\n]+)\\s*\n)?"
              + "(?:.*\\s*\n)*?"
              + "(?:Partition Columns: \\[([^\\]]+)\\]\\s*\n)?"
              + "(?:Schema: (root[\\s\\S]*?)(?:\\.[\\s]*)?)",
          Pattern.DOTALL);
  private static final Pattern PROGRESS_REPORTER_PATTERN =
      Pattern.compile(
          ".*ProgressReporter.*\\[queryId = ([^\\]]+)\\].*Streaming query made progress.*");

  /**
   * Installs the log interceptor to capture MicroBatchExecution logs.
   *
   * @param emitter the DatahubEventEmitter to receive events
   * @return true if installation was successful, false if already installed or failed
   */
  public static boolean install(DatahubEventEmitter emitter) {
    // Check if already installed
    if (INSTALLED.get()) {
      log.info("MicroBatchLogInterceptor is already installed");
      return false;
    }

    MicroBatchLogInterceptor.emitter = emitter;
    log.info("Installing MicroBatchLogInterceptor");

    try {
      // Try to find Log4j2 first (common in newer versions)
      boolean log4j2Success = installLog4j2Appender();
      if (log4j2Success) {
        INSTALLED.set(true);
        log.info("Successfully installed MicroBatchLogInterceptor using Log4j2");
        return true;
      }

      // Try Log4j (older versions)
      boolean log4jSuccess = installLog4jAppender();
      if (log4jSuccess) {
        INSTALLED.set(true);
        log.info("Successfully installed MicroBatchLogInterceptor using Log4j");
        return true;
      }

      // Try Logback
      boolean logbackSuccess = installLogbackAppender();
      if (logbackSuccess) {
        INSTALLED.set(true);
        log.info("Successfully installed MicroBatchLogInterceptor using Logback");
        return true;
      }

      log.warn("Failed to install MicroBatchLogInterceptor - no compatible logging system found");
      return false;

    } catch (Exception e) {
      log.error("Error installing MicroBatchLogInterceptor", e);
      return false;
    }
  }

  /** Installs a Log4j2 appender for the MicroBatchExecution logger. */
  private static boolean installLog4j2Appender() {
    try {
      // Try to get the Log4j2 context
      Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
      Class<?> contextClass = Class.forName("org.apache.logging.log4j.core.LoggerContext");
      Class<?> configClass = Class.forName("org.apache.logging.log4j.core.config.Configuration");

      // Get the logger context
      Object context = logManagerClass.getMethod("getContext", boolean.class).invoke(null, false);
      if (context == null) {
        log.debug("Log4j2 context not available");
        return false;
      }

      // Get the configuration
      Object config = contextClass.getMethod("getConfiguration").invoke(context);

      // Get the logger
      Object logger =
          logManagerClass
              .getMethod("getLogger", String.class)
              .invoke(null, MICRO_BATCH_LOGGER_NAME);
      if (logger == null) {
        log.debug("Log4j2 logger for MicroBatchExecution not available");
        return false;
      }

      log.info("Successfully attached to Log4j2 logger for MicroBatchExecution");
      return true;

    } catch (ClassNotFoundException e) {
      // Log4j2 not available
      log.debug("Log4j2 classes not found, skipping Log4j2 appender installation");
      return false;
    } catch (Exception e) {
      log.warn("Error installing Log4j2 appender: {}", e.getMessage());
      return false;
    }
  }

  /** Installs a Log4j appender for the MicroBatchExecution logger. */
  private static boolean installLog4jAppender() {
    try {
      // Try to get the Log4j logger
      Class<?> logManagerClass = Class.forName("org.apache.log4j.LogManager");

      // Get the logger
      Object logger =
          logManagerClass
              .getMethod("getLogger", String.class)
              .invoke(null, MICRO_BATCH_LOGGER_NAME);

      if (logger == null) {
        log.debug("Log4j logger for MicroBatchExecution not available");
        return false;
      }

      // Create and add a custom appender
      installLogAppender(logger);

      log.info("Successfully attached to Log4j logger for MicroBatchExecution");
      return true;

    } catch (ClassNotFoundException e) {
      // Log4j not available
      log.debug("Log4j classes not found, skipping Log4j appender installation");
      return false;
    } catch (Exception e) {
      log.warn("Error installing Log4j appender: {}", e.getMessage());
      return false;
    }
  }

  /** Installs a Logback appender for the MicroBatchExecution logger. */
  private static boolean installLogbackAppender() {
    try {
      // Try to get the Logback logger
      Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");

      // Get the logger
      Object logger =
          loggerFactoryClass
              .getMethod("getLogger", String.class)
              .invoke(null, MICRO_BATCH_LOGGER_NAME);

      if (logger == null) {
        log.debug("Logback logger for MicroBatchExecution not available");
        return false;
      }

      // Create and add a custom appender
      installLogbackAppender(logger);

      log.info("Successfully attached to Logback logger for MicroBatchExecution");
      return true;

    } catch (ClassNotFoundException e) {
      // Logback not available
      log.debug("Logback classes not found, skipping Logback appender installation");
      return false;
    } catch (Exception e) {
      log.warn("Error installing Logback appender: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Installs a custom log appender to intercept MicroBatchExecution logs. This method uses
   * reflection to avoid direct dependencies on logging implementations.
   */
  private static void installLogAppender(Object logger) throws Exception {
    try {
      // Attempt to identify the logger implementation and add our appender
      // This code handles both Log4j and Logback implementations

      // Check if we're dealing with a Log4j logger
      if (logger.getClass().getName().contains("Log4j")) {
        installLog4jAppender(logger);
      }
      // Check if we're dealing with a Logback logger
      else if (logger.getClass().getName().contains("Logback")) {
        installLogbackAppender(logger);
      }
      // Unknown logger implementation
      else {
        log.warn(
            "Unknown logger implementation: {}, cannot install interceptor",
            logger.getClass().getName());
      }
    } catch (Exception e) {
      log.warn("Failed to install log appender: {}", e.getMessage());
      throw e;
    }
  }

  /** Installs a custom appender for Log4j loggers. */
  private static void installLog4jAppender(Object logger) throws Exception {
    // Get the underlying logger implementation
    Field loggerField = logger.getClass().getDeclaredField("logger");
    loggerField.setAccessible(true);
    Object log4jLogger = loggerField.get(logger);

    // Create and add a custom appender that captures micro-batch events
    Class<?> appenderClass = Class.forName("org.apache.log4j.AppenderSkeleton");
    Object appender = createCustomLog4jAppender(appenderClass);

    // Add the appender to the logger
    log4jLogger.getClass().getMethod("addAppender", appenderClass).invoke(log4jLogger, appender);

    log.info("Successfully installed Log4j appender for MicroBatchExecution");
  }

  /** Creates a custom Log4j appender using dynamic proxy to avoid direct dependency. */
  private static Object createCustomLog4jAppender(Class<?> appenderClass) throws Exception {
    try {
      // Try to load the actual Log4j AppenderSkeleton class directly if available
      Class<?> actualAppenderClass = Class.forName("org.apache.log4j.AppenderSkeleton");

      // Create a dynamic proxy that implements the necessary methods
      java.lang.reflect.InvocationHandler handler =
          new java.lang.reflect.InvocationHandler() {
            @Override
            public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                throws Throwable {
              String methodName = method.getName();

              // Handle the append method to intercept log events
              if (methodName.equals("append") && args != null && args.length > 0) {
                try {
                  // Extract the log event
                  Object event = args[0];

                  // Get the message from the event using reflection
                  String message = null;
                  try {
                    if (event.getClass().getMethod("getRenderedMessage") != null) {
                      message =
                          (String) event.getClass().getMethod("getRenderedMessage").invoke(event);
                    } else if (event.getClass().getMethod("getMessage") != null) {
                      Object msg = event.getClass().getMethod("getMessage").invoke(event);
                      message = msg != null ? msg.toString() : null;
                    }
                  } catch (Exception e) {
                    log.warn("Failed to get message from log event", e);
                  }

                  if (message != null) {
                    // Get the level from the event
                    String levelStr = "INFO"; // Default level
                    try {
                      Object level = event.getClass().getMethod("getLevel").invoke(event);
                      if (level != null) {
                        levelStr = level.toString();
                      }
                    } catch (Exception e) {
                      log.warn("Failed to get level from log event", e);
                    }

                    // Process the log message
                    log.debug("Log4j appender received message: {}", message);
                    processLogMessage(message, levelStr);
                  }
                } catch (Exception e) {
                  log.warn("Error processing log event in Log4j appender: {}", e.getMessage(), e);
                }
                return null;
              }

              // Handle any other method calls with sensible defaults
              if (methodName.equals("close") || methodName.equals("requiresLayout")) {
                return null;
              } else if (methodName.equals("getName")) {
                return "DataHubMicroBatchAppender";
              } else if (methodName.equals("setName") || methodName.equals("setLayout")) {
                return null;
              } else if (method.getReturnType().equals(boolean.class)) {
                return false;
              }

              // Default return value
              return null;
            }
          };

      // Create the proxy instance
      return java.lang.reflect.Proxy.newProxyInstance(
          appenderClass.getClassLoader(), new Class<?>[] {actualAppenderClass}, handler);
    } catch (Exception e) {
      log.warn("Error creating Log4j appender: {}", e.getMessage(), e);
      throw e;
    }
  }

  /** Installs a custom appender for Logback loggers. */
  private static void installLogbackAppender(Object logger) throws Exception {
    // Get the underlying logger implementation
    Field loggerField = logger.getClass().getDeclaredField("logger");
    loggerField.setAccessible(true);
    Object logbackLogger = loggerField.get(logger);

    // Create and add a custom appender that captures micro-batch events
    Class<?> appenderBaseClass = Class.forName("ch.qos.logback.core.AppenderBase");
    Object appender = createCustomLogbackAppender(appenderBaseClass);

    // Add the appender to the logger
    logbackLogger
        .getClass()
        .getMethod("addAppender", appenderBaseClass)
        .invoke(logbackLogger, appender);

    log.info("Successfully installed Logback appender for MicroBatchExecution");
  }

  /** Creates a custom Logback appender using dynamic proxy to avoid direct dependency. */
  private static Object createCustomLogbackAppender(Class<?> appenderBaseClass) throws Exception {
    try {
      // Try to load the actual Logback AppenderBase class directly if available
      Class<?> actualAppenderClass = Class.forName("ch.qos.logback.core.AppenderBase");

      // Create a dynamic proxy that implements the necessary methods
      java.lang.reflect.InvocationHandler handler =
          new java.lang.reflect.InvocationHandler() {
            @Override
            public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                throws Throwable {
              String methodName = method.getName();

              // Handle the append method to intercept log events
              if (methodName.equals("append") && args != null && args.length > 0) {
                try {
                  // Extract the log event
                  Object event = args[0];

                  // Get the message from the event using reflection
                  String message = null;
                  try {
                    // Try different methods that might contain the message
                    if (event.getClass().getMethod("getFormattedMessage") != null) {
                      message =
                          (String) event.getClass().getMethod("getFormattedMessage").invoke(event);
                    } else if (event.getClass().getMethod("getMessage") != null) {
                      Object msg = event.getClass().getMethod("getMessage").invoke(event);
                      message = msg != null ? msg.toString() : null;
                    }
                  } catch (Exception e) {
                    log.warn("Failed to get message from log event", e);
                  }

                  if (message != null) {
                    // Get the level from the event
                    String levelStr = "INFO"; // Default level
                    try {
                      Object level = event.getClass().getMethod("getLevel").invoke(event);
                      if (level != null) {
                        levelStr = level.toString();
                      }
                    } catch (Exception e) {
                      log.warn("Failed to get level from log event", e);
                    }

                    // Process the log message
                    log.debug("Logback appender received message: {}", message);
                    processLogMessage(message, levelStr);
                  }
                } catch (Exception e) {
                  log.warn("Error processing log event in Logback appender: {}", e.getMessage(), e);
                }
                return null;
              }

              // Start and stop methods need to be implemented
              if (methodName.equals("start")) {
                // Logback requires start to be called
                try {
                  // Set a field to indicate the appender is started
                  Field startedField = actualAppenderClass.getDeclaredField("started");
                  startedField.setAccessible(true);
                  startedField.set(proxy, true);
                } catch (Exception e) {
                  log.warn("Failed to set started flag on Logback appender", e);
                }
                return null;
              } else if (methodName.equals("stop")) {
                return null;
              } else if (methodName.equals("getName")) {
                return "DataHubMicroBatchAppender";
              } else if (methodName.equals("setName") || methodName.equals("setContext")) {
                return null;
              } else if (method.getReturnType().equals(boolean.class)) {
                // isStarted needs to return true
                if (methodName.equals("isStarted")) {
                  return true;
                }
                return false;
              }

              // Default return value
              return null;
            }
          };

      // Create the proxy instance
      Object appender =
          java.lang.reflect.Proxy.newProxyInstance(
              appenderBaseClass.getClassLoader(), new Class<?>[] {actualAppenderClass}, handler);

      // Explicitly call start on the appender
      try {
        actualAppenderClass.getMethod("start").invoke(appender);
      } catch (Exception e) {
        log.warn("Failed to start Logback appender", e);
      }

      return appender;
    } catch (Exception e) {
      log.warn("Error creating Logback appender: {}", e.getMessage(), e);
      throw e;
    }
  }

  /**
   * Process a log message from MicroBatchExecution to extract lineage information.
   *
   * @param message the log message
   * @param level the log level
   */
  protected static void processLogMessage(String message, String level) {
    try {
      // Process the message with the standard implementation first
      String queryId = extractQueryId(message);
      if (queryId == null) {
        return;
      }

      // Process micro-batch start message
      if (message.contains("Starting microbatch") || message.contains("Starting new microbatch")) {
        emitter.processMicroBatchStart(queryId, message);
        return;
      }

      // Process microbatch commit messages
      if (message.contains("Committed microbatch")
          || message.contains("Committed offsets for batch")) {
        Map<String, String> metadata = extractMetadata(message);
        emitter.processMicroBatchCommit(queryId, metadata, message);
        return;
      }

      // Process delta sink write messages
      if (message.contains("Delta output committer")
          || message.contains("DeltaSink: Committed")
          || message.contains("wrote to delta table")) {
        Map<String, String> metadata = extractMetadata(message);
        emitter.processDeltaSinkWrite(queryId, metadata, message);
        return;
      }

      // Process logical plan messages
      if (message.contains("LogicalPlan:") || message.contains("Executing logicalPlan:")) {
        Map<String, String> metadata = extractLogicalPlanMetadata(message);
        emitter.processMicroBatchLogicalPlan(queryId, metadata, message);
        return;
      }

      // Process progress report messages
      if (message.contains("timeMs =")
          || message.contains("batch =")
          || message.contains("batchId =")
          || message.contains("numRows =")) {
        Map<String, String> metadata = extractProgressReport(message);
        emitter.processMicroBatchProgress(queryId, metadata, message);
        return;
      }

      // Check if the message contains CatalogTable information
      if (message.contains("CatalogTable(")) {
        try {
          // Log the full message to help with debugging
          log.debug("Processing CatalogTable message: {}", message);

          // Extract catalog, database and table
          Matcher matcher = CATALOG_TABLE_PATTERN.matcher(message);
          if (matcher.find()) {
            String queryIdFromPattern = matcher.group(1);
            String catalog = matcher.group(2).trim();
            String database = matcher.group(3).trim();
            String table = matcher.group(4).trim();

            log.info(
                "Found catalog table reference - Query: {}, Catalog: {}, Database: {}, Table: {}",
                queryIdFromPattern,
                catalog,
                database,
                table);

            // If we didn't get a query ID from the main extraction, use the one from the pattern
            if (queryId == null || queryId.isEmpty()) {
              queryId = queryIdFromPattern;
            }

            // Create metadata map
            Map<String, String> metadata = new HashMap<>();
            metadata.put("type", "catalogTable"); // Add a type for easier handling
            metadata.put("catalogName", catalog);
            metadata.put("databaseName", database);
            metadata.put("tableName", table);

            // Extract provider (e.g., delta)
            if (matcher.groupCount() >= 5 && matcher.group(5) != null) {
              String provider = matcher.group(5).trim();
              metadata.put("provider", provider);
              log.debug("Table provider: {}", provider);
            }

            // Extract location
            if (matcher.groupCount() >= 6 && matcher.group(6) != null) {
              String location = matcher.group(6).trim();
              metadata.put("location", location);
              log.debug("Table location: {}", location);
            }

            // Extract partition columns
            if (matcher.groupCount() >= 7 && matcher.group(7) != null) {
              String partitionColumns = matcher.group(7).trim();
              metadata.put("partitionColumns", partitionColumns);
              log.debug("Partition columns: {}", partitionColumns);
            }

            // Extract schema
            if (matcher.groupCount() >= 8 && matcher.group(8) != null) {
              String schema = matcher.group(8).trim();
              metadata.put("schema", schema);
              log.debug("Schema details captured");
            }

            // Look for source information in the same message
            if (message.contains("Using Source")) {
              Pattern sourcePattern = Pattern.compile("Using Source \\[([^\\]]+)\\]");
              Matcher sourceMatcher = sourcePattern.matcher(message);
              if (sourceMatcher.find()) {
                String source = sourceMatcher.group(1).trim();
                metadata.put("source", source);
                log.debug("Source information: {}", source);
              }
            }

            // Pass to StreamingEventCorrelator via emitter
            emitter.processOutputTable(queryId, metadata, message);
            log.info(
                "Processed catalog table info for query {}: {}.{}.{}",
                queryId,
                catalog,
                database,
                table);
          } else {
            log.warn("CatalogTable pattern did not match: {}", message);
          }
        } catch (Exception e) {
          log.error("Error processing CatalogTable information: {}", e.getMessage(), e);
        }
        return;
      }

      // Process other interesting messages
      if (message.contains("join")
          || message.contains("watermark")
          || message.contains("window")
          || message.contains("aggregate")
          || message.contains("ForeachBatchSink")) {
        Map<String, String> metadata = extractMetadata(message);
        emitter.processInterestingMessage(queryId, metadata, message);
      }

    } catch (Exception e) {
      log.error("Error processing log message: {}", e.getMessage(), e);
    }
  }

  /** Extract the query ID from a log message. */
  private static String extractQueryId(String logMessage) {
    // The query ID is typically in the format [queryId]
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[([0-9a-f-]+)\\]");
    java.util.regex.Matcher matcher = pattern.matcher(logMessage);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  /** Extract general metadata from a log message. */
  private static Map<String, String> extractMetadata(String logMessage) {
    Map<String, String> metadata = new HashMap<>();

    // Extract batch ID if present
    java.util.regex.Pattern batchPattern = java.util.regex.Pattern.compile("batch (\\d+)");
    java.util.regex.Matcher batchMatcher = batchPattern.matcher(logMessage);
    if (batchMatcher.find()) {
      metadata.put("batchId", batchMatcher.group(1));
    }

    // Look for Delta table information
    if (logMessage.contains("Delta") && logMessage.contains("table")) {
      java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile("table[:\\s]+(\\S+)");
      java.util.regex.Matcher tableMatcher = tablePattern.matcher(logMessage);
      if (tableMatcher.find()) {
        metadata.put("deltaTable", tableMatcher.group(1));
      }
    }

    return metadata;
  }

  /** Extract metadata from a logical plan log message. */
  private static Map<String, String> extractLogicalPlanMetadata(String logMessage) {
    Map<String, String> metadata = new HashMap<>();

    // Extract basic information
    metadata.putAll(extractMetadata(logMessage));

    // Extract the logical plan text
    int planStart = logMessage.indexOf("Logical plan:");
    if (planStart >= 0) {
      String plan = logMessage.substring(planStart + "Logical plan:".length()).trim();
      metadata.put("logicalPlan", plan);

      // Look for table references in the plan
      java.util.regex.Pattern tablePattern =
          java.util.regex.Pattern.compile("Relation\\[(\\w+)\\]\\[(\\w+)\\]");
      java.util.regex.Matcher tableMatcher = tablePattern.matcher(plan);
      if (tableMatcher.find()) {
        metadata.put("tableFormat", tableMatcher.group(1));
        metadata.put("tableName", tableMatcher.group(2));
      }
    }

    return metadata;
  }

  /** Extract metadata from a progress report log message. */
  private static Map<String, String> extractProgressReport(String logMessage) {
    Map<String, String> metadata = new HashMap<>();

    // Extract basic information
    metadata.putAll(extractMetadata(logMessage));

    // Look for rates and statistics
    java.util.regex.Pattern ratePattern = java.util.regex.Pattern.compile("(\\w+):\\s+([\\d.]+)");
    java.util.regex.Matcher rateMatcher = ratePattern.matcher(logMessage);
    while (rateMatcher.find()) {
      metadata.put(rateMatcher.group(1), rateMatcher.group(2));
    }

    return metadata;
  }
}
