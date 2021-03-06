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

package t.viewer.client.table;

import com.google.gwt.cell.client.TextCell;

import t.common.shared.sample.ExpressionRow;
import t.viewer.client.Utils;
import t.viewer.shared.SortKey;

public class ExpressionColumn extends TooltipColumn<ExpressionRow> implements MatrixSortable {
  final int i;
  protected ColumnInfo columnInfo;

  /**
   * @param matColumn column index in the underlying data matrix (e.g. in ManagedMatrixInfo)
   */
  public ExpressionColumn(TextCell tc, int matColumn, ColumnInfo columnInfo) {
    super(tc);
    this.columnInfo = columnInfo;
    this.i = matColumn;
  }

  public ColumnInfo columnInfo() {
    return columnInfo;
  }

  public int matrixColumn() {
    return i;
  }

  @Override
  public SortKey sortKey() {
    return new SortKey.MatrixColumn(i);
  }

  @Override
  public String getValue(ExpressionRow er) {
    if (er != null) {
      if (!er.getValue(i).getPresent()) {
        return "(absent)";
      } else {
        return Utils.formatNumber(er.getValue(i).getValue());
      }
    } else {
      return "";
    }
  }

  @Override protected String getTooltip(ExpressionRow er) {
    return er.getValue(i).getTooltip();
  }
}
