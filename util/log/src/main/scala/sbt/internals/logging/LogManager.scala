package sbt
package internals
package logging

/**
 * This represents a factory for generating
 *  logger instances that target some kind of backend.
 */
trait LogManager[LoggerId] extends java.io.Closeable {
  /**
   * Construct a new thread-safe logger which will log its events into
   * the desired target.
   */
  def createLogger(id: LoggerId): EventLogger
}
object LogManager {
  /** Factory function to create the default log manager for sbt projects. */
  def apply[LoggerId](name: String, mediator: LogStreamHandler[LoggerId]): LogManager[LoggerId] =
    disruptor(name, mediator)

  //TODO - disruptor specific config options
  def disruptor[LoggerId](name: String, mediator: LogStreamHandler[LoggerId]): LogManager[LoggerId] =
    new DisruptiveLogManager(name, mediator)
}

/**
 * A strategy for how to append logs to streams.
 *
 *  This will be used in a single-threaded context.
 */
private[sbt] trait LogStreamHandler[LoggerId] extends java.io.Closeable {
  /**
   * Consume and do something useful with the next log event.
   *
   *  @param e The log event *and* the target for the log, i.e. which stream/file the info needs to go out.
   *  @param endOfBatch  Represents whether this is the last log event from the current batch of blasted events.
   */
  def handleNext(e: FullLogEvent[LoggerId], endOfBatch: Boolean): Unit
}