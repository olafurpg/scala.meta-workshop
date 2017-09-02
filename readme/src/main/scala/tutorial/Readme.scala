package scalaworld

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.Calendar
import scala.collection.mutable
import scala.compat.Platform.EOL
import scala.meta._
import scala.meta.tutorial.BuildInfo
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.ILoop
import scala.util.Try
import scalatags.Text
import scalatags.Text.TypedTag
import scalatags.Text.all._
import scalatex.site.Highlighter
import ammonite.ops._
import org.langmeta.internal.io.FileIO
import org.langmeta.internal.io.PathIO
import org.pegdown.PegDownProcessor
import org.scalameta.logger

class ScalametaSite(directory: RelPath, frag: => Frag)
    extends scalatex.site.Main(
      url = "https://github.com/scalameta/scalameta-tutorial/tree/master",
      wd = ScalametaSite.pwd,
      output = ScalametaSite.pwd / "readme" / "target" / "scalatex" / directory,
      extraAutoResources = Nil,
      extraManualResources = ScalametaSite.manualResources,
      frag
    )

object ScalametaSite {
  val pwd = Path(BuildInfo.baseDirectory)
  lazy val manualResources: Seq[ResourcePath] = {
    BuildInfo.resources.withFilter(_.isDirectory).flatMap { r =>
      FileIO
        .listAllFilesRecursively(AbsolutePath(r))
        .files
        .map(p => resource / RelPath(p.toNIO))
    }
  }
}

object LandingPage extends ScalametaSite("x" / up, scalatex.LandingPage())
object Paradise extends ScalametaSite("paradise", scalatex.paradise.Paradise())
object Tutorial extends ScalametaSite("tutorial", scalatex.tutorial.Readme()) {
  def paradise = lnk("scalameta/paradise", "../paradise")
}

object Readme {

  def main(args: Array[String]): Unit = {
    try {
      Tutorial.main(args)
      Paradise.main(args)
      LandingPage.main(args)
    } finally {
      saveCache()
    }
  }

  def copyrightBadge: TypedTag[String] = {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val text = s"(c) 2014 - $currentYear scalameta contributors"
    div(
      style := "margin: 0px;color: #ccc;text-align: center;padding: 0.5em 2em 0.5em 0em;border-top: 1px solid #eee;display: block;")(
      text)
  }

  lazy val iloopCacheFile: File =
    Paths.get("target", "iloopCache.serialized").toFile
  lazy val iloopCache: mutable.Map[String, String] = {
    Try {
      val ois = new ObjectInputStream(new FileInputStream(iloopCacheFile))
      val obj = ois.readObject().asInstanceOf[mutable.Map[String, String]]
      println("Loaded iloop cache...")
      obj
    }.getOrElse(mutable.Map.empty[String, String])
  }
  def saveCache(): Unit = {
    val fos = new FileOutputStream(iloopCacheFile)
    val oos = new ObjectOutputStream(fos)
    oos.writeObject(iloopCache)
    oos.close()
    fos.close()
    println("Wrote iloop cache...")
  }
  def gitter: Text.RawFrag = raw(
    """<a href="https://gitter.im/scalameta/scalameta?utm_source=badge&amp;utm_medium=badge&amp;utm_campaign=pr-badge&amp;utm_content=badge"><img src="https://camo.githubusercontent.com/da2edb525cde1455a622c58c0effc3a90b9a181c/68747470733a2f2f6261646765732e6769747465722e696d2f4a6f696e253230436861742e737667" alt="Join the chat at https://gitter.im/scalameta/scalameta" data-canonical-src="https://badges.gitter.im/Join%20Chat.svg" style="max-width:100%;"></a>"""
  )
  def github: String = {
    "https://github.com"
  }
  def repo: String = "http://scalameta.org/tutorial"
  def dotty: TypedTag[String] = a(href := "http://dotty.epfl.ch/", "Dotty")
  def issue(id: Int): TypedTag[String] =
    a(href := repo + s"/issues/$id", s"#$id")
  def note: TypedTag[String] = b("NOTE")
  def issues(ids: Int*): TypedTag[String] = span(ids.map(issue): _*)
  val pegdown = new PegDownProcessor
  def database: Database = {
    val cp = Classpath(BuildInfo.semanticClassdirectory)
    val db = Database.load(cp)
    assert(
      db.documents.nonEmpty,
      s"""db.documents.nonEmpty.
         |$db
         |$cp
         |""".stripMargin)
    db
  }

