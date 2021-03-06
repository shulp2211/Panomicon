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

package t.viewer.server

import t.platform.Probe

import t.sparql.ProbeStore
import t.platform.Species.Species

class PlatformRegistry(probeStore: ProbeStore) {
  private val platformStore = new t.sparql.PlatformStore(probeStore.config)
  private def allPlatforms = ProbeStore.platformsAndProbes(platformStore, probeStore)

  //map platform to probe sets
  private lazy val platformSets = allPlatforms.mapValues(_.map(_.identifier).toSet)

  //map ID to probe
  private lazy val identifierLookup =
    Map() ++ allPlatforms.toSeq.flatMap(_._2.toSeq).map(x => x.identifier -> x)

  def allProbes: Iterable[Probe] = allPlatforms.values.toSeq.flatten
  def getProbe(id: String): Option[Probe] = identifierLookup.get(id)
  def probeIdentifiers(platform: String): Set[String] =
    probeStore.probesForPlatform(platform).map(_.identifier).toSet
  def platformProbes(platform: String): Iterable[Probe] =
    probeStore.probesForPlatform(platform)

  lazy val geneLookup = {
    val raw = (for (
      (pf, probes) <- allPlatforms.toSeq;
      pr <- probes;
      gene <- pr.genes
      ) yield (gene, pr))
    Map() ++ raw.groupBy(_._1).mapValues(_.map(_._2))
  }

  def resolve(identifiers: Seq[String]): Seq[Probe] =
    identifiers.flatMap(identifierLookup.get(_))

  /**
   * Filter probes for a number of platforms.
   */
  def filterProbes(probes: Iterable[String],
      platforms: Iterable[String],
      species: Option[Species] = None): Iterable[String] = {
    var rem = Set() ++ probes
    var r = Set[String]()
    for (p <- platforms; valid = filterProbes(rem, p, species)) {
      rem --= valid
      r ++= valid
    }
    r.toSeq
  }

  /**
   * Filter probes for all platforms.
   */
  def filterProbesAllPlatforms(probes: Seq[String]): Seq[String] =
    probes.filter(identifierLookup.keySet.contains)

  def platformForProbe(p: String): Option[String] =
    platformSets.find(_._2.contains(p)).map(_._1)

  /**
   * Filter probes for one platform. Returns all probes in the platform if the input
   * set is empty.
   */
  def filterProbes(probes: Iterable[String], platform: String,
      species: Option[Species]): Iterable[String] = {
    if (probes.size == 0) {
      allProbes(platform, species)
    } else {
      val pset = probes.toSet
      println(s"Filter (${pset.size}) ${pset take 20} ...")
      val r = pset.intersect(probeIdentifiers(platform))
      println(s"Result (${r.size}) ${r take 20} ...")
      r.toSeq
    }
  }

  private def allProbes(platform: String, species: Option[Species]) = {
    val all = probeIdentifiers(platform)
    if (platform.startsWith("mirbase")) {
      species match {
        case Some(sp) => all.filter(_.startsWith(sp.shortCode))
        case _ => all
      }
    } else {
      all
    }
  }
}
