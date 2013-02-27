package de.sciss

import java.io.{FileFilter, File}
import collection.immutable.{IndexedSeq => IIdxSeq}
import strugatzki.ProcessorCompanion
import concurrent.{Await, ExecutionContext}
import ExecutionContext.Implicits.global
import concurrent.duration.Duration

package object sliding {
  def file(path: String) = new File(path)

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
}
