/*
 * Copyright (c) 2012-2017 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition
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

package t.testing

import t.DataConfig
import t.TriplestoreConfig
import t.BaseConfig
import otg.OTGBConfig

object TestConfig {
  //Not in use
  val dataConfig = new DataConfig("/shiba/toxygates/data_dev",
    "#bnum=6250000#pccap=1073741824#apow=8")
  //	val tsConfig = new TriplestoreConfig("http://localhost:3030/data/sparql",
  //	    "http://localhost:3030/data/update", null, null, null)

  //TODO replace with in-memory triplestore or similar
  val tsConfig = new TriplestoreConfig("http://sontaran:8081/owlim-workbench-webapp-5.3.1",
    null, "ttest", "ttest", "ttest")
  val config = new OTGBConfig(tsConfig, dataConfig)
}
