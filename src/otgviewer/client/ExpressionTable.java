package otgviewer.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import otgviewer.client.charts.AdjustableChartGrid;
import otgviewer.client.charts.ChartGridFactory;
import otgviewer.client.charts.ChartGridFactory.AChartAcceptor;
import otgviewer.client.components.AssociationTable;
import otgviewer.client.components.DataListenerWidget;
import otgviewer.client.components.ImageClickCell;
import otgviewer.client.components.PendingAsyncCallback;
import otgviewer.client.components.Screen;
import otgviewer.shared.AType;
import otgviewer.shared.Barcode;
import otgviewer.shared.DataColumn;
import otgviewer.shared.ExpressionRow;
import otgviewer.shared.Group;
import bioweb.shared.SharedUtils;
import otgviewer.shared.Synthetic;
import otgviewer.shared.ValueType;

import bioweb.shared.Pair;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.OpenEvent;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.cellview.client.PageSizePager;
import com.google.gwt.user.cellview.client.SimplePager;
import com.google.gwt.user.cellview.client.SimplePager.Resources;
import com.google.gwt.user.cellview.client.SimplePager.TextLocation;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.DoubleBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.NoSelectionModel;
import com.google.gwt.view.client.Range;

public class ExpressionTable extends AssociationTable<ExpressionRow> { 

	private final int PAGE_SIZE = 25;
	
	private Screen screen;
	private KCAsyncProvider asyncProvider = new KCAsyncProvider();
	
	private HorizontalPanel tools, analysisTools;
	
	private DoubleBox absValBox;
	private ListBox valueTypeList = new ListBox();
	
	private final KCServiceAsync kcService = (KCServiceAsync) GWT
			.create(KCService.class);	
	private static otgviewer.client.Resources resources = GWT.create(otgviewer.client.Resources.class);
	
	private List<Synthetic> synthetics = new ArrayList<Synthetic>();
	private List<Column<ExpressionRow, ?>> synthColumns = new ArrayList<Column<ExpressionRow, ?>>();
	
	private ListBox groupsel1 = new ListBox(), groupsel2 = new ListBox();
	
	private String[] displayedProbes;

 	private boolean loadedData = false;
 	
 	private Barcode[] chartBarcodes = null;

	public ExpressionTable(Screen _screen) {
		super();
		screen = _screen;
		
		grid.setStyleName("exprGrid");
		grid.setPageSize(PAGE_SIZE);
		
		grid.setSelectionModel(new NoSelectionModel<ExpressionRow>());		
		grid.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);	
		asyncProvider.addDataDisplay(grid);		

