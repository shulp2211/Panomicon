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

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Widget;
import t.common.shared.Dataset;
import t.common.shared.SharedUtils;
import t.common.shared.sample.Group;
import t.model.SampleClass;
import t.model.sample.AttributeSet;
import t.model.sample.OTGAttribute;
import t.viewer.client.ClientGroup;
import t.viewer.client.Utils;
import t.viewer.client.components.FilterTools;
import t.viewer.client.components.compoundsel.CompoundSelector;
import t.viewer.client.future.Future;
import t.viewer.client.screen.FilterAndSelectorScreen;
import t.viewer.client.screen.ScreenManager;
import t.viewer.client.screen.data.DataScreen;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This screen allows for column (group) definition as well as compound ranking.
 */
public class ColumnScreen extends FilterAndSelectorScreen implements FilterTools.Delegate,
    GroupInspector.Delegate, CompoundSelector.Delegate {
  public final static String key = "columns";

  private GroupInspector groupInspector;

  @Override
  public void loadState(AttributeSet attributes) {
    boolean datasetsChanged = chosenDatasets != null && 
        !chosenDatasets.equals(getStorage().datasetsStorage.getIgnoringException());
    List<String> oldCompounds = chosenCompounds;
    loadDatasetsAndSampleClass(attributes).addCallback(future -> {
      // update group inspector state if compounds were fetched, or if chosen compounds changed
      if (future.doneAndSuccessful() || 
          (future.doneWithoutError() && oldCompounds != null && !oldCompounds.equals(chosenCompounds))) {
        groupInspector.initializeState(chosenSampleClass, chosenCompounds,
            datasetsChanged);
      }
      if (future.doneWithoutError()) {
        groupInspector.loadGroups();
      }
    });
  }
  
  public ColumnScreen(ScreenManager man) {
    super("Sample groups", key, man, man.resources().groupDefinitionHTML(),
        man.resources().groupDefinitionHelp());

    compoundSelector = new CompoundSelector(this, man.schema().majorParameter().title(), true, true);
    compoundSelector.addStyleName("compoundSelector");
    filterTools = new FilterTools(this);
  }

  @Override
  protected void addToolbars() {   
    super.addToolbars();   
    HorizontalPanel hp = Utils.mkHorizontalPanel(false, filterTools);
    addToolbar(hp, 45);
    addLeftbar(compoundSelector, 350);
  }

  @Override
  protected boolean shouldShowStatusBar() {
    return false;
  }

  @Override
  protected Widget content() {
    groupInspector = factory().groupInspector(this, this);
    return groupInspector;
  }

  @Override
  protected Widget bottomContent() {
    HorizontalPanel hp = Utils.mkWidePanel();

    Button b = new Button("Delete all groups",
        (ClickHandler) e -> groupInspector.confirmDeleteAllGroups());     

    Button b2 = new Button("Next: View data", 
        (ClickHandler) e -> {
        if (groupInspector.groups.size() == 0) {
          Window.alert("Please define and activate at least one group.");
        } else {
            manager.attemptProceed(DataScreen.key);
        }
      });

    hp.add(Utils.mkHorizontalPanel(true, b, b2));
    return hp;
  }

  @Override
  public void resizeInterface() {
    // Test carefully in IE8, IE9 and all other browsers if changing this method
    compoundSelector.resizeInterface();
    super.resizeInterface();
  }

  @Override
  public String getGuideText() {
    return "Please define at least one sample group to proceed. Start by selecting compounds to the left. Then select doses and times.";
  }

  public List<Dataset> additionalNeededDatasets(Collection<ClientGroup> groups, 
      List<Dataset> currentDatasets) {
    // If chosenDatasets is empty, the user hasn't chosen datasets, and all of them
    // are considered to be enabled
    if (chosenDatasets.size() == 0) {
      return new ArrayList<Dataset>();
    }
    List<String> neededDatasetNames = Group.collectAll(groups, OTGAttribute.Dataset)
         .collect(Collectors.toList());
    logger.info("Needed datasets: " + SharedUtils.mkString(neededDatasetNames, ", "));
    
    Set<String> enabledDatasetNames = currentDatasets.stream()
        .map(d -> d.getId()).collect(Collectors.toSet());
    logger.info("Enabled: " + SharedUtils.mkString(enabledDatasetNames, ", "));
    
    List<Dataset> additionalNeededDatasets = new ArrayList<Dataset>();
    if (!enabledDatasetNames.containsAll(neededDatasetNames)) {
      HashSet<String> missing = new HashSet<String>(neededDatasetNames);
      missing.removeAll(enabledDatasetNames);
      
      for (Dataset d : manager().datasets()) {
        if (missing.contains(d.getId())) {
          additionalNeededDatasets.add(d);
        }
      }
    }   
    return additionalNeededDatasets;
  }
  
  // FilterTools.Delegate methods 
  @Override
  public Future<String[]> setSampleClassAndFetchCompounds(SampleClass newSampleClass) {
    Future<String[]> future = super.setSampleClassAndFetchCompounds(newSampleClass);
    future.addSuccessCallback(r ->  {
      groupInspector.initializeState(newSampleClass, chosenCompounds, false);
    });
    return future;
  }
  
  @Override
  public Future<?> filterToolsDatasetsChanged(List<Dataset> datasets) {
    Future<?> compoundsFuture = super.filterToolsDatasetsChanged(datasets);
    compoundsFuture.addSuccessCallback(sampleClasses -> {
      groupInspector.initializeState(chosenSampleClass, chosenCompounds, true);
    });
    groupInspector.datasetsChanged(datasets);
    return compoundsFuture;
  }

  // GroupInspector.Delegate methods  
  @Override
  public Future<SampleClass[]> enableDatasetsIfNeeded(Collection<ClientGroup> groups) {
    List<Dataset> additionalNeededDatasets = additionalNeededDatasets(groups, chosenDatasets);
    Future<SampleClass[]> future = new Future<SampleClass[]>();
    
    if (additionalNeededDatasets.size() > 0) {
      List<Dataset> newEnabledList = new ArrayList<Dataset>(additionalNeededDatasets);
      newEnabledList.addAll(chosenDatasets);
      chosenDatasets = newEnabledList;
      getStorage().datasetsStorage.store(chosenDatasets);
      filterTools.setDatasets(chosenDatasets);
    
      fetchSampleClasses(future, chosenDatasets).addSuccessCallback(sampleClasses -> {
        if (sampleClasses != null) {
          Window.alert(additionalNeededDatasets.size() + " dataset(s) were activated " + 
              "because of your group choice.");
        }
      });
    } else {
      future.bypass();
    }
    
    return future;
  }

  @Override
  public void groupInspectorEditGroup(ClientGroup group, SampleClass sampleClass, List<String> compounds) {
    Future<SampleClass[]> sampleClassesFuture =  enableDatasetsIfNeeded(Collections.singletonList(group));
    Future<String[]> compoundsFuture = new Future<String[]>();
    
    warnLaterIfSampleClassInvalid(sampleClassesFuture);
    processSampleClassesLater(sampleClassesFuture, compoundsFuture, sampleClass,
        !sampleClass.equals(chosenSampleClass), compounds);
    chosenSampleClass = getStorage().sampleClassStorage.store(sampleClass);
  
    compoundsFuture.addNonErrorCallback(f1 ->  {
      groupInspector.selectionGrid.initializeState(chosenSampleClass,
          chosenCompounds, group.getUnits(), false).addNonErrorCallback(f2 -> {
        groupInspector.setEditMode();
      });
    });
  }
  
  @Override
  public void groupInspectorClearCompounds() {
    compoundSelector.setChosenCompounds(new ArrayList<String>());
    chosenCompounds = getStorage().compoundsStorage.store(new ArrayList<String>());
  }
  
  // CompoundSelector.Delegate methods
  @Override
  public void compoundSelectorCompoundsChanged(List<String> compounds) {
    super.compoundSelectorCompoundsChanged(compounds);
    groupInspector.setCompounds(compounds);
  }
}
