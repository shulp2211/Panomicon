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

import otgviewer.shared.intermine.EnrichmentParams;
import otgviewer.shared.intermine.IntermineInstance;
import t.common.shared.StringList;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface IntermineServiceAsync {

  public void importLists(IntermineInstance instance, String user, String pass, boolean asProbes,
      AsyncCallback<StringList[]> callback);

  public void exportLists(IntermineInstance instance, String user, String pass, StringList[] lists, boolean replace,
      AsyncCallback<Void> callback);

  void enrichment(IntermineInstance instance, StringList list, EnrichmentParams params,
      AsyncCallback<String[][]> callback);

  void multiEnrichment(IntermineInstance instance, StringList[] list,
      EnrichmentParams params,
      AsyncCallback<String[][][]> callback);
}
