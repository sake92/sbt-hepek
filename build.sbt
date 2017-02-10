organization := "ba.sake"

name := "sbt-hepek"

version := "0.0.1-SNAPSHOT"

description := "Hepek sbt plugin"

libraryDependencies ++= Seq(
  "ba.sake" % "hepek-core" % "0.0.1"
)

sbtPlugin := true

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

developers += Developer("sake92", "Sakib Hadžiavdić", "sakib@sake.ba", url("http://sake.ba"))

scmInfo := Some(ScmInfo(url("https://github.com/sake92/sbt-hepek"), "scm:git:git@github.com:sake92/sbt-hepek.git"))

homepage := Some(url("http://sake.ba")) // url in maven

