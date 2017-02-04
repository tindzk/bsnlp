package bspnlp

import org.scalatest.FunSuite

class StreamsTests extends FunSuite {
  import Streams._

  test("dropWhileNoMatch") {
    assert(dropWhileNoMatch("test", "tes") == "test")
    assert(dropWhileNoMatch("t", "tes") == "t")
    assert(dropWhileNoMatch("tes", "tes") == "tes")
    assert(dropWhileNoMatch("test1", "test2") == "")
    assert(dropWhileNoMatch("test1test2", "test2") == "test2")
    assert(dropWhileNoMatch("test1t", "test2") == "t")
    assert(dropWhileNoMatch("1b", "2") == "")
  }

  test("seekTo") {
    assert((fs2.Stream("a", "b") ++ fs2.Stream("b", "c"))
      .pull(seekTo[Nothing]("ab")(_).flatMap(echo)).runLog.right.get == Vector("b", "c"))

    assert((fs2.Stream("a", "b") ++ fs2.Stream("b", "c"))
      .pull(seekTo[Nothing]("bb")(_).flatMap(echo)).runLog.right.get == Vector("c"))

    assert((fs2.Stream("a", "b") ++ fs2.Stream("b", "c"))
      .pull(seekTo[Nothing]("b")(_).flatMap(echo)).runLog.right.get == Vector("b", "c"))

    assert((fs2.Stream("a", "b", "c") ++ fs2.Stream("a", "b", "c", "d", "e"))
      .pull(seekTo[Nothing]("bcd")(_).flatMap(echo)).runLog.right.get == Vector("e"))

    assert((fs2.Stream("a", "b", "c") ++ fs2.Stream("d", "a", "b", "c", "d", "e"))
      .pull(seekTo[Nothing]("bcd")(_).flatMap(echo)).runLog.right.get == Vector("a", "b", "c", "d", "e"))
  }

  test("readUntil") {
    assert((fs2.Stream("a", "{") ++ fs2.Stream("{", "b"))
      .pull(readUntil[Nothing]("{")(_).flatMap(echo)).runLog.right.get == Vector("a", "{", "b"))

    assert((fs2.Stream("a", "{") ++ fs2.Stream("{", "b"))
      .pull(readUntil[Nothing]("{{")(_).flatMap(echo)).runLog.right.get == Vector("a", "b"))

    assert((fs2.Stream("a", "b") ++ fs2.Stream("}"))
      .pull(readUntil[Nothing]("}")(_).flatMap(echo)).runLog.right.get == Vector("ab"))

    assert(fs2.Stream("ab}c").pull(readUntil[Nothing]("}")(_).flatMap(echo))
      .runLog.right.get == Vector("ab", "c"))

    assert(fs2.Stream("ab}").pull(readUntil[Nothing]("}")(_).flatMap(echo))
      .runLog.right.get == Vector("ab"))

    assert(fs2.Stream("}ab").pull(readUntil[Nothing]("}")(_).flatMap(echo))
      .runLog.right.get == Vector("ab"))
  }

  test("between") {
    assert(fs2.Stream("{ab}").pull(between("{", "}")).runLog.right.get == Vector("ab"))

    assert((fs2.Stream("{", "a") ++ fs2.Stream("b", "}")).pull(between("{", "}"))
      .runLog.right.get == Vector("ab"))

    assert((fs2.Stream("{", "a") ++ fs2.Stream("b", "}", "{", "c", "d", "}"))
      .pull(between("{", "}")).runLog.right.get == Vector("ab", "cd"))

    assert((fs2.Stream("{", "a") ++ fs2.Stream("b", "}", "{", "c", "d", "}", "e"))
      .pull(between("{", "}")(_).flatMap(echo)).runLog.right.get == Vector("ab", "cd"))
  }

  test("outside") {
    assert(fs2.Stream("a{test}b").pull(outside("{", "}")(_))
      .runLog.right.get == Vector("a", "b"))

    assert(fs2.Stream("a{test}b{test2}c").pull(outside("{", "}")(_))
      .runLog.right.get == Vector("a", "b", "c"))

    assert(fs2.Stream("{abc}def").pull(outside("{", "}")(_))
      .runLog.right.get == Vector("def"))
  }

  test("outside (2)") {
    // Should not lead to a stack overflow
    assert(fs2.Stream(
      "{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}b"
    ).pull(outside("{{", "}}")(_)).runLog.right.get == Vector("b"))
  }
}
