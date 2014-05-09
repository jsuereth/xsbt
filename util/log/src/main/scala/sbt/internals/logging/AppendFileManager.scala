package sbt
package internals
package logging

import java.io.{ File, FileWriter, FileOutputStream, OutputStream, OutputStreamWriter, Writer }

import java.util.{
  LinkedHashMap => JLinkedHashMap,
  Map => JMap
}
/**
 * This class represents something which can, in a thread-safe way, manage
 * multiple streams, ensuring that only a limited number are open at one time.
 *
 * Note: Files are looked up by AbsolutePath, so they only share an appender instance
 * if their "canonical" nature is resolved first.
 */
final class AppendFileManager(maxFilesOpen: Int) {
  private case class Writers(binary: OutputStream, text: Writer) {
    def close(): Unit = binary.close()
  }
  /** A cache of file streams that evicts the oldest and closes it. */
  private object lruStreamCache extends JLinkedHashMap[String, Writers](16, 0.75f, true) {
    override protected def removeEldestEntry(eldest: JMap.Entry[String, Writers]): Boolean = {
      val tooLarge = size() > maxFilesOpen
      if (tooLarge) {
        eldest.getValue.close()
        true
      }
      tooLarge
    }
  }
  /**
   * Opens (or finds a previously open) file stream for appending.
   */
  def text(f: File): Writer = writer(f).text
  def binary(f: File): OutputStream = writer(f).binary
  private[this] def writer(f: File): Writers = {
    val key = hash(f)
    lruStreamCache.get(key) match {
      case null => // YAY java
        if (!f.getParentFile.isDirectory) f.getParentFile.mkdirs()
        val writer = newWriters(f)
        lruStreamCache.put(key, writer)
        writer
      case writer => writer
    }
  }

  private[this] def newWriters(f: File): Writers = {
    if (!f.exists) f.setLastModified(System.currentTimeMillis)
    val binary = new FileOutputStream(f, true)
    val text = new OutputStreamWriter(binary, java.nio.charset.Charset.defaultCharset)
    Writers(binary, text)
  }
  /**
   * Closes all open files in this stream manager.
   */
  def close(): Unit = {
    import scala.collection.JavaConverters._
    lruStreamCache.asScala foreach {
      case (key, value) => value.close()
    }
    lruStreamCache.clear()
  }
  private def hash(f: File): String = f.getAbsolutePath
}