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

package t.common.client.rpc;

import javax.annotation.Nullable;

import t.common.shared.Dataset;
import t.common.shared.maintenance.Batch;
import t.common.shared.maintenance.MaintenanceException;
import t.model.sample.Attribute;

/**
 * Management operations for batches.
 */
public interface BatchOperations extends MaintenanceOperations {
  /**
   * Get the batches that the user is allowed to manage.
   * @param datasets The datasets to request batches from, or null/empty to get all batches 
   * (if this is allowed)
   * @return Batches in the datasets
   * @throws MaintenanceException
   */
  Batch[] getBatches(@Nullable String[] datasets) throws MaintenanceException;
  
  void addBatchAsync(Batch b) throws MaintenanceException;

  void updateBatchMetadataAsync(Batch b, boolean recalculate) throws MaintenanceException;

  /**
   * Get attribute summaries for samples in a batch.
   * The result is a row-major table. The first row will be column headers.
   */
  String[][] batchAttributeSummary(Batch b) throws MaintenanceException;


  /**
   * Get attribute summaries and optionally sample counts for a dataset
   * as a pivot table.
   * The result is a row-major table. The first row will be column headers.
   * @param d
   * @param cellAttribute The attribute to put in individual table cells, or 
     null if the sample count is desired.
   * @return
   */
  String[][] datasetSampleSummary(Dataset d, 
                                  Attribute[] rowAttributes, 
                                  Attribute[] columnAttributes,
                                  @Nullable Attribute cellAttribute);

  
  /**
   * Delete a batch.
   * @param b
   * @throws MaintenanceException
   */
  void deleteBatchAsync(Batch b) throws MaintenanceException;
}
