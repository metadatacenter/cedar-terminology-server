import sbt._
import sbt.Keys._

object MyBuild extends Build {

  val projectArtifactId = Pom.projectArtifactId(file("."))
  val projectScalaVersion = Pom.scalaVersion(file("."))
  val coreProjectName = projectArtifactId + "-core"
  val playProjectName = projectArtifactId + "-play"

  val coreProject = Project(coreProjectName, file(coreProjectName))
    .settings(
      version := Pom.projectVersion(baseDirectory.value),
      scalaVersion := projectScalaVersion,
      libraryDependencies ++= Pom.dependencies(baseDirectory.value))

  val playProject = Project(playProjectName, file(playProjectName))
    .enablePlugins(play.PlayScala)
    .dependsOn(coreProject)
    .settings(
      version := Pom.projectVersion(baseDirectory.value),
      scalaVersion := projectScalaVersion,
      libraryDependencies ++= Pom.dependencies(baseDirectory.value).filterNot(d => d.name == coreProject.id))

  override def rootProject = Some(playProject)
}
