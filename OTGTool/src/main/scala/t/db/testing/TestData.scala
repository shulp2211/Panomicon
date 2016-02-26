/*
 * Copyright (c) 2012-2015 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition
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

package t.db.testing

import kyotocabinet.DB
import t.db.ExtMatrixDB
import t.db.MatrixDB
import t.db.PExprValue
import t.db.ProbeIndex
import t.db.RawExpressionData
import t.db.Sample
import t.db.SampleIndex
import t.db.kyotocabinet.KCExtMatrixDB
import t.testing.FakeContext
import t.platform.OrthologMapping

object TestData {
  def pickOne[T](xs: Seq[T]): T = {
    val n = Math.random * xs.size
    xs(n.toInt)
  }

  private def mm(ss: Seq[String]) =
    Map() ++ ss.zipWithIndex

  val enumMaps = Map(
    "compound_name" -> mm(Seq("acetaminophen", "methapyrilene", "cocoa", "water")),
    "dose_level" -> mm(Seq("Control", "Low", "Middle", "High", "Really high")),
    "organism" -> mm(Seq("Giraffe", "Squirrel", "Rat", "Mouse", "Human")),
    "exposure_time" -> mm(Seq("3 hr", "6 hr", "9 hr", "24 hr")),
    "sin_rep_type" -> mm(Seq("Single", "Repeat")),
    "organ_id" -> mm(Seq("Liver", "Kidney")),
    "test_type" -> mm(Seq("Vitro", "Vivo")))

  private def em(k: String) = enumMaps(k).keySet
  def enumValues(key: String) = em(key)

  def calls = List('A', 'P', 'M')

  val ids = (0 until (5 * 4 * 4 * 3)).toStream.iterator
  val samples = for (
    dose <- em("dose_level"); time <- em("exposure_time");
    ind <- Set("1", "2", "3"); compound <- em("compound_name");
    s = Sample("s" + ids.next, Map("dose_level" -> dose, "individual_id" -> ind,
      "exposure_time" -> time, "compound_name" -> compound))
  ) yield s

  def randomExpr(): (Double, Char, Double) = {
    val v = Math.random * 100000
    val call = pickOne(calls)
    (v, call, Math.abs(Math.random))
  }

  def randomPExpr(probe: String): PExprValue = {
    val v = randomExpr
    new PExprValue(v._1, v._3, v._2, probe)
  }

  val probes = (0 until 500)

  implicit val probeMap = {
    val pmap = Map() ++ probes.map(x => ("probe_" + x -> x))
    new ProbeIndex(pmap)
  }

  val unpackedProbes = probes.map(probeMap.unpack)

  val dbIdMap = {
    val dbIds = Map() ++ samples.zipWithIndex.map(s => (s._1.sampleId -> s._2))
    new SampleIndex(dbIds)
  }

  def makeTestData(sparse: Boolean): RawExpressionData = {
    var testData = Map[Sample, Map[String, (Double, Char, Double)]]()
    for (s <- samples) {
      var thisProbe = Map[String, (Double, Char, Double)]()
      for (p <- probeMap.tokens) {
        if (!sparse || Math.random > 0.5) {
          thisProbe += (p -> randomExpr())
        }
      }
      testData += (s -> thisProbe)
    }
    new RawExpressionData {
      val data = testData
    }
  }

  /**
   * Obtain a cache hash database (in-memory)
   */
  def memDBHash: DB = {
    val r = new DB
    r.open("*", DB.OCREATE | DB.OWRITER)
    r
  }

  /**
   * Ditto, cache tree database
   */
  def memDBTree: DB = {
     val r = new DB
    r.open("%", DB.OCREATE | DB.OWRITER)
    r
  }

  def populate(db: ExtMatrixDB, d: RawExpressionData) {
    val evs = d.asExtValues
    for ((s, vs) <- evs; (p, v) <- vs) {
      db.write(s, probeMap.pack(p), v)
    }
  }

  implicit val context = new FakeContext(dbIdMap, probeMap)

  val orthologs: OrthologMapping = {
    val n = 100
    val pmap = probeMap
    val orths = (0 until n).map(p =>
      List(pmap.unpack(p), pmap.unpack(p + n), pmap.unpack(p + n * 2)))
    OrthologMapping("test", orths)
  }
}
