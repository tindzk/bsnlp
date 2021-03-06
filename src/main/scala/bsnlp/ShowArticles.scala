package bsnlp

import fs2.{Task, io, text}

object ShowArticles extends App {
  import Streams._
  import Wiki._

  val cf = compressedFile("dumps/plwiki-latest-pages-articles.xml.bz2")
  val pages =
    io.readInputStream(Task.now(cf), 4096)
      .through(text.utf8Decode)
      .pull(between("<page>", "</page>")(_).flatMap(echo))
      .drop(25)
      .take(1)
      .map(article)
      .runLog
      .unsafeRun()
      .mkString("\n\n")

  println(pages)
}
