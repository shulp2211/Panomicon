package t.sparql

import t.TriplestoreConfig
import t.db.Sample
import otg.Annotation
import t.sparql.secondary.GOTerm
import t.sparql.{Filter => TFilter}
import t.BaseConfig

object Samples extends RDFClass {
  val defaultPrefix = s"$tRoot/sample"
  val itemClass = "t:Sample"
}

case class SampleFilter(instanceURI: Option[String] = None,
    batchURI: Option[String] = None,
    datasetURIs: List[String] = List()) {
  
  def visibilityRel(v: String) = instanceURI match {
    case Some(u) => s"$v ${Batches.memberRelation} <$u> ."
    case None => ""
  }
  
  def instanceFilter: String = visibilityRel("?batchGraph")
  def datasetFilter: String = {
    if (datasetURIs.isEmpty)
      ""
    else 
      " FILTER(?dataset IN (" +
        datasetURIs.map(x => s"<$x>").mkString(",") +
        ") )."    
    }
    
  def batchFilter: String = batchURI match {
      case None => ""
      case Some(bu) => s" FILTER(?batchGraph = <$bu>)."
    }
   
  def standardSampleFilters = s"\n$instanceFilter " +
    s"?batchGraph ${Datasets.memberRelation} ?dataset. " +
    s"?dataset a ${Datasets.itemClass}." +
    s"$datasetFilter $batchFilter\n"
}

abstract class Samples(bc: BaseConfig) extends ListManager(bc.triplestore) {
  import Triplestore._
  import QueryUtils._
  
  def itemClass: String = Samples.itemClass
  def defaultPrefix = Samples.defaultPrefix
  
  val standardAttributes = bc.sampleParameters.required.map(_.identifier)
  val hlAttributes = bc.sampleParameters.highLevel.map(_.identifier)
  val tsCon: TriplestoreConfig = bc.triplestore
     
  val hasRelation = "t:hasSample"
  def hasRelation(batch: String, sample: String): String =
    s"<${Batches.defaultPrefix}/$batch> $hasRelation <$defaultPrefix/$sample>"

  def addSamples(batch: String, samples: Iterable[String]): Unit = {
    ts.update(tPrefixes + " " +
      "insert data { " +
      samples.map(s => hasRelation(batch, s)).mkString(". ") +
      samples.map(s => s"$defaultPrefix/$s a $itemClass.").mkString(" ") +
      " }")
  }
      
  //TODO is this the best way to handle URI/title conversion?
  //Is such conversion needed?
  protected def adjustSample(m: Map[String, String]): Map[String, String] = {
    if (m.contains("dataset")) {
      m + ("dataset" -> Datasets.unpackURI(m("dataset")))
    } else {
      m
    }
  }
   
  protected def graphCon(g: Option[String]) = g.map("<" + _ + ">").getOrElse("?g")
    
  /**
   * The sample query must query for ?batchGraph and ?dataset.
   */
  def sampleQuery(implicit sf: SampleFilter): Query[Vector[Sample]] 
     
  def samples(implicit sf: SampleFilter): Seq[Sample] =
    sampleQuery(sf)()
      
  def allValuesForSampleAttribute(attribute: String, 
      graphURI: Option[String] = None): Iterable[String] = {
    val g = graphCon(graphURI)
    
    val q = tPrefixes + 
    s"SELECT DISTINCT ?q WHERE { GRAPH $g { ?x a t:sample; t:$attribute ?q } }"
    ts.simpleQuery(q)    		
  }
  
  def platforms(samples: Iterable[String]): Iterable[String] = {
    ts.simpleQuery(tPrefixes + " SELECT distinct ?p WHERE { GRAPH ?batchGraph { " + 
    		"?x a t:sample; rdfs:label ?id; t:platform_id ?p }  " +
    		multiFilter("?id", samples.map("\"" + _ + "\"")) +
    		" }")
  }

  def samples(filter: TFilter, fparam: String, fvalues: Iterable[String])
  (implicit sf: SampleFilter): Seq[Sample] = {
    sampleQuery.constrain(filter).constrain(               
      multiFilter(s"?$fparam", fvalues.map("\"" + _ + "\""))
      )()    
  }

  def sampleClasses(implicit sf: SampleFilter): Seq[Map[String, String]]
  
  // Returns: (human readable, identifier, value)
  def annotationQuery(sample: String, 
      querySet: List[String] = Nil): Iterable[(String, String, Option[String])] = {
    List() //TODO think about this. Could be OTG only, or redundant concept
  }
  
  /**
   * Produces human-readable values
   */
  def annotations(sample: String, querySet: List[String] = Nil): Annotation = {
    null //TODO think about this. Could be OTG only, or redundant concept    
  }

  
  def sampleAttributeQuery(attribute: String)
  (implicit sf: SampleFilter): Query[Seq[String]] = {  
      Query(tPrefixes,
      "SELECT DISTINCT ?q " +
      s"WHERE { GRAPH ?batchGraph { " +
      "?x " + attribute + " ?q . ",
      s"} ${sf.standardSampleFilters} } ",
      ts.simpleQueryNonQuiet)
  }

  def attributeValues(filter: TFilter, attribute: String)(implicit sf: SampleFilter) = 
    sampleAttributeQuery("t:" + attribute).constrain(filter)()

  def sampleGroups(implicit sf: SampleFilter): Iterable[(String, Iterable[Sample])] = {
    val q = tPrefixes + 
    "SELECT DISTINCT ?l ?sid WHERE { " +
    s"?g a ${SampleGroups.itemClass}. " +
    sf.visibilityRel("?g") +     
    s"?g ${SampleGroups.memberRelation} ?sid; rdfs:label ?l" +
    "}"
    
    //Note that ?sid is the rdfs:label of samples
    
    val mq = ts.mapQuery(q)
    val byGroup = mq.groupBy(_("l"))
    val allIds = mq.map(_("sid")).distinct
    val withAttributes = sampleQuery.constrain(
        "FILTER (?id IN (" + allIds.map('"' + _ + '"').mkString(",") +")).")()
    val lookup = Map() ++ withAttributes.map(x => (x.identifier -> x))
        
    for ((group, all) <- byGroup; 
      samples = all.flatMap(m => lookup.get(m("sid"))))
      yield (group, samples)
  }
}