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

  object autoImport {
    lazy val hepek       = taskKey[Long]("Runs hepek")
    lazy val hepekTarget = settingKey[File]("Output directory of hepek")

    lazy val hepekIncremental =
      settingKey[Boolean]("If true, hepek will try it's best to render minimum number of files.")
  }
  import autoImport._

  def rawHepekSettings: Seq[Setting[_]] =
    Seq(
      hepek := {
        val publicFolder = resourceDirectory.value / "public"
        IO.copyDirectory(publicFolder, hepekTarget.value)

        // render hepek files
        val lastRun  = target.value / "hepek.lastrun"
        val classDir = classDirectory.value
        val fcp      = (Runtime / fullClasspath).value.files // deps + user classes
        Tasks.runHepek(
          lastRun,
          hepekIncremental.value,
          fcp,
          hepekTarget.value,
          classDir,
          streams.value.log
        )
      },
      sourceGenerators += Def.task {
        val file         = (Compile / sourceManaged).value / "public_resources.scala"
        val publicFolder = resourceDirectory.value / "public"
        Tasks.makePublicResources(file, publicFolder)
      }.taskValue
    )

  override def projectSettings: Seq[Setting[_]] =
    Seq(
      hepekTarget := baseDirectory.value / "hepek_output",
      hepekIncremental := false,
      libraryDependencies += "ba.sake" % "hepek-core" % "0.2.0" // add "hepek-core" to user's deps
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
  private val RenderableFQN      = "ba.sake.hepek.core.Renderable"
  private val MultiRenderableFQN = "ba.sake.hepek.core.MultiRenderable"

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

    /* render */
    val fcpClassloader = internal.inc.classpath.ClasspathUtil.toLoader(fcp)
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
    classNames.filter(_.startsWith("files.")).toSet
  }

  /** Writes Renderable object to its path */
  private def writeRenderableObject(
      hepekTargetDir: File,
      className: String,
      content: String,
      relPath: Path,
      logger: Logger
  ): Unit = {
    if (relPath.startsWith("files")) {
      val relPathAdapted = relPath.subpath(1, relPath.getNameCount()) // chop off "files" prefix
      val p              = hepekTargetDir.toPath.resolve(relPathAdapted)
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

  private def isSuperclassOf(clazzParent: Class[_], clazz: Class[_]): Boolean = {
    clazzParent.isAssignableFrom(clazz)
  }

  // generate handy resources
  def makePublicResources(file: File, publicFolder: File): Seq[File] = {
    if (!publicFolder.exists()) return Seq.empty

    var res    = ""
    var indent = 0
    def writeResource(path: Path, parentPath: String): Unit = {
      val fileName = path.getFileName.toString
      val pathName = if (parentPath.isEmpty) fileName else s"${parentPath}/${fileName}"
      if (Files.isDirectory(path)) {
        res += (" " * indent) + s"object ${fileName} {\n"
        indent += 2
        Files.list(path).forEach(p => writeResource(p, pathName))
        indent -= 2
        res += (" " * indent) + "}\n\n"
      } else {
        res += (" " * indent) + s"""val `${fileName}` = Resource("${pathName}")\n"""
      }
    }
    Files.list(publicFolder.toPath).forEach(p => writeResource(p, ""))

    IO.write(file, s"""package files
                      |import ba.sake.hepek.Resource
                      |
                      |${res}
                      |""".stripMargin)
    Seq(file)
  }
}
