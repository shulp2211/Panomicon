/*
 * Copyright (c) 2012-2015 Toxygates authors, National Institutes of Biomedical Innovation, Health
 * and Nutrition (NIBIOHN), Japan.
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

package otgviewer.client.intermine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import otgviewer.client.StringListsStoreHelper;
import otgviewer.client.components.PendingAsyncCallback;
import otgviewer.client.components.Screen;
import otgviewer.client.dialog.TargetMineEnrichDialog;
import otgviewer.client.dialog.TargetMineSyncDialog;
import otgviewer.shared.intermine.EnrichmentParams;
import otgviewer.shared.intermine.IntermineInstance;
import t.common.client.components.StringArrayTable;
import t.common.shared.ClusteringList;
import t.common.shared.ItemList;
import t.common.shared.SharedUtils;
import t.common.shared.StringList;
import t.viewer.client.dialog.DialogPosition;
import t.viewer.client.dialog.InteractionDialog;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;

/**
 * Client side helper for TargetMine data import/export.
 */
public class TargetMineData {

  final Screen parent;
  final IntermineServiceAsync tmService = (IntermineServiceAsync) GWT
      .create(IntermineService.class);

  private Logger logger = SharedUtils.getLogger("intermine");
  private @Nullable IntermineInstance preferredInstance;
  
  public TargetMineData(Screen parent, @Nullable IntermineInstance preferredInstance) {
    this.parent = parent;
    this.preferredInstance = preferredInstance;
  }

  DialogBox dialog;

  /**
   * PreferredInstance must not be null
   * @param asProbes
   */
  public void importLists(final boolean asProbes) {
    InteractionDialog ui = new TargetMineSyncDialog(parent, "Import", true, true, preferredInstance) {
      @Override
      protected void userProceed(IntermineInstance instance, String user, String pass, boolean replace) {
        super.userProceed();
        doImport(instance, user, pass, asProbes, replace);
      }

    };
    ui.display("TargetMine import details", DialogPosition.Center);
  }

  private void doImport(final IntermineInstance instance, 
      final String user, final String pass, final boolean asProbes,
      final boolean replace) {
    tmService.importLists(instance, user, pass, asProbes, new PendingAsyncCallback<StringList[]>(
        parent, "Unable to import lists from TargetMine") {
      public void handleSuccess(StringList[] data) {
        Collection<ItemList> rebuild = 
            StringListsStoreHelper.rebuildLists(parent, Arrays.asList(data));
        List<StringList> normal = StringList.pickProbeLists(rebuild, null);
        List<ClusteringList> clustering = ClusteringList.pickUserClusteringLists(rebuild, null);
        
        //TODO revise pop-up message handling for this process
        parent.itemListsChanged(
            mergeLists(parent.chosenItemLists, normal, replace));        
        parent.storeItemLists(parent.getParser());
        parent.clusteringListsChanged(
            mergeLists(parent.chosenClusteringList, clustering, replace));
        parent.storeClusteringLists(parent.getParser());
      }
    });
  }

  /**
   * PreferredInstance must not be null
   * @param asProbes
   */
  public void exportLists() {
    InteractionDialog ui = new TargetMineSyncDialog(parent, "Export", true, true, preferredInstance) {
      @Override
      protected void userProceed(IntermineInstance instance, String user, String pass, boolean replace) {
        super.userProceed();
        doExport(instance, user, pass, StringListsStoreHelper.compileLists(parent), replace);   
      }

    };
    ui.display("TargetMine export details", DialogPosition.Center);
  }

  // Could factor out code that is shared with doImport, but the two dialogs may diverge
  // further in the future.
  private void doExport(final IntermineInstance instance, final String user, final String pass, 
      final List<StringList> lists,
      final boolean replace) {
    tmService.exportLists(instance, user, pass, lists.toArray(new StringList[0]), replace,
        new PendingAsyncCallback<Void>(parent,
            "Unable to export lists to TargetMine") {
          public void handleSuccess(Void v) {
            Window.alert("The lists were successfully exported.");
          }
        });
  }

