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

package t.viewer.client.intermine;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

import t.viewer.shared.StringList;
import t.viewer.shared.intermine.*;

@RemoteServiceRelativePath("intermine")
public interface IntermineService extends RemoteService {

  /**
   * Import gene lists from an intermine user account.
   * 
   * @param user
   * @param pass
   * @param asProbes if true, the items will be imported as affymetrix probes. If false, as genes.
   * @return
   */
  StringList[] importLists(IntermineInstance instance,
                           String user, String pass, boolean asProbes)
      throws IntermineException;

  /**
   * Export gene lists to an intermine user account
   */
  void exportLists(IntermineInstance instance,
                   String user, String pass, StringList[] lists, boolean replace)
      throws IntermineException;

  /**
   * Enrich one gene list, returning all the results
   * @param instance
   * @param list
   * @param params
   * @param session the intermine session key, previously obtained from the getSession request
   * @return
   * @throws IntermineException
   */
  String[][] enrichment(IntermineInstance instance,
                        StringList list, EnrichmentParams params, String session) throws IntermineException;

  /**
   * Enrich multiple gene lists simultaneously, returning only the top result for each
   * @param instance
   * @param lists
   * @param params
   * @param session the intermine session key, previously obtained from the getSession request
   * @return
   * @throws IntermineException
   */
  String[][][] multiEnrichment(IntermineInstance instance,
                               StringList[] lists, EnrichmentParams params, String session)
      throws IntermineException;

  /**
   * Obtain a temporary session from the given intermine server.
   * @param instance
   * @return A session key
   * @throws IntermineException
   */
  String getSession(IntermineInstance instance)
		  throws IntermineException;
}
