package bsnlp

import java.nio.file.Paths

import io.circe.syntax._
import fs2.{Task, io, text}

object ConvertArticles extends App {
  import Streams._
  import Wiki._

  val cf = compressedFile("../plwiki-latest-pages-articles.xml.bz2")
  val out = Paths.get("data-pl.json")

  if (out.toFile.exists()) out.toFile.delete()

  val pages =
    io.readInputStream(Task.now(cf), 4096)
      .through(text.utf8Decode)
      .pull(between("<page>", "</page>")(_).flatMap(echo))
      .take(1000)
      .map(article)
      .map(articleToChars)
      .fold(List.empty[(Char, Boolean)])(_ ++ _)
      .map(_.asJson.noSpaces)
      .through(text.utf8Encode)
      .through(io.file.writeAll(out))
      .run
      .unsafeRun()
}
