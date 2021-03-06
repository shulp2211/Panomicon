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

package t.viewer.server.network

import t.viewer.server.matrix.MatrixController
import t.Context
import t.common.shared.ValueType
import t.common.shared.sample.Group
import t.db.MatrixContext
import t.viewer.server.matrix.ManagedMatrix
import t.platform.mirna.TargetTable
import t.viewer.server.PlatformRegistry
import t.viewer.server.matrix.ControllerParams
import t.viewer.shared.network.Network

/**
 * A MatrixController that turns the main matrix into a ManagedNetwork
 * instead of a ManagedMatrix.
 *
 * @param targets target table for the initial network load.
 * Will not be used for subsequent calls to makeNetwork, as we expect the
 * ManagedNetwork (managedMatrix) to contain the latest updated targets.
 */
class NetworkController(context: Context, platforms: PlatformRegistry,
                        params: ControllerParams,
                        val sideMatrix: ManagedMatrix, targets: TargetTable,
                        initMainPageSize: Int,
                        sideIsMRNA: Boolean) extends MatrixController(context, platforms, params) {

  type Mat = ManagedNetwork

  override def finish(mm: ManagedMatrix): Mat = {
    new ManagedNetwork(mm.params, sideMatrix, targets, platforms, initMainPageSize, sideIsMRNA)
  }

  /**
   * Produce a network object that reflects the current view.
   */
  def makeNetwork: Network =
    new NetworkBuilder(managedMatrix.targets, platforms, managedMatrix, sideMatrix).build

}
