package ba.sake.hepek.sbt

import java.lang.reflect.Modifier
import java.nio.file.Path
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import classycle.ClassAttributes
import scala.collection.JavaConverters._
import sbt._
import sbt.Keys._
import ba.sake.hepek.core._

object HepekPlugin extends sbt.AutoPlugin {
  override def requires = plugins.JvmPlugin

  val HepekCoreVersion = "0.2.0"

  object autoImport {
    lazy val hepek       = taskKey[Long]("Runs hepek.")
    lazy val hepekTarget = taskKey[File]("Output directory of hepek.")

    lazy val hepekIncremental =
      settingKey[Boolean]("If true, hepek will try it's best to render minimum number of files.")
  }
  import autoImport._

  def rawHepekSettings: Seq[Setting[_]] =
    Seq(hepek := {
      val lastRun  = target.value / "hepek.lastrun"
      val classDir = classDirectory.value
      val fcp      = (fullClasspath in Runtime).value.files // deps + user classes
      Tasks.runHepek(
        lastRun,
        hepekIncremental.value,
        fcp,
        hepekTarget.value,
        classDir,
        streams.value.log
      )
    })

  override def projectSettings: Seq[Setting[_]] =
    Seq(
      hepekTarget := target.value / "web" / "public" / "main", // mimic sbt-web folder structure
      hepekIncremental := true,
      libraryDependencies += "ba.sake" % "hepek-core" % HepekCoreVersion // add "hepek-core" to user's deps
    ) ++ inConfig(Compile)(rawHepekSettings)
}

/*
 * Can't use scala reflection, because we want to support multiple Scala versions.
 * Getting the "Scala signature package has wrong version [error] expected: 5.0 [error] found: 5.2"
 * when I tried it.
 * Sbt's Scala version is fixed to 2.12 so I can't update it to 2.13 either. :)
 */
object Tasks {
  private val ModuleFieldName    = "MODULE$"
  private val RenderableFQN      = classOf[Renderable].getCanonicalName()
  private val MultiRenderableFQN = classOf[MultiRenderable].getCanonicalName()

  /**
    * @param lastRunFile File that contains timestamp of last run.
    * @param isIncremental True if you want to render files incrementally (via traversing reverse dependencies).
    * @param fcp Full classpath (allUserClassFiles ++ JAR dependencies ...)
    * @param hepekTargetDir Base target folder where files will be RENDERED.
    * @param classDir Usually "target" ... (used for getting class names, because we only know the filename; and for incremental rendering)
    * @param logger Logging stuff.
    * @return Timestamp of last run, IDK why.
    */
  def runHepek(
      lastRunFile: File,
      isIncremental: Boolean,
      fcp: Seq[File],
      hepekTargetDir: File,
      classDir: File,
      logger: Logger
  ): Long = {
    val allUserClassFiles = (classDir ** "*.class").get

    val userClassFiles = if (isIncremental && lastRunFile.exists) {
      val lastRun = IO.readLines(lastRunFile).head.toLong
      allUserClassFiles.filter(_.lastModified > lastRun) // only handle classes CHANGED since last hepek-ing
    } else allUserClassFiles                             // else ALL classes compiled

    /* resolving */
    val fcpClassloader = internal.inc.classpath.ClasspathUtilities.toLoader(fcp)
    renderObjects(hepekTargetDir, userClassFiles, classDir, isIncremental, fcpClassloader, logger)
    writeLastRun(lastRunFile)
  }

  /** @return Renderable/MultiRenderable classes set */
  private def renderObjects(
      hepekTargetDir: File,
      userClassFiles: Seq[File],
      classDir: File,
      isIncremental: Boolean,
      classloader: ClassLoader,
      logger: Logger
  ): Unit = {
    val userClassNames = for {
      classFile <- userClassFiles.get
      p         <- IO.relativize(classDir, classFile) // e.g. com/myproject/MyClass.class
    } yield p
      .dropRight(6)                // remove ".class" suffix
      .replaceAll("\\\\|/", "\\.") // replace "\" and "/" with "."

    val classNamesToRender   = possibleClassNamesToRender(classDir, isIncremental, userClassNames)
    val renderableClazz      = classloader.loadClass(RenderableFQN)
    val multiRenderableClazz = classloader.loadClass(MultiRenderableFQN)

    classNamesToRender.foreach { className =>
      val clazz      = classloader.loadClass(className)
      val mods       = clazz.getModifiers
      val fieldNames = clazz.getDeclaredFields.map(_.getName).toSeq

      val isScalaObject = !Modifier.isAbstract(mods) && fieldNames.contains(ModuleFieldName)
      if (isScalaObject) {
        if (isSuperclassOf(renderableClazz, clazz)) {
          val objClazz = classloader.loadClass(className.dropRight(1)) // without $ at end
          val content  = objClazz.getMethod("render").invoke(null).asInstanceOf[String]
          val relPath  = objClazz.getMethod("relPath").invoke(null).asInstanceOf[Path]
          writeRenderableObject(hepekTargetDir, className, content, relPath, logger)
        } else if (isSuperclassOf(multiRenderableClazz, clazz)) {
          val objClazz = classloader.loadClass(className.dropRight(1)) // without $ at end
          val renderables = objClazz
            .getMethod("renderables")
            .invoke(null)
            .asInstanceOf[java.util.List[_]]
          renderables.asScala.foreach { r =>
            val content = renderableClazz.getMethod("render").invoke(r).asInstanceOf[String]
            val relPath = renderableClazz.getMethod("relPath").invoke(r).asInstanceOf[Path]
            writeRenderableObject(hepekTargetDir, className, content, relPath, logger)
          }
        }
      }
    }
  }

  private def possibleClassNamesToRender(
      classDir: File,
      isIncremental: Boolean,
      userClassNames: Seq[String] // only the changed ones, if incremental
  ): Set[String] = {
    val classNames = if (isIncremental) {
      val filesJavaList = new java.util.ArrayList[File]()
      filesJavaList.add(classDir)
      val userClassRevDeps = ClassycleDependencyUtils
        .reverseDependencies(filesJavaList, false)
        .asScala
        .filterKeys(
          vertex =>
            userClassNames.contains(vertex.getAttributes.asInstanceOf[ClassAttributes].getName)
        )
        .mapValues { _.asScala.map(_.getAttributes.asInstanceOf[ClassAttributes].getName) }
      userClassNames ++ userClassRevDeps.values.flatten
    } else userClassNames
    classNames.toSet
  }

  /** Writes Renderable object to its path */
  private def writeRenderableObject(
      hepekTargetDir: File,
      className: String,
      content: String,
      relPath: Path,
      logger: Logger
  ): Unit = {
    val p = hepekTargetDir.toPath.resolve(relPath)
    logger.debug(s"Rendering '$className' to '${p.toString}'")
    Files.createDirectories(p.getParent)
    Files.write(p, content.getBytes(StandardCharsets.UTF_8))
  }

  /** Writes last run to "target/hepek.lastrun" file */
  private def writeLastRun(lastRunFile: File): Long = {
    val lastRunTimestamp = System.currentTimeMillis
    IO.write(lastRunFile, lastRunTimestamp.toString)
    lastRunTimestamp
  }

  private def isSuperclassOf(clazzParent: Class[_], clazz: Class[_]): Boolean = {
    clazzParent.isAssignableFrom(clazz)
  }
}
