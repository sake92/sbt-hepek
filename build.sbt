inThisBuild(
  List(
    organization := "ba.sake",
    homepage := Some(url("https://sake92.github.io/sbt-hepek")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/sake92/sbt-hepek"),
        "scm:git:git@github.com:sake92/sbt-hepek.git"
      )
    ),
    developers := List(
      Developer(
        "sake92",
        "Sakib Hadžiavdić",
        "sakib@sake.ba",
        url("https://sake.ba")
      )
    ),
    scalaVersion := "2.12.18"
  )
)

val root = (project in file("."))
  .settings(
    name := "sbt-hepek",
    description := "Hepek sbt plugin",
    libraryDependencies ++= Seq(
      "ba.sake" % "hepek-core" % "0.2.0"
    ),
    sbtPlugin := true,
    publishMavenStyle := true
  )
