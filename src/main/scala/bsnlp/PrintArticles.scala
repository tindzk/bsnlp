package bsnlp

import fs2.{Task, io, text}
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp
import org.sweble.wikitext.engine.{PageId, PageTitle, WtEngineImpl}

object PrintArticles extends App {
  import Streams._

  def parse(wikiText: String): (String, List[(String, String)]) = {
    val title     = "title"
    val config    = DefaultConfigEnWp.generate()
    val engine    = new WtEngineImpl(config)
    val pageTitle = PageTitle.make(config, title)
    val pageId    = new PageId(pageTitle, -1)
    val proc      = engine.postprocess(pageId, wikiText, null)
    val plainText = new PlainText(config)
    plainText.go(proc.getPage)

    val t    = plainText.sb.toString
    val text = fs2.Stream.eval(fs2.Task.delay(t))
      .pull(outside("<ref", "</ref>"))  // Remove references, like <ref name="..."> and <ref>
      .runLog
      .unsafeRun
      .mkString
      .trim

    (text, plainText.entities.toList)
  }

  val cf = compressedFile("dumps/plwiki-latest-pages-articles.xml.bz2")

  val pages =
    io.readInputStream(Task.now(cf), 4096)
      .through(text.utf8Decode)
      .pull(between("<text xml:space=\"preserve\">", "</text>")(_).flatMap(echo))
      .drop(25)
      .take(1)
      .map(parse)
      .runLog
      .unsafeRun()

  println(pages)
}
