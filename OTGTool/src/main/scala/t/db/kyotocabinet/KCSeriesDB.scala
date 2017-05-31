/*
 * Copyright (c) 2012-2017 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition
 * (NIBIOHN), Japan.
 *
 * This file is part of Toxygates.
 *
 * Toxygates is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Toxygates is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Toxygates. If not, see <http://www.gnu.org/licenses/>.
 */

package t.db.kyotocabinet

import java.nio.ByteBuffer
import scala.annotation.tailrec
import kyotocabinet.DB
import t.db.ProbeMap
import t.db.SeriesDB
import otg.Context
import t.db.BasicExprValue
import t.db.MatrixContext
import t.db.Series
import t.db.SeriesBuilder
import t.db.SeriesPoint
import t.global.KCDBRegistry
import t.platform.Probe

object KCSeriesDB {
  val c20g = 20l * 1204 * 1204 * 1024
  val c1g = 1l * 1204 * 1204 * 1024
  val c4g = 1l * 1204 * 1204 * 1024
  //TODO possibly re-tune this
  val options = s"#bnum=10000000#apow=1#pccap=$c1g"

  /**
   * Options: linear, no alignment, 10 million buckets (approx 10% of size), 5g memory mapped
   */
  def apply[S <: Series[S]](file: String, writeMode: Boolean, builder: SeriesBuilder[S],
      normalize: Boolean)
  (implicit context: MatrixContext): KCSeriesDB[S] = {
    val db = KCDBRegistry.get(file, writeMode)
    db match {
      case Some(d) => new KCSeriesDB(d, writeMode, builder, normalize)
      case None => throw new Exception("Unable to get DB")
    }
  }
}

/**
 * The database format used for "series" data,
 * e.g. dose series and time series.
 * Indexed by species/organ/repeat, probe, compound and time/dose.

 * Key size: 12 bytes, value size: 40-70 bytes (for Open TG-Gates)
 * Expected number of records: < 100 million
 * Expected DB size: about 3 G
 *
 *
 */
class KCSeriesDB[S <: Series[S]](db: DB, writeMode: Boolean,
    builder: SeriesBuilder[S], normalize: Boolean)(implicit val context: MatrixContext) extends
  KyotoCabinetDB(db, writeMode) with SeriesDB[S] {

  private[this] def formKey(series: S): Array[Byte] = {
    val r = ByteBuffer.allocate(12)
    r.putInt(series.probe)
    r.putLong(series.classCode)
    r.array()
  }

  private[this] def extractKey(data: Array[Byte]): S = {
    val b = ByteBuffer.wrap(data)
    val probe = b.getInt
    val sclass = b.getLong
    builder.build(sclass, probe)
  }

  private[this] def formValue(series: S): Array[Byte] = {
    val r = ByteBuffer.allocate(14 * series.points.size)
    for (v <- series.points) {
      r.putInt(v.code)
      r.putDouble(v.value.value)
      r.putChar(v.value.call)
    }
    r.array()
  }

  private[this] def extractValue(data: Array[Byte], into: S): S = {
    val b = ByteBuffer.wrap(data)
    val pmap = context.probeMap
    val vs = Vector() ++ (0 until data.size / 14).map(i => {
      val p = b.getInt
      val v = b.getDouble
      val c = b.getChar
      SeriesPoint(p, BasicExprValue(v, c, pmap.unpack(into.probe)))
    })
    builder.rebuild(into, vs)
  }

   /**
   * Obtain the series that match the constraints specified in the key.
   */
  def read(key: S): Iterable[S] = {
    val keys = builder.keysFor(key).map(formKey)
    val data = db.get_bulk(keys.toArray, false)
    var r = Vector[S]()
    for (i <- Range(0, data.length, 2)) {
      val k = data(i)
      val v = data(i + 1)
      r :+= extractValue(v, extractKey(k))
    }
    if (normalize) {
      builder.normalize(r)
    } else {
      r
    }
  }

  def addPoints(s: S): Unit = {
    get(formKey(s)) match {
      case Some(d) =>
        val exist = extractValue(d, s)
        write(exist.addPoints(s, builder))
      case None =>
        write(s)
    }
  }

  def removePoints(s: S): Unit = {
    val k = formKey(s)
    get(k) match {
      case Some(d) =>
        val exist = extractValue(d, s)
        val removed = exist.removePoints(s, builder)
        if (removed.points.size > 0) {
          write(removed)
        } else {
          db.remove(k)
        }
      case None =>
        write(s)
    }
  }

   /**
   * Insert or replace a series.
   */
  private def write(s: S): Unit = {
    val key = formKey(s)
    val v = formValue(s)
    db.set(key, v)
  }
}
