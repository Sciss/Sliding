package de.sciss.sliding

import de.sciss.strugatzki.{FeatureExtraction => Extr, FeatureSegmentation => Segm}
import de.sciss.synth.io.AudioFile
import xml.XML
import de.sciss.kontur.session.{AudioRegion, MatrixDiffusion, AudioTrack, BasicTimeline, AudioFileElement, Session}
import de.sciss.kontur.util.Matrix2D
import collection.breakOut
import collection.immutable.{IndexedSeq => IIdxSeq}
import java.io.File
import de.sciss.io.Span

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
  val insideF           = (baseF / "inside" ).files(_.extension == ".aif").sortBy(_.name)
  val outsideF          = (baseF / "outside").files(_.extension == ".aif").sortBy(_.name)
  val renderF           = baseF / "render"

  val minFrames         = (minDur * sampleRate).toLong
  val avgFrames         = (avgDur * sampleRate).toLong
  val cSegm             = Segm.Config()
  cSegm.normalize       = false
  val cExtr             = Extr.Config()
  cSegm.minSpacing      = minFrames

  // ---- initialise Kontur session ----

  val doc               = Session.newEmpty
  val tl                = BasicTimeline.newEmpty(doc)
  val trk               = new AudioTrack(doc)
  val trail             = trk.trail
  val diff              = new MatrixDiffusion(doc)
  diff.numInputChannels = 2
  diff.numOutputChannels= 2
  diff.matrix           = Matrix2D.fromSeq(Seq(Seq(1f, 0f), Seq(0f, 1f)))
  doc.diffusions.insert(0, diff)
  trk.diffusion         = Some(diff)
  tl.tracks.insert(0, trk)
  doc.timelines.insert(0, tl)

  val audioInFs         = (insideF ++ outsideF)
  val audioFElems: Map[String, AudioFileElement] = audioInFs.map(audioInF => {
    val afe = AudioFileElement.fromPath(doc, audioInF)
    audioInF.name -> afe
  })(breakOut)

  // ---- segmentation procedure ----

  def spansForFile(audioInF: File): IIdxSeq[Span] = {
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

    val pos   = 0L +: segm.map(_.pos).sorted /* ! */ :+ spec.numFrames
//    assert(pos == pos.sorted)
    val spans = pos.sliding(2, 1).map { case Seq(start, stop) => span(start, stop) }
    spans.toIndexedSeq
  }

  // ---- register all files ----

  audioFElems.values.foreach { afe =>
    doc.audioFiles.insert(doc.audioFiles.size, afe)
  }

  // ---- segment files and zip spans ----

  def makeRegions(coll: IIdxSeq[File]): IIdxSeq[AudioRegion] = coll.flatMap { f =>
    val sps   = spansForFile(f)
    val afe   = audioFElems(f.name)
    val name  = f.nameWithoutExtension
    sps.zipWithIndex.map { case (sp, idx) =>
      AudioRegion(span = span(0L, sp.length), name = s"$name.${idx+1}", audioFile = afe, offset = sp.start, gain = 1f,
        fadeIn = None, fadeOut = None)
    }
  }

  val outsideRegions  = makeRegions(outsideF)
  val insideRegions   = makeRegions(insideF)
  val numRegionsH     = math.min(outsideRegions.size, insideRegions.size)
  val zipped          = thin(outsideRegions, numRegionsH) zip thin(insideRegions, numRegionsH)
  val flat            = zipped.flatMap { case (a, b) => a :: b :: Nil }

  var cursor = 0L
  flat.foreach { r =>
    val r1 = r.moveTo(cursor)
    trail.add(r1)
    val gap = (exprand(minGap, maxGap) * sampleRate + 0.5).toLong
    cursor  = r1.span.stop + gap
//    println("+ SPAN " + r1.span + "; GAP = " + gap + "; new CURSOR = " + cursor)
  }

  tl.span = span(0L, cursor)
  doc.save(renderF / "kontur_session.xml")
  println("\nSession saved.")

//  case class AudioFileSelection(f: File, span: Span)
}