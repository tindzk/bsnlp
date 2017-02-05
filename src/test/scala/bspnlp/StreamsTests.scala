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
      "1{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}{{aa}}2"
    ).pull(outside("{{", "}}")(_)).runLog.right.get == Vector("1", "2"))
  }

  test("mapLinks") {
    assert(fs2.Stream("[[test]]").pull(mapLinks(_ => "")).runLog.right.get == Vector(""))
    assert(fs2.Stream("a[[test]]b").pull(mapLinks(_ => "")).runLog.right.get == Vector("a", "b"))
    assert(fs2.Stream("a[[test]]b").pull(mapLinks(_ + "2")).runLog.right.get == Vector("a", "test2b"))
    assert(fs2.Stream("a[[1]][[2]][[3]]b").pull(mapLinks(_ + "a")).runLog.right.get == Vector("a", "1a", "2a", "3ab"))
  }

  test("mapLinks (2)") {
    val s =
      """
        |'''AmigaOS''' – [[system operacyjny]] opracowany przez firmę [[Commodore International]] dla produkowanych przez nią komputerów [[Amiga]]. Wersja 1.0 została wydana w [[1985 w informatyce|1985]] roku, wraz z premierą komputera [[Amiga 1000]].
        |
        |== Charakterystyka ==
        |System od początku [[architektura 32-bitowa|32-bitowy]], napisany został dla procesora [[M68000|Motorola 68000]]. Obsługiwane procesory to: [[MC68000]], [[MC68010]], [[MC68020]], [[MC68030]], [[MC68040]], [[MC68060]]. Systemy w wersjach 3.x obsługują również procesory [[PowerPC]] znane także jako PPC, dzięki podsystemom WarpOS albo PowerUP. System pracuje nadal na M68x00, istnieje jednak możliwość uruchamiania programów napisanych dla PPC. Konstrukcja i oprogramowanie kart procesorowych umożliwia jednoczesną pracę obu procesorów, przy czym PPC jest wykorzystywany jako koprocesor. W krótkim czasie od pojawienia się kart z układami PPC powstał projekt [[MorphOS]] – mający na celu przeniesienie systemu Amiga OS na architekturę PPC. Pracę programistów utrudniał brak dokumentacji – kody źródłowe systemu v3.0/3.1 stały się niedostępne krótko po upadku firmy Commodore. Istnieje także otwarta implementacja systemu AmigaOS pod nazwą [[AROS Research Operating System|AROS]]. Dostępna jest ona między innymi na platformę [[x86]].
        |
        |Obecna wersja AmigaOS 4.1 została przepisana całkowicie dla procesorów PowerPC i działa na komputerach [[AmigaOne]], micro A1, SAM440ep, SAM440 Flex, [[Pegasos]] II. Oprogramowanie, które zostało napisane dla klasycznych Amig wyposażonych w procesory serii MC68x może być uruchamiane dzięki dwóm wbudowanym w system emulatorom: interpretowanemu, zapewniającemu wysoką zgodność z oryginalnymi układami sprzętowymi oraz JIT o nazwie Petunia zapewniającemu dużą prędkość emulacji.
        |
        |[[Plik:AmigaOS 3 and clones.svg|thumb|200px|AmigaOS i jego klony]]
        |[[jądro systemu operacyjnego|Jądro systemu]] charakteryzowało się [[wielozadaniowość|wielozadaniowością]], zaawansowanymi możliwościami graficznymi, ściśle związanymi z budową komputerów [[Amiga]], a także niskim czasem reakcji, dzięki czemu znalazł on miejsce w zastosowaniach [[system operacyjny czasu rzeczywistego|real-time]], chociaż nie był do nich projektowany. Wadą był brak ''ochrony [[pamięć komputerowa|pamięci]]''. Zgodność ze standardem [[POSIX]] oraz [[X Window System]] można uzyskać przy użyciu zewnętrznych [[biblioteka (informatyka)|bibliotek]].
        |
        |System oferował nowoczesny [[interfejs graficzny|system graficzny]], w którym rolę [[Powłoka systemowa|powłoki]] pełnił [[Workbench]]. Istnieją też niezależne powłoki tekstowe, działające w oknie [[Interfejs graficzny|trybu graficznego]]. Charakterystyczną cechą układów graficznych i samego systemu był brak czysto tekstowych trybów graficznych. System już w pierwszej fazie startu oferował graficzny interfejs.
        |
        |Standardowymi [[System plików|systemami plików]] są [[OFS]] (Old File System – kickstart w wersji poniżej 2.0) i [[Amiga Fast File System|FFS]] (Fast File System). Dla tego systemu powstały również alternatywne systemy plików: [[MUFS]] (Multi User File System), [[Profesional File System|PFS]] (Profesional File System), [[SFS]] (Smart File System), [[budowa modułowa]] umożliwiała prostą i bezproblemową instalację dodatkowych systemów plików np.: [[ISO 9660]], [[File Allocation Table|FAT]], [[FAT32]], [[HFS|MacFS]] itd.
        |
        |[[Kickstart]] – część systemu zawarta w pamięci [[ROM]] lub na [[Dysk twardy|twardym dysku]] komputerów [[Amiga]], zawiera jądro systemu oraz biblioteki potrzebne do uruchomienia systemu.
        |
        |=== AmigaOS 4.0 ===
        |Czwarta generacja [[system operacyjny|systemu operacyjnego]] [[Amiga|Amigi]] – AmigaOS. Prace nad systemem rozpoczęła firma [[Hyperion Entertainment]] na mocy podpisanej [[1 listopada]] 2001 r. umowy z firmą [[Amiga Inc]]. W czerwcu 2004 r. ukazała się pierwsza publiczna wersja systemu nazwana "'''AmigaOS 4.0 Developer Pre-release'''". Kolejne aktualizacje AmigaOS 4.0 Pre-release ukazały się w październiku 2004 r. (pierwsza aktualizacja), w czerwcu 2005 r. (trzecia aktualizacja),  luty 2006 r. (czwarta aktualizacja). [[24 grudnia]] 2006 r. ukazała się finalna wersja '''AmigaOS 4.0''' dla komputerów [[Amiga One]] i [[micro Amiga One]]. W lipcu 2007 r. ukazała się kolejna aktualizacja AmigaOS 4.0. Wersja systemu na klasyczne Amigi wyposażone w karty z procesorem PowerPC ukazała się dnia 30.11.2007 (pierwszy pokaz tej wersji na AmiWest 21.10.2007).
        |
        |[[Kategoria:Amiga]]
        |[[Kategoria:Systemy operacyjne]]
      """.stripMargin

    // TODO Causes stack overflow
    // assert(fs2.Stream(s).pull(mapLinks(_ => "")).runLog.right.get.nonEmpty)

    assert(fs2.Stream.eval(fs2.Task.delay(s)).pull(mapLinks(_ => "")).runLog.unsafeRun.nonEmpty)
  }
}
