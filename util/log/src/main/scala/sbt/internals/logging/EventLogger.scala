package sbt
package internals
package logging

/**
 * Internal implementation of loggers as simple
 *  event producers.
 */
trait EventLogger {
  /** Fires the given log event. */
  def fire(event: LogEvent): Unit
}