/*
 * Copyright (c) 2012-2018 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition 
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

package otg.viewer.client.components;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gwt.user.client.ui.*;

import t.common.shared.*;
import t.common.shared.sample.*;
import t.model.SampleClass;
import t.viewer.client.Utils;
import t.viewer.client.components.CallbackWaiter;
import t.viewer.client.components.PendingAsyncCallback;
import t.viewer.client.rpc.SampleServiceAsync;

/**
 * A widget that displays times and doses for a number of compounds in a grid layout. For each
 * position in the grid, an arbitrary widget can be displayed.
 */
abstract public class TimeDoseGrid extends Composite {
  private Grid grid = new Grid();

  protected VerticalPanel rootPanel;
  protected VerticalPanel mainPanel;

  protected final boolean hasDoseTimeGUIs;

  protected final SampleServiceAsync sampleService;

  protected OTGScreen screen;
  protected final DataSchema schema;

  protected List<String> mediumValues = new ArrayList<String>();
  protected List<String> minorValues = new ArrayList<String>();

  // First pair member: treated samples, second: control samples or null
  protected List<Pair<Unit, Unit>> availableUnits = new ArrayList<Pair<Unit, Unit>>();
  protected Logger logger = SharedUtils.getLogger("tdgrid");

  protected String emptyMessage;

  protected SampleClass chosenSampleClass;
  protected List<String> chosenCompounds = new ArrayList<String>();

  /**
   * To be overridden by subclasses
   */
  protected void initTools(HorizontalPanel toolPanel) {}

  public TimeDoseGrid(OTGScreen screen, boolean hasDoseTimeGUIs) {
    rootPanel = Utils.mkVerticalPanel();
    this.screen = screen;
    this.sampleService = screen.manager().sampleService();
    initWidget(rootPanel);
    rootPanel.setWidth("730px");
    mainPanel = new VerticalPanel();
    this.schema = screen.schema();
    try {
      mediumValues = new ArrayList<String>();
      String[] mvs = schema.sortedValues(schema.mediumParameter());
      for (String v : mvs) {
        if (!schema.isControlValue(v)) {
          mediumValues.add(v);
        }
      }
    } catch (Exception e) {
      logger.warning("Unable to sort medium parameters");
    }

//    logger.info("Medium: " + schema.mediumParameter().id() + 
//        " minor: " + schema.minorParameter().id());

    HorizontalPanel selectionPanel = Utils.mkHorizontalPanel();
    mainPanel.add(selectionPanel);
    initTools(selectionPanel);
    selectionPanel.setSpacing(2);

    this.hasDoseTimeGUIs = hasDoseTimeGUIs;
    String mtitle = schema.majorParameter().title();
    emptyMessage = "Please select at least one " + mtitle;

    grid.addStyleName("timeDoseGrid");
    grid.setWidth("100%");
    grid.setBorderWidth(0);
    mainPanel.add(grid);
  }
  
  public void initializeState(SampleClass sampleClass, List<String> compounds) {
    CallbackWaiter waiter = new CallbackWaiter(screen);
    
    boolean shouldFetchMinors = prepareToFetchMinors(sampleClass);
    boolean shouldFetchSamples = prepareToFetchSamples(compounds);
 
    CallbackWaiter.MultiCallback<String[]> minorsCallback = shouldFetchMinors ? 
        waiter.makeCallback("Unable to fetch minor parameter for samples") : null;
    CallbackWaiter.MultiCallback<Pair<Unit, Unit>[]> samplesCallback = shouldFetchSamples ?
        waiter.makeCallback("Unable to obtain samples.") : null;
    
    waiter.setFinalAction(successful -> {
      if (successful) {
        if (shouldFetchSamples) {
          availableUnits = Arrays.asList(samplesCallback.result());
          samplesAvailable(); // danger here
        }
        if (shouldFetchMinors) {
          try {
            schema.sort(schema.minorParameter(), minorsCallback.result());
          } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to sort times", e);
          }
          minorValues = Arrays.asList(minorsCallback.result());
          drawGridInner(grid);
          onMinorsDone(); // also danger here
        }
      }
    });
    
