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

import java.util
import java.util.{List => JList}

import t.common.server.GWTUtils._
import t.common.shared._
import t.common.shared.sample._
import t.common.shared.sample.search.MatchCondition
import t.db
import t.db.SimpleVarianceSet
import t.model.SampleClass
import t.model.sample.{Attribute, CoreParameter, SampleLike}
import t.sparql._
import t.sparql.secondary._
import t.viewer.client.rpc._
import t.viewer.server.CSVHelper.CSVFile
import t.viewer.server.Conversions._
import t.viewer.server.{rpc, _}
import t.viewer.shared.{Pathology, _}
import t.common.shared.Dataset

import scala.collection.JavaConverters._

class SampleState(instanceURI: Option[String]) {
  var sampleFilter: SampleFilter = SampleFilter(instanceURI = instanceURI)
}

/**
 * Servlet for querying sample related information.
 */
class SampleServiceImpl extends StatefulServlet[SampleState] with SampleService {

  type DataColumn = t.common.shared.sample.DataColumn[Sample]

  var instanceURI: Option[String] = None

  private def sampleStore: SampleStore = context.sampleStore

  protected var uniprot: Uniprot = _
  protected var configuration: Configuration = _

  private def probeStore: ProbeStore = context.probeStore

  lazy val annotationStore = new AnnotationStore(schema, baseConfig)

  override def localInit(conf: Configuration) {
    super.localInit(conf)

    val platforms = new t.sparql.PlatformStore(baseConfig)
    platforms.populateAttributes(baseConfig.attributes)

    this.configuration = conf
    this.instanceURI = conf.instanceURI
  }

  protected def stateKey = "sparql"
  protected def newState = {
    // This default is fine because chooseDatasets always gets called
    // during initialization
    new SampleState(instanceURI)
  }

  def datasetsForUser(userKey: String): Array[Dataset] = {
    val datasets = new DatasetStore(baseConfig.triplestore) with SharedDatasets
    var r: Array[Dataset] = datasets.sharedList(instanceURI).
      filter(ds => Dataset.isDataVisible(ds.getId, userKey)).toArray

    r
  }

  private def sampleFilterFor(datasets: Iterable[Dataset], base: Option[SampleFilter]) = {
     val ids = datasets.toList.map(_.getId)
     val URIs = ids.map(DatasetStore.packURI(_))
     base match {
       case Some(b) => b.copy(datasetURIs = URIs)
       case None => SampleFilter(datasetURIs = URIs)
     }
  }

  def chooseDatasets(userKey: String, userSelection: Array[Dataset]): Array[t.model.SampleClass] = {
    println("Choose datasets: " + userSelection.map(_.getId).mkString(" "))
    val datasets: Iterable[Dataset] = if (userSelection.size == 0) datasetsForUser(userKey) else userSelection

    getState.sampleFilter = sampleFilterFor(datasets, Some(getState.sampleFilter))

    sampleStore.sampleClasses(getState.sampleFilter).map(x => new SampleClass(x.asGWT)).toArray
  }

  @throws[TimeoutException]
  def parameterValues(datasets: Array[Dataset], sampleClass: SampleClass,
                      parameter: String): Array[String] = {
    //Get the parameters without changing the persistent datasets in getState
    val filter = sampleFilterFor(datasets, Some(getState.sampleFilter))
    val attr = baseConfig.attributes.byId(parameter)
    sampleStore.attributeValues(SampleClassFilter(sampleClass).filterAll, attr, filter).
      filter(x => !schema.isControlValue(parameter, x)).toArray
  }

  @throws[TimeoutException]
  def parameterValues(sampleClass: SampleClass, parameter: String): Array[String] = {
    val attr = baseConfig.attributes.byId(parameter)
    sampleStore.attributeValues(SampleClassFilter(sampleClass).filterAll, attr, getState.sampleFilter).
      filter(x => !schema.isControlValue(parameter, x)).toArray
  }

  private def samplesById(ids: Array[String]) =
    sampleStore.samples(SampleClassFilter(), "id", ids, getState.sampleFilter).map(asJavaSample(_)).toArray

  def samplesById(ids: JList[Array[String]]): JList[Array[Sample]] =
    ids.asScala.map(samplesById(_)).asGWT

  @throws[TimeoutException]
  def samples(sampleClass: SampleClass): Array[Sample] = {
    val samples = sampleStore.sampleQuery(SampleClassFilter(sampleClass), getState.sampleFilter)()
    samples.map(asJavaSample).toArray
  }

  @throws[TimeoutException]
  def samplesWithAttributes(sampleClass: SampleClass, importantOnly: Boolean = false
                           ): Array[Sample] = {
    val matchingSamples = samples(sampleClass)
    attributeValuesForSamples(matchingSamples, importantOnly)
  }

  private def samples(sampleClass: SampleClass, param: String,
                      paramValues: Array[String]) =
    sampleStore.samples(SampleClassFilter(sampleClass), param, paramValues, getState.sampleFilter).map(asJavaSample(_))

  @throws[TimeoutException]
  def samples(sampleClasses: Array[SampleClass], param: String,
              paramValues: Array[String]): Array[Sample] =
        sampleClasses.flatMap(x => samples(x, param, paramValues)).distinct.toArray

