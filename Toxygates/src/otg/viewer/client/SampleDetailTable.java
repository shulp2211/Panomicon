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

package otg.viewer.client;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.cellview.client.SafeHtmlHeader;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.view.client.NoSelectionModel;
import otg.viewer.client.components.OTGScreen;
import t.common.shared.sample.*;
import t.model.sample.Attribute;
import t.viewer.client.Utils;
import t.viewer.client.components.PendingAsyncCallback;
import t.viewer.client.rpc.SampleServiceAsync;
import t.viewer.client.table.TooltipColumn;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A table that displays attribute values for a small set of samples.
 */

public class SampleDetailTable extends Composite {
  private CellTable<Attribute> table;
  private Sample[] samples;
  private SampleServiceAsync sampleService;
  private final @Nullable String sectionTitle;
  private final boolean isSection;
  private final OTGScreen screen;
  public Delegate delegate;

  public static final String DEFAULT_SECTION_TITLE = "Sample details";

  public interface Delegate {
    void sampleDetailTableFinishedSettingData();
  }

  public interface Resources extends CellTable.Resources {
    @Override
    @Source("t/viewer/client/table/Tables.gss")
    CellTable.Style cellTableStyle();
  }

  protected static class AttributeValueColumn extends TooltipColumn<Attribute> {

    private final Sample sample;
    public AttributeValueColumn(Cell<String> cell, Sample sample) {
      super(cell);
      this.sample = sample;
    }

    @Override
    public String getValue(Attribute attribute) {
      return sample.get(attribute);
    }

    @Override
    protected String getTooltip(Attribute attribute) {
      return sample.get(attribute);
    }

    @Override
    protected void htmlBeforeContent(SafeHtmlBuilder sb, Attribute attribute) {
      super.htmlBeforeContent(sb, attribute);
      if (attribute.isNumerical()) {
        try {
          Double value = Double.parseDouble(sample.get(attribute));
        } catch (NumberFormatException e) {
          // TODO: deal with numbers that are above/below
          //          sb.append(TEMPLATES.startStyled("numericalParameterAbove"));
          //          sb.append(TEMPLATES.startStyled("numericalParameterBelow"));
          sb.append(TEMPLATES.startStyled("numericalParameterHealthy"));
        }
      }
    }

    @Override
    protected void htmlAfterContent(SafeHtmlBuilder sb, Attribute object) {
      super.htmlAfterContent(sb, object);
      if (object.isNumerical()) {
        sb.append(TEMPLATES.endStyled());
      }
    }
  }

  public SampleDetailTable(OTGScreen screen, @Nullable String sectionTitle, boolean isSection) {
    this.sectionTitle = sectionTitle != null ? sectionTitle : DEFAULT_SECTION_TITLE;
    this.isSection = isSection;
    this.screen = screen;
    sampleService = screen.manager().sampleService();
    Resources resources = GWT.create(Resources.class);
    table = new CellTable<Attribute>(15, resources);
    initWidget(table);
    table.setWidth("100%", true); // use fixed layout so we can control column width explicitly
    table.setSelectionModel(new NoSelectionModel<Attribute>());
    table.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);
  }

  public @Nullable String sectionTitle() { return sectionTitle; }
  
  public void loadFrom(final HasSamples<Sample> sampleSource, boolean importantOnly) {
    sampleService.samplesWithAttributeValues(sampleSource.getSamples(), importantOnly, new PendingAsyncCallback<Sample[]>(
            screen) {
      @Override
      public void handleFailure(Throwable caught) {
        screen.getLogger().log(Level.WARNING, "sampleService.annotations failed", caught);
        Window.alert("Unable to get sample annotations.");
      }

      @Override
      public void handleSuccess(Sample[] samples) {
        setData(samples);
      }
    });
  }
  
  private void setupColumns(Sample[] samples) {
    this.samples = samples;
    while (table.getColumnCount() > 0) {
      table.removeColumn(0);
    }
    
    TextColumn<Attribute> labelCol = new TextColumn<Attribute>() {
      @Override
      public String getValue(Attribute attribute) {
        return attribute.title();
      }      
    };   
    table.addColumn(labelCol, sectionTitle);
    table.addColumnStyleName(0, "sampleDetailTitleColumn");
    
    TextCell cell = new TextCell();
    for (int i = 1; i < samples.length + 1; ++i) {
      String name = samples[i - 1].id();
      AttributeValueColumn column = new AttributeValueColumn(cell, samples[i-1]);
      String borderStyle = i == 1 ? "darkBorderLeft" : "lightBorderLeft";
      column.setCellStyleNames(borderStyle);
      String displayTitle = abbreviate(name);      
      SafeHtmlHeader header = new SafeHtmlHeader(Utils.tooltipSpan(name, displayTitle));
      header.setHeaderStyleNames(borderStyle);
      table.addColumn(column, header);
      table.addColumnStyleName(i, "sampleDetailDataColumn");
    }
    table.setWidth((15 + 9 * samples.length) + "em", true);
  }
  
  private static String abbreviate(String sampleId) {
    if (sampleId.length() <= 14) {
      return sampleId;
    } else {
      int l = sampleId.length();
      return sampleId.substring(0, 5) + "..." + sampleId.substring(l - 5, l);
    }
  }

  void setData(Sample[] samples) {
    setupColumns(samples);
    if (samples.length > 0) {
      List<Attribute> processed = new ArrayList();
      Sample firstSample = samples[0];

      // This assumes all samples will have the same attributes
      List<Attribute> sortedAttributes = firstSample.sampleClass().getKeys().stream().collect(Collectors.toList());
      Collections.sort(sortedAttributes);
      final int numEntries = sortedAttributes.size();

      for (int i = 0; i < numEntries; i++) {
        Attribute attribute = sortedAttributes.get(i);
        String sectionForAttribute = attribute.section();
        if (!isSection ||
            (sectionForAttribute == null && sectionTitle.equals(DEFAULT_SECTION_TITLE)) ||
            (sectionForAttribute != null && sectionForAttribute.equals(sectionTitle))) {
          processed.add(attribute);
        }
      }
      table.setRowData(processed);
    }
    if (delegate != null) {
      delegate.sampleDetailTableFinishedSettingData();
    }
  }
}
