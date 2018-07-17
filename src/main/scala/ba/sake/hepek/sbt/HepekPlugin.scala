package ba.sake.hepek.sbt

import java.lang.reflect.Modifier
import scala.collection.JavaConverters._
import scala.reflect.runtime.{ universe => ru }
import sbt._
import sbt.Keys._
import ba.sake.hepek.core.ClassycleDependencyUtils
import ba.sake.hepek.core.Renderable
import classycle.ClassAttributes
import java.nio.file.Path
import java.nio.file.Files
import java.nio.charset.StandardCharsets

object HepekPlugin extends sbt.AutoPlugin {

  override def requires = plugins.JvmPlugin

  private val HepekCoreVersion = "0.1.1"

  object autoImport {
    lazy val hepek = taskKey[Long]("Runs hepek.")
    lazy val hepekTarget = taskKey[File]("Output directory of hepek.")
    lazy val hepekIncremental = settingKey[Boolean]("If true, hepek will try it's best to render minimum number of files.")
  }
  import autoImport._

  def rawHepekSettings: Seq[Setting[_]] = Seq(
    hepek := {
      val lastRun = target.value / "hepek.lastrun"
      val classDir = classDirectory.value
      val fcp = (fullClasspath in Runtime).value.files // WHOLE classpath: deps & user classes
      Tasks.runHepek(lastRun, hepekIncremental.value, fcp, hepekTarget.value, classDir, streams.value.log)
    })

  override def projectSettings: Seq[Setting[_]] = Seq(
    hepekTarget := target.value / "web" / "public" / "main", // mimic sbt-web folder structure
    hepekIncremental := true,
    libraryDependencies += "ba.sake" % "hepek-core" % HepekCoreVersion // add "hepek-core" to classpath, we need Renderable
  ) ++ inConfig(Compile)(rawHepekSettings)

}

object Tasks {

  /**
   * @param lastRunFile File that contains timestamp of last run.
   * @param isIncremental True if you want to render files incrementally (via traversing reverse dependencies).
   * @param fcp Full classpath (allUserClassFiles ++ JAR dependencies ...)
   * @param hepekTargetDir Base target folder where files will be RENDERED.
   * @param classDir Usually "target" ... (used for getting class names, because we only know the filename; and for incremental rendering)
   * @param streamsV Logging stuff.
   * @return Timestamp of last run, IDK why.
   */
  def runHepek(lastRunFile: File, isIncremental: Boolean, fcp: Seq[File], hepekTargetDir: File, classDir: File, logger: Logger) = {
    val allUserClassFiles = (classDir ** "*.class").get

    val userClassFiles = if (isIncremental && lastRunFile.exists) {
      val lastRun = IO.readLines(lastRunFile).head.toLong
      allUserClassFiles.filter(_.lastModified > lastRun) // only handle classes CHANGED since last hepek-ing
    } else allUserClassFiles // else ALL classes compiled

    val fcpClassloader = internal.inc.classpath.ClasspathUtilities.toLoader(fcp)
    val classNamesToRender = getClassNamesToRender(userClassFiles, classDir, isIncremental, fcpClassloader)
    writeObjects2Files(classNamesToRender, fcpClassloader, hepekTargetDir, logger)
    writeLastRun(lastRunFile)
  }

  /** checks if it's a Scala object that extends Renderable */
  private def getClassNamesToRender(userClassFiles: Seq[File], classDir: File, isIncremental: Boolean, fcpClassloader: ClassLoader): Set[String] = {
    val ruMirror = ru.runtimeMirror(fcpClassloader)
    val renderableSymbol = ruMirror.staticClass(classOf[Renderable].getCanonicalName)

    val userClassNames = userClassFiles.get.flatMap { classFile =>
      IO.relativize(classDir, classFile).map { p => // e.g. com/myproject/MyClass.class
        // remove ".class" and replace "\" or "/" with "."
        p.dropRight(6).replaceAll("\\\\|/", "\\.")
      }
    }

    val classNamesToRender = if (isIncremental) {
      val filesJavaList = new java.util.ArrayList[File]()
      filesJavaList.add(classDir)
      val userClassRevDeps = ClassycleDependencyUtils.reverseDependencies(filesJavaList, false).asScala.
        filterKeys(vertex => userClassNames.contains(vertex.getAttributes.asInstanceOf[ClassAttributes].getName)).
        mapValues { _.asScala.map(_.getAttributes.asInstanceOf[ClassAttributes].getName) }
      userClassNames ++ userClassRevDeps.values.flatten
    } else userClassNames

    // dont consider lambdas etc... further nested classes
    val classNamesToRenderFiltered = classNamesToRender.filterNot(_.count(_ == '$') > 1)

    classNamesToRenderFiltered.flatMap { className =>
      val clazz = fcpClassloader.loadClass(className)
      val isAbstract = Modifier.isAbstract(clazz.getModifiers) || Modifier.isInterface(clazz.getModifiers)
      val extendsApp = className.contains("$delayedInit")
      /*  Getting java.lang.IncompatibleClassChangeError
       *  when a class that extends App trait is present.
       */
      if (isAbstract || extendsApp) { // no traits! no abstract classes! no interfaces! no APPs plz :D
        None
      } else {
        val clazzSymbol = ruMirror.staticClass(className)
        val isSubclassOfRenderable = clazzSymbol.baseClasses.contains(renderableSymbol)
        if (isSubclassOfRenderable) {
          // Scala object's inner class IMPLEMENTS the Renderable, but object has static methods!
          // So, we leave only the object in the SET
          Option(className.replaceAll("\\$", ""))
        } else {
          None
        }
      }
    }.toSet
  }

  /** Does the actual job of writing String representations of objects to files */
  private def writeObjects2Files(classNamesToRender: Set[String], fcpClassloader: ClassLoader, hepekTargetDir: File, logger: Logger): Unit = {
    if (classNamesToRender.size > 0) {
      logger.info(s"Rendering ${classNamesToRender.size} files to ${hepekTargetDir.toString}...")
    }
    classNamesToRender.foreach { className => // TODO write files in parallel? O.o
      val clazz = fcpClassloader.loadClass(className)
      val classNamePath = clazz.getMethod("relPath").invoke(null).asInstanceOf[Path]
      val renderMethod = clazz.getMethod("render")
      val content = renderMethod.invoke(null).asInstanceOf[String] // null => should be static method!
      val p = hepekTargetDir.toPath.resolve(classNamePath)
      logger.debug(s"Rendering '$className' to '${p.toString}'")
      Files.createDirectories(p.getParent)
      Files.write(p, content.getBytes(StandardCharsets.UTF_8))
    }
  }

  /** Writes last run to "target/hepek.lastrun" file */
  private def writeLastRun(lastRunFile: File): Long = {
    val lastRunTimestamp = System.currentTimeMillis
    IO.write(lastRunFile, lastRunTimestamp.toString)
    lastRunTimestamp
  }

}