		makeTools();
		makeAnalysisTools();
		setEnabled(false);
	}
	
	private ValueType getValueType() {
		String vt = valueTypeList.getItemText(valueTypeList
				.getSelectedIndex());
		return ValueType.unpack(vt);		
	}
	
	public Widget tools() { return this.tools; }
	
	private void setEnabled(boolean enabled) {
		Utils.setEnabled(tools, enabled);
		Utils.setEnabled(analysisTools, enabled);
	}
	
	private void makeTools() {
		tools = Utils.mkHorizontalPanel();		
		
		HorizontalPanel horizontalPanel = Utils.mkHorizontalPanel(true);		
		horizontalPanel.setStyleName("colored");
		tools.add(horizontalPanel);
		
		valueTypeList.addItem(ValueType.Folds.toString());
		valueTypeList.addItem(ValueType.Absolute.toString());
		changeValueType(ValueType.Folds);
		valueTypeList.setVisibleItemCount(1);
		horizontalPanel.add(valueTypeList);
		valueTypeList.addChangeHandler(new ChangeHandler() {			
			@Override
			public void onChange(ChangeEvent event) {
				changeValueType(getValueType());
				getExpressions();
			}
		});

		Resources r = GWT.create(Resources.class);

		SimplePager sp = new SimplePager(TextLocation.CENTER, r, true, 500, true);
		sp.setStyleName("slightlySpaced");
		horizontalPanel.add(sp);		
		sp.setDisplay(grid);
		
		PageSizePager pager = new PageSizePager(25) {
			@Override
			protected void onRangeOrRowCountChanged() {
				super.onRangeOrRowCountChanged();
				if (getPageSize() > 100) {
					setPageSize(100);					
				}				
			}			
		};
		
		pager.setStyleName("slightlySpaced");
		horizontalPanel.add(pager);
		pager.setDisplay(grid);		
		
		Label label = new Label("Magnitude >=");
		label.setStyleName("highlySpaced");		
		horizontalPanel.add(label);

		absValBox = new DoubleBox();
		absValBox.setText("0.00");
		absValBox.setWidth("5em");
		horizontalPanel.add(absValBox);
		absValBox.addValueChangeHandler(new ValueChangeHandler<Double>() {			
			public void onValueChange(ValueChangeEvent<Double> event) {
				refilterData();				
			}
		});
		
		horizontalPanel.add(new Button("Apply", new ClickHandler() {
			public void onClick(ClickEvent e) {
				refilterData();
			}
		}));
		horizontalPanel.add(new Button("No filter", new ClickHandler() {
			public void onClick(ClickEvent e) {
				absValBox.setValue(0.0);
				refilterData();
			}
		}));

		DisclosurePanel analysisDisclosure = new DisclosurePanel("Analysis");
		tools.add(analysisDisclosure);
		analysisDisclosure.addOpenHandler(new OpenHandler<DisclosurePanel>() {			
			@Override
			public void onOpen(OpenEvent<DisclosurePanel> event) {
				screen.showToolbar(analysisTools, 35); //hack for IE8!
			}
		});
		analysisDisclosure.addCloseHandler(new CloseHandler<DisclosurePanel>() {			
			@Override
			public void onClose(CloseEvent<DisclosurePanel> event) {
				screen.hideToolbar(analysisTools);				
			}
		});		
	}
	
	public Widget analysisTools() { return analysisTools; }
	
	private void makeAnalysisTools() {
		analysisTools = Utils.mkHorizontalPanel(true);
		analysisTools.setStyleName("colored2");
		
		analysisTools.add(groupsel1);
		groupsel1.setVisibleItemCount(1);
		analysisTools.add(groupsel2);
		groupsel2.setVisibleItemCount(1);
		
		
		analysisTools.add(new Button("Add T-Test", new ClickHandler() {
			public void onClick(ClickEvent e) { addTwoGroupSynthetic(new Synthetic.TTest(null, null), "T-Test"); }							
		}));
		
		analysisTools.add(new Button("Add U-Test", new ClickHandler() {
			public void onClick(ClickEvent e) { addTwoGroupSynthetic(new Synthetic.UTest(null, null), "U-Test"); }							
		}));
		
		analysisTools.add(new Button("Remove tests", new ClickHandler() {
			public void onClick(ClickEvent ce) {
				if (!synthetics.isEmpty()) {
					for (int i = 0; i < synthColumns.size(); ++i) {						
						Column<ExpressionRow, ?> c = synthColumns.get(i);
						removeDataColumn(c);
					}
					synthColumns.clear();
					synthetics.clear();							
				}
			}
		}));
		analysisTools.setVisible(false); //initially hidden		
	}
	
	private String selectedGroup(ListBox groupSelector) {
		return groupSelector.getItemText(groupSelector.getSelectedIndex());
	}
	
	private void addTwoGroupSynthetic(final Synthetic.TwoGroupSynthetic synth, final String name) {
		if (groupsel1.getSelectedIndex() == -1 || groupsel2.getSelectedIndex() == -1) {
			Window.alert("Please select two groups to perform " + name + ".");
		} else if (groupsel1.getSelectedIndex() == groupsel2.getSelectedIndex()) {
			Window.alert("Please select two different groups to perform " + name + ".");
		} else {
			final Group g1 = Utils.findGroup(chosenColumns, selectedGroup(groupsel1));
			final Group g2 = Utils.findGroup(chosenColumns, selectedGroup(groupsel2));
			synth.setGroups(g1, g2);
			kcService.addTwoGroupTest(synth, new AsyncCallback<Void>() {
				public void onSuccess(Void v) {					
					addSynthColumn(synth, "p-value");					
					//force reload
					grid.setVisibleRangeAndClearData(grid.getVisibleRange(), true); 
				}
				public void onFailure(Throwable caught) {
					Window.alert("Unable to perform " + name);
				}
			});
		}
	}
	
	MenuItem[] menuItems() {
		MenuItem[] r = new MenuItem[2];
		MenuBar menuBar = new MenuBar(true);
		
		MenuItem mActions = new MenuItem("Actions", false, menuBar);		
		final DataListenerWidget w = this;
		MenuItem mntmDownloadCsv = new MenuItem("Download CSV...", false, new Command() {
			public void execute() {
				kcService.prepareCSVDownload(new PendingAsyncCallback<String>(w, "Unable to prepare the requested data for download.") {
					
					public void handleSuccess(String url) {
						final String downloadUrl = url;
						final DialogBox db = new DialogBox(false, true);							
												
						db.setHTML("Your download is ready.");				
						HorizontalPanel hp = new HorizontalPanel();
						
						hp.add(new Button("Download", new ClickHandler() {
							public void onClick(ClickEvent ev) {
								Window.open(downloadUrl, "_blank", "");
								db.hide();
							}
						}));
						
						hp.add(new Button("Cancel", new ClickHandler() {
							public void onClick(ClickEvent ev) {
								db.hide();								
							}
						}));
						
						db.add(hp);
						db.setPopupPositionAndShow(Utils.displayInCenter(db));						
					}
				});
				
			}
		});
		menuBar.addItem(mntmDownloadCsv);
		
		MenuItem mi = new MenuItem("Export to TargetMine...", false, new Command() {
			public void execute() {
				Utils.displayInPopup("TargetMine export", new GeneExporter(w, grid.getRowCount()));
			}
		});
		
		menuBar.addItem(mi);
		
		r[0] = mActions;
		
		menuBar = new MenuBar(true);
		MenuItem mColumns = new MenuItem("Columns", false, menuBar);
		setupMenuItems(menuBar);

		r[1] = mColumns;
		return r;
	}

	@Override
	protected void setupColumns() {
		super.setupColumns();
		TextCell tc = new TextCell();
				
		//columns with data
		for (DataColumn c : chosenColumns) {
			Column<ExpressionRow, String> valueCol = new ExpressionColumn(tc, dataColumns);			
			addDataColumn(valueCol, c.getShortTitle(), "Average of sample values");			
			valueCol.setCellStyleNames(((Group) c).getStyleName());
		}
		
		for (Synthetic s: synthetics) {
			addSynthColumn(s, "p-value");			
		}				
	}
	
	@Override
	protected Column<ExpressionRow, String> toolColumn(Cell<String> cell) {
		return new Column<ExpressionRow, String>(cell) {
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

	private void addSynthColumn(Synthetic s, String tooltip) {
		TextCell tc = new TextCell();
		synthetics.add(s);
		Column<ExpressionRow, String> ttestCol = new ExpressionColumn(tc, dataColumns);
		synthColumns.add(ttestCol); 				
		addDataColumn(ttestCol, s.getShortTitle(), tooltip);		
		ttestCol.setCellStyleNames("extraColumn");				
	}
	
	protected List<HideableColumn> initHideableColumns() {
		SafeHtmlCell shc = new SafeHtmlCell();
		List<HideableColumn> r = new ArrayList<HideableColumn>();
		
		r.add(new LinkingColumn<ExpressionRow>(shc, "Gene ID", false) {
			@Override
			protected String formLink(String value) {
				return AType.formGeneLink(value);
			}
			@Override
			protected Collection<Pair<String, String>> getLinkableValues(ExpressionRow er) {
				return Pair.duplicate(Arrays.asList(er.getGeneIds()));
			}						
		});
		
		r.add(new DefHideableColumn<ExpressionRow>("Gene Sym", true) {
			public String getValue(ExpressionRow er) {				
				return SharedUtils.mkString(er.getGeneSyms(), ", ");
			}
		});
		r.add(new DefHideableColumn<ExpressionRow>("Probe title", true) {
			public String getValue(ExpressionRow er) {				
				return er.getTitle();
			}
		});
		r.add(new DefHideableColumn<ExpressionRow>("Probe", true) {
			public String getValue(ExpressionRow er) {				
				return er.getProbe();
			}
		});		
		
		//We want gene sym, probe title etc. to be before the association columns going left to right
		r.addAll(super.initHideableColumns());
		
		return r;
	}
	
	protected String[] displayedProbes() { return displayedProbes; }
	protected String probeForRow(ExpressionRow row) { return row.getProbe(); }
	protected String[] geneIdsForRow(ExpressionRow row) { return row.getGeneIds(); }
	
	class KCAsyncProvider extends AsyncDataProvider<ExpressionRow> {
		private int start = 0;
		
		AsyncCallback<List<ExpressionRow>> rowCallback = new AsyncCallback<List<ExpressionRow>>() {
			public void onFailure(Throwable caught) {
				Window.alert("Unable to get expression values: " + caught.getMessage());
			}

			public void onSuccess(List<ExpressionRow> result) {
				if (result.size() > 0) {
					grid.setRowData(start, result);
					displayedProbes = new String[result.size()];
		
					for (int i = 0; i < displayedProbes.length; ++i) {			
						displayedProbes[i] = result.get(i).getProbe();
					}		

					highlightedRow = -1;							
					getAssociations();
				} else {
					Window.alert("Unable to obtain data. If you have not used Toxygates in a while, try reloading the page.");
				}
			}
		};

		protected void onRangeChanged(HasData<ExpressionRow> display) {
			if (loadedData) {
				Range range = display.getVisibleRange();		
				start = range.getStart();
				computeSortParams();
				if (range.getLength() > 0) {
					kcService.datasetItems(range.getStart(), range.getLength(),
							sortDataColumnIdx(), sortAscending(), rowCallback);
				}
			}
		}
	}

	@Override
	public void columnsChanged(List<Group> columns) {
		super.columnsChanged(columns);
		 //invalidate synthetic columns, since they depend on
		//normal columns
		dataColumns -= synthetics.size();
		synthetics.clear();
		
		groupsel1.clear();
		groupsel2.clear();
		for (DataColumn dc: columns) {
			if (dc instanceof Group) {
				groupsel1.addItem(dc.getShortTitle());
				groupsel2.addItem(dc.getShortTitle());
			}
		}
		
		if (columns.size() >= 2) {
			groupsel1.setSelectedIndex(0);
			groupsel2.setSelectedIndex(1);			
		}
		
		chartBarcodes = null;
	}
	
	void refilterData() {
		if (loadedData) {
			setEnabled(false);
			grid.setRowCount(0, false);
			List<DataColumn> cols = new ArrayList<DataColumn>();
			cols.addAll(chosenColumns);
			kcService.refilterData(chosenDataFilter, cols, chosenProbes,
					absValBox.getValue(), synthetics,
					new AsyncCallback<Integer>() {
						public void onFailure(Throwable caught) {
							getExpressions(); //the user probably let the session expire							
						}

						public void onSuccess(Integer result) {
							grid.setRowCount(result);
							grid.setVisibleRangeAndClearData(new Range(0,
									PAGE_SIZE), true);
							setEnabled(true);
						}
					});
		}
	}
	
	public void getExpressions() {
		setEnabled(false);
		grid.setRowCount(0, false);
		setupColumns();
		List<DataColumn> cols = new ArrayList<DataColumn>();
		cols.addAll(chosenColumns);

		// load data
		kcService.loadDataset(chosenDataFilter, cols, chosenProbes,
				chosenValueType, absValBox.getValue(), synthetics,
				new AsyncCallback<Integer>() {
					public void onFailure(Throwable caught) {
						Window.alert("Unable to load dataset");
					}

					public void onSuccess(Integer result) {
						if (result > 0) {
							loadedData = true;
							setEnabled(true);
							grid.setRowCount(result);
							grid.setVisibleRangeAndClearData(new Range(0,
									PAGE_SIZE), true);
						} else {
							Window.alert("No data was available. If you have not used Toxygates for a while, try reloading the page.");
						}
					}
				});
	}

	class ExpressionColumn extends Column<ExpressionRow, String> {
		int i;
		NumberFormat df = NumberFormat.getDecimalFormat();
		NumberFormat sf = NumberFormat.getScientificFormat();
		
		public ExpressionColumn(TextCell tc, int i) {
			super(tc);
			this.i = i;	
		}

		public String getValue(ExpressionRow er) {
			if (!er.getValue(i).getPresent()) {
				return "(absent)";
			} else {	
				return Utils.formatNumber(er.getValue(i).getValue());								
			}
		}
	}
		
	class ToolCell extends ImageClickCell {
		
		public ToolCell(DataListenerWidget owner) {
			super(resources.chart());
		}
		
		public void onClick(final String value) {			
			highlightedRow = SharedUtils.indexOf(displayedProbes, value);
			grid.redraw();
			
			final ChartGridFactory cgf = new ChartGridFactory(chosenDataFilter, chosenColumns);
			Utils.ensureVisualisationAndThen(new Runnable() {
				public void run() {
					cgf.makeRowCharts(screen, chartBarcodes, chosenValueType, value, 
							new AChartAcceptor() {
						public void acceptCharts(final AdjustableChartGrid cg) {
							Utils.displayInPopup("Charts", cg, true);							
						}

						public void acceptBarcodes(Barcode[] bcs) {
							chartBarcodes = bcs;
						}
					});			
				}
			});
		}
	}	
}
