package sbt
package internals
package logging

sealed trait RawLogEvent
final case class RealLogEvent(e: LogEvent) extends RawLogEvent
final case class RawString(in: String) extends RawLogEvent
final case class RawBytes(in: Array[Byte]) extends RawLogEvent

/**
 * Internal implementation of loggers as simple
 *  event producers.
 */
trait EventLogger {
  /** Fires the given log event. */
  def fire(event: RawLogEvent): Unit
}