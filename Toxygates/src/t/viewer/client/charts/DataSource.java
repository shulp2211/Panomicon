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

package t.viewer.client.charts;

import t.viewer.client.screen.Screen;
import t.common.shared.*;
import t.common.shared.sample.*;
import t.model.SampleClass;
import t.model.sample.Attribute;
import t.viewer.client.components.PendingAsyncCallback;
import t.viewer.client.rpc.MatrixServiceAsync;
import t.viewer.shared.FullMatrix;
import t.viewer.shared.Series;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class brings series and row data into a unified data fetching interface for the purposes of
 * chart drawing.
 */
abstract public class DataSource {

  private static Logger logger = SharedUtils.getLogger("chartdata");

  protected List<DataPoint> chartPoints = new ArrayList<DataPoint>();

  protected String[] minorVals;
  protected String[] mediumVals;

  String[] minorVals() {
    return minorVals;
  }

  String[] mediumVals() {
    return mediumVals;
  }

  protected DataSchema schema;
  protected boolean controlMedVals = false;

  DataSource(DataSchema schema) {
    this.schema = schema;
  }

  protected void initParams(List<? extends HasClass> from, boolean controlMedVals) {
    try {
      Attribute minorParam = schema.minorParameter();
      Attribute medParam = schema.mediumParameter();
      minorVals = SampleClassUtils.collectInner(from, minorParam).toArray(String[]::new);
      schema.sort(minorParam, minorVals);

      Set<String> medVals = new HashSet<String>();
      for (HasClass f : from) {
        /*
         * Note: this control-check could be further generalised
         */
        if (controlMedVals || !schema.isControlValue(schema.getMedium(f))) {
          medVals.add(schema.getMedium(f));
        }
      }
      mediumVals = medVals.toArray(new String[0]);
      schema.sort(medParam, mediumVals);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Unable to sort chart data", e);
    }
  }

  /**
   * Obtain datapoints for the chart.
   */
  List<DataPoint> getPoints(ValueType vt, SampleMultiFilter smf, ColorPolicy policy) {
    List<DataPoint> usePoints;
    if (smf.contains(schema.majorParameter())) {
      usePoints =
          chartPoints.stream().filter(s -> smf.accepts(s)).distinct().collect(Collectors.toList());
    } else {
      usePoints = chartPoints;
    }
    for (DataPoint s : usePoints) {
      s.color = policy.colorFor(s);
    }
    return usePoints;
  }

  static class SeriesSource extends DataSource {
    SeriesSource(DataSchema schema, List<Series> series, Attribute indepAttribute,
        String[] indepPoints) {
      super(schema);
      for (Series s : series) {
        for (int i = 0; i < s.values().length; ++i) {
          ExpressionValue ev = s.values()[i];
          String indep = indepPoints[i];
          SampleClass sc = s.sampleClass().copyWith(indepAttribute, indep);
          DataPoint p =
              new DataPoint(sc, schema, ev.getValue(), null, s.probe(), ev.getCall(), null);
          chartPoints.add(p);
        }
      }
      initParams(chartPoints, false);
    }
  }

  /**
   * An expression row source with a fixed dataset. This source supports asynchronous fetching and
   * will return data through a callback.
   */
  public static class ExpressionRowSource extends DataSource {
    protected Sample[] samples;

    interface DataPointAcceptor {
      void accept(List<DataPoint> points);
    }

    /**
     * Obtain datapoints, making an asynchronous call if necessary, and pass them on to
     * the sample acceptor when they are available.
     */
    void getPointsAsync(ValueType vt, SampleMultiFilter smf, ColorPolicy policy,
        DataPointAcceptor acceptor) {
      acceptor.accept(getPoints(vt, smf, policy));
    }

    ExpressionRowSource(DataSchema schema, Sample[] samples, List<ExpressionRow> rows) {
      super(schema);
      this.samples = samples;
      addPointsFromSamples(samples, rows);
      initParams(Arrays.asList(samples), true);
    }

