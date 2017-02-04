package bspnlp

import fs2.{Handle, Pull, Task, io, text}
import pl.metastack.metaweb.HtmlHelpers

object ReadWiki extends App {
  import Streams._

  def mapEntities[F[_]](h: Handle[F, Char]): Pull[F, (Char, Char), Any] = ???

  def article(input: String): String = {
    val decoded = HtmlHelpers.decodeText(input)
    val stream = fs2.Stream(decoded)
    val updated = stream
      .pull(between("""<text xml:space="preserve">""", "</text>"))
      .pull(outside("{{", "}}")(_))  // Remove boxes
      .runLog
      .right.get.mkString

    // Remove all [[...]] containing colon
    // Remove ''awk''
    // Remove <ref>...</ref>

    // [[tablica asocjacyjna|tablice asocjacyjne]]
    // [[Unix|UNIX]]-a
    updated
  }

  val cf = compressedFile("../plwiki-latest-pages-articles.xml.bz2")
  val pages =
    io.readInputStream[Task](Task.now(cf), 4096)
      .through(text.utf8Decode)
      .pull(between("<page>", "</page>")(_).flatMap(echo))
      .drop(9).take(1)
      .map(article)
      .runLog
      .unsafeRun()

  println(pages(0))
}
