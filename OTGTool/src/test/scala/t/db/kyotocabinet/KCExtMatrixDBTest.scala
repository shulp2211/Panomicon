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

import org.scalatest.junit.JUnitRunner
import t.TTestSuite
import t.db.testing.DBTestData
import org.junit.runner.RunWith
import t.db.kyotocabinet.chunk.KCChunkMatrixDB

@RunWith(classOf[JUnitRunner])
class KCExtMatrixDBTest extends TTestSuite {
  import KCDBTest._
  import DBTestData._

  test("basic") {
    val db = memDBHash
    val edb = new KCChunkMatrixDB(db, true)

    testExtDb(edb, makeTestData(false))

    edb.release
  }

  test("sparse data") {
    val db = memDBHash
    val edb = new KCChunkMatrixDB(db, true)

    testExtDb(edb, makeTestData(true))

    edb.release
  }
}
