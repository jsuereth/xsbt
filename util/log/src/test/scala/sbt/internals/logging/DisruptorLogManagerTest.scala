package sbt
package internals
package logging

import org.scalacheck._
import Arbitrary.{ arbitrary => arb, _ }
import Gen.{ listOfN, oneOf }
import Prop._
import LogGen._

object DisruptorLogManagerTest extends Properties("DisruptorLogManager") {
  /* Tests that content written through a LoggerWriter is properly passed to the underlying Logger.
	* Each line, determined by the specified newline separator, must be logged at the correct logging level. */
  property("properly logged, one thread") = forAll { (lines: Seq[LogLine]) =>
    object Appender extends LogStreamHandler[String] {
      val recorded: collection.mutable.ListBuffer[LogLine] =
        collection.mutable.ListBuffer.empty
      def close(): Unit = ()
      def handleNext(e: FullLogEvent[String], endOfBatch: Boolean): Unit = {
        recorded.append(disentangle(e.event))
      }
    }
    val logManager = LogManager("properlyLogged", Appender)
    val logger = new EventPassingLogger(logManager.createLogger("test"))
    val events =
      for {
        LogLine(level, line) <- lines
      } yield new Log(level, line)
    // Feed to the logger
    logger.logAll(events)
    logManager.close() // This will block until all active events are handled.
    val diffs =
      for {
        (r, orig) <- Appender.recorded zip lines
      } yield (r, orig, r == orig)
    val diffString =
      diffs map {
        case (l, r, d) => s"----${d}----\nFound   : [${l}]\nExpected: [${r}]"
      } mkString "\n"
    val same = diffs.forall(_._3)
    s"Recorded vs Original:\n${diffString}" |: (same)
  }
  def disentangle(e: LogEvent): LogLine = e match {
    case e: Log => LogLine(e.level, e.msg)
    case _      => sys.error("Invalid log event: " + e)
  }

  case class LogLine(level: Level.Value, line: String)

  implicit lazy val genLogLine: Gen[LogLine] =
    for {
      level <- genLevel
      line <- genLine
    } yield LogLine(level, line map (_.contentOnly) mkString "")
  implicit lazy val arbLogLine = Arbitrary(genLogLine)
  implicit lazy val genLogLines: Gen[Seq[LogLine]] = listOf[LogLine](MaxSegments)
}