  def url(src: String): TypedTag[String] = a(href := src, src)

  private def unindent(frag: String): String = {
    // code frags are passed in raw from *.scalatex.
    val toStrip =
      " " * Try(
        frag.lines
          .withFilter(_.nonEmpty)
          .map(_.takeWhile(_ == ' ').length)
          .min).getOrElse(0)
    frag.lines.map(_.stripPrefix(toStrip)).mkString("\n")
  }

  def markdown(code: Frag*): Text.RawFrag =
    raw(pegdown.markdownToHtml(unindent(code.render)))

  def getMetaCode(indentedCode: String): String = {
    s"""
       |import scala.meta._, contrib._
       |${unindent(indentedCode)}
       """.stripMargin
  }
  def callout(kind: String, msg: Frag*): TypedTag[String] =
    div(cls := s"bs-callout bs-callout-${kind}", p(msg))

  def info(msg: Frag*) = callout("info", msg: _*)
  def success(msg: Frag*) = callout("success", msg: _*)
  def warning(msg: Frag*) = callout("warning", msg: _*)
  def danger(msg: Frag*) = callout("danger", msg: _*)
  lazy val classpath = this.getClass.getClassLoader match {
    case u: URLClassLoader => u.getURLs.map(_.getPath).toList
  }

  /**
    * repl session that has an invisible "import scala.meta._" attached to it.
    */
  def metaRepl(code0: String) =  meta(code0)
  def meta(code0: String) = {
    val code1 = s"import scala.meta._, contrib._$EOL${unindent(code0).trim}"
    val result0 = executeInRepl(code1)
    val result1 = result0.split(EOL).drop(4).mkString(EOL)
    Paradise.hl.scala(result1)
  }

  val settings = {
    val s = new Settings
    s.deprecation.value = true
    s.Xnojline.value = true
    s.usejavacp.value = false
    s.classpath.value = classpath.mkString(File.pathSeparator)
    s
  }

  def evaluateCode(code: String): String = {
    iloopCache.getOrElseUpdate(
      code, {
        println(
          s"""|#################
              |# Running repl...
              |#################
              |$code
              |""".stripMargin
        )
        println(code)
        ILoop.runForTranscript(code, settings)
      }
    )
  }

  private def executeInRepl(code: String): String = {
    case class RedFlag(pattern: String, directive: String, message: String)
    val redFlags = List(
      RedFlag(
        "Abandoning crashed session.",
        "compilation crash",
        "crash in repl invocation"),
      RedFlag(
        s"error:",
        "compilation error",
        "compilation error in repl invocation")
    )

    def validatePrintout(printout: String): Unit = {
      redFlags.foreach {
        case flag @ RedFlag(pat, directive, msg) =>
          if (printout.contains(pat) &&
            !code.contains("// " + directive)) {
            sys.error(s"$flag $msg + : $printout")
          }
      }
    }
    val postprocessedCode = redFlags
      .foldLeft(code)((acc, curr) => acc.replace("// " + curr.directive, ""))
    val lines = evaluateCode(postprocessedCode).lines.toList
    validatePrintout(lines.mkString(EOL))
    lines
      .drop(3)
      .dropRight(2)
      .map(_.replaceAll("\\s+$", ""))
      .mkString(EOL)
      .trim
  }

  /** Scalatex doesn't support default args */
  def repl(code: String): TypedTag[String] = {
    Paradise.hl.scala(executeInRepl(unindent(code)))
  }

  def image(file: String, caption: String = ""): TypedTag[String] = div(
    cls := "text-center",
    img(style := "width: 100%", src := "img/" + file),
    if (caption.nonEmpty) p("Caption: " + caption)
    else p()
  )

  def half(frags: Frag*): TypedTag[String] =
    div(frags, width := "50%", float.left)

  def pairs(frags: Frag*): TypedTag[String] = div(frags, div(clear := "both"))

  def sideBySide(left: String, right: String): TypedTag[String] =
    pairs(List(left, right).map(x => half(Paradise.hl.scala(x))): _*)

}
