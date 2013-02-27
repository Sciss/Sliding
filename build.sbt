name := "Sliding"

version := "0.0.1-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.10.0"

homepage <<= name { n => Some(url("https://github.com/Sciss/" + n)) }

licenses := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

libraryDependencies := Seq(
  "de.sciss" %% "strugatzki" % "1.5.+",
  "de.sciss" %% "kontur" % "1.1.+"
)

retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

// ---- console ----

initialCommands in console :=
"""import de.sciss.sliding._
""".stripMargin

