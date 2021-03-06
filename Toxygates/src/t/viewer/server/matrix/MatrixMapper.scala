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

import t.db.{BasicExprValue, ExprValue}
import t.viewer.shared.ManagedMatrixInfo

/**
 * A matrix mapper converts a whole matrix from one domain into
 * a matrix in another domain. For example, we might want to convert a
 * transcript matrix into a gene matrix, or a gene matrix into a protein
 * matrix.
 *
 * The only actual use for this presently is converting non-orthogonal matrices
 * into orthogonal (multi-platform) ones.
 *
 * This process changes the number and index keys of the rows, but
 * preserves columns.
 */
class MatrixMapper(val pm: ProbeMapper, val vm: ValueMapper) {

  private def padToSize(vs: Iterable[BasicExprValue], n: Int): Iterable[BasicExprValue] = {
    val diff = n - vs.size
    val empty = (0 until diff).map(x => ExprValue(Double.NaN, 'A'))
    vs ++ empty
  }

  case class MappedValue(post: BasicExprValue, prior: Seq[BasicExprValue])

  /**
   * Converts grouped into (grouped, ungrouped)
   */
  private def convert(from: ExpressionMatrix): (ExpressionMatrix, ExpressionMatrix, Map[Int, Seq[Int]]) = {
    println(s"Convert $from")

    val rangeProbes = pm.range.toSeq
    val fromRowSet = from.rowKeys.toSet

    val nrows = rangeProbes.flatMap(rng => {
      val domProbes = pm.toDomain(rng).toSeq.filter(fromRowSet.contains(_))
      if (!domProbes.isEmpty) {

        //pull out e.g. all the rows corresponding to probes (domain)
        //for gene G1 (range)
        val domainRows = domProbes.map(from.row(_))
        val nr = (0 until from.columns).map(c => {
          val xs = domainRows.map(dr => dr(c)).filter(_.present)
          val v = vm.convert(rng, xs)
          MappedValue(v, xs)
        })
        Some((nr, RowAnnotation(rng, domProbes)))
      } else {
        None
      }
    })

    val cols = from.sortedColumnMap.map(_._1)

    val annots = nrows.map(_._2)
    val groupedVals = nrows.map(_._1.map(_.post))

    //the max. size of each ungrouped column
    val ungroupedSizes = (
      for (
        c <- 0 until from.columns;
        ss = nrows.map(_._1(c).prior.size)
      ) yield ss.max)

    //pad to size to get a matrix
    val ungroupedVals = for (r <- nrows)
      yield (0 until from.columns).flatMap(c => padToSize(r._1(c).prior, ungroupedSizes(c)))

    //The base map will be used for generating tooltips from the ungrouped matrix
    //that we constructed above
    var at = 0
    var baseMap = Map[Int, Seq[Int]]()
    for (i <- 0 until from.columns) {
      baseMap += i -> (at until (at + ungroupedSizes(i)))
      at += ungroupedSizes(i)
    }

    val usedRowNames = annots.map(_.probe)
    val ungroupedColNames = (0 until ungroupedVals(0).size).map(i => s"Ungrouped-$i")
    val grouped = ExpressionMatrix.withRows(groupedVals, usedRowNames, cols).copyWithAnnotations(annots)
    val ungrouped = ExpressionMatrix.withRows(ungroupedVals, usedRowNames, ungroupedColNames)
    (grouped, ungrouped, baseMap)
  }

  def convert(from: ManagedMatrix): ManagedMatrix = {
    val (gr, ungr, bm) = convert(from.rawGrouped)
    val rks = (0 until ungr.rows).map(ungr.rowAt)

    //Note, we re-fix initProbes for the new matrix
    new ManagedMatrix(
      LoadParams(rks, convert(from.currentInfo, rks), ungr, gr, bm)
      )
  }

  /**
   * This conversion keeps the columns and column names (etc),
   * but removes synthetics and filtering options.
   * Task: synthetics handling needs to be tested
   */
  private def convert(from: ManagedMatrixInfo, newRows: Seq[String]): ManagedMatrixInfo = {
    val r = new ManagedMatrixInfo()
    for (i <- 0 until from.numDataColumns()) {
      r.addColumn(false, from.columnName(i), from.columnHint(i),
        from.columnFilter(i), from.columnGroup(i), from.isPValueColumn(i),
        from.samples(i))
    }
    r.setPlatforms(from.getPlatforms())
    r.setNumRows(newRows.size)
    r
  }
}
