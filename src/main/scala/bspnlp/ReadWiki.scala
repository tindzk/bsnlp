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

    val updated2 = fs2.Stream(updated)
      .pull(outside("<ref>", "</ref>")(_))  // Remove references
      .runLog
      .right.get.mkString

    def linkF(s: String): String =
      if (s.contains(":")) ""  // Delete all links containing colons (categories)
      else {
        // e.g. [[architektura 32-bitowa|32-bitowy]], [[Unix|UNIX]]-a
        if (s.contains("|")) s.split("|").last
        else s
      }

    val updated3 = fs2.Stream(updated2)
      .pull(mapLinks(linkF)(_))
      .runLog
      .right.get.mkString

    // TODO Remove bold and italic text (''', '')
    // TODO Map URLs, e.g. [http://www.amigaos.net/ Oficjalna strona systemu AmigaOS 4]

    updated3
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
