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

package t.viewer.client.components;

import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.TextArea;

public class ResizableTextArea extends TextArea implements RequiresResize {

  public ResizableTextArea(int widthDelta, int heightDelta) {
    this.widthDelta = widthDelta;
    this.heightDelta = heightDelta;
  }

  private final int widthDelta, heightDelta;

  @Override
  public void onResize() {
    int width = getParent().getOffsetWidth();
    int height = getParent().getOffsetHeight();
    if (width > widthDelta && height > heightDelta) {
      setSize((width - widthDelta) + "px", (height - heightDelta) + "px");
    }
  }
}
