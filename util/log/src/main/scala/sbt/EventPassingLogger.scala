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

  private def fire(e: LogEvent): Unit =
    delegate fire (internals.logging.RealLogEvent(e))
  override def setLevel(newLevel: Level.Value): Unit =
    fire(new SetLevel(newLevel))

  override def setSuccessEnabled(flag: Boolean): Unit =
    fire(new SetSuccess(flag))

  override def setTrace(level: Int): Unit =
    fire(new SetTrace(level))

  override def trace(t: => Throwable): Unit =
    fire(new Trace(t))
  override def success(message: => String): Unit =
    fire(new Success(message))
  override def log(level: Level.Value, message: => String): Unit =
    fire(new Log(level, message))
  // TODO - this should send the sequence directly to the other API for performance reasons.
  override def logAll(events: Seq[LogEvent]): Unit =
    events foreach fire
  override def control(event: ControlEvent.Value, message: => String): Unit =
    fire(new ControlEvent(event, message))

  // Note: We do not support this aspect of the API.
  // Here we just try to keep everything as open as possible, as configuration
  // will be in the log-appender downstream.
  override def getLevel: sbt.Level.Value = Level.Debug
  override def getTrace: Int = 0
  override def successEnabled: Boolean = true
}