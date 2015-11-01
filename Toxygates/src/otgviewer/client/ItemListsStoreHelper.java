/*
 * Copyright (c) 2012-2015 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition
 * (NIBIOHN), Japan.
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
package otgviewer.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import otgviewer.client.components.Screen;
import otgviewer.client.components.StorageParser;
import otgviewer.client.dialog.InputDialog;
import t.common.shared.ItemList;
import t.common.shared.SharedUtils;
import t.common.shared.StringList;
import t.viewer.client.Utils;
import t.viewer.client.dialog.DialogPosition;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;

public class ItemListsStoreHelper {

  private final Logger logger = SharedUtils.getLogger("ItemListsStoreHelper");

  private Screen screen;
  private String type;

  private Collection<StringList> predefinedLists;
  private Map<String, Map<String, Collection<String>>> itemLists; // { type -> { name -> [items] } }

  private DialogBox inputDialog;

  public ItemListsStoreHelper(String type, Screen screen) {
    this.screen = screen;
    this.type = type;
    this.predefinedLists = screen.manager().appInfo().predefinedProbeLists();
    this.itemLists = new HashMap<String, Map<String, Collection<String>>>();

    init();
  }

  private void init() {
    for (ItemList il : screen.chosenItemLists) {
      if (il instanceof StringList == false) {
        continue;
      }
      StringList sl = (StringList) il;
      List<String> itemList = new ArrayList<String>(Arrays.asList(sl.items()));
      putIfAbsent(sl.type()).put(sl.name(), itemList);
    }
  }
  
  /*
   *  Override this function to handle what genes were saved
   */
  protected void onSaveSuccess(String name, Collection<String> items) {}

  /*
   *  Save a gene set to local storage.
   *  An input box that ask the gene set title will be shown.
   */
  public void save(Collection<String> list) {
    List<Collection<String>> lists = new ArrayList<Collection<String>>();
    lists.add(new ArrayList<String>(list));
    saveAction("Name entry", "Please enter a name for the list.", lists);
  }
  
  /*
   *  Save gene sets to local storage.
   *  An input box that ask the prefix of gene sets will be shown.
   */
  public void save(List<Collection<String>> lists) {
    saveAction("Name entry", "Please enter a name prefix for each lists.",
        lists);
  }
  
  /*
   *  Save gene set with specified title.
   *  No input box will be shown.
   */
  public void saveAs(Collection<String> list, String title) {
    List<Collection<String>> lists = new ArrayList<Collection<String>>();
    lists.add(list);
    saveAction("Name entry", "Please enter a name for the list.", lists, title);
  }

  /*
   *  Check whether if same list type and same title is contained in the local storage.
   */
  public boolean contains(String listType, String title) {
    if (itemLists.containsKey(listType)
        && itemLists.get(listType).containsKey(title)) {
      return true;
    }
    return false;
  }

  private Map<String, Collection<String>> putIfAbsent(String type) {
    Map<String, Collection<String>> value = itemLists.get(type);
    if (value == null) {
      itemLists.put(type, new HashMap<String, Collection<String>>());
      value = itemLists.get(type);
    }
    return value;
  }

  private boolean isContainedInPredefinedLists(String listType, String name) {
    for (StringList sl : predefinedLists) {
      if (sl.type().equals(listType) && sl.name().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean validate(String name) {
    if (name == null) {
      return false;
    }
    if (name.equals("")) {
      Window.alert("You must enter a non-empty name.");
      return false;
    }
    if (!StorageParser.isAcceptableString(name, "Unacceptable list name.")) {
      return false;
    }
    if (isContainedInPredefinedLists(type, name)) {
      Window.alert("This name is reserved for the system and cannot be used.");
      return false;
    }
    if (contains(type, name)) {
      Window.alert("The title \"" + name + "\" is already taken.\n"
          + "Please choose a different name.");
      return false;
    }
    return true;
  }

  private boolean validate(List<String> names) {
    for (String name : names) {
      if (!validate(name)) {
        return false;
      }
    }
    return true;
  }

  private void saveAction(String title, String message,
      final List<Collection<String>> lists) {
    InputDialog entry = new InputDialog(message) {
      @Override
      protected void onChange(String value) {
        if (value == null) { // on Cancel clicked
          inputDialog.setVisible(false);
          return;
        }

        List<String> names = generateNameList(value, lists.size());
        if (!validate(names)) {
          return;
        }

        Iterator<Collection<String>> itr = lists.iterator();
        for (int i = 0; itr.hasNext(); ++i) {
          putIfAbsent(type).put(names.get(i), itr.next());
        }

        storeItemLists();
        logger.info("Stored " + lists.size() + " list(s).");
        inputDialog.setVisible(false);

        onSaveSuccess(names.get(0), lists.get(0));
      }
    };
    inputDialog = Utils.displayInPopup(title, entry, DialogPosition.Center);
  }

  private void saveAction(String title, String message,
      final List<Collection<String>> lists, String name) {
    if (name == null) {
      return;
    }

    List<String> names = generateNameList(name, lists.size());
    if (!validate(names)) {
      return;
    }

    Iterator<Collection<String>> itr = lists.iterator();
    for (int i = 0; itr.hasNext(); ++i) {
      putIfAbsent(type).put(names.get(i), itr.next());
    }

    storeItemLists();
    logger.info("Stored " + lists.size() + " list(s).");

    onSaveSuccess(names.get(0), lists.get(0));
  }

  private List<String> generateNameList(String base, int size) {
    if (size > 1) {
      return getSerialNumberedNames(base, size);
    } else {
      return new ArrayList<String>(Arrays.asList(new String[] {base}));
    }
  }

  private void storeItemLists() {
    screen.itemListsChanged(buildItemLists());
    screen.storeItemLists(screen.getParser());
  }

  private List<ItemList> buildItemLists() {
    List<ItemList> lists = new ArrayList<ItemList>();
    for (Entry<String, Map<String, Collection<String>>> e1 : itemLists
        .entrySet()) {
      String type = e1.getKey();
      for (Entry<String, Collection<String>> e2 : e1.getValue().entrySet()) {
        String name = e2.getKey();
        Collection<String> items = e2.getValue();
        StringList i = new StringList(type, name, items.toArray(new String[0]));
        lists.add(i);
      }
    }
    return lists;
  }

  private List<String> getSerialNumberedNames(String base, int count) {
    List<String> names = new ArrayList<String>(count);
    for (int i = 0; i < count; ++i) {
      names.add(base + " " + (i + 1));
    }
    return names;
  }

}
