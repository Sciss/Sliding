package de.sciss.sliding

import de.sciss.common.BasicApplication

object Application {
  lazy val instance = new Application
}
final class Application private() extends BasicApplication(classOf[Application], "Sliding") {
  def createMenuFactory() = ???
  def createWindowHandler() = ???
  def createDocumentHandler() = ???

  def getVersion = 0.1
  def getMacOSCreator = ???
}