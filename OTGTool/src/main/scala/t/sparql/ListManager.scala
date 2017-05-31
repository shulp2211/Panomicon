/*
 * Copyright (c) 2012-2017 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition
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

import Triplestore.tPrefixes
import t.TriplestoreConfig
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat
import t.Closeable

/**
 * Manages a list of items of some given class in the triplestore.
 */
abstract class ListManager(config: TriplestoreConfig) extends Closeable {
  import Triplestore._

  //RDFS class
  def itemClass: String

  //URI prefix
  def defaultPrefix: String

  lazy val ts = config.get

  def close() {
    ts.close()
  }

  def list(): Seq[String] = {
    ts.simpleQuery(s"$tPrefixes\n SELECT ?l { ?x a $itemClass ; rdfs:label ?l }")
  }

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  def addWithTimestamp(name: String, comment: String): Unit = {
    if (comment.contains('"')) {
      throw new Exception("A comment may not contain the \" character.")
    }
    val encodedDate = dateFormat.format(new Date())

    ts.update(s"$tPrefixes\n INSERT DATA { <$defaultPrefix/$name> a $itemClass ; " +
      " rdfs:label \"" + name + "\"; t:comment \"" + comment + "\"; " +
      " t:timestamp \"" + encodedDate + "\". " +
      " }")
  }

  def add(name: String): Unit = {
    ts.update(s"$tPrefixes\n INSERT DATA { <$defaultPrefix/$name> a $itemClass ; " +
      " rdfs:label \"" + name + "\" }")
  }

  private def attributeQuery[T](attr: String, decode: String => T): Map[String, T] = {
    Map() ++ ts.mapQuery(s"$tPrefixes SELECT ?l ?att WHERE { ?item a $itemClass; rdfs:label ?l ; " +
      s"$attr ?att } ").map(x => {
      x("l") -> decode(x("att"))
    })
  }

  def timestamps: Map[String, Date] =
    attributeQuery("t:timestamp", dateFormat.parse)

  def comments: Map[String, String] =
    attributeQuery("t:comment", x => x)

  def publicComments: Map[String, String] =
    attributeQuery("t:publicComment", x => x)

  def delete(name: String): Unit = {
    ts.update(s"$tPrefixes\n " +
      s"DELETE { <$defaultPrefix/$name> ?p ?o. } \n" +
      s"WHERE { <$defaultPrefix/$name> ?p ?o. } ")

    ts.update(s"$tPrefixes\n " +
      s"DELETE { ?s ?p <$defaultPrefix/$name> . } \n" +
      s"WHERE { ?s ?p <$defaultPrefix/$name> . } ")
  }

  def updateSinglePredicate(name: String, key: String, value: String): Unit = {
       ts.update(s"$tPrefixes\n " +
        s"DELETE { <$defaultPrefix/$name> $key ?o. } \n" +
        s"WHERE { <$defaultPrefix/$name> $key ?o. } ")
    ts.update(s"$tPrefixes\n " +
        s"INSERT DATA { <$defaultPrefix/$name> $key $value }")
  }

  def setComment(name: String, comment: String): Unit = {
    updateSinglePredicate(name, "t:comment", "\"" + comment + "\"")
  }

  def setPublicComment(name: String, comment: String): Unit = {
    updateSinglePredicate(name, "t:publicComment", "\"" + comment + "\"")
  }
}
