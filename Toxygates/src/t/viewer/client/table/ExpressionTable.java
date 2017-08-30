/*
 * Copyright (c) 2012-2017 Toxygates authors, National Institutes of Biomedical Innovation, Health
 * and Nutrition (NIBIOHN), Japan.
 * 
 * This file is part of Toxygates.
 * 
 * Toxygates is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * Toxygates is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Toxygates. If not,
 * see <http://www.gnu.org/licenses/>.
 */

package t.viewer.client.table;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import otgviewer.client.StandardColumns;
import otgviewer.client.charts.*;
import otgviewer.client.charts.Charts.AChartAcceptor;
import otgviewer.client.components.*;
import t.common.client.ImageClickCell;
import t.common.shared.*;
import t.common.shared.sample.*;
import t.model.SampleClass;
import t.viewer.client.Analytics;
import t.viewer.client.Utils;
import t.viewer.client.dialog.DialogPosition;
import t.viewer.client.dialog.FilterEditor;
import t.viewer.client.rpc.MatrixServiceAsync;
import t.viewer.shared.*;
import t.viewer.shared.table.SortKey;

import com.google.gwt.cell.client.*;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.*;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.cellview.client.SimplePager.Resources;
import com.google.gwt.user.cellview.client.SimplePager.TextLocation;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.view.client.*;

/**
 * The main data display table. This class has many different functionalities. (too many, should be
 * refactored into something like an MVC architecture, almost certainly)
 * 
 * It requests microarray expression data dynamically, displays it, as well as displaying additional
 * dynamic data. It also provides functionality for chart popups. It also has an interface for
 * adding and removing t-tests and u-tests, which can be hidden and displayed on demand.
 * 
 * Hideable columns and clickable icons are handled by the RichTable superclass. Dynamic
 * (association) columns are handled by the AssociationTable superclass.
 * 
 * @author johan
 *
 */
public class ExpressionTable extends AssociationTable<ExpressionRow> {

  /**
   * Initial number of items to show per page at a time (but note that this number can be adjusted
   * by the user in the 0-250 range)
   */
  private final int INIT_PAGE_SIZE = 50;
  private final int MAX_PAGE_SIZE = 250;
  private final int PAGE_SIZE_INCREMENT = 50;

  private Screen screen;
  private KCAsyncProvider asyncProvider = new KCAsyncProvider();

  private HorizontalPanel tools, analysisTools;
  // We enable/disable this button when the value type changes
  private Button foldChangeBtn = new Button("Add fold-change difference");

  protected ListBox tableList = new ListBox();

  private final MatrixServiceAsync matrixService;
  private final t.common.client.Resources resources;

  protected boolean displayPColumns = true;
  protected SortKey sortKey;
  protected boolean sortAsc;

  private boolean withPValueOption;

  // For Analytics: we count every matrix load other than the first as a gene set change
  private boolean firstMatrixLoad = true;

  /**
   * For selecting sample groups to apply t-test/u-test to
   */
  private ListBox groupsel1 = new ListBox(), groupsel2 = new ListBox();

  /**
   * Names of the probes currently displayed
   */
  private String[] displayedAtomicProbes;
  /**
   * Names of the (potentially merged) probes being displayed
   */
  private String[] displayedProbes;

  private boolean loadedData = false;
  private ManagedMatrixInfo matrixInfo = null;

  private Sample[] chartBarcodes = null;

  private DialogBox filterDialog = null;

  private final Logger logger = SharedUtils.getLogger("expressionTable");

  protected ValueType chosenValueType;
  
  private CheckBox pcb;

  public ExpressionTable(Screen _screen, boolean withPValueOption) {
    super(_screen);
    this.withPValueOption = withPValueOption;
    this.matrixService = _screen.manager().matrixService();
    this.resources = _screen.resources();
    screen = _screen;

    grid.setStylePrimaryName("exprGrid");
    grid.setPageSize(INIT_PAGE_SIZE);

    grid.setSelectionModel(new NoSelectionModel<ExpressionRow>());
    grid.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);
    asyncProvider.addDataDisplay(grid);

