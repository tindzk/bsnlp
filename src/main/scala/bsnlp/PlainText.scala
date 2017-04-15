package bsnlp

/**
  * Copyright 2011 The Open Source Research Group,
  *                University of Erlangen-Nürnberg
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

import java.util.regex.Pattern

import de.fau.cs.osr.ptk.common.AstVisitor
import de.fau.cs.osr.utils.StringUtils.strrep
import org.sweble.wikitext.engine.PageTitle
import org.sweble.wikitext.engine.config.WikiConfig
import org.sweble.wikitext.engine.nodes.EngPage
import org.sweble.wikitext.parser.nodes._
import org.sweble.wikitext.parser.parser.LinkTargetException

import scala.collection.mutable.ArrayBuffer

object PlainText {
  val WordSplit = Pattern.compile("\\s+")
}

class PlainText(private val config: WikiConfig) extends AstVisitor[WtNode] {
  var sb = new StringBuilder()
  val line = new StringBuilder()

  /** True once we are no longer at the beginning of the document */
  var pastBeginning = false
  var needNewlines = 0
  var needSpace = false
  var noWrap = false
  val entities = ArrayBuffer.empty[(String, String)]

  /** This method is called by go() after visitation has finished */
  protected override def after(node: WtNode, result: AnyRef): AnyRef = {
    finishLine()
    null
  }

  /** Fallback for all nodes that are not explicitly handled below */
  def visit(n: WtNode): Unit = {
    write("<")
    write(n.getNodeName)
    write(" />")
  }

  def visit(n: WtTable): Unit = {}
  def visit(n: WtNodeList): Unit = iterate(n)
  def visit(e: WtUnorderedList): Unit = iterate(e)
  def visit(e: WtOrderedList): Unit = iterate(e)
  def visit(item: WtListItem): Unit = {
    wantNewLine(1)
    write("* ")
    iterate(item)
  }

  def visit(p: EngPage): Unit = iterate(p)
  def visit(text: WtText): Unit = write(text.getContent)
  def visit(w: WtWhitespace): Unit = write(" ")
  def visit(b: WtBold): Unit = iterate(b)
  def visit(i: WtItalics): Unit = iterate(i)
  def visit(cr: WtXmlCharRef): Unit = write(Character.toChars(cr.getCodePoint))

  def visit(er: WtXmlEntityRef): Unit =
    er.getResolved match {
      case null => write('&' + er.getName + ';')
      case ch   => write(ch)
    }

  def visit(wtUrl: WtUrl): Unit = {
    if (!wtUrl.getProtocol.isEmpty) {
      write(wtUrl.getProtocol)
      write(":")
    }

    write(wtUrl.getPath)
  }

  def visit(link: WtExternalLink): Unit = iterate(link.getTitle)

  def getText(contentNode: WtContentNode): String =
    contentNode.get(0).asInstanceOf[WtText].getContent

  def visit(link: WtInternalLink): Unit = {
    try {
      if (link.getTarget.isResolved) {
        val page = PageTitle.make(config, link.getTarget.getAsString)
        if (page.getNamespace == config.getNamespace("Category")) return
      }
    } catch { case _: LinkTargetException =>

    }

    // Most likely an image or a Wikimedia link (category)
    if (!getText(link.getTarget).contains(":")) {
      if (link.hasTitle)
        entities.append((getText(link.getTitle), getText(link.getTarget)))
      else
        entities.append((getText(link.getTarget), getText(link.getTarget)))

      write(link.getPrefix)
      if (link.hasTitle) iterate(link.getTitle) else iterate(link.getTarget)
      write(link.getPostfix)
    }
  }

  def visit(s: WtSection): Unit = {
    finishLine()

    val saveSb = sb
    val saveNoWrap = noWrap
    sb = new StringBuilder()
    noWrap = true
    iterate(s.getHeading)
    finishLine()
    val title = sb.toString.trim()
    sb = saveSb

    wantNewLine(2)
    write((0 until s.getLevel).map(_ => '#').mkString + " " + title)
    wantNewLine(1)

    noWrap = saveNoWrap
    iterate(s.getBody)
  }

  def visit(p: WtParagraph): Unit = {
    iterate(p)
    wantNewLine(2)
  }

  def visit(hr: WtHorizontalRule): Unit = {
    wantNewLine(1)
    write("---")
    wantNewLine(2)
  }

  def visit(e: WtXmlElement): Unit =
    if (e.getName.equalsIgnoreCase("br")) wantNewLine(1) else iterate(e.getBody)

  // Hide
  def visit(n: WtImageLink): Unit = {}
  def visit(n: WtIllegalCodePoint): Unit = {}
  def visit(n: WtXmlComment): Unit = {}
  def visit(n: WtTemplate): Unit = {}
  def visit(n: WtTemplateArgument): Unit = {}
  def visit(n: WtTemplateParameter): Unit = {}
  def visit(n: WtTagExtension): Unit = {}
  def visit(n: WtPageSwitch): Unit = {}

  def wantNewLine(num: Int): Unit =
    if (pastBeginning && num > needNewlines) needNewlines = num

  def wantSpace(): Unit =
    if (pastBeginning) needSpace = true

  def finishLine(): Unit = {
    sb.append(line.toString)
    line.setLength(0)
  }

  def writeNewlines(num: Int): Unit = {
    finishLine()
    sb.append(strrep('\n', num))
    needNewlines = 0
    needSpace = false
  }

  def writeWord(s: String): Unit = {
    if (needSpace && needNewlines <= 0) line.append(' ')
    if (needNewlines > 0) writeNewlines(needNewlines)
    needSpace = false
    pastBeginning = true
    line.append(s)
  }

  def write(s: String): Unit =
    if (s.nonEmpty) {
      if (Character.isSpaceChar(s.head)) wantSpace()

      val words = PlainText.WordSplit.split(s).toList
      words.zipWithIndex.foreach { case (word, i) =>
        writeWord(word)
        if (i != words.length - 1) wantSpace()
      }

      if (Character.isSpaceChar(s.last)) wantSpace()
    }

  def write(cs: Array[Char]): Unit = write(String.valueOf(cs))
}
