package bspnlp

import fs2.{Handle, Pull, Task, io, text}
import pl.metastack.metaweb.HtmlHelpers

import scala.collection.mutable.ListBuffer

object ReadWiki extends App {
  import Streams._

  def mapEntities[F[_]](h: Handle[F, Char]): Pull[F, (Char, Char), Any] = ???

  // e.g. [[abc]], [[architektura 32-bitowa|32-bitowy]], [[Unix|UNIX]]-a
  // Delete all links containing colons (i.e. categories, articles in other languages)
  def internalLink(s: String): Option[Entity] =
    if (s.contains(":")) None
    else if (!s.contains("|")) Some(Entity(s, s))
    else {
      val split = s.split("\\|")
      Some(Entity(split.tail.mkString("|"), split.head))
    }

  // e.g. [http://www.example.org link name]
  def externalLink(s: String): String =
    if (s.contains(" ")) s.split(" ").tail.mkString(" ")
    else ""

  case class Entity(text: String, base: String)

  def article(input: String): (String, List[Entity]) = {
    val entities = ListBuffer.empty[Entity]

    val decoded = HtmlHelpers.decodeText(input)

    // Use Task as a workaround to prevent stack overflows
    val text = fs2.Stream.eval(fs2.Task.delay(decoded))
      .pull(between("""<text xml:space="preserve">""", "</text>"))
      .pull(outside("<ref", "</ref>"))  // Remove references, like <ref name="..."> and <ref>
      .pull(outside("<!--", "-->"))  // Remove comments
      .pull(outside("<source", "</source>"))  // Remove source codes
      .pull(outside("<timeline>", "</timeline>"))
      .pull(outside("<syntaxhighlight", "</syntaxhighlight>"))  // Remove source codes
      .pull(outside("{{", "}}"))  // Remove boxes
      .pull(outside("{|", "|}"))  // Remove tables
      .pull(mapContent("<div", "</div>", identity))
      .pull(mapContent("<blockquote>", "</blockquote>", identity))
      .pull(mapContent("<small>", "</small>", identity))
      .pull(mapContent("<code>", "</code>", s => s"`$s`"))
      .pull(mapContent("<nowiki>", "</nowiki>", identity))
      .pull(mapContent("<u>", "</u>", identity))  // Underlined
      .pull(mapContent("<sub>", "</sub>", s => s"_{$s}"))  // Subscript
      .pull(mapContent("<sup>", "</sup>", s => s"^{$s}"))  // Superscript
      .pull(mapContent("'''", "'''", identity))  // Bold
      .pull(mapContent("''", "''", identity))  // Italic
      .pull(mapContent("[[", "]]", l => {
        val ent = internalLink(l)
        ent.foreach(entities += _)
        ent.fold("")(_.text)
      }))
      .pull(mapContent("[", "]", externalLink))
      .runLog
      .unsafeRun
      .mkString
      .trim

    // TODO Replace <br />, <br>
    // TODO Remove all remaining HTML tags, e.g. <center><gallery>...</gallery></center>
    // TODO outside(), mapContent() do not support nesting

    (text, entities.toList)
  }

  def articleToChars(article: (String, List[Entity])): (List[(Char, Boolean)]) = {
    val (text, entities) = article

    // TODO This will not work if there are multiple occurrences or the entity text
    // occurs within other words
    val ents = entities.map { ent =>
      val i = text.indexOf(ent.text)
      (i, i + ent.text.length)
    }

    text.toList.zipWithIndex.map { case (c, i) =>
      if (ents.exists { case (l, r) => Range(l, r).contains(i) }) (c, true)
      else (c, false)
    }
  }

  val cf = compressedFile("../plwiki-latest-pages-articles.xml.bz2")
  val pages =
    io.readInputStream(Task.now(cf), 4096)
      .through(text.utf8Decode)
      .pull(between("<page>", "</page>")(_).flatMap(echo))
      .take(5)
      .map(article)
      .map(articleToChars)
      .runLog
      .unsafeRun()

  println(pages)
}
