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

package t

import t.db.MatrixContext
import t.sparql.{ProbeStore, SampleStore}

object Context {
  val factory = new Factory()

  def apply(bc: BaseConfig) =
    new Context(bc, factory,
      factory.probes(bc.triplestore), factory.samples(bc),
      new OTGMatrixContext(bc))
}

/**
 * Top level configuration object for a T framework
 * application
 */
class Context(val config: BaseConfig,
              val factory: Factory,
              val probeStore: ProbeStore,
              val sampleStore: SampleStore,
              val matrix: MatrixContext) {

  /*
   * Note: this may not be the best location for the auxSortMap
   */

  /**
   * Obtain an ordering of the probes, identified by a string key.
   *
   * This mechanism was formerly used to sort by an association in Tritigate,
   * but is not currently used.
   */
  def auxSortMap(key: String): Map[String, Double] = {
    val allProbes = matrix.probeMap.tokens
    println("Aux map for " + allProbes.size + " probes key " + key)
    probeStore.auxSortMap(allProbes, key)
  }
}
