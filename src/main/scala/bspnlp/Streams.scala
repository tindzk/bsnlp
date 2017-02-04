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

  def seekToMap[F[_]](needle: String,
                      f: String => String,
                      buffer: String = ""
                     )(h: Handle[F, String]): Pull[F, String, Handle[F, String]] =
    h.receive1 { case (s, h) =>
      val newBuffer = buffer + s
      val position = newBuffer.indexOf(needle)
      if (position == -1) seekToMap(needle, f, newBuffer)(h)
      else {
        val str = f(newBuffer.take(position)) +
          newBuffer.drop(position + needle.length)
        val newH = if (str.isEmpty) h else h.push1(str)
        Pull.pure(newH)
      }
    }

  def echo[F[_]](h: Handle[F, String]): Pull[F, String, Unit] = h.echo

  def readUntilResidual[F[_]](needle: String,
                              buffer: String = ""
                             )(h: Handle[F, String]): Pull[F, String, Either[Handle[F, String], Handle[F, String]]] =
    h.receive1Option {
      case None         => Pull.pure(Right(h.push1(buffer)))
      case Some((s, h)) =>
        val newBuffer = buffer + s
        if (!newBuffer.contains(needle)) readUntilResidual(needle, newBuffer)(h)
        else {
          val i           = newBuffer.indexOf(needle)
          val after       = newBuffer.take(i)
          val enqueue     = newBuffer.drop(i + needle.length)
          val enqueuePull =
            if (enqueue.isEmpty) Pull.pure(Left(h))
            else                 Pull.pure(Left(h.push1(enqueue)))
          if (after.isEmpty) enqueuePull
          else               Pull.output1(after) >> enqueuePull
        }
    }

  def readUntil[F[_]](needle: String)
                     (h: Handle[F, String]): Pull[F, String, Handle[F, String]] =
    readUntilResidual(needle)(h).flatMap {
      case Left(h)  => Pull.pure(h)
      case Right(_) => Pull.done
    }

  def between[F[_]](l: String, r: String)
                   (h: Handle[F, String]): Pull[F, String, Handle[F, String]] =
    seekTo[F](l)(h)
      .flatMap(readUntil[F](r))
      .flatMap(between[F](l, r))

  def outside[F[_]](l: String, r: String)
                   (h: Handle[F, String]): Pull[F, String, Any] = {
    def f(h: Handle[F, String]): Pull[F, String, Handle[F, String]] =
      readUntilResidual[F](l)(h).flatMap {
        case Left(h)  => seekTo[F](r)(h).flatMap(f)
        case Right(s) => fs2.Pull.pure(s)
      }

    f(h).flatMap(echo[F])
  }

  def mapLinks[F[_]](f: String => String)
                    (h: Handle[F, String]): Pull[F, String, Any] = {
    def f2(h: Handle[F, String]): Pull[F, String, Handle[F, String]] =
      readUntilResidual[F]("[[")(h).flatMap {
        case Left(h)  => seekToMap[F]("]]", f)(h).flatMap(f2)
        case Right(s) => fs2.Pull.pure(s)
      }

    f2(h).flatMap(echo[F])
  }
}