  @throws[TimeoutException]
  def units(sampleClass: SampleClass,
            param: String, paramValues: Array[String]): Array[Pair[Unit, Unit]] =
      new UnitStore(schema, sampleStore).units(sampleClass, param, paramValues,
        getState.sampleFilter)

  def units(sampleClasses: Array[SampleClass], param: String,
            paramValues: Array[String]): Array[Pair[Unit, Unit]] = {
    sampleClasses.flatMap(units(_, param, paramValues))
  }

  def attributesForSamples(sampleClass: SampleClass): Array[Attribute] = {
    sampleStore.attributesForSamples(SampleClassFilter(sampleClass),
      getState.sampleFilter)()
      .sortBy(attribute => attribute.title())
      .toArray
  }

  @throws[TimeoutException]
  def parameterValuesForSamples(samples: Array[Sample],
                                attributes: Array[Attribute]
                               ): Array[Sample] = {
    val queryResult: Seq[db.Sample] = sampleStore.sampleAttributeValues(samples.map(_.id), attributes)
    queryResult.map(asJavaSample(_)).toArray
  }

  @throws[TimeoutException]
  def attributeValuesAndVariance(samples: Array[Sample],
                                 importantOnly: Boolean = false
                                ): Pair[Array[Sample], util.Map[String, PrecomputedVarianceSet]] = {
    val mp = schema.mediumParameter()
    val numericalAttributes = baseConfig.attributes.getAll.asScala.filter(_.isNumerical).toArray

    val samplesWithAttributes = attributeValuesForSamples(samples, importantOnly)
    val groupedSamples = samplesWithAttributes.groupBy(_.get(CoreParameter.ControlGroup))

    val varianceSetMap: Map[String, PrecomputedVarianceSet] = Map() ++ (for {
      (_, samples) <- groupedSamples
      controlSamples = samples.filter(s => schema.isControlValue(s.get(mp)))
      varianceSet = new PrecomputedVarianceSet(new SimpleVarianceSet(controlSamples),
                                               numericalAttributes)
      sample <- samples
    } yield sample.id -> varianceSet)
    new Pair(samplesWithAttributes,
             new util.HashMap[String, PrecomputedVarianceSet](varianceSetMap.asJava))
  }

  @throws[TimeoutException]
  def attributeValuesForSamples(samples: Array[Sample],
                                 importantOnly: Boolean = false
                                ): Array[Sample] = {
    val keys = if (importantOnly) baseConfig.attributes.getPreviewDisplay.asScala.toSeq
      else baseConfig.attributes.getAll.asScala.toSeq

    samples.map(sample => {
      val ps: Seq[(Attribute, Option[String])] = sampleStore.parameterQuery(sample.id, keys)
      val attributeValueMap = (Map() ++ (for {
        (attribute, valueOption) <- ps
        value <- valueOption
      } yield (attribute, value))).asJava
      new Sample(sample.id, new SampleClass(attributeValueMap))
    })
  }

  @throws[TimeoutException]
  def prepareAnnotationCSVDownload(samples: Array[Sample]): String =
    annotationStore.prepareCSVDownload(sampleStore, samples,
      configuration.csvDirectory, configuration.csvUrlBase)

  def sampleSearch(sc: SampleClass, cond: MatchCondition, maxResults: Int):
      RequestResult[Pair[Sample, Pair[Unit, Unit]]] = {

    val sampleSearch = t.common.server.sample.search.IndividualSearch(cond,
        sc, sampleStore, schema, baseConfig.attributes, getState.sampleFilter)
    val pairs = sampleSearch.pairedResults.take(maxResults).map {
      case (sample, (treated, control)) =>
        new Pair(sample, new Pair(treated, control))
    }.toArray
    new RequestResult(pairs.toArray, pairs.size)
  }

  def unitSearch(sampleClass: SampleClass, condition: MatchCondition, maxResults: Int):
      RequestResult[Pair[Unit, Unit]] = {

    val unitSearch = t.common.server.sample.search.UnitSearch(condition,
      sampleClass, sampleStore, schema, baseConfig.attributes,
      getState.sampleFilter)
    val pairs = unitSearch.pairedResults.take(maxResults).map {
      case (treated, control) =>
        new Pair(treated, control)
    }.toArray
    new RequestResult(pairs, pairs.size)
  }

  def prepareCSVDownload(samples: Array[SampleLike],
      attributes: Array[Attribute]): String = {

    val csvFile = new CSVFile{
      def colCount = attributes.size
    	def rowCount = samples.size + 1

      def apply(x: Int, y: Int) = if (y == 0) {
        attributes(x)
      } else {  //y > 0
        samples(y - 1).get(attributes(x))
      }
    }

    configuration.csvUrlBase + "/" +
      CSVHelper.writeCSV("toxygates", configuration.csvDirectory, csvFile)
  }

  @throws[TimeoutException]
  override def pathologies(column: Array[Sample]): Array[Pathology] =
    column.flatMap(x => sampleStore.pathologies(x.id)).map(
      rpc.Conversions.asJava(_))
}
