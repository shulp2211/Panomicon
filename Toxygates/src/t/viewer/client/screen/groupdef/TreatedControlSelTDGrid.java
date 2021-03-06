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
package t.viewer.client.screen.groupdef;

import t.viewer.client.screen.Screen;
import t.common.shared.sample.Unit;

/**
 * A SelectionTDGrid that displays counts of both treated and control samples.
 */
public class TreatedControlSelTDGrid extends SelectionTDGrid {

  public TreatedControlSelTDGrid(Screen screen) {
    super(screen);
  }

  protected class TreatedControlUnit extends UnitUI {
    TreatedControlUnit(Unit u) {
      super(u);
    }

    @Override
    protected String unitHoverText() {
      return "Treated samples/Control samples";
    }
    
    @Override
    protected String unitLabel(int treatedCount, int controlCount) {      
      return " " + treatedCount + "/" + controlCount;
    }

  }

  @Override
  protected UnitUI makeUnitUI(Unit unit) {
    return new TreatedControlUnit(unit);
  }
}
