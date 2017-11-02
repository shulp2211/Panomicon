/*
 * Copyright (c) 2012-2017 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition 
 * (NIBIOHN), Japan.
 *
 * This file is part of Toxygates.
 * 
 * Toxygates is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * Toxygates is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Toxygates. If not,
 * see <http://www.gnu.org/licenses/>.
 */

package otgviewer.client;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import otg.model.sample.OTGAttribute;
import otgviewer.client.components.Screen;
import otgviewer.client.components.ScreenManager;
import otgviewer.client.rpc.SampleServiceAsync;
import otgviewer.shared.Pathology;
import t.common.client.ImageClickCell;
import t.common.shared.GroupUtils;
import t.common.shared.sample.*;
import t.model.SampleClass;
import t.viewer.client.Utils;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.*;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;

/**
 * This screen displays information about pathological findings in a given set of sample groups.
 */
public class PathologyScreen extends Screen {
  public static final String key = "path";

  private CellTable<Pathology> pathologyTable = new CellTable<Pathology>();
  private ScrollPanel scrollPanel = new ScrollPanel();
  private Set<Pathology> pathologies = new HashSet<Pathology>();
  private final Resources resources;

  private SampleClass lastClass;
  private List<Group> lastColumns;

  @Override
  public boolean enabled() {
    // TODO check the groups for vivo samples instead
    // CellType ct = CellType.valueOf(chosenSampleClass.get("test_type"));
    return manager.isConfigured(ColumnScreen.key); // && ct == CellType.Vivo;
  }

  private final SampleServiceAsync sampleService;

  public PathologyScreen(ScreenManager man) {
    super("Pathologies", key, true, man);
    resources = man.resources();
    sampleService = man.sampleService();
    mkTools();
  }

  private HorizontalPanel tools = Utils.mkWidePanel();

  private void mkTools() {
    HTML h = new HTML();
    h.setHTML("<a href=\"" + appInfo().pathologyTermsURL() + "\" target=_new>"
        + "Pathology terms reference</a>");
    tools.add(h);
  }

  @Override
  protected void addToolbars() {
    super.addToolbars();
    addToolbar(tools, 30);
  }

  @Override
  public Widget content() {

    scrollPanel.setWidget(pathologyTable);
    pathologyTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);
    pathologyTable.setWidth("100%");

    TextColumn<Pathology> col = new TextColumn<Pathology>() {
      @Override
      public String getValue(Pathology p) {
        Stream<Group> gs = GroupUtils.groupsFor(chosenColumns, p.barcode());        
        String r = gs.map(g -> g.getName()).collect(Collectors.joining(" "));
        if (r.length() == 0) {
          return "None";
        }
        return r;
      }
    };
    pathologyTable.addColumn(col, "Group");

    //Note: we may need to stop including p.barcode() at some point
    //if pathologies get to have longer barcodes (currently only OTG samples)
    col = new TextColumn<Pathology>() {
      @Override
      public String getValue(Pathology p) {
        Sample b = GroupUtils.sampleFor(chosenColumns, p.barcode());
        return b.get(OTGAttribute.Compound) + "/" + b.getShortTitle(schema()) +
            " [" + p.barcode() + "]";
      }
    };
    pathologyTable.addColumn(col, "Sample");

    col = new TextColumn<Pathology>() {
      @Override
      public String getValue(Pathology p) {
        return p.finding();
      }
    };

    ToolColumn tcl = new ToolColumn(new InspectCell());
    pathologyTable.addColumn(tcl, "");
    pathologyTable.setColumnWidth(tcl, "40px");
    tcl.setCellStyleNames("clickCell");

    pathologyTable.addColumn(col, "Finding");

    col = new TextColumn<Pathology>() {
      @Override
      public String getValue(Pathology p) {
        return p.topography();
      }
    };
    pathologyTable.addColumn(col, "Topography");

    col = new TextColumn<Pathology>() {
      @Override
      public String getValue(Pathology p) {
        return p.grade();
      }
    };
    pathologyTable.addColumn(col, "Grade");

    col = new TextColumn<Pathology>() {
      @Override
      public String getValue(Pathology p) {
        return "" + p.spontaneous();
      }
    };
    pathologyTable.addColumn(col, "Spontaneous");

    Column<Pathology, SafeHtml> lcol = new Column<Pathology, SafeHtml>(new SafeHtmlCell()) {
      @Override
      public SafeHtml getValue(Pathology p) {
        SafeHtmlBuilder b = new SafeHtmlBuilder();
        if (p.viewerLink() != null) {
          b.appendHtmlConstant("<a target=_new href=\"" + p.viewerLink() + "\">Viewer</a>");
        } else {
          b.appendHtmlConstant("No image");
        }
        return b.toSafeHtml();
      }
    };
    pathologyTable.addColumn(lcol, "Digital viewer");

    return scrollPanel;
  }

  @Override
  public void show() {
    super.show();
    if (visible
        && (lastClass == null || !lastClass.equals(chosenSampleClass) || lastColumns == null || !chosenColumns
            .equals(lastColumns))) {
      pathologies.clear();
      for (SampleColumn c : chosenColumns) {
        sampleService.pathologies(c, new AsyncCallback<Pathology[]>() {
          @Override
          public void onFailure(Throwable caught) {
            Window.alert("Unable to get pathologies.");
          }

          @Override
          public void onSuccess(Pathology[] values) {
            pathologies.addAll(Arrays.asList(values));
            pathologyTable.setRowData(new ArrayList<Pathology>(pathologies));
          }
        });
      }
      lastClass = chosenSampleClass;
      lastColumns = chosenColumns;
    }
  }


  class InspectCell extends ImageClickCell.StringImageClickCell {
    InspectCell() {
      super(resources.magnify(), false);
    }

    @Override
    public void onClick(String value) {
      displaySampleDetail(GroupUtils.sampleFor(chosenColumns, value));
    }
  }

  class ToolColumn extends Column<Pathology, String> {
    public ToolColumn(InspectCell tc) {
      super(tc);
    }

    @Override
    public String getValue(Pathology p) {
      return p.barcode();
    }
  }

  @Override
  public String getGuideText() {
    return "This is the list of pathologies in the sample groups you have defined. Click on an icon to see detailed sample information.";
  }
}
