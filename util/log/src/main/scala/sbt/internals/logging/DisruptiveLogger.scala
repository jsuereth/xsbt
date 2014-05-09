package sbt
package internals
package logging

import com.lmax.disruptor.{
  RingBuffer,
  EventFactory,
  PhasedBackoffWaitStrategy,
  BlockingWaitStrategy,
  EventHandler
}
import com.lmax.disruptor.dsl.{
  Disruptor,
  ProducerType
}
import java.util.concurrent.{
  TimeUnit,
  Executors
}
import java.util.concurrent.ThreadFactory

/**
 * Represents a log event cell in a ring buffer.
 *  Here we insert/remove data.
 */
private[logging] class LogEventCell[LoggerId] {

  private var eventOpt: Option[FullLogEvent[LoggerId]] = None

  // Here we assume there is an event in a cell.
  def get: FullLogEvent[LoggerId] = eventOpt.get
  def set(e: FullLogEvent[LoggerId]): Unit = {
    eventOpt = Some(e)
  }

}
/** Represents the full logging information we send over our event queues. */
private[sbt] case class FullLogEvent[LogTarget](source: LogTarget, event: RawLogEvent)

/**
 * Internal implementation of loggers as simple event producers.
 *
 * TODO - Better log event, possibly including the "source"
 */
private[logging] class DisruptiveLogger[LoggerId](source: LoggerId, buffer: RingBuffer[LogEventCell[LoggerId]])
    extends EventLogger {
  /** Fires the given log event. */
  def fire(event: RawLogEvent): Unit =
    pushEvent(FullLogEvent(source, event))

  private def pushEvent(event: FullLogEvent[LoggerId]): Boolean = {
    // Note: we push any back-off strategy onto the disruptor lib
    val idx = buffer.next()
    try buffer.get(idx).set(event)
    finally buffer.publish(idx)
    true
  }
}

/**
 * A manager for creating loggers which feed their events through a disruptor into
 *  a single appender thread, in control of all back-end files.
 */
private[logging] final class DisruptiveLogManager[LoggerId](name: String, mediator: LogStreamHandler[LoggerId]) extends LogManager[LoggerId] {

  // TODO - All this setup should be configurable...
  private val waitStrategy =
    new PhasedBackoffWaitStrategy(
      10, // spin timeout
      100, // yield timeout
      TimeUnit.NANOSECONDS,
      new BlockingWaitStrategy() // Block when done.
    )
  private object cellFactory extends EventFactory[LogEventCell[LoggerId]] {
    def newInstance: LogEventCell[LoggerId] = new LogEventCell
  }
  private val ringSize = 2048
  private object namedThreadFactory extends ThreadFactory {
    override def newThread(r: Runnable): Thread =
      new Thread(r, s"log-appender-${name}")
  }
  private val executor = Executors.newSingleThreadExecutor(namedThreadFactory)
  // Construct a disruptor (pattern) which will allow us to concurrently fire log events
  // and handle them in a central location.
  private val disruptor = new Disruptor(
    cellFactory,
    ringSize,
    executor,
    ProducerType.MULTI,
    waitStrategy
  )
  disruptor.handleEventsWith(new EventHandler[LogEventCell[LoggerId]] {
    def onEvent(event: LogEventCell[LoggerId], sequence: Long, endOfBatch: Boolean): Unit = {
      mediator.handleNext(event.get, endOfBatch)
    }
  })
  // TODO - do this here?
  disruptor.start()

  // The buffer used to publish logs.
  private[internals] val buffer = disruptor.getRingBuffer

  // Public API which allows users to create loggers.
  final def createLogger(id: LoggerId): EventLogger =
    new DisruptiveLogger(id, buffer)

  /** Closes all the resources we hold open*/
  final def close(): Unit = {
    disruptor.shutdown()
    executor.shutdown()
  }

}