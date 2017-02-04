package bspnlp

import java.io.{BufferedInputStream, FileInputStream, InputStream}

import fs2.{Handle, Pull}
import org.apache.commons.compress.compressors.CompressorStreamFactory

object Streams {
  def compressedFile(filePath: String): InputStream = {
    val fin = new FileInputStream(filePath)
    val bis = new BufferedInputStream(fin)
    new CompressorStreamFactory().createCompressorInputStream(bis)
  }

  def dropWhileNoMatch(s: String, find: String): String = {
    var pointerBuf  = 0
    var pointerFind = 0

    while (pointerBuf < s.length && pointerFind < find.length) {
      if (s(pointerBuf) == find(pointerFind)) pointerFind += 1
      else pointerFind = 0
      pointerBuf += 1
    }

    s.drop(pointerBuf - pointerFind)
  }

  // Seek to the end of `needle`
  def seekTo[F[_]](needle: String,
                   buffer: String = ""
                  )(h: Handle[F, String]): Pull[F, String, Handle[F, String]] =
    h.receive1 { case (s, h) =>
      val newBuffer = dropWhileNoMatch(buffer + s, needle)
      if (!newBuffer.startsWith(needle)) seekTo(needle, newBuffer)(h)
      else {
        val after = newBuffer.drop(needle.length)
        if (after.isEmpty) Pull.pure(h)
        else Pull.pure(h.push1(after))
      }
    }

  def echo[F[_]](h: Handle[F, String]): Pull[F, String, Unit] = h.echo

  def readUntil[F[_]](needle: String,
                      buffer: String = ""
                     )(h: Handle[F, String]): Pull[F, String, Handle[F, String]] =
    h.receive1 { case (s, h) =>
      val newBuffer = buffer + s
      if (!newBuffer.contains(needle)) readUntil(needle, newBuffer)(h)
      else {
        val i           = newBuffer.indexOf(needle)
        val after       = newBuffer.take(i)
        val enqueue     = newBuffer.drop(i + needle.length)
        val enqueuePull =
          if (enqueue.isEmpty) Pull.pure(h)
          else                 Pull.pure(h.push1(enqueue))
        if (after.isEmpty) enqueuePull
        else               Pull.output1(after) >> enqueuePull
      }
    }

  def between[F[_]](l: String, r: String)
                   (h: Handle[F, String]): Pull[F, String, Handle[F, String]] =
    seekTo[F](l)(h)
      .flatMap(readUntil[F](r))
      .flatMap(between[F](l, r))

  def outside[F[_]](l: String, r: String)
                   (h: Handle[F, String]): Pull[F, String, Any] =
    readUntil[F](l)(h)
      .flatMap(seekTo[F](r))
      .flatMap(outside[F](l, r))

    // TODO This is inefficient
    /*h.fold("")(_ + _).flatMap { s =>
      println(s"outside($s)")
      if (!s.contains(l) || !s.contains(r)) echo(h)
      else {
      }
    }*/
}