  public void enrich(final StringList probes) {
    InteractionDialog ui = new TargetMineEnrichDialog(parent, "Enrich", preferredInstance) {
      @Override
      protected void userProceed(IntermineInstance instance, String user, String pass, boolean replace) {
        super.userProceed();
        if (probes.items() != null && probes.items().length > 0) {        
          doEnrich(instance, probes, getParams());
        } else {
          Window.alert("Please define and select a gene set first.");
        }        
      }
    };
    ui.display("Enrichment", DialogPosition.Center);
  }

  public void doEnrich(final IntermineInstance instance, StringList list,
      EnrichmentParams params) {
    tmService.enrichment(instance, list, params, new PendingAsyncCallback<String[][]>(parent,
        "Unable to perform enrichment analysis") {
      public void handleSuccess(String[][] result) {
        StringArrayTable.displayDialog(result, "Enrichment results", 800, 600);            
      }
    });
  }
  
  public void multiEnrich(final StringList[] lists) {
    InteractionDialog ui = new TargetMineEnrichDialog(parent, "Cluster enrichment", preferredInstance) {
      @Override
      protected void userProceed(IntermineInstance instance, String user, String pass, boolean replace) {
        super.userProceed();
        doEnrich(instance, lists, getParams());
      }
    };
    ui.display("Cluster enrichment", DialogPosition.Center);
  }
  
  private static String[] append(String[] first, String[] last) {    
    String[] r = new String[first.length + last.length];
    for (int i = 0; i < first.length; ++i) {
      r[i] = first[i];      
    }
    for (int i = 0; i < last.length; ++i) {
      r[i + first.length] = last[i];
    }
    return r;    
  }
  
  public void doEnrich(IntermineInstance instance, final StringList[] lists,
      EnrichmentParams params) {
    tmService.multiEnrichment(instance, lists, params,
         new PendingAsyncCallback<String[][][]>(parent,
        "Unable to perform enrichment analysis") {
      public void handleSuccess(String[][][] result) {
        List<String[]> best = new ArrayList<String[]>();
        best.add(append(new String[] {"Cluster", "Size"}, result[0][0])); //Headers
        int i = 1;
        for (String[][] clust: result) {
          int n = lists[i-1].size();
          if (clust.length < 2) {
            //TODO don't hardcode length here
            String[] res = new String[] { "Cluster " + i, "" + n, "(No result)", "", "", "" };
            best.add(res);
          } else {
            best.add(append(new String[] {"Cluster " + i, "" + n }, clust[1]));
          }
          i += 1;
        }
        StringArrayTable.displayDialog(best.toArray(new String[0][0]), "Best enrichment results", 800, 400);            
      }
    });
  }

  List<ItemList> mergeLists(List<? extends ItemList> into, List<? extends ItemList> from,
      boolean replace) {
    Map<String, ItemList> allLists = new HashMap<String, ItemList>();
    int addedLists = 0;
    int addedItems = 0;
    int nonImported = 0;

    for (ItemList l : into) {
      allLists.put(l.name(), l);
    }

    for (ItemList l : from) {
      if (replace || !allLists.containsKey(l.name())) {
        allLists.put(l.name(), l);
        addedLists++;
        addedItems += l.size();        

        logger.info("Import list " + l.name() + " size " + l.size());
      } else if (allLists.containsKey(l.name())) {
        logger.info("Do not import list " + l.name());
        nonImported += 1;
      }
    }

    String msg =
        addedLists + " lists with " + addedItems + " items were successfully imported.";
    

    if (nonImported > 0) {
      msg = msg + "\n" + nonImported + " lists with identical names were not imported.";
    }
    
    Window.alert(msg);
    return new ArrayList<ItemList>(allLists.values());
  }
}
