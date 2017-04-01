package bsnlp

import fs2.{Task, text}

import io.circe.generic.auto._
import io.circe.parser._

import bsnlp.Wikidata._

// Create entity index
// E.g. map all children of https://www.wikidata.org/wiki/Q15284 to LOC
object ExtractEntities extends App {
  import Streams._

  def parseEntry(s: String): Entry = decode[Entry](s).right.get

  val cf = compressedFile("dumps/wikidata-20170329-all.json.bz2")

  val items =
    fs2.io.readInputStream(Task.now(cf), 4096)
      .drop(2) // [\n
      .through(text.utf8Decode)
      .through(text.lines)
      .map(_.init)  // Strip trailing ","
      .map(parseEntry)
      .take(100)
      .runLog
      .unsafeRun()

  println(items.mkString("\n\n"))
}
