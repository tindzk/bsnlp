package bspnlp

import fs2.{Handle, Pull, Task, io, text}
import pl.metastack.metaweb.HtmlHelpers

object ReadWiki extends App {
  import Streams._

  def mapEntities[F[_]](h: Handle[F, Char]): Pull[F, (Char, Char), Any] = ???

  def linkF(s: String): String =
    if (s.contains(":")) ""  // Delete all links containing colons (categories)
    else {
      // e.g. [[architektura 32-bitowa|32-bitowy]], [[Unix|UNIX]]-a
      if (s.contains("|")) s.split("|").last
      else s
    }

  def article(input: String): String = {
    val decoded = HtmlHelpers.decodeText(input)

    // Use Task as a workaround to prevent stack overflows
    val stream = fs2.Stream.eval(fs2.Task.delay(decoded))

    val updated = stream
      .pull(between("""<text xml:space="preserve">""", "</text>"))
      .pull(outside("{{", "}}")(_))  // Remove boxes
      .pull(outside("<ref>", "</ref>")(_))  // Remove references
      .pull(mapLinks(linkF)(_))
      .runLog
      .unsafeRun.mkString

    // TODO Remove bold and italic text (''', '')
    // TODO Map URLs, e.g. [http://www.amigaos.net/ Oficjalna strona systemu AmigaOS 4]

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