    protected void addPointsFromSamples(Sample[] samples, List<ExpressionRow> rows) {
      logger.info("Add samples from " + samples.length + " samples and " + rows.size() + " rows");
      for (int i = 0; i < samples.length; ++i) {
        for (ExpressionRow er : rows) {
          ExpressionValue ev = er.getValue(i);
          DataPoint cs = new DataPoint(samples[i], schema, ev.getValue(), er.getProbe(),
              ev.getCall(), schema.chartLabel(samples[i]));
          chartPoints.add(cs);
        }
      }
    }
  }

  /**
   * An expression row source that dynamically loads data.
   */
  static class DynamicExpressionRowSource extends ExpressionRowSource {
    protected final MatrixServiceAsync matrixService;

    protected String[] probes;
    protected Screen screen;

    DynamicExpressionRowSource(DataSchema schema, String[] probes, Sample[] samples,
        Screen screen) {
      super(schema, samples, new ArrayList<ExpressionRow>());
      this.probes = probes;
      this.screen = screen;
      this.matrixService = screen.manager().matrixService();
    }

    void loadData(final ValueType vt, final SampleMultiFilter smf, final ColorPolicy policy,
        final DataPointAcceptor acceptor) {
      logger.info("Dynamic source: load for " + smf + " " + vt);


      Sample[] useSamples =
          Arrays.stream(samples).filter(s -> smf.accepts(s)).toArray(Sample[]::new);

      chartPoints.clear();
      Group g = new Group(schema, "temporary", useSamples);
      List<Group> gs = new ArrayList<Group>();
      gs.add(g);
      matrixService.getFullData(gs, probes, false, vt,
          new PendingAsyncCallback<FullMatrix>(screen.manager(), "Unable to obtain chart data.", mat -> {
            addPointsFromSamples(useSamples, mat.rows());
            getLoadedPoints(vt, smf, policy, acceptor);
          }));
    }

    // Note: think about the way these methods interact with superclass
    // - bad design
    @Override
    void getPointsAsync(ValueType vt, SampleMultiFilter smf, ColorPolicy policy,
        DataPointAcceptor acceptor) {
      loadData(vt, smf, policy, acceptor);
    }

    protected void getLoadedPoints(ValueType vt, SampleMultiFilter smf, ColorPolicy policy,
        DataPointAcceptor acceptor) {
      super.getPointsAsync(vt, smf, policy, acceptor);
    }
  }

  /**
   * A dynamic source that makes requests based on a list of units.
   */
  static class DynamicUnitSource extends DynamicExpressionRowSource {
    private Unit[] units;

    DynamicUnitSource(DataSchema schema, String[] probes, Unit[] units, Screen screen) {
      super(schema, probes, Unit.collectSamples(units), screen);
      this.units = units;
    }

    @Override
    void loadData(final ValueType vt, final SampleMultiFilter smf, final ColorPolicy policy,
        final DataPointAcceptor acceptor) {
      logger.info("Dynamic unit source: load for " + smf + " " + vt);

      final List<Group> groups = new ArrayList<Group>();
      final List<Unit> useUnits = new ArrayList<Unit>();
      int i = 0;

      for (Unit u : units) {
        if (smf.accepts(u)) {
          Group g = new Group(schema, "g" + i, u.getSamples());
          i++;
          groups.add(g);
          useUnits.add(u);
        }
      }

      chartPoints.clear();
      matrixService.getFullData(groups, probes, false, vt,
          new PendingAsyncCallback<FullMatrix>(screen.manager(), "Unable to obtain chart data",
              mat -> {
              addPointsFromUnits(useUnits, mat.rows());
              getLoadedPoints(vt, smf, policy, acceptor);
          }));
    }

    protected void addPointsFromUnits(List<Unit> units, List<ExpressionRow> rows) {
      logger.info("Add samples from " + units.size() + " units and " + rows.size() + " rows");
      for (int i = 0; i < units.size(); ++i) {
        for (ExpressionRow er : rows) {
          ExpressionValue ev = er.getValue(i);
          DataPoint cs =
              new DataPoint(units.get(i), schema, ev.getValue(), er.getProbe(), ev.getCall());
          chartPoints.add(cs);
        }
      }
    }
  }

}
