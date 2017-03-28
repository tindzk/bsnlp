package bsnlp

import java.nio.file.Paths

import io.circe.syntax._
import fs2.{Task, io, text}

object ConvertArticles extends App {
  import Wiki._
  import Streams._

  val cf = compressedFile("../plwiki-latest-pages-articles.xml.bz2")
  val out = Paths.get("data-pl.json")

  if (out.toFile.exists()) out.toFile.delete()

  val pages =
    io.readInputStream(Task.now(cf), 4096)
      .through(text.utf8Decode)
      .pull(between("<page>", "</page>")(_).flatMap(echo))
      .take(10000)
      .map(article)
      .map(articleToChars)
      .map { case (text, entities) => (text, entities.map(if (_) 1 else 0)) }
      .fold(List.empty[(String, List[Int])])(_ :+ _)
      .map(_.asJson.noSpaces)
      .through(text.utf8Encode)
      .through(io.file.writeAll(out))
      .run
      .unsafeRun()
}
