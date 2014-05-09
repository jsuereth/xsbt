/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package std

import java.io.{ InputStream, IOException, OutputStream, Reader, Writer }
import java.io.{ BufferedInputStream, BufferedOutputStream, BufferedReader, BufferedWriter, PrintWriter }
import java.io.{ Closeable, File, FileInputStream, FileOutputStream, InputStreamReader, OutputStreamWriter }

import Path._

// no longer specific to Tasks, so 'TaskStreams' should be renamed
/**
 * Represents a set of streams associated with a context.
 * In sbt, this is a named set of streams for a particular scoped key.
 * For example, logging for test:compile is by default sent to the "out" stream in the test:compile context.
 */
sealed trait TaskStreams[Key] {
  /** The default stream ID, used when an ID is not provided. */
  def default = outID

  def outID = "out"
  def errorID = "err"

  /**
   * Provides a reader to read text from the stream `sid` for `key`.
   * It is the caller's responsibility to coordinate writing to the stream.
   * That is, no synchronization or ordering is provided and so this method should only be called when writing is complete.
   */
  def readText(key: Key, sid: String = default): BufferedReader

  /**
   * Provides an output stream to read from the stream `sid` for `key`.
   * It is the caller's responsibility to coordinate writing to the stream.
   * That is, no synchronization or ordering is provided and so this method should only be called when writing is complete.
   */
  def readBinary(a: Key, sid: String = default): BufferedInputStream

  final def readText(a: Key, sid: Option[String]): BufferedReader = readText(a, getID(sid))
  final def readBinary(a: Key, sid: Option[String]): BufferedInputStream = readBinary(a, getID(sid))

  def key: Key

  /** Provides a writer for writing text to the stream with the given ID. */
  def text(sid: String = default): PrintWriter

  /** Provides an output stream for writing to the stream with the given ID. */
  def binary(sid: String = default): BufferedOutputStream

  /** A cache directory that is unique to the context of this streams instance.*/
  def cacheDirectory: File

  // default logger
  /** Obtains the default logger. */
  final lazy val log: Logger = log(default)

  /** Creates a Logger that logs to stream with ID `sid`.*/
  def log(sid: String): Logger

  private[this] def getID(s: Option[String]) = s getOrElse default
}
sealed trait ManagedStreams[Key] extends TaskStreams[Key] {
  def open()
  def close()
  def isClosed: Boolean
}

trait Streams[Key] {
  def apply(a: Key): ManagedStreams[Key]
  def use[T](key: Key)(f: TaskStreams[Key] => T): T =
    {
      val s = apply(key)
      s.open()
      try { f(s) } finally { s.close() }
    }
}
trait CloseableStreams[Key] extends Streams[Key] with java.io.Closeable

private[sbt] class LimitedOpenFileStreams[Key](
    logDirectory: Key => File,
    name: Key => String,
    maxAppenders: Int = 500) extends CloseableStreams[Key] {
  import internals.logging._
  /** The id used to denote which log we're writing to. */
  private case class LogId(key: Key, id: String) {
    lazy val file: File = fileFor(key, id)
  }

  private def fileFor(key: Key, id: String): File = logDirectory(key) / id

  private object FileAppender extends internals.logging.LogStreamHandler[LogId] {
    private val fileAppenders = new AppendFileManager(maxAppenders)
    def handleNext(e: FullLogEvent[LogId], endOfBatch: Boolean): Unit = {
      val target = e.source.file
      // TODO - Log into the target file
      writeEntry(e.event, target)
    }
    def close(): Unit = fileAppenders.close()

    private def writeEntry(e: RawLogEvent, target: File): Unit = {
      e match {
        case RawString(msg)  => fileAppenders.text(target).write(msg)
        case RawBytes(msg)   => fileAppenders.binary(target).write(msg)
        case RealLogEvent(e) => writeLogEvent(e, target)
      }
    }
    private def writeLogEvent(e: LogEvent, target: File): Unit = e match {
      case e: Success => printLabeledLine(Level.SuccessLabel, e.msg, fileAppenders.text(target))
      case e: Log     => printLabeledLine(e.level.toString, e.msg, fileAppenders.text(target))
      case e: Trace =>
        val out = fileAppenders.text(target)
        out.write(StackTrace.trimmed(e.exception, 0))
      // TODO - Supressed messages...
      case _ => // ignore control for now.
    }
    // TODO - Colors?
    private def printLabeledLine(label: String, line: String, out: Writer): Unit = {
      out.write("[")
      out.write(label)
      out.write("] ")
      out.write(line)
      // TODO - System end of line...
      out.write("\n")
    }
  }
  private val logManager = LogManager("LimitedOpenFileStreams", FileAppender)

  private class LogBackedManagedStreams(override val key: Key) extends ManagedStreams[Key] {
    private[this] var openedReaders: List[Closeable] = Nil
    private[this] var closed: Boolean = false
    lazy val cacheDirectory: File = {
      val dir = logDirectory(key)
      IO.createDirectory(dir)
      dir
    }
    // TODO - binary/text reading...
    def log(sid: String): Logger =
      new EventPassingLogger(eventLogger(sid))

    def text(sid: String = default): PrintWriter =
      new PrintWriter(new Writer() {
        private val logger = eventLogger(sid)
        def write(buf: Array[Char], start: Int, len: Int): Unit = {
          logger.fire(RawString(new String(buf, start, len)))
        }
        // TODO - Can we implement this?
        def flush(): Unit = ()
        def close(): Unit = ()
      })
    // TODO - Delegate to the eventLogger
    //make(a, sid)(f => new PrintWriter(new DeferredWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), IO.defaultCharset)))))

    def binary(sid: String = default): BufferedOutputStream =
      new BufferedOutputStream(new OutputStream() {
        private val logger = eventLogger(sid)
        // TODO - We never want this called 
        def write(b: Int): Unit = {
          logger.fire(RawBytes(Array(b.toByte)))
        }
        override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
          // IT may not be the most efficient to copy all the time like this, but we have no
          // idea if we can own the underlying bytes array.
          val buf = new Array[Byte](length)
          System.arraycopy(bytes, offset, buf, 0, length)
          logger.fire(RawBytes(buf))
        }

      })
    // TODO - Delegate to the event logger.

    // TODO - Manage binary/text writers separately from logging.
    def readBinary(a: Key, id: String): BufferedInputStream =
      makeReader(a, id)(f => new BufferedInputStream(new FileInputStream(f)))
    def readText(a: Key, id: String): BufferedReader =
      makeReader(a, id)(f => new BufferedReader(new InputStreamReader(new FileInputStream(f), IO.defaultCharset)))

    def makeReader[T <: Closeable](a: Key, sid: String)(f: File => T): T = synchronized {
      // TODO - Some sort of central store for this.
      if (isClosed) sys.error(s"Streams for '${name(a)}' have been closed.")
      val file = fileFor(a, sid)
      IO.touch(file, false)
      val t = f(file)
      openedReaders ::= t
      t
    }

    def eventLogger(sid: String): EventLogger = {
      // TODO - We shoudln't have to touch
      val file = fileFor(key, sid)
      if (!file.getParentFile.isDirectory) file.getParentFile.mkdirs()
      IO.touch(file, false)
      logManager.createLogger(LogId(key, sid))
    }
    // These do nothing as the stream doesn't hold the file anymore.
    def open(): Unit = ()
    def close(): Unit = {
      if (!isClosed) {
        openedReaders foreach closeQuietly
      }
      closed = true
    }
    def isClosed: Boolean = closed
    private[this] val closeQuietly = (c: Closeable) => try { c.close() } catch { case _: IOException => () }
  }

  // Actual public API

  def apply(a: Key): ManagedStreams[Key] =
    new LogBackedManagedStreams(a)
  def close(): Unit = {
    logManager.close()
    FileAppender.close()
  }
}

