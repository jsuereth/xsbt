package sbt

import org.scalacheck._
import Arbitrary.{ arbitrary => arb, _ }
import Gen.{ listOfN, oneOf }
import Prop._

object LogGen {
  final val MaxLines = 100
  final val MaxSegments = 10
  /* The following are implicit generators to build up a write sequence.
	* ToLog represents a written segment.  NewLine represents one of the possible
	* newline separators.  A List[ToLog] represents a full line and always includes a
	* final ToLog with a trailing '\n'.  Newline characters are otherwise not present in
	* the `content` of a ToLog instance.*/

  implicit lazy val arbOut: Arbitrary[Output] = Arbitrary(genOutput)
  implicit lazy val arbLog: Arbitrary[ToLog] = Arbitrary(genLog)
  implicit lazy val arbLine: Arbitrary[List[ToLog]] = Arbitrary(genLine)
  implicit lazy val arbNewLine: Arbitrary[NewLine] = Arbitrary(genNewLine)
  implicit lazy val arbLevel: Arbitrary[Level.Value] = Arbitrary(genLevel)

  implicit def genLine(implicit logG: Gen[ToLog]): Gen[List[ToLog]] =
    for (l <- listOf[ToLog](MaxSegments); last <- logG) yield (addNewline(last) :: l.filter(!_.content.isEmpty)).reverse

  implicit def genLog(implicit content: Arbitrary[String], byChar: Arbitrary[Boolean]): Gen[ToLog] =
    for (c <- content.arbitrary; by <- byChar.arbitrary) yield {
      assert(c != null)
      new ToLog(removeNewlines(c), by)
    }

  implicit lazy val genNewLine: Gen[NewLine] =
    for (str <- oneOf("\n", "\r", "\r\n")) yield new NewLine(str)

  implicit lazy val genLevel: Gen[Level.Value] =
    oneOf(Level.values.toSeq)

  implicit lazy val genOutput: Gen[Output] =
    for (ls <- listOf[List[ToLog]](MaxLines); lv <- genLevel) yield new Output(ls, lv)

  def removeNewlines(s: String) = s.replaceAll("""[\n\r]+""", "")
  def addNewline(l: ToLog): ToLog =
    new ToLog(l.content + "\n", l.byCharacter) // \n will be replaced by a random line terminator for all lines

  def listOf[T](max: Int)(implicit content: Arbitrary[T]): Gen[List[T]] =
    Gen.choose(0, max) flatMap { sz => listOfN(sz, content.arbitrary) }
}

/* Helper classes*/

final class Output(val lines: List[List[ToLog]], val level: Level.Value) extends NotNull {
  override def toString =
    "Level: " + level + "\n" + lines.map(_.mkString).mkString("\n")
}
final class NewLine(val str: String) extends NotNull {
  override def toString = Escape(str)
}
final class ToLog(val content: String, val byCharacter: Boolean) extends NotNull {
  def contentOnly = Escape.newline(content, "")
  override def toString = if (content.isEmpty) "" else "ToLog('" + Escape(contentOnly) + "', " + byCharacter + ")"
}
/** Defines some utility methods for escaping unprintable characters.*/
object Escape {
  /** Escapes characters with code less than 20 by printing them as unicode escapes.*/
  def apply(s: String): String =
    {
      val builder = new StringBuilder(s.length)
      for (c <- s) {
        def escaped = pad(c.toInt.toHexString.toUpperCase, 4, '0')
        if (c < 20) builder.append("\\u").append(escaped) else builder.append(c)
      }
      builder.toString
    }
  def pad(s: String, minLength: Int, extra: Char) =
    {
      val diff = minLength - s.length
      if (diff <= 0) s else List.make(diff, extra).mkString("", "", s)
    }
  /** Replaces a \n character at the end of a string `s` with `nl`.*/
  def newline(s: String, nl: String): String =
    if (s.endsWith("\n")) s.substring(0, s.length - 1) + nl else s
}