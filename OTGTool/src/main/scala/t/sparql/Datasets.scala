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

package t.sparql

import t.TriplestoreConfig

/**
 * Datasets group batches in such a way that the user
 * can control visibility.
 * Each batch belongs to exactly one dataset.
 */
object Datasets extends RDFClass {
  val defaultPrefix: String = s"$tRoot/dataset"
  val memberRelation = "t:visibleIn"
  val itemClass = "t:dataset"
}

class Datasets(config: TriplestoreConfig) extends BatchGroups(config) {
  import Triplestore._

  def memberRelation = Datasets.memberRelation
  def itemClass: String = Datasets.itemClass
  def groupClass = Datasets.itemClass
  def groupPrefix = Datasets.defaultPrefix
  def defaultPrefix = Datasets.defaultPrefix

  def descriptions: Map[String, String] = {
    Map() ++ ts.mapQuery(s"$tPrefixes select ?l ?desc where { ?item a $itemClass; rdfs:label ?l ; " +
      "t:description ?desc } ").map(x => {
      x("l") -> x("desc")
    })
  }

  def setDescription(name: String, desc: String) = {
    ts.update(s"$tPrefixes delete { <$defaultPrefix/$name> t:description ?desc } " +
      s"where { <$defaultPrefix/$name> t:description ?desc } ")
    ts.update(s"$tPrefixes insert data { <$defaultPrefix/$name> t:description " +
      "\"" + desc + "\" } ")
  }

  def withBatchesInInstance(instanceURI: String): Seq[String] = {
    ts.simpleQuery(s"$tPrefixes select distinct ?l WHERE " +
      s"{ ?item a $itemClass; rdfs:label ?l. " +
      s"?b a ${Batches.itemClass}; $memberRelation ?item; " +
        s"${Batches.memberRelation} <$instanceURI> }")
  }
}
