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

package t.intermine

import org.intermine.pathquery.PathQuery
import t.platform.Species.Species
import org.intermine.pathquery.Constraints
import scala.collection.JavaConverters._
import scala.io.Source
import t.platform._
import t.platform.mirna._

class EnsemblRefseq(conn: Connector, sp: Species) extends Query(conn) {

  def makeQuery(constraint: String): org.intermine.pathquery.PathQuery = {
    val pq: org.intermine.pathquery.PathQuery = new PathQuery(model)

    val synonymsView: String = "Gene.synonyms.value"

    pq.addViews("Gene.primaryIdentifier", synonymsView)

    //NM is to match RefSeq IDs like NM_000389
    pq.addConstraint(Constraints.contains(synonymsView, constraint))
    pq.addConstraint(Constraints.equalsExactly("Gene.organism.shortName", sp.shortName))
    println(s"Intermine query: ${pq.toXml}")
    pq
  }

  def ensemblQuery = makeQuery("ENS")
  def refseqQuery = makeQuery("NM_")

  def asMap(q: PathQuery) = {
    queryService.getRowListIterator(q).asScala.map(row => {
      (row.get(0).toString, row.get(1).toString)
    }).toList.groupBy(_._1).map(x => (x._1, x._2.map(_._2)))
  }

  val ensemblMap = asMap(ensemblQuery)
  val refseqMap = asMap(refseqQuery)

  val ensToRefseq: Map[String, Seq[String]] = (for {
    (id, enss) <- ensemblMap.toSeq
    e <- enss
    r <- refseqMap.getOrElse(id, Seq())
  } yield (e, r)).groupBy(_._1).map(x => (x._1 -> x._2.map(_._2)))
}

/**
 * Application to fetch an Ensembl -> RefSeq conversion table from Intermine.
 */
object EnsemblRefseq {
  def main(args: Array[String]) {
    val conn = new Connector("targetmine", "https://targetmine.mizuguchilab.org/targetmine/service")

    for (s <- t.platform.Species.values) {
      val er = new EnsemblRefseq(conn, s)
      println(er.ensemblMap take 10)
      println(er.refseqMap take 10)
      println(er.ensToRefseq take 10)
    }
  }
}

/**
 * Application to import MiRAW output data, converting Ensembl gene IDs into RefSeq transcripts.
 */
object MiRawImporter {
  /**
   * Produce a TargetTable by loading a file previously generated by this tool.
   */
  def makeTable(dbName: String, file: String, knownTranscripts: Set[RefSeq]) = {
    def lines = Source.fromFile(file).getLines
    val info = new BlankSourceInfo(dbName)
    val builder = new TargetTableBuilder

    for (l <- lines) {
      val spl = l.split("\\t")
      val refSeq = RefSeq(spl(2))
      if (knownTranscripts.contains(refSeq))
      builder.add(MiRNA(spl(1)), refSeq, 100.0,
          info)
    }
    builder.build
  }

  /**
   * Map ENS genes into RefSeq transcripts, producing a target table file.
   */
  def main(args: Array[String]) {
    val lines = Source.fromFile(args(0)).getLines.drop(1)
    val species = t.platform.Species.withName(args(1))
    val conn = new Connector("targetmine", "https://targetmine.mizuguchilab.org/targetmine/service")
    val er = new EnsemblRefseq(conn, species)

    for (l <- lines) {
      val s = l.split("\t", -1)
      val mirbase = s(1)
      val gene = s(0)
      val realClass = s(2)
      val prediction = s(3)

      for (ref <- er.ensToRefseq.getOrElse(gene, Seq())) {
        if (prediction == "Positive") {
          println(s"$gene $mirbase\t$ref")
        }
      }
    }
  }
}