    if (shouldFetchMinors) {
      sampleService.parameterValues(chosenSampleClass, schema.minorParameter().id(), 
          minorsCallback);
    }
    if (shouldFetchSamples) {
      sampleService.units(chosenSampleClass, schema.majorParameter().id(), 
          chosenCompounds.toArray(new String[0]),
          samplesCallback);
    }
  }
  
  public void changeCompounds(List<String> compounds) {
    if (prepareToFetchSamples(compounds)) {
      sampleService.units(chosenSampleClass, schema.majorParameter().id(), 
          chosenCompounds.toArray(new String[0]),
          new PendingAsyncCallback<Pair<Unit, Unit>[]>(screen, "", result -> {
            availableUnits = Arrays.asList(result);
            drawGridInner(grid);
            samplesAvailable(); // danger here
          }));
    }
  }
  
  private boolean prepareToFetchMinors(SampleClass sampleClass) {
    if (!sampleClass.equals(chosenSampleClass)) {
      chosenSampleClass = sampleClass;
      minorValues = new ArrayList<String>();
      return true;
    }
    return false;
  }
  
  private boolean prepareToFetchSamples(List<String> compounds) {
    chosenCompounds = compounds;
    rootPanel.clear();
    if (compounds.isEmpty()) {
      setEmptyMessage(emptyMessage);
      return false;
    } else {
      rootPanel.add(mainPanel);
      redrawGrid();
      return true; 
    }
  }

  /**
   * Change the message that is to be displayed when no major values have been selected.
   */
  public void setEmptyMessage(String message) {
    this.emptyMessage = message;
    if (chosenCompounds.isEmpty()) {
      rootPanel.add(Utils.mkEmphLabel(emptyMessage));
    }
  }
  
  protected void onMinorsDone() {}
  protected void samplesAvailable() {}
  
  protected String keyFor(Sample b) {
    return SampleClassUtils.tripleString(b.sampleClass(), schema);
  }

  private void redrawGrid() {
    final int numRows = chosenCompounds.size() + 1 + (hasDoseTimeGUIs ? 1 : 0);

    grid.resize(numRows, mediumValues.size() + 1);

    int r = 0;
    for (int i = 0; i < mediumValues.size(); ++i) {
      grid.setWidget(r, i + 1, Utils.mkEmphLabel(mediumValues.get(i)));
    }
    r++;

    if (hasDoseTimeGUIs) {
      grid.setWidget(r, 0, new Label("All"));
      r++;
    }

    for (int i = 1; i < chosenCompounds.size() + 1; ++i) {
      grid.setWidget(r, 0, Utils.mkEmphLabel(chosenCompounds.get(i - 1)));
      r++;
    }
  }

  /**
   * Obtain the widget to display for a compound/dose/time combination.
   */
  abstract protected Widget guiForUnit(Unit unit);

  /**
   * An optional extra widget on the right hand side of a compound/dose combination.
   */
  protected Widget guiForCompoundDose(int compound, int dose) {
    return null;
  }

  /**
   * An optional extra widget above all compounds for a given time/dose combination.
   */
  protected Widget guiForDoseTime(int dose, int time) {
    return null;
  }

  protected void drawGridInner(Grid grid) {
    int r = 1;
    final int numMed = mediumValues.size();
    final int numMin = minorValues.size();
    if (hasDoseTimeGUIs && chosenCompounds.size() > 0) {
      for (int d = 0; d < numMed; ++d) {
        HorizontalPanel hp = Utils.mkHorizontalPanel(true);
        for (int t = 0; t < numMin; ++t) {
          hp.add(guiForDoseTime(d, t));
        }
        SimplePanel sp = new SimplePanel(hp);
        sp.addStyleName("doseTimeBox");
        grid.setWidget(r, d + 1, sp);
      }
      r++;
    }

    List<Pair<Unit, Unit>> allUnits = new ArrayList<Pair<Unit, Unit>>();
    for (int c = 0; c < chosenCompounds.size(); ++c) {
      for (int d = 0; d < numMed; ++d) {
        HorizontalPanel hp = Utils.mkHorizontalPanel(true);
        for (int t = 0; t < numMin; ++t) {
          SampleClass sc = new SampleClass();
          sc.put(schema.majorParameter(), chosenCompounds.get(c));
          sc.put(schema.mediumParameter(), mediumValues.get(d));
          sc.put(schema.minorParameter(), minorValues.get(t));
          sc.mergeDeferred(chosenSampleClass);
          Unit unit = new Unit(sc, new Sample[] {});
          allUnits.add(new Pair<Unit, Unit>(unit, null));
          hp.add(guiForUnit(unit));
        }
        Widget fin = guiForCompoundDose(c, d);
        if (fin != null) {
          hp.add(fin);
        }

        SimplePanel sp = new SimplePanel(hp);
        sp.addStyleName("compoundDoseTimeBox");
        grid.setWidget(r, d + 1, sp);
      }
      r++;
    }
    if (availableUnits.size() == 0 && allUnits.size() > 0) {
      availableUnits = allUnits;
    }
  }
}
