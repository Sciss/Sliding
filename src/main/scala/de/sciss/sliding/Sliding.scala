package de.sciss.sliding

import de.sciss.strugatzki.{FeatureExtraction => Extr, FeatureSegmentation => Segm}
import de.sciss.synth.io.AudioFile
import xml.XML
import de.sciss.kontur.session.{FadeSpec, AudioRegion, MatrixDiffusion, AudioTrack, BasicTimeline, AudioFileElement, Session}
import de.sciss.kontur.util.Matrix2D
import collection.breakOut
import collection.immutable.{IndexedSeq => IIdxSeq}
import java.io.File
import de.sciss.io.Span
import de.sciss.synth
import synth._

object Sliding {
  println("Rendering...\n")

  /*

  - segmentation of inside/outside files
  - 'zipping' of pieces (alternating inside<->outside)
  - [drop random pieces until target duration is not exceeded]
  - parameters
    - min/max segmentation dur
    - min/max gap dur
    - probability white vs. hard cut
    - min/max white dur
        // the whitening stuff is not necessary, at least for this occasion it doesn't make sense.
    - min/max gain boost before cut
    - min/max fade in
    - min/max fade out

   */

  // ---- parameters of the composition ----

  val avgDur            = 90.0        // average duration of chunk in seconds
  val minDur            = 60.0        // minimum duration of chunk in seconds
  val minGap            =  6.0        // minimum duration of gap between chunks in seconds
  val maxGap            = 20.0        // maximum duration of gap between chunks in seconds
  val outsideGain       = 0.0.dbamp   // gain factor applied to outside chunks
  val insideGain        = 0.0.dbamp   // gain factor applied to inside chunks
  val minFadeIn         = 20.0
  val maxFadeIn         = 30.0
  val minFadeOut        = 0.01
  val maxFadeOut        = 0.02

  val useBoost          = false // true        // whether to apply slight boost before cut offs
  val minBoostGain      = 0.dbamp
  val maxBoostGain      = 3.dbamp
  val minBoostDur       = 1.0         // seconds
  val maxBoostDur       = 4.0         // seconds

  // ---- basic configuration ----

  val sampleRate        = 44100.0
  val baseF             = file("audio_work")

  // ---- derived settings ----

  val outsideF          = (baseF / "outside").files(_.extension == ".aif").sortBy(_.name)
  val insideF           = (baseF / "inside" ).files(_.extension == ".aif").sortBy(_.name)
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
  diff.name             = "Stereo"
  doc.diffusions.insert(0, diff)
  trk.name              = "T1"
  trk.diffusion         = Some(diff)
  tl.tracks.insert(0, trk)
  doc.timelines.insert(0, tl)

  val trkBoost  = if (useBoost) {
    val res       = new AudioTrack(doc)
    res.name      = "T2"
    res.diffusion = Some(diff)
    tl.tracks.insert(1, res)
    res
  } else {
    trk
  }

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

    val pos     = 0L +: segm.map(_.pos).sorted /* ! */ :+ spec.numFrames
    val spans0  = pos.sliding(2, 1).map { case Seq(start, stop) => span(start, stop) }
    val spans   = spans0.filter(_.length >= minFrames)  // the last artifically added span might be too short
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
//      val fdInFr  = (powexprand(minFadeIn, maxFadeIn) * sampleRate + 0.5).toLong
      val fdInFr  = (exprand(minFadeIn, maxFadeIn) * sampleRate + 0.5).toLong
//      val fdIn    = FadeSpec(fdInFr, sinShape)
      val fdIn    = FadeSpec(fdInFr, expShape, floor = -40.dbamp)
//      val fdOutFr = (powexprand(minFadeOut, maxFadeOut) * sampleRate + 0.5).toLong
      val fdOutFr = (exprand(minFadeOut, maxFadeOut) * sampleRate + 0.5).toLong
      val fdOut   = FadeSpec(fdOutFr, welchShape)
      AudioRegion(span = span(0L, sp.length), name = s"$name.${idx+1}", audioFile = afe, offset = sp.start, gain = 1f,
        fadeIn = Some(fdIn), fadeOut = Some(fdOut))
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

    if (useBoost) {
      val boostGain = exprand(minBoostGain, maxBoostGain)
      val addGain   = boostGain - 1.0
      if (addGain > -30.dbamp) {
        val boostDur    = exprand(minBoostDur, maxBoostDur)
        val maxFrames   = r1.span.length - r1.fadeIn.map(_.numFrames).getOrElse(0L)
        val fadeOutLen  = r1.fadeOut.map(_.numFrames).getOrElse(0L)
        val minFrames   = fadeOutLen + 100
        val boostFrames = math.min(maxFrames, (boostDur * sampleRate + 0.5).toLong)
        if (boostFrames >= minFrames) {
          val boostOff = r1.span.length - boostFrames
          val r2a = r1.moveStart(boostOff)
          val r2  = r2a.copy(fadeIn = Some(FadeSpec(boostFrames - fadeOutLen, expShape, floor = -40.dbamp)))
          trkBoost.trail.add(r2)
        }
      }
    }

    val gap = (linrand(minGap, maxGap) * sampleRate + 0.5).toLong
    cursor  = r1.span.stop + gap
  }

  tl.span = span(0L, cursor)
  doc.save(renderF / "kontur_session.xml")
  println("\nSession saved.")
}
