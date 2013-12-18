package otgviewer.server

import scala.collection.JavaConversions._
import otg.Sample
import otg.SeriesRanking
import otg.Species
import otgviewer.shared.Barcode
import otgviewer.shared.CellType
import otgviewer.shared.DataFilter
import otgviewer.shared.Organism
import otgviewer.shared.Pathology
import otgviewer.shared.RankRule
import otgviewer.shared.Series
import otgviewer.shared.RuleType
import bioweb.shared.array._
import otg.Species
import otg.SeriesRanking
import otg.Context


/**
 * Conversions between Scala and Java types.
 * In many cases these can be used as implicits.
 * The main reason why this is sometimes needed is that for RPC,
 * GWT can only serialise Java classes that follow certain constraints.
 */
object Conversions {
  import language.implicitConversions

  implicit def asScala(filter: DataFilter): otg.Filter = {
    val or = if (filter.cellType == CellType.Vitro) {
      otg.Vitro
    } else {
      otg.Organ(filter.organ.toString()).get
    }
    new otg.Filter(Some(or), otg.RepeatType(filter.repeatType.toString()), 
        otg.Species(filter.organism.toString()));
  }

  implicit def asJava(path: otg.Pathology): Pathology =
    new Pathology(path.barcode, path.topography.getOrElse(null), 
        path.finding.getOrElse(null), 
        path.spontaneous, path.grade.getOrElse(null), path.digitalViewerLink);

  implicit def asJava(annot: otg.Annotation): Annotation = {
    val entries = annot.data.map(x => new Annotation.Entry(x._1, x._2, otg.Annotation.isNumerical(x._1)))
    new Annotation(annot.barcode, new java.util.ArrayList(entries))        
  }

  def asJava(s: Sample): Barcode = new Barcode(s.code, s.individual, s.dose,
    s.time, s.compound);

  implicit def speciesFromFilter(filter: DataFilter): Species = {
    filter.organism match {
      case Organism.Rat   => otg.Rat
      case Organism.Human => otg.Human
    }
  }

  implicit def asScala(filter: DataFilter, series: Series)(implicit context: Context): otg.Series = {
	val sf = asScala(filter)
	val p = speciesFromFilter(filter).probeMap.pack(series.probe)
	new otg.Series(sf.repeatType.get, sf.organ.get, sf.species.get, 
	    p, series.compound, series.timeDose, Vector())
  }

  implicit def asJava(series: otg.Series)(implicit context: Context): Series = {
	new Series(series.compound + " " + series.timeDose, series.probeStr, series.timeDose,
	    series.compound, series.data.map(asJava).toArray)
  }
  
  implicit def asJava(ev: otg.ExprValue): ExpressionValue = new ExpressionValue(ev.value, ev.call)
  //Loses probe information!
  implicit def asScala(ev: ExpressionValue): otg.ExprValue = otg.ExprValue(ev.getValue, ev.getCall, "")
  
  def nullToOption[T](v: T): Option[T] = {
    if (v == null) {
      None
    } else {
      Some(v)
    }
  }

  implicit def asScala(rr: RankRule): SeriesRanking.RankType = {    
    rr.`type`() match {      
      case s: RuleType.Synthetic.type  => {
        println("Correlation curve: " + rr.data.toVector)
        SeriesRanking.MultiSynthetic(rr.data.toVector)
      }
      case r: RuleType.HighVariance.type => SeriesRanking.HighVariance()
      case r: RuleType.LowVariance.type => SeriesRanking.LowVariance()
      case r: RuleType.Sum.type => SeriesRanking.Sum()
      case r: RuleType.NegativeSum.type => SeriesRanking.NegativeSum()
      case r: RuleType.Unchanged.type => SeriesRanking.Unchanged()
      case r: RuleType.MonotonicUp.type => SeriesRanking.MonotonicIncreasing()
      case r: RuleType.MonotonicDown.type => SeriesRanking.MonotonicDecreasing()
      case r: RuleType.MaximalFold.type => SeriesRanking.MaxFold()
      case r: RuleType.MinimalFold.type => SeriesRanking.MinFold()
      case r: RuleType.ReferenceCompound.type => SeriesRanking.ReferenceCompound(rr.compound, rr.dose)
    }
  }
  
  implicit def asJava[T,U](v: (T, U)) = new bioweb.shared.Pair(v._1, v._2)
  
}