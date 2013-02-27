package de.sciss

import io.Span
import java.io.{FileFilter, File}
import collection.immutable.{IndexedSeq => IIdxSeq}
import kontur.session.AudioRegion
import strugatzki.ProcessorCompanion
import concurrent.{Await, ExecutionContext}
import ExecutionContext.Implicits.global
import concurrent.duration.Duration
import collection.breakOut

package object sliding {
  val SEED  = 0L
  val random = new scala.util.Random(SEED)

  def file(path: String) = new File(path)

  def span(start: Long, stop: Long) = new Span(start, stop)

  implicit class RichSpan(span: Span) {
    def start   = span.getStart
    def stop    = span.getStop
    def length  = span.getLength
  }

  def process[Res, Cfg, CfgB](pc: ProcessorCompanion { type PayLoad = Res; type Config = Cfg }, cfg: CfgB, info: String)
                             (implicit build: CfgB => Cfg): Res = {
    println(info)
    var prog = 0
    val proc = pc(cfg) {
      case p: pc.Progress =>
        val max = p.percent / 3
        while(prog < max) {
          print("#")
          prog += 1
        }
      case p: pc.Result => println(" Done.")
    }
    Await.result(proc, Duration.Inf)
  }

  implicit class RichFile(f: File) {
    def / (child: String) = new File(f, child)
    def files: IIdxSeq[File] = files(_ => true)
    def files(filter: File => Boolean): IIdxSeq[File] = {
      val arr = f.listFiles(new FileFilter {
        def accept(_f: File) = filter(_f)
      })
      if (arr == null) Vector.empty else arr.toIndexedSeq
    }
    def parent  = f.getParentFile
    def path    = f.getPath
    def name    = f.getName
    def nameWithoutExtension = {
      val n = name
      val i = n.lastIndexOf('.')
      if (i < 0) n else n.substring(0, i)
    }
    /** includes the period. empty string if no extension found */
    def extension = {
      val n = name
      val i = n.lastIndexOf('.')
      if (i < 0) n else n.substring(i)
    }
    def updateExtension(extension: String) = parent / (nameWithoutExtension + extension)
  }

//  implicit class RichSession(doc: Session) {
//    def addAudioFile(f: File) {
//
//    }
//  }

  def thin[A](xs: IIdxSeq[A], num: Int): IIdxSeq[A] = {
    require(num >= 0)
    if (num == 0) return Vector.empty
    val drop = xs.size - num
    if (drop <= 0) return xs

    val step = xs.size.toDouble / (drop + 1)
    val indices: Set[Int] = (1 to drop).map(i => (i * step + 0.5).toInt)(breakOut)
    assert(indices.size == drop)

    xs.zipWithIndex.collect {
      case (x, i) if !indices.contains(i) => x
    }
  }

  implicit class RichAudioRegion(r: AudioRegion) {
    def moveTo(start: Long): AudioRegion = r.copy(span = span(start, start + r.span.length))
  }

  def exprand(lo: Double, hi: Double): Double = {
    import synth._
    val d = random.nextDouble()
    d.linlin(0, 1, lo, hi)
  }
}
