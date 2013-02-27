package de.sciss.sliding

import swing.Swing

object Main extends App {
  Swing.onEDT {
    Application.instance
    Sliding
  }
}