    makeTools();
    makeAnalysisTools();
    setEnabled(false);
  }

  protected boolean isMergeMode() {
    if (displayedProbes == null || displayedAtomicProbes == null) {
      return false;
    }
    return displayedProbes.length != displayedAtomicProbes.length;
  }
  
  public ValueType getValueType() {
    String vt = tableList.getItemText(tableList.getSelectedIndex());
    return ValueType.unpack(vt);
  }

  public Widget tools() {
    return this.tools;
  }
  
  public ManagedMatrixInfo currentMatrixInfo() {
    return matrixInfo;
  }

  /**
   * Enable or disable the GUI
   * 
   * @param enabled
   */
  private void setEnabled(boolean enabled) {
    Utils.setEnabled(tools, enabled);
    Utils.setEnabled(analysisTools, enabled);
    enableFoldChangeUI(enabled);
  }

  private void enableFoldChangeUI(boolean enabled) {
    switch (chosenValueType) {
      case Absolute:
        foldChangeBtn.setEnabled(false);
        break;
      case Folds:
        foldChangeBtn.setEnabled(true && enabled);
        break;
    }
  }

  /**
   * The main (navigation) tool panel
   */
  private void makeTools() {
    tools = Utils.mkHorizontalPanel();

    HorizontalPanel horizontalPanel = Utils.mkHorizontalPanel(true);
    horizontalPanel.setStylePrimaryName("colored");
    horizontalPanel.addStyleName("slightlySpaced");
    tools.add(horizontalPanel);

    tableList.setVisibleItemCount(1);
    horizontalPanel.add(tableList);
    initTableList();
    tableList.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        removeTests();
        chosenValueType = getValueType();
        getExpressions();
      }
    });

    Resources r = GWT.create(Resources.class);

    SimplePager sp = new SimplePager(TextLocation.CENTER, r, true, 500, true) {
      @Override
      public void nextPage() {
        super.nextPage();
        Analytics.trackEvent(Analytics.CATEGORY_TABLE, Analytics.ACTION_PAGE_CHANGE);
      }

      @Override
      public void previousPage() {
        super.previousPage();
        Analytics.trackEvent(Analytics.CATEGORY_TABLE, Analytics.ACTION_PAGE_CHANGE);
      }

      @Override
      public void setPage(int index) {
        super.setPage(index);
        Analytics.trackEvent(Analytics.CATEGORY_TABLE, Analytics.ACTION_PAGE_CHANGE);
      }
    };
    sp.setStylePrimaryName("slightlySpaced"); 
    horizontalPanel.add(sp);
    sp.setDisplay(grid);

    PageSizePager pager = new PageSizePager(PAGE_SIZE_INCREMENT) {
      @Override
      protected void onRangeOrRowCountChanged() {
        super.onRangeOrRowCountChanged();
        if (getPageSize() > MAX_PAGE_SIZE) {
          setPageSize(MAX_PAGE_SIZE);
        }
      }
    };

    pager.setStylePrimaryName("slightlySpaced");
    horizontalPanel.add(pager);

    if (withPValueOption) {
      pcb = new CheckBox("p-value columns");
      horizontalPanel.add(pcb);
      pcb.setValue(false);      
      pcb.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          if (pcb.getValue() && ! hasPValueColumns()) {
            Window.alert("Precomputed p-values are only available for sample groups "
                + " in fold-change mode, consisting of a single time and dose.\n"
                + "If you wish to compare two columns, use "
                + "\"Compare two sample groups\" in the tools menu.");
            
            setDisplayPColumns(false);                        
          } else {
            setDisplayPColumns(pcb.getValue());
            setupColumns();
          }
        }
      });
    }

    pager.setDisplay(grid);
  }

  public void setDisplayPColumns(boolean displayPColumns) {
    if (withPValueOption) {
      this.displayPColumns = displayPColumns;
      pcb.setValue(displayPColumns);
    }    
  }

  protected void initTableList() {
    tableList.addItem(ValueType.Folds.toString());
    tableList.addItem(ValueType.Absolute.toString());
    chosenValueType = ValueType.Folds;
  }

  public Widget analysisTools() {
    return analysisTools;
  }

  private void removeTests() {    
    matrixService.removeTwoGroupTests(new PendingAsyncCallback<ManagedMatrixInfo>(this, 
        "There was an error removing the test columns.") {

      @Override
      public void handleSuccess(ManagedMatrixInfo result) {
        matrixInfo = result; // no need to do the full setMatrix
        setupColumns();
      }
    });    
  }

  /**
   * The tool panel for controlling t-tests and u-tests
   */
  private void makeAnalysisTools() {
    analysisTools = Utils.mkHorizontalPanel(true);
    analysisTools.setStylePrimaryName("colored2");

    analysisTools.add(groupsel1);
    groupsel1.setVisibleItemCount(1);
    analysisTools.add(groupsel2);
    groupsel2.setVisibleItemCount(1);

    analysisTools.add(new Button("Add T-test", new ClickHandler() {
      @Override
      public void onClick(ClickEvent e) {
        addTwoGroupSynthetic(new Synthetic.TTest(null, null), "T-test");
        Analytics.trackEvent(Analytics.CATEGORY_ANALYSIS, Analytics.ACTION_ADD_COMPARISON_COLUMN,
            Analytics.LABEL_T_TEST);
      }
    }));

    analysisTools.add(new Button("Add U-test", new ClickHandler() {
      @Override
      public void onClick(ClickEvent e) {
        addTwoGroupSynthetic(new Synthetic.UTest(null, null), "U-test");
        Analytics.trackEvent(Analytics.CATEGORY_ANALYSIS, Analytics.ACTION_ADD_COMPARISON_COLUMN,
            Analytics.LABEL_U_TEST);
      }
    }));

    foldChangeBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent e) {
        addTwoGroupSynthetic(new Synthetic.MeanDifference(null, null), "Fold-change difference");
        Analytics.trackEvent(Analytics.CATEGORY_ANALYSIS, Analytics.ACTION_ADD_COMPARISON_COLUMN,
            Analytics.LABEL_FOLD_CHANGE_DIFFERENCE);
      }
    });
    analysisTools.add(foldChangeBtn);

    analysisTools.add(new Button("Remove tests", new ClickHandler() {
      @Override
      public void onClick(ClickEvent ce) {
        removeTests();
      }
    }));
    analysisTools.setVisible(false); // initially hidden
  }

  private static String selectedGroup(ListBox groupSelector) {
    return groupSelector.getItemText(groupSelector.getSelectedIndex());
  }

  private void addTwoGroupSynthetic(final Synthetic.TwoGroupSynthetic synth, final String name) {
    if (groupsel1.getSelectedIndex() == -1 || groupsel2.getSelectedIndex() == -1) {
      Window.alert("Please select two groups to compute " + name + ".");
    } else if (groupsel1.getSelectedIndex() == groupsel2.getSelectedIndex()) {
      Window.alert("Please select two different groups to perform " + name + ".");
    } else {
      final Group g1 = GroupUtils.findGroup(chosenColumns, selectedGroup(groupsel1));
      final Group g2 = GroupUtils.findGroup(chosenColumns, selectedGroup(groupsel2));
      synth.setGroups(g1, g2);
      matrixService.addTwoGroupTest(synth, new PendingAsyncCallback<ManagedMatrixInfo>(this,
          "Adding test column failed") {
        @Override
        public void handleSuccess(ManagedMatrixInfo r) {
          setMatrix(r);
          setupColumns();
        }
      });
    }
  }

  public void downloadCSV(boolean individualSamples) {
    if (individualSamples && isMergeMode()) {
      Window.alert("Individual samples cannot be downloaded in orthologous mode.\n" +
          "Please inspect one group at a time.");
      return;
    }
    
    matrixService.prepareCSVDownload(individualSamples, new PendingAsyncCallback<String>(this,
        "Unable to prepare the requested data for download.") {

      @Override
      public void handleSuccess(String url) {
        Utils.displayURL("Your download is ready.", "Download", url);
      }
    });
  }
  
  protected boolean hasPValueColumns() {
    for (int i = 0; i < matrixInfo.numDataColumns(); ++i) {
      if (matrixInfo.isPValueColumn(i)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void setupColumns() {
    super.setupColumns();
    ensureSection("synthetic");

    TextCell tc = new TextCell();

    for (int i = 0; i < matrixInfo.numDataColumns(); ++i) {
      if (displayPColumns || !matrixInfo.isPValueColumn(i)) {
        Column<ExpressionRow, String> valueCol = new ExpressionColumn(tc, i);
        ColumnInfo ci =
            new ColumnInfo(matrixInfo.columnName(i), 
                matrixInfo.columnHint(i), true, false, true,
                matrixInfo.columnFilter(i).active());
        ci.setCellStyleNames("dataColumn");
        addColumn(valueCol, "data", ci);
        Group g = matrixInfo.columnGroup(i);
        if (g != null) {
          valueCol.setCellStyleNames(g.getStyleName());
        }
      }
    }
    
    for (int i = matrixInfo.numDataColumns(); i < matrixInfo.numColumns(); i++) {
      addSynthColumn(matrixInfo.columnName(i), matrixInfo.columnHint(i), i);
    }    
  }

  @Override
  protected Column<ExpressionRow, String> toolColumn(Cell<String> cell) {
    return new Column<ExpressionRow, String>(cell) {
      @Override
      public String getValue(ExpressionRow er) {
        if (er != null) {
          return er.getProbe();
        } else {
          return "";
        }
      }
    };
  }

  @Override
  protected Cell<String> toolCell() {
    return new ToolCell(this);
  }

  private void addSynthColumn(String title, String tooltip, int matIndex) {
    TextCell tc = new TextCell();    
    Column<ExpressionRow, String> synCol = new ExpressionColumn(tc, matIndex);    
    ColumnInfo info = new ColumnInfo(title, tooltip, true, false, true, false);
    info.setCellStyleNames("extraColumn");
    info.setDefaultSortAsc(true);
    addColumn(synCol, "synthetic", info);
  }

  @Override
  protected Header<SafeHtml> getColumnHeader(ColumnInfo info) {
    Header<SafeHtml> superHeader = super.getColumnHeader(info);
    if (info.filterable()) {
      return new FilteringHeader(superHeader.getValue(), info.filterActive());
    } else {
      return superHeader;
    }
  }

  private void computeSortParams() {
    ColumnSortList csl = grid.getColumnSortList();
    sortAsc = false;
    sortKey = new SortKey.MatrixColumn(0);
    if (csl.size() > 0) {
      Column<?, ?> col = csl.get(0).getColumn();
      if (col instanceof MatrixSortable) {
        MatrixSortable ec = (MatrixSortable) csl.get(0).getColumn();
        sortKey = ec.sortKey();
        sortAsc = csl.get(0).isAscending();
      } else {
        Window.alert("Sorting for this column is not implemented yet.");
      }
    }
  }

  @Override
  protected boolean interceptGridClick(String target, int x, int y) {
    /**
     * To prevent unwanted interactions between the sorting system and the filtering system, we have
     * to intercept click events at this high level and choose whether to pass them on (non-filter
     * clicks) or not (filter clicks).
     */

    // Identify a click on the filter image.
    // TODO use a more robust identification method (!!)
    boolean isFilterClick =
        ((target.startsWith("<img") || target.startsWith("<IMG")) && 
            (target.indexOf("width:12") != -1 || // most browsers
            target.indexOf("WIDTH: 12") != -1 || // IE9
        target.indexOf("width: 12") != -1)); // IE8
    if (isFilterClick) {
      // Identify the column that was filtered.
      int col = columnAt(x);
      ExpressionColumn ec = (ExpressionColumn) grid.getColumn(col);
      editColumnFilter(ec.matrixColumn());
    }
    // If we return true, the click will be passed on to the other widgets
    return !isFilterClick;
  }
  
  protected void editColumnFilter(int column) {
    ColumnFilter filt = matrixInfo.columnFilter(column);    
    FilterEditor fe =
        new FilterEditor(matrixInfo.columnName(column), column, filt) {

          @Override
          protected void onChange(ColumnFilter newVal) {
            applyColumnFilter(editColumn, newVal);
          }
        };
    filterDialog = Utils.displayInPopup("Edit filter", fe, DialogPosition.Center);
  }

  protected void applyColumnFilter(final int column, 
      final @Nullable ColumnFilter filter) {
    setEnabled(false);
    matrixService.setColumnFilter(column, filter, new AsyncCallback<ManagedMatrixInfo>() {
      @Override
      public void onFailure(Throwable caught) {
        Window.alert("An error occurred when the column filter was changed.");
        filterDialog.setVisible(false);
        setEnabled(true);
      }

      @Override
      public void onSuccess(ManagedMatrixInfo result) {
        if (result.numRows() == 0 && filter.active()) {
          Window.alert("No rows match the selected filter. The filter will be reset.");
          applyColumnFilter(column, filter.asInactive());
        } else {
          setMatrix(result);
          setupColumns();
          filterDialog.setVisible(false);
        }
      }
    });
  }

  protected @Nullable String probeLink(String identifier) {
    return null;
  }

  private String mkAssociationList(String[] values) {
    return SharedUtils.mkString("<div class=\"associationValue\">", values, "</div> ");
  }

  @Override
  protected List<HideableColumn<ExpressionRow, ?>> initHideableColumns(DataSchema schema) {
    SafeHtmlCell htmlCell = new SafeHtmlCell();
    List<HideableColumn<ExpressionRow, ?>> r = new ArrayList<HideableColumn<ExpressionRow, ?>>();

    r.add(new LinkingColumn<ExpressionRow>(htmlCell, "Gene ID", initVisibility(StandardColumns.GeneID),
        initWidth(StandardColumns.GeneID)) {
      @Override
      protected String formLink(String value) {
        return AType.formGeneLink(value);
      }

      @Override
      protected Collection<Pair<String, String>> getLinkableValues(ExpressionRow er) {
        String[] geneIds = er.getGeneIds(); // basis for the URL
        String[] labels = er.getGeneIdLabels();
        List<Pair<String, String>> r = new ArrayList<Pair<String, String>>();
        for (int i = 0; i < geneIds.length; i++) {
          r.add(new Pair<String, String>(labels[i], geneIds[i]));
        }
        return r;
      }
    });

    r.add(new HTMLHideableColumn<ExpressionRow>(htmlCell, "Gene Symbol",
        initVisibility(StandardColumns.GeneSym), initWidth(StandardColumns.GeneSym)) {
      @Override
      protected String getHtml(ExpressionRow er) {
        return mkAssociationList(er.getGeneSyms());
      }

    });

    r.add(new HTMLHideableColumn<ExpressionRow>(htmlCell, "Probe Title",
        initVisibility(StandardColumns.ProbeTitle), initWidth(StandardColumns.ProbeTitle)) {
      @Override
      protected String getHtml(ExpressionRow er) {
        return mkAssociationList(er.getAtomicProbeTitles());
      }
    });

    r.add(new LinkingColumn<ExpressionRow>(htmlCell, "Probe", initVisibility(StandardColumns.Probe),
        initWidth(StandardColumns.Probe)) {

      @Override
      protected String formLink(String value) {
        return probeLink(value);
      }

      @Override
      protected Collection<Pair<String, String>> getLinkableValues(ExpressionRow er) {
        return Pair.duplicate(Arrays.asList(er.getAtomicProbes()));
      }

    });

    // We want gene sym, probe title etc. to be before the association
    // columns going left to right
    r.addAll(super.initHideableColumns(schema));

    return r;
  }

  protected boolean initVisibility(StandardColumns col) {
    return col != StandardColumns.GeneID;
  }

  protected String initWidth(StandardColumns col) {
    switch (col) {
      case Probe:
        return "8em";
      case GeneSym:
        return "10em";
      case ProbeTitle:
        return "18em";
      case GeneID:
        return "12em";
      default:
        return "15em";
    }
  }

  /**
   * The list of atomic probes currently on screen.
   */
  @Override
  public String[] displayedAtomicProbes() {
    return displayedAtomicProbes;
  }

  @Override
  protected String probeForRow(ExpressionRow row) {
    return row.getProbe();
  }

  @Override
  protected String[] atomicProbesForRow(ExpressionRow row) {
    return row.getAtomicProbes();
  }

  @Override
  protected String[] geneIdsForRow(ExpressionRow row) {
    return row.getGeneIds();
  }

  /**
   * This class fetches data on demand when the user requests a different page. Data must first be
   * loaded with getExpressions.
   * 
   * @author johan
   *
   */
  class KCAsyncProvider extends AsyncDataProvider<ExpressionRow> {
    private Range range;    
    AsyncCallback<List<ExpressionRow>> rowCallback = new AsyncCallback<List<ExpressionRow>>() {
      
      private String errMsg() {
        String appName = screen.appInfo().applicationName();
        return "Unable to obtain data. If you have not used " + appName + " in a while, try reloading the page.";
      }
      @Override
      public void onFailure(Throwable caught) {        
        loadedData = false;
        Window.alert(errMsg());
      }

      @Override
      public void onSuccess(List<ExpressionRow> result) {
        if (result.size() > 0) {
          updateRowData(range.getStart(), result);
          List<String> dispAts = new ArrayList<String>();
          List<String> dispPs = new ArrayList<String>();

          for (int i = 0; i < result.size(); ++i) {
            String[] ats = result.get(i).getAtomicProbes();
            for (String at : ats) {
              dispAts.add(at);
            }
            dispPs.add(result.get(i).getProbe());
          }

          displayedAtomicProbes = dispAts.toArray(new String[0]);
          displayedProbes = dispPs.toArray(new String[0]);
          highlightedRow = -1;
          getAssociations();
        } else {
          Window.alert(errMsg());
        }
      }
    };

    @Override
    protected void onRangeChanged(HasData<ExpressionRow> display) {
      if (loadedData) {
        range = display.getVisibleRange();
        computeSortParams();
        if (range.getLength() > 0) {
          matrixService.matrixRows(range.getStart(), range.getLength(), sortKey, sortAsc,
              rowCallback);
        }
      }
    }
  }

  @Override
  public void sampleClassChanged(SampleClass sc) {
    logger.info("Change SC to " + sc);
  }

  @Override
  public void columnsChanged(List<Group> columns) {
    HashSet<Group> oldColumns = new HashSet<Group>(chosenColumns);
    HashSet<Group> newColumns = new HashSet<Group>(columns);
    if (newColumns.equals(oldColumns) && newColumns.size() > 0) {
      logger.info("Ignoring column change signal");
      return;
    }

    super.columnsChanged(columns);

    // we set chosenSampleClass to the intersection of all the samples
    // in the groups here. Needed later for e.g. the associations() call.
    // TODO: this may need to be moved.
    // TODO: efficiency of this operation for 100's of samples
    List<SampleClass> allCs = new LinkedList<SampleClass>();
    for (Group g : columns) {
      allCs.addAll(SampleClassUtils.classes(Arrays.asList(g.getSamples())));
    }
    changeSampleClass(SampleClass.intersection(allCs));
    logger.info("Set SC to: " + chosenSampleClass.toString());

    groupsel1.clear();
    groupsel2.clear();
    for (DataColumn<?> dc : columns) {
      if (dc instanceof Group) {
        groupsel1.addItem(dc.getShortTitle(schema));
        groupsel2.addItem(dc.getShortTitle(schema));
      }
    }

    if (columns.size() >= 2) {
      groupsel1.setSelectedIndex(0);
      groupsel2.setSelectedIndex(1);
    }

    chartBarcodes = null;
    loadedData = false;
    logger.info("Columns changed (" + columns.size() + ")");
  }

  /**
   * Filter data that has already been loaded
   */
  public void refilterData() {
    if (!loadedData) {
      logger.info("Request to refilter but data was not loaded");
      return;
    }
    setEnabled(false);
    asyncProvider.updateRowCount(0, false);
    // grid.setRowCount(0, false);
    logger.info("Refilter for " + chosenProbes.length + " probes");
    matrixService.selectProbes(chosenProbes, dataUpdateCallback());
  }

  private AsyncCallback<ManagedMatrixInfo> dataUpdateCallback() {
    return new AsyncCallback<ManagedMatrixInfo>() {
      @Override
      public void onFailure(Throwable caught) {
        logger.log(Level.WARNING, "Exception in data update callback", caught);
        getExpressions(); // the user probably let the session
        // expire
      }

      @Override
      public void onSuccess(ManagedMatrixInfo result) {
        setMatrix(result);        
      }
    };
  }

  protected void setMatrix(ManagedMatrixInfo matrix) {
    matrixInfo = matrix;
    asyncProvider.updateRowCount(matrix.numRows(), true);
    int displayRows = (matrix.numRows() > INIT_PAGE_SIZE) ? INIT_PAGE_SIZE : matrix.numRows();
    grid.setVisibleRangeAndClearData(new Range(0, displayRows), true);
    setEnabled(true);
  }

  /**
   * Called when data is successfully loaded for the first time
   */
  private void onFirstLoad() {
    if (matrixInfo.isOrthologous()) {
      Analytics.trackEvent(Analytics.CATEGORY_TABLE, Analytics.ACTION_VIEW_ORTHOLOGOUS_DATA);
    }
  }

  /**
   * Load data (when there is nothing stored in our server side session)
   */
  public void getExpressions() {
    setEnabled(false);
    asyncProvider.updateRowCount(0, false);

    logger.info("begin loading data for " + chosenColumns.size() + " columns and "
        + chosenProbes.length + " probes");
    // load data
    matrixService.loadMatrix(chosenColumns, chosenProbes, chosenValueType,
        new AsyncCallback<ManagedMatrixInfo>() {
          @Override
          public void onFailure(Throwable caught) {
            Window.alert("Unable to load dataset");
            logger.log(Level.SEVERE, "Unable to load dataset", caught);
          }

          @Override
          public void onSuccess(ManagedMatrixInfo result) {
            if (result.numRows() > 0) {
              matrixInfo = result;
              if (!loadedData) {
                loadedData = true;
                onFirstLoad();
              }
              setupColumns();
              setMatrix(result);

              if (firstMatrixLoad) {
                firstMatrixLoad = false;
              } else {
                Analytics.trackEvent(Analytics.CATEGORY_TABLE, Analytics.ACTION_CHANGE_GENE_SET);
              }

              logger.info("Data successfully loaded");
            } else {
              Window
                  .alert("No data was available for this gene set.\nThe view will switch to default selection.");
              onGettingExpressionFailed();
            }
          }
        });
  }
  
  protected void onGettingExpressionFailed() {}

  private void displayCharts() {
    final Charts cgf = new Charts(screen, chosenColumns);
    ExpressionRow dispRow = grid.getVisibleItem(highlightedRow);
    final String[] probes = dispRow.getAtomicProbes();
    cgf.makeRowCharts(screen, chartBarcodes, chosenValueType, probes, new AChartAcceptor() {
      @Override
      public void acceptCharts(final AdjustableGrid<?, ?> cg) {
        Utils.displayInPopup("Charts", cg, true, DialogPosition.Side);
      }

      @Override
      public void acceptBarcodes(Sample[] bcs) {
        chartBarcodes = bcs;
      }
    });
    Analytics.trackEvent(Analytics.CATEGORY_VISUALIZATION, Analytics.ACTION_DISPLAY_CHARTS);
  }

  /**
   * This cell displays an image that can be clicked to display charts.
   */
  class ToolCell extends ImageClickCell.StringImageClickCell {

    public ToolCell(DataListenerWidget owner) {
      super(resources.chart(), false);
    }

    @Override
    public void onClick(final String value) {
      highlightedRow = SharedUtils.indexOf(displayedProbes, value);
      grid.redraw();
      Utils.ensureVisualisationAndThen(new Runnable() {
        @Override
        public void run() {
          displayCharts();
        }
      });
    }
  }


}
