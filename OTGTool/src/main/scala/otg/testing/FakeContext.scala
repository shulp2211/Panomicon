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

package otg.testing

import t.db.SampleMap
import t.db.ProbeMap
import t.{OTGDoseSeriesBuilder, OTGTimeSeriesBuilder}
import t.db.kyotocabinet.KCSeriesDB
import t.db.testing.{TestData => TData}
import t.db.Sample

class FakeContext(sampleMap: SampleMap = TData.dbIdMap,
    probeMap: ProbeMap = TData.probeMap,
    enumMaps: Map[String, Map[String, Int]] = TData.enumMaps)
  extends t.testing.FakeContext(sampleMap, probeMap, enumMaps) {

  val timeSeriesDB = t.db.testing.TestData.memDBHash
  override def timeSeriesBuilder = OTGTimeSeriesBuilder
  override def timeSeriesDBReader = new KCSeriesDB(timeSeriesDB, false, timeSeriesBuilder, true)(this)
  val doseSeriesDB = t.db.testing.TestData.memDBHash
  override def doseSeriesBuilder = OTGDoseSeriesBuilder
  override def doseSeriesDBReader = new KCSeriesDB(doseSeriesDB, false, doseSeriesBuilder, true)(this)
}
