package otgviewer.server

import com.google.gwt.user.server.rpc.RemoteServiceServlet
import otgviewer.shared.DataFilter
import otg.OTGSeriesQuery
import otg.SeriesMatchContext
import otgviewer.shared.RankRule
import otgviewer.client.SeriesService
import otg.sparql.AffyProbes
import otgviewer.shared.Series
import otgviewer.shared.MatchResult
import java.util.ArrayList
import java.util.{ List => JList, ArrayList }
import otgviewer.shared.NoSuchProbeException
import kyotocabinet.DB
import javax.servlet.ServletConfig
import javax.servlet.ServletException
import otg.sparql.OwlimLocalRDF


class SeriesServiceImpl extends RemoteServiceServlet with SeriesService {
  import Conversions._
  import scala.collection.JavaConversions._
  import UtilsS._

  import java.lang.{Double => JDouble}

  private var seriesDB: DB = _

  @throws(classOf[ServletException])
  override def init(config: ServletConfig) {
    super.init(config)
    localInit(Configuration.fromServletConfig(config))
  }

  // Useful for testing
  def localInit(config: Configuration) {
    val homePath = config.toxygatesHomeDir
    seriesDB = OTGSeriesQuery.open(homePath + "/otgfs.kct")
    otg.Configuration.otgHomeDir = config.toxygatesHomeDir
    otg.Configuration.owlimRepositoryName = config.owlimRepositoryName
    println("Series DB is open")
  }
  
  override def destroy() {
    seriesDB.close()
    super.destroy()
  }
  
  def rankedCompounds(filter: DataFilter, rules: Array[RankRule]): Array[MatchResult] = {
    val nnr = rules.takeWhile(_ != null)
    var srs = nnr.map(asScala(_))    
    var probesRules = nnr.map(_.probe).zip(srs)
    
    //Convert the input probes (which may actually be genes) into definite probes
    probesRules = probesRules.flatMap(pr => {
      val resolved = AffyProbes.identifiersToProbes(filter, Array(pr._1), true, true)
      if (resolved.size == 0) {
        throw new NoSuchProbeException(pr._1)
      }
      resolved.map(r => (r.identifier, pr._2))
    })
    
    //TODO: probe is actually irrelevant here but the API is not well designed
    //Same for timeDose = High
    val key = asScala(filter, new otgviewer.shared.Series("", probesRules.head._1, "High", null, Array.empty)) 
    
    val ranked = OTGSeriesQuery.rankCompoundsCombined(SeriesMatchContext(seriesDB, filter, key), probesRules) 
    val r = ranked.map(p => new MatchResult(p._1, p._2._1, p._2._2)).toArray
    val rr = r.sortWith((x1, x2) => {
      if (JDouble.isNaN(x1.score)) {
        false
      } else if (JDouble.isNaN(x2.score)) {
        true
      } else {
        x1.score > x2.score
      }
    })

    for (s <- rr.take(10)) {
      println(s)
    }
    rr
  }
  
  
  def getSingleSeries(filter: DataFilter, probe: String, timeDose: String, compound: String): Series = {
    OTGSeriesQuery.getSeries(seriesDB, 
        asScala(filter, new Series("", probe, timeDose, compound, Array.empty))
        ).head
  }

  def getSeries(filter: DataFilter, probes: Array[String], timeDose: String, compounds: Array[String]): JList[Series] = {
    val validated = AffyProbes.identifiersToProbes(filter, probes, true, true).map(_.identifier)
    val ss = validated.flatMap(p =>
      compounds.flatMap(c =>
        OTGSeriesQuery.getSeries(seriesDB, asScala(filter, new Series("", p, timeDose, c, Array.empty)))
        ))
    val jss = ss.map(asJava(_))
    new ArrayList[Series](asJavaCollection(jss))
  }
}