object Streams {
  private[this] val closeQuietly = (c: Closeable) => try { c.close() } catch { case _: IOException => () }

  def closeable[Key](delegate: Streams[Key]): CloseableStreams[Key] = new CloseableStreams[Key] {
    private[this] val streams = new collection.mutable.HashMap[Key, ManagedStreams[Key]]

    def apply(key: Key): ManagedStreams[Key] =
      synchronized {
        streams.get(key) match {
          case Some(s) if !s.isClosed => s
          case _ =>
            val newS = delegate(key)
            streams.put(key, newS)
            newS
        }
      }

    def close(): Unit =
      synchronized { streams.values.foreach(_.close()); streams.clear() }
  }
  // TODO - Is this ok?
  def apply[Key](taskDirectory: Key => File, name: Key => String, mkLogger: (Key, PrintWriter) => Logger): Streams[Key] =
    new LimitedOpenFileStreams(taskDirectory, name)
  def applyOld[Key](taskDirectory: Key => File, name: Key => String, mkLogger: (Key, PrintWriter) => Logger): Streams[Key] = new Streams[Key] {

    def apply(a: Key): ManagedStreams[Key] = new ManagedStreams[Key] {
      private[this] var opened: List[Closeable] = Nil
      private[this] var closed = false

      def readText(a: Key, sid: String = default): BufferedReader =
        make(a, sid)(f => new BufferedReader(new InputStreamReader(new FileInputStream(f), IO.defaultCharset)))

      def readBinary(a: Key, sid: String = default): BufferedInputStream =
        make(a, sid)(f => new BufferedInputStream(new FileInputStream(f)))

      def text(sid: String = default): PrintWriter =
        make(a, sid)(f => new PrintWriter(new DeferredWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), IO.defaultCharset)))))

      def binary(sid: String = default): BufferedOutputStream =
        make(a, sid)(f => new BufferedOutputStream(new FileOutputStream(f)))

      lazy val cacheDirectory: File = {
        val dir = taskDirectory(a)
        IO.createDirectory(dir)
        dir
      }

      def log(sid: String): Logger = mkLogger(a, text(sid))

      def make[T <: Closeable](a: Key, sid: String)(f: File => T): T = synchronized {
        checkOpen()
        val file = taskDirectory(a) / sid
        IO.touch(file, false)
        val t = f(file)
        opened ::= t
        t
      }

      def key: Key = a
      def open() {}
      def isClosed: Boolean = synchronized { closed }

      def close(): Unit = synchronized {
        if (!closed) {
          closed = true
          opened foreach closeQuietly
        }
      }
      def checkOpen(): Unit = synchronized {
        if (closed) error("Streams for '" + name(a) + "' have been closed.")
      }
    }
  }
}