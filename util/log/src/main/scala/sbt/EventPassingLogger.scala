package sbt

/**
 * Represents a logger that turns all calls into
 *  events and sends them down the internal logger.
 *
 *  This wraps a pure "LogEvent" API and provides
 *  sbt's "AbstractLogger".
 */
final class EventPassingLogger(
    delegate: sbt.internals.logging.EventLogger,
    override val ansiCodesSupported: Boolean = false) extends AbstractLogger {
  override def setLevel(newLevel: Level.Value): Unit =
    delegate fire new SetLevel(newLevel)

  override def setSuccessEnabled(flag: Boolean): Unit =
    delegate fire new SetSuccess(flag)

  override def setTrace(level: Int): Unit =
    delegate fire new SetTrace(level)

  override def trace(t: => Throwable): Unit =
    delegate fire new Trace(t)
  override def success(message: => String): Unit =
    delegate fire new Success(message)
  override def log(level: Level.Value, message: => String): Unit =
    delegate fire new Log(level, message)
  // TODO - this should send the sequence directly to the other API for performance reasons.
  override def logAll(events: Seq[LogEvent]): Unit =
    events foreach delegate.fire
  override def control(event: ControlEvent.Value, message: => String): Unit =
    delegate fire new ControlEvent(event, message)

  // Note: We do not support this aspect of the API.
  // Here we just try to keep everything as open as possible, as configuration
  // will be in the log-appender downstream.
  override def getLevel: sbt.Level.Value = Level.Debug
  override def getTrace: Int = 0
  override def successEnabled: Boolean = true
}