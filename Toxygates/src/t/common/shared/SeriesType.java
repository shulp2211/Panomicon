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

package t.common.shared;
import t.model.sample.Attribute;
import static t.model.sample.OTGAttribute.*;

/**
 * The type of series to be ranked.
 */
public enum SeriesType {
  Time(DoseLevel, ExposureTime), Dose(ExposureTime, DoseLevel);
  
  private Attribute fixed;
  private Attribute independent;
  
  //GWT constructor
  SeriesType() {}
  
  SeriesType(Attribute fixed, Attribute independent) {
    this.fixed = fixed;
    this.independent = independent;
  }
  
  public Attribute fixedAttribute() { return fixed; }
  public Attribute independentAttribute() { return independent; }
}
