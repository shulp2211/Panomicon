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

package t.viewer.server.matrix

import org.apache.commons.math3.stat.inference.MannWhitneyUTest
import org.apache.commons.math3.stat.inference.TTest
import friedrich.data.immutable._
import t.db.BasicExprValue
import t.db.ExprValue

import scala.collection.mutable.WrappedArray

object ExpressionMatrix {
  val ttest = new TTest()
  val utest = new MannWhitneyUTest()

  /**
   * Rebuild any Seq into the vector type expected by this
   * matrix.
   */
  def fromSeq(s: Seq[BasicExprValue]) = {
    s match {
      //toIndexedSeq will return an immutable IndexedSeq only.
      //Thus, WrappedArray is ineligible.
      //We check this case here to avoid rebuilding arrays into vectors.
      case a: WrappedArray[BasicExprValue] => a
      case _                  => s.toIndexedSeq
    }
  }

  def fromSeqSeq(ss: Seq[Seq[BasicExprValue]]) =
    ss.map(fromSeq).toIndexedSeq

  def safeCountColumns(rows: Seq[Seq[Any]]) =
    if (rows.nonEmpty) { rows.head.size } else 0

  def withRows(data: Seq[Seq[BasicExprValue]], metadata: ExpressionMatrix = null) = {
    if (metadata != null) {
      metadata.copyWith(data)
    } else {
      val rows = data.size
      val columns = safeCountColumns(data)
      new ExpressionMatrix(data.map(fromSeq).toIndexedSeq,
          rows, columns, Map(), Map(), emptyAnnotations(rows))
    }
  }

  def withRows(data: Seq[Seq[BasicExprValue]], rowNames: Seq[String], colNames: Seq[String]) =
    new ExpressionMatrix(fromSeqSeq(data), data.size, safeCountColumns(data),
        Map() ++ rowNames.zipWithIndex, Map() ++ colNames.zipWithIndex,
        emptyAnnotations(data.size))

  val emptyAnnotation = RowAnnotation(null, List())
  def emptyAnnotations(rows: Int) = Array.fill(rows)(emptyAnnotation)
}

case class RowAnnotation(probe: String, atomics: Iterable[String])

/**
 * The main data matrix class. Tracks names of columns and rows.
 * This class is immutable. The various operations produce modified copies.
 */
class ExpressionMatrix(rowData: IndexedSeq[IndexedSeq[BasicExprValue]], rows: Int, columns: Int,
                       rowMap: Map[String, Int], columnMap: Map[String, Int],
                       val annotations: Seq[RowAnnotation])
    extends KeyedDataMatrix[BasicExprValue, IndexedSeq[BasicExprValue], String, String](rowData, rows, columns, rowMap, columnMap) {

  import ExpressionMatrix._
  import t.util.SafeMath._

  type Self = ExpressionMatrix

  println(this)

  override def toString:String = s"ExprMatrix $rows x $columns"

  def makeVector(s: Seq[BasicExprValue]) = ExpressionMatrix.fromSeq(s)

  /**
   * This is the bottom level copyWith method - all the other ones ultimately delegate to this one.
   */
  def copyWith(rowData: Seq[Seq[BasicExprValue]], rowMap: Map[String, Int],
      columnMap: Map[String, Int],
      annotations: Seq[RowAnnotation]): ExpressionMatrix =  {

        new ExpressionMatrix(fromSeqSeq(rowData), rowData.size,
            safeCountColumns(rowData),
            rowMap, columnMap, annotations)
  }

  def copyWith(rowData: Seq[Seq[BasicExprValue]], rowMap: Map[String, Int],
      columnMap: Map[String, Int]): ExpressionMatrix = {
    copyWith(fromSeqSeq(rowData), rowMap, columnMap, annotations)
  }

  def copyWithAnnotations(annots: Seq[RowAnnotation]): ExpressionMatrix = {
    copyWith(rowData, rowMap, columnMap, annots)
  }

  lazy val sortedRowMap = rowMap.toSeq.sortWith(_._2 < _._2)
  lazy val sortedColumnMap = columnMap.toSeq.sortWith(_._2 < _._2)

  lazy val asRows: Seq[ExpressionRow] = toRowVectors.zip(annotations).map(x => {
    val ann = x._2
    ExpressionRow(ann.probe, ann.atomics.toArray, Array(), Array(), Array(), x._1.toArray)
  })

  override def selectRows(rows: Seq[Int]): ExpressionMatrix =
    super.selectRows(rows).copyWithAnnotations(rows.map(annotations(_)))

  def selectRowsFromAtomics(atomics: Seq[String]): ExpressionMatrix = {
    val useProbes = atomics.toSet
    val is = for (
      (r, i) <- asRows.zipWithIndex;
      ats = r.atomicProbes.toSet;
      if useProbes.intersect(ats).nonEmpty
    ) yield i
    selectRows(is)
  }

  /**
   * Append a two column test, which is based on the data in "sourceData".
   * sourceData must have the same number of rows as this matrix.
   */
  def appendTwoColTest(sourceData: ExpressionMatrix, group1: Seq[String], group2: Seq[String],
                       test: (Seq[Double], Seq[Double]) => Double, minValues: Int, colName: String): ExpressionMatrix = {
    val sourceCols1 = sourceData.selectNamedColumns(group1)
    val sourceCols2 = sourceData.selectNamedColumns(group2)

    val ps = sourceCols1.toRowVectors.zip(sourceCols2.toRowVectors).zipWithIndex
    val pvals = ps.map(r => {
      val probe = sourceData.rowAt(r._2)
      val vs1 = r._1._1.filter(_.present).map(_.value)
      val vs2 = r._1._2.filter(_.present).map(_.value)

      if (vs1.size >= minValues && vs2.size >= minValues) {
        new BasicExprValue(test(vs1, vs2), 'P')
      } else {
        new BasicExprValue(Double.NaN, 'A')
      }
    })
    appendColumn(pvals, colName)
  }

  private def equals0(x: Double) = java.lang.Double.compare(x, 0d) == 0

  def appendTTest(sourceData: ExpressionMatrix, group1: Seq[String], group2: Seq[String],
                  colName: String): ExpressionMatrix =
    appendTwoColTest(sourceData, group1, group2,
        (x,y) => ttest.tTest(x.toArray, y.toArray), 2, colName)

  def appendUTest(sourceData: ExpressionMatrix, group1: Seq[String], group2: Seq[String],
                  colName: String): ExpressionMatrix =
    appendTwoColTest(sourceData, group1, group2,
        (x,y) => utest.mannWhitneyUTest(x.toArray, y.toArray), 2, colName)

  def appendDiffTest(sourceData: ExpressionMatrix, group1: Seq[String], group2: Seq[String],
                     colName: String): ExpressionMatrix = {
    def diffTest(a1: Seq[Double], a2: Seq[Double]): Double = safeMean(a1) - safeMean(a2)

    appendTwoColTest(sourceData, group1, group2, diffTest, 1, colName)
  }

  def appendStatic(data: Seq[Double], name: String): ExpressionMatrix = {
    val vs = data.map(x => new BasicExprValue(x, 'P'))
    appendColumn(vs, name)
  }

}
