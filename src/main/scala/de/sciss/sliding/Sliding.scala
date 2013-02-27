package de.sciss.sliding

import de.sciss.strugatzki.{FeatureExtraction => Extr, FeatureSegmentation => Segm}

object Sliding extends App {
  /*

  - segmentation of inside/outside files
  - random shuffling of pieces (so also the transitions inside<->outside will be random)
    // perhaps keep linear order and just 'zip' both sides, let's try that first
  - drop random pieces until target duration is not exceeded
  - parameters
    - min/max segmentation dur
    - min/max gap dur
    - probability white vs. hard cut
    - min/max white dur
    - min/max gain boost before cut
    - min/max fade in
    - min/max fade out

   */

  val avgDur            = 90.0
  val minDur            = 60.0

  val sampleRate        = 44100.0

  val baseF             = file("audio_work")
  val insideF           = (baseF / "inside" ).files(_.extension == ".aif")
  val outsideF          = (baseF / "outside").files(_.extension == ".aif")
  val renderF           = baseF / "render"

  val cSeg              = Segm.Config()
  cSeg.normalize        = false
  val cExtr             = Extr.Config()

  (insideF ++ outsideF).foreach { audioInF =>
    val name            = audioInF.nameWithoutExtension
    val featF           = (renderF / name).updateExtension("_feat.aif")
    val metaF           = featF.updateExtension(".xml")
    val isInside        = name.startsWith("Inside")

    cExtr.audioInput    = audioInF
    cExtr.featureOutput = featF
    cExtr.metaOutput    = Some(metaF)

    if (!metaF.isFile) process(Extr, cExtr, s"Extraction for '$name'")

//    cSeg.metaInput      = metaF
//    cSeg.minSpacing     =
//    cSeg.temporalWeight =
//    cSeg.numBreaks      =
  }
}