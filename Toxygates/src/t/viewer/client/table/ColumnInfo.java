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

import javax.annotation.Nullable;

import com.google.gwt.safehtml.shared.SafeHtml;

import t.viewer.client.Utils;

public class ColumnInfo {

  static final String DEFAULT_COL_WIDTH = "8em";

  private String title, tooltip, width;
  private @Nullable String cellStyleNames, headerStyleNames;
  private boolean sortable, hideable, defaultSortAsc, filterable, filterActive;

  public ColumnInfo(String title, String tooltip, boolean sortable, boolean hideable, String width,
      String cellStyleNames, boolean defaultSortAsc, boolean filterable, boolean filterActive) {
    this.title = title;
    this.sortable = sortable;
    this.hideable = hideable;
    this.width = width;
    this.tooltip = tooltip;
    this.cellStyleNames = cellStyleNames;
    this.defaultSortAsc = defaultSortAsc;
    this.filterable = filterable;
    this.filterActive = filterActive;
  }

  public ColumnInfo(String title, String tooltip, boolean sortable, boolean hideable,
      boolean filterable, boolean filterActive) {
    this(title, tooltip, sortable, hideable, DEFAULT_COL_WIDTH, null, false, filterable,
        filterActive);
  }

  public ColumnInfo(String name, String width, boolean sortable) {
    this(name, name, sortable, true, false, false);
    this.width = width;
  }

  public SafeHtml headerHtml() {
    return Utils.tooltipSpan(tooltip, title);
  }

  /**
   * Adjust title and tooltip so that title fits inside the maximum length.
   */
  public ColumnInfo trimTitle(int maxLength) {
    String ntitle = title;
    if (ntitle.length() > maxLength) {
      ntitle = ntitle.substring(0, maxLength - 2) + "...";
    }
    return new ColumnInfo(ntitle, tooltip, sortable, hideable, width, cellStyleNames,
        defaultSortAsc, filterable, filterActive);
  }

  public boolean filterable() {
    return filterable;
  }

  public boolean filterActive() {
    return filterActive;
  }
  
  public String title() {
    return title;
  }

  public String tooltip() {
    return tooltip;
  }

  public boolean sortable() {
    return sortable;
  }

  public boolean hideable() {
    return hideable;
  }

  public String cellStyleNames() {
    return cellStyleNames;
  }

  public void setCellStyleNames(String v) {
    cellStyleNames = v;
  }

  public String headerStyleNames() {
    return headerStyleNames;
  }

  public void setHeaderStyleNames(String v) {
    headerStyleNames = v;
  }

  public boolean defaultSortAsc() {
    return defaultSortAsc;
  }

  public void setDefaultSortAsc(boolean v) {
    defaultSortAsc = v;
  }

  public String width() {
    return width;
  }

  public void setWidth(String v) {
    width = v;
  }

}
