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

package t.viewer.server.rpc

import java.util.Date

import t.TTestSuite
import t.common.shared.{Dataset, SeriesType}
import t.viewer.server.AssociationResolverTest
import t.viewer.shared.{RankRule, RuleType}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SeriesServiceTest extends TTestSuite {

  var s: SeriesServiceImpl = _
  before {
    val conf = t.viewer.testing.TestConfiguration.config
    s = new SeriesServiceImpl()
    s.tServletInit(conf)
    s.localInit(conf)
  }

  after {
	  s.destroy
  }

  test("Ranking") {
    val sc = AssociationResolverTest.testSampleClass
    val r = new RankRule(RuleType.MaximalFold, "1370365_at") //GSS gene

   val ds = new Dataset("otg", "", "", new Date(), "", 1)
    //Note: needs a valid dataset for the first argument
    val res = s.rankedCompounds(SeriesType.Time, Array(ds), sc, Array(r)).toSeq
    println(res take 10)
  }
}
