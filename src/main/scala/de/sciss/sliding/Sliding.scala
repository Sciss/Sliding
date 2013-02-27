package de.sciss.sliding

import de.sciss.strugatzki.{FeatureExtraction => Extr, FeatureSegmentation => Segm}
import de.sciss.synth.io.AudioFile
import xml.XML
import de.sciss.kontur.session.{AudioFileElement, Session}

object Sliding {
  println("Rendering...\n")

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
  val minGap            =  2.0
  val maxGap            = 16.0

  val sampleRate        = 44100.0

  val baseF             = file("audio_work")
  val insideF           = (baseF / "inside" ).files(_.extension == ".aif")
  val outsideF          = (baseF / "outside").files(_.extension == ".aif")
  val renderF           = baseF / "render"

  val minFrames         = (minDur * sampleRate).toLong
  val avgFrames         = (avgDur * sampleRate).toLong
  val cSegm             = Segm.Config()
  cSegm.normalize       = false
  val cExtr             = Extr.Config()
  cSegm.minSpacing      = minFrames

  val doc               = Session.newEmpty

  (insideF ++ outsideF).sortBy(_.name).foreach { audioInF =>
    val name            = audioInF.nameWithoutExtension
    val featF           = (renderF / name).updateExtension("_feat.aif")
    val metaF           = featF.updateExtension(".xml")
    val isInside        = name.startsWith("Inside")

    cExtr.audioInput    = audioInF
    cExtr.featureOutput = featF
    cExtr.metaOutput    = Some(metaF)

    if (!metaF.isFile) process(Extr, cExtr, s"Extraction for '$name'")

    val spec            = AudioFile.readSpec(audioInF)
    cSegm.metaInput     = metaF
    cSegm.temporalWeight= if (isInside) 0.8f else 0.2f
    cSegm.numBreaks     = ((spec.numFrames.toDouble / avgFrames) + 0.5).toInt

    val segmF           = (renderF / name).updateExtension("_segm.xml")
    val segm: Segm.PayLoad = if (segmF.isFile) {
      val node = XML.loadFile(segmF)
      (node \\ "break").map(Segm.Break.fromXML(_)).toIndexedSeq
    } else {
      val res: Segm.PayLoad = process(Segm, cSegm, s"Segmentation for '$name'")
      val node = <breaks>{res.map(_.toXML)}</breaks>
      XML.save(segmF.path, node, xmlDecl = true)
      res
    }

    val pos = 0L +: segm.map(_.pos) :+ spec.numFrames
//    pos.sliding(2, 1).map { case Seq(start, stop) =>

//    println(s"\nname = $name")
//    segm.foreach(println)

    val afe = AudioFileElement.fromPath(doc, audioInF)
    doc.audioFiles.insert(doc.audioFiles.size, afe)
  }

  doc.save(renderF / "kontur_session.xml")

//  case class AudioFileSelection(f: File, span: Span)
}