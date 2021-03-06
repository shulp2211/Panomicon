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

package t.viewer.server.rpc

import t._
import t.db.MatrixContext
import t.model.sample.OTGAttribute
import t.model.sample.OTGAttribute._
import t.viewer.shared
import t.viewer.shared.{RankRule, Series}

import scala.language.implicitConversions

/**
 * Conversions between Scala and Java types.
 * In many cases these can be used as implicits.
 * The main reason why this is sometimes needed is that for RPC,
 * GWT can only serialise Java classes that follow certain constraints.
 */
object Conversions {

  implicit def asJava(path: t.Pathology): shared.Pathology =
    new shared.Pathology(path.sampleId, path.topography.getOrElse(null),
      path.finding.getOrElse(null),
      path.spontaneous, path.grade.getOrElse(null), path.digitalViewerLink);

  implicit def asScala(series: Series)(implicit context: MatrixContext): OTGSeries = {
    val p = context.probeMap.pack(series.probe) //Task: filtering
    val sc = series.sampleClass
    val seriesType = if (series.independentParam() == OTGAttribute.ExposureTime)
      TimeSeries else DoseSeries

    new OTGSeries(seriesType, sc.get(Repeat),
      sc.get(Organ), sc.get(Organism),
      p, sc.get(Compound), sc.get(DoseLevel), sc.get(TestType), Vector())
  }

  def asJava(series: OTGSeries, geneSym: String)(implicit context: Context): Series = {
    implicit val mc = context.matrix
    val name = series.compound + " " + series.doseOrTime
    val sc = new t.model.SampleClass
    sc.put(series.seriesType.lastConstraint, series.doseOrTime)
    sc.put(Compound, series.compound)
    sc.put(Organism, series.organism)
    sc.put(TestType, series.testType)
    sc.put(Organ, series.organ)
    sc.put(Repeat, series.repeat)
    new Series(name, series.probeStr, geneSym, series.seriesType.independentVariable,
        sc, series.values.map(t.viewer.server.Conversions.asJava).toArray)
  }

  implicit def asJava(series: OTGSeries)(implicit context: Context): Series = {
    asJava(series, "")
  }

  implicit def asScala(rr: RankRule): SeriesRanking.RankType = {
    import t.viewer.shared.RuleType._
    rr.`type`() match {
      case Synthetic => {
        println("Correlation curve: " + rr.data.toVector)
        SeriesRanking.MultiSynthetic(rr.data.toVector)
      }
      case HighVariance      => SeriesRanking.HighVariance
      case LowVariance       => SeriesRanking.LowVariance
      case Sum               => SeriesRanking.Sum
      case NegativeSum       => SeriesRanking.NegativeSum
      case Unchanged         => SeriesRanking.Unchanged
      case MonotonicUp       => SeriesRanking.MonotonicIncreasing
      case MonotonicDown     => SeriesRanking.MonotonicDecreasing
      case MaximalFold       => SeriesRanking.MaxFold
      case MinimalFold       => SeriesRanking.MinFold
      case ReferenceCompound => new SeriesRanking.ReferenceCompound(rr.compound, rr.dose)
    }
  }
}
