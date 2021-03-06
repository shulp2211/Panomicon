/*
 * Copyright (c) 2012-2019 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition (NIBIOHN), Japan.
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

package t.db.testing

import t.db._
import scala.reflect._

abstract class AbsFakeMatrixDB[E >: Null <: ExprValue](var records: Seq[(Sample, Int, E)] = Vector())(implicit val probeMap: ProbeMap) extends MatrixDB[E, E] {
  var closed = false
  var released = false

  def emptyValue(probe: String): E

  def sortSamples(xs: Iterable[Sample]): Seq[Sample] = xs.toSeq

  def allSamples: Iterable[Sample] = records.map(_._1).distinct

  def valuesForProbe(probe: Int, xs: Seq[Sample]): Iterable[(Sample, E)] = {
    null
  }

  def valuesInSample(x: Sample, probes: Seq[Int], padMissingValues: Boolean): Iterable[E] =
    records.filter(_._1 == x).map(x => x._3)

  def write(s: Sample, probe: Int, e: E) {
    records :+= (s, probe, e)
  }

  def close() {
    closed = true
    println("Fake DB closed")
  }

  def release() {
    released = true
    println("Fake DB released")
  }
}

class FakeBasicMatrixDB(initRecords: Seq[(Sample, Int, BasicExprValue)] = Seq())(implicit probes: ProbeMap)
extends AbsFakeMatrixDB[BasicExprValue](initRecords) {

  def emptyValue(probe: String) = ExprValue(Double.NaN, 'A', probe)

  def deleteSample(x: Sample): Unit = {}
}
