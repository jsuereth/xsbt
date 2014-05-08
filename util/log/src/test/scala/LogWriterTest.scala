/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah */

package sbt

import org.scalacheck._
import Arbitrary.{ arbitrary => arb, _ }
import Gen.{ listOfN, oneOf }
import Prop._
import LogGen._

import java.io.Writer

object LogWriterTest extends Properties("Log Writer") {

  /* Tests that content written through a LoggerWriter is properly passed to the underlying Logger.
	* Each line, determined by the specified newline separator, must be logged at the correct logging level. */
  property("properly logged") = forAll { (output: Output, newLine: NewLine) =>
    import output.{ lines, level }
    val log = new RecordingLogger
    val writer = new LoggerWriter(log, Some(level), newLine.str)
    logLines(writer, lines, newLine.str)
    val events = log.getEvents
    ("Recorded:\n" + events.map(show).mkString("\n")) |:
      check(toLines(lines), events, level)
  }

  /**
   * Displays a LogEvent in a useful format for debugging.  In particular, we are only interested in `Log` types
   * and non-printable characters should be escaped
   */
  def show(event: LogEvent): String =
    event match {
      case l: Log => "Log('" + Escape(l.msg) + "', " + l.level + ")"
      case _      => "Not Log"
    }
  /**
   * Writes the given lines to the Writer.  `lines` is taken to be a list of lines, which are
   * represented as separately written segments (ToLog instances).  ToLog.`byCharacter`
   * indicates whether to write the segment by character (true) or all at once (false)
   */
  def logLines(writer: Writer, lines: List[List[ToLog]], newLine: String) {
    for (line <- lines; section <- line) {
      val content = section.content
      val normalized = Escape.newline(content, newLine)
      if (section.byCharacter)
        normalized.foreach { c => writer.write(c.toInt) }
      else
        writer.write(normalized)
    }
    writer.flush()
  }

  /** Converts the given lines in segments to lines as Strings for checking the results of the test.*/
  def toLines(lines: List[List[ToLog]]): List[String] =
    lines.map(_.map(_.contentOnly).mkString)
  /** Checks that the expected `lines` were recorded as `events` at level `Lvl`.*/
  def check(lines: List[String], events: List[LogEvent], Lvl: Level.Value): Boolean =
    (lines zip events) forall {
      case (line, log: Log) => log.level == Lvl && line == log.msg
      case _                => false
    }
}

/* Helper classes*/
/** Records logging events for later retrieval.*/
final class RecordingLogger extends BasicLogger {
  private var events: List[LogEvent] = Nil

  def getEvents = events.reverse

  override def ansiCodesSupported = true
  def trace(t: => Throwable) { events ::= new Trace(t) }
  def log(level: Level.Value, message: => String) { events ::= new Log(level, message) }
  def success(message: => String) { events ::= new Success(message) }
  def logAll(es: Seq[LogEvent]) { events :::= es.toList }
  def control(event: ControlEvent.Value, message: => String) { events ::= new ControlEvent(event, message) }

}