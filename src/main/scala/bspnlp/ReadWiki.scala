package bspnlp

import fs2.{Handle, Pull, Task, io, text}
import pl.metastack.metaweb.HtmlHelpers

object ReadWiki extends App {
  import Streams._

  def mapEntities[F[_]](h: Handle[F, Char]): Pull[F, (Char, Char), Any] = ???

  // e.g. [[abc]], [[architektura 32-bitowa|32-bitowy]], [[Unix|UNIX]]-a
  def internalLink(s: String): String =
    if (s.contains(":")) ""  // Delete all links containing colons (i.e. categories, articles in other languages)
    else if (s.contains("|")) s.split("\\|").tail.mkString("|")
    else s

  // e.g. [http://www.example.org link name]
  def externalLink(s: String): String =
    if (s.contains(" ")) s.split(" ").tail.mkString(" ")
    else ""

  def article(input: String): String = {
    val decoded = HtmlHelpers.decodeText(input)

    // Use Task as a workaround to prevent stack overflows
    fs2.Stream.eval(fs2.Task.delay(decoded))
      .pull(between("""<text xml:space="preserve">""", "</text>"))
      .pull(outside("<ref", "</ref>")(_))  // Remove references, like <ref name="..."> and <ref>
      .pull(outside("<!--", "-->")(_))  // Remove comments
      .pull(outside("<source", "</source>")(_))  // Remove source codes
      .pull(outside("<timeline>", "</timeline>")(_))
      .pull(outside("<syntaxhighlight", "</syntaxhighlight>")(_))  // Remove source codes
      .pull(outside("{{", "}}")(_))  // Remove boxes
      .pull(outside("{|", "|}")(_))  // Remove tables
      .pull(mapContent("[[", "]]", internalLink)(_))
      .pull(mapContent("[", "]", externalLink)(_))
      .pull(mapContent("<div", "</div>", identity)(_))
      .pull(mapContent("<small>", "</small>", identity)(_))
      .pull(mapContent("<nowiki>", "</nowiki>", identity)(_))
      .pull(mapContent("<u>", "</u>", identity)(_))  // Underlined
      .pull(mapContent("<sub>", "</sub>", s => s"_{$s}")(_))  // Subscript
      .pull(mapContent("<sup>", "</sup>", s => s"^{$s}")(_))  // Superscript
      .pull(mapContent("'''", "'''", identity)(_))  // Bold
      .pull(mapContent("''", "''", identity)(_))  // Italic
      .runLog
      .unsafeRun
      .mkString
      .trim

    // TODO Replace <br />, <br>
    // TODO Remove all remaining HTML tags, e.g. <center><gallery>... </gallery></center>
    // TODO outside(), mapContent() do not support nesting
  }

  val cf = compressedFile("../plwiki-latest-pages-articles.xml.bz2")
  val pages =
    io.readInputStream[Task](Task.now(cf), 4096)
      .through(text.utf8Decode)
      .pull(between("<page>", "</page>")(_).flatMap(echo))
      .take(100)
      .map(article)
      .runLog
      .unsafeRun()

  println(pages)
}
