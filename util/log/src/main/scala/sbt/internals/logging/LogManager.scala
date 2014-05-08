package sbt
package internals
package logging

/**
 * This represents a factory for generating
 *  logger instances that target some kind of backend.
 */
trait LogManager[LogTarget] extends java.io.Closeable {
  /**
   * Construct a new thread-safe logger which will log its events into
   * the desired target.
   */
  def createLogger(target: LogTarget): EventLogger
}
object LogManager {
  /** Factory function to create the default log manager for sbt projects. */
  def apply[LogTarget](name: String, appender: LogAppender[LogTarget]): LogManager[LogTarget] =
    disruptor(name, appender)

  //TODO - disruptor specific config options
  def disruptor[LogTarget](name: String, appender: LogAppender[LogTarget]): LogManager[LogTarget] =
    new DisruptiveLogManager(name, appender)
}

/**
 * A strategy for how to append logs to streams.
 *
 *  This will be used in a single-threaded context.
 */
private[sbt] trait LogAppender[LogTarget] extends java.io.Closeable {
  /**
   * Consume and do something useful with the next log event.
   *
   *  @param e The log event *and* the target for the log, i.e. which stream/file the info needs to go out.
   *  @param endOfBatch  Represents whether this is the last log event from the current batch of blasted events.
   */
  def handleNext(e: FullLogEvent[LogTarget], endOfBatch: Boolean): Unit
}