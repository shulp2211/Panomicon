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

package t.db.kyotocabinet

import java.io.Closeable

import kyotocabinet.DB
import t.global.KCDBRegistry

abstract class KyotoCabinetDB(db: DB, writeMode: Boolean) extends Closeable {
  def get(key: Array[Byte]): Option[Array[Byte]] =
    Option(db.get(key))

  def release() {
    val path = db.path()
    if (path != null &&
        path != "*" && //in memory cache DB
        path != "%" && //in-memory tree DB
      //if maintenance mode, all DBs are closed together when the application exits
      ! KCDBRegistry.isMaintenanceMode
        ) {
      if (writeMode) {
       KCDBRegistry.releaseWriter(path)
      } else {
        db.close()
      }
    }
  }

  def close() { release() }
}
