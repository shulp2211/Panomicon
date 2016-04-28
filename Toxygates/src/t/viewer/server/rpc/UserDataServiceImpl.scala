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

package t.viewer.server.rpc

import t.viewer.client.rpc.UserDataService
import t.common.server.maintenance.BatchOpsImpl
import t.viewer.server.Configuration
import t.common.shared.maintenance.Batch
import t.common.shared.Dataset
import t.global.KCDBRegistry

/**
 * A servlet for managing user data (as batches).
 * In practice, this is a restricted variant of the maintenanc
 * servlet.
 */
abstract class UserDataServiceImpl extends TServiceServlet
  with BatchOpsImpl with UserDataService {
  private var homeDir: String = _

  override def localInit(config: Configuration) {
    super.localInit(config)
    homeDir = config.webappHomeDir
  }

  override protected def getAttribute[T](name: String) =
    getThreadLocalRequest().getSession().getAttribute(name).asInstanceOf[T]

  override protected def setAttribute(name: String, x: AnyRef): Unit =
     getThreadLocalRequest().getSession().setAttribute(name, x)

  override protected def request = getThreadLocalRequest

  protected override def mayAppendBatch: Boolean = false

  override def addBatchAsync(b: Batch): Unit = {
    //Here, we must first ensure existence of the dataset.
    //For user data, the unique user id will here be supplied from the client side.
    //(e.g. user-a1f8032011c0f...)
    //can also be user-shared in the case of shared user data.

    val d = new Dataset(b.getDataset, "User data",
        "Automatically generated", null, "Automatically generated")
    addDataset(d, false)
    super.addBatchAsync(b)
  }

  override protected def afterTaskCleanup(): Unit = {
    super.afterTaskCleanup()
    KCDBRegistry.closeWriters()
  }
}