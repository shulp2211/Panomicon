package gwttest.client;

import gwttest.shared.ValueType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.gwt.cell.client.NumberCell;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.cellview.client.SimplePager;
import com.google.gwt.user.cellview.client.SimplePager.TextLocation;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.MenuItemSeparator;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.view.client.ListDataProvider;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class Gwttest implements EntryPoint {
	/**
	 * The message displayed to the user when the server cannot be reached or
	 * returns an error.
	 */
	private static final String SERVER_ERROR = "An error occurred while "
			+ "attempting to contact the server. Please check your network "
			+ "connection and try again.";

	private OwlimServiceAsync owlimService = (OwlimServiceAsync) GWT.create(OwlimService.class);

	private KCServiceAsync kcService = (KCServiceAsync) GWT.create(KCService.class);

	private ListBox compoundList, organList, doseLevelList, barcodeList;
	private DataGrid<ExpressionRow> exprGrid;
	private ListDataProvider<ExpressionRow> listDataProvider;

	private ValueType chosenValueType = ValueType.Folds;

	enum DataSet {
		HumanVitro,
		RatVitro,
		RatVivoKidneySingle,
		RatVivoKidneyRepeat,
		RatVivoLiverSingle,
		RatVivoLiverRepeat
	}

	private DataSet chosenDataSet = DataSet.HumanVitro;

	/**
	 * This is the entry point method.
	 */
	public void onModuleLoad() {


		// Add the nameField and sendButton to the RootPanel
		// Use RootPanel.get() to get the entire body element
		RootPanel rootPanel = RootPanel.get("rootPanelContainer");
		rootPanel.setSize("850", "");
		rootPanel.getElement().getStyle().setPosition(Position.RELATIVE);

		NumberCell nc = new NumberCell();

		VerticalPanel verticalPanel = new VerticalPanel();
		rootPanel.add(verticalPanel);

		SimplePager.Resources pagerResources = GWT.create(SimplePager.Resources.class);

		listDataProvider = new ListDataProvider<ExpressionRow>();

		MenuBar menuBar = new MenuBar(false);
		verticalPanel.add(menuBar);
		MenuBar menuBar_1 = new MenuBar(true);

		MenuItem mntmNewMenu = new MenuItem("New menu", false, menuBar_1);

		MenuItem mntmNewItem = new MenuItem("New item", false, (Command) null);
		mntmNewItem.setHTML("Human, in vitro");
		menuBar_1.addItem(mntmNewItem);

		MenuItem mntmNewItem_1 = new MenuItem("New item", false, (Command) null);
		mntmNewItem_1.setHTML("Rat, in vitro");
		menuBar_1.addItem(mntmNewItem_1);

		MenuItem mntmNewItem_2 = new MenuItem("New item", false, (Command) null);
		mntmNewItem_2.setHTML("Rat, in vivo, liver, single");
		menuBar_1.addItem(mntmNewItem_2);

		MenuItem mntmNewItem_3 = new MenuItem("New item", false, (Command) null);
		mntmNewItem_3.setHTML("Rat, in vivo, liver, repeat");
		menuBar_1.addItem(mntmNewItem_3);

		MenuItem mntmNewItem_4 = new MenuItem("New item", false, new Command() {
			public void execute() {
			}
		});
		mntmNewItem_4.setHTML("Rat, in vivo, kidney, single");
		menuBar_1.addItem(mntmNewItem_4);

		MenuItem mntmNewItem_5 = new MenuItem("New item", false, (Command) null);
		mntmNewItem_5.setHTML("Rat, in vivo, kidney, repeat");
		menuBar_1.addItem(mntmNewItem_5);

		MenuItemSeparator separator = new MenuItemSeparator();
		menuBar_1.addSeparator(separator);

		MenuItem mntmFolds = new MenuItem("Fold values", false, new Command() {
			public void execute() {
				chosenValueType = ValueType.Folds;
				reloadData();
			}
		});		
		menuBar_1.addItem(mntmFolds);

		MenuItem mntmAbsoluteValues = new MenuItem("Absolute values", false, new Command() {
			public void execute() {
				chosenValueType = ValueType.Absolute;
				reloadData();
			}
		});
		
		menuBar_1.addItem(mntmAbsoluteValues);
		mntmNewMenu.setHTML("Data set");
		menuBar.addItem(mntmNewMenu);

		MenuItem mntmSettings = new MenuItem("Settings", false, (Command) null);
		menuBar.addItem(mntmSettings);

		FlowPanel flowPanel = new FlowPanel();
		verticalPanel.add(flowPanel);
		flowPanel.setSize("768px", "");

		compoundList = new ListBox();
		flowPanel.add(compoundList);
		compoundList.setSize("210px", "202px");
		compoundList.setVisibleItemCount(10);
		compoundList.addChangeHandler(new ChangeHandler() {
			public void onChange(ChangeEvent event) {
				String compound = compounds[compoundList.getSelectedIndex()];
				getOrgans(compound);
				getDoseLevels(compound, null);
			}
		});


		organList = new ListBox();
		flowPanel.add(organList);
		organList.setSize("11em", "202px");
		organList.setVisibleItemCount(10);

		doseLevelList = new ListBox();
		flowPanel.add(doseLevelList);
		doseLevelList.setSize("10em", "202px");
		doseLevelList.setVisibleItemCount(10);
		doseLevelList.addChangeHandler(new ChangeHandler() {
			public void onChange(ChangeEvent event) {
				String doseLevel = doseLevels[doseLevelList.getSelectedIndex()];
				String organ = organs[organList.getSelectedIndex()];
				String compound = compounds[compoundList.getSelectedIndex()];
				getBarcodes(compound, organ, doseLevel);
			}
		});

		barcodeList = new ListBox();
		flowPanel.add(barcodeList);
		barcodeList.setVisibleItemCount(10);
		barcodeList.setSize("15em", "202px");

		barcodeList.addChangeHandler(new ChangeHandler() {
			public void onChange(ChangeEvent event) {
				String barcode = barcodes[barcodeList.getSelectedIndex()];
				getExpressions(barcode);				
			}
		});
		organList.addChangeHandler(new ChangeHandler() {
			public void onChange(ChangeEvent event) {
				String compound = compounds[compoundList.getSelectedIndex()];
				String organ = organs[organList.getSelectedIndex()];
				getDoseLevels(compound, organ);
			}
		});
		SimplePager exprPager = new SimplePager(TextLocation.CENTER, pagerResources, true, 100, true);		
		verticalPanel.add(exprPager);

		exprGrid = new DataGrid<ExpressionRow>();		
		exprGrid.setSize("", "400px");
		exprGrid.setPageSize(20);
		exprGrid.setEmptyTableWidget(new HTML("No Data to Display Yet"));

		TextColumn<ExpressionRow> probeCol = new TextColumn<ExpressionRow>() {
			public String getValue(ExpressionRow er) {
				return er.getProbe();
			}
		};
		exprGrid.addColumn(probeCol, "Probe");

		TextColumn<ExpressionRow> titleCol = new TextColumn<ExpressionRow>() {
			public String getValue(ExpressionRow er) {
				return er.getTitle();
			}	
		};
		exprGrid.addColumn(titleCol, "Title");

		Column<ExpressionRow, Number> valueCol = new Column<ExpressionRow, Number>(nc) {
			public Double getValue(ExpressionRow er) {
				return er.getValue();
			}
		};		
		exprGrid.addColumn(valueCol, "Value");

		verticalPanel.add(exprGrid);
		listDataProvider.addDataDisplay(exprGrid);
		exprPager.setDisplay(exprGrid);

		reloadData();
	}

	private void reloadData() {
		getCompounds();				
	}

	private String[] compounds = new String[0];
	void getCompounds() {
		compoundList.clear();
		organList.clear();
		doseLevelList.clear();
		barcodeList.clear();
		owlimService.compounds(new AsyncCallback<String[]>() {
			public void onFailure(Throwable caught) {
				Window.alert("Unable to get compounds.");				
			}
			public void onSuccess(String[] result) {
				compounds = result;
				for (String compound: result) {					
					compoundList.addItem(compound);					
				}				
			}
		});
	}

	private String[] organs = new String[0];
	void getOrgans(String compound) {
		organList.clear();
		doseLevelList.clear();
		barcodeList.clear();
		owlimService.organs(compound, new AsyncCallback<String[]>() {
			public void onFailure(Throwable caught) {
				Window.alert("Unable to get organs.");				
			}
			public void onSuccess(String[] result) {
				organs = result;
				for (String organ: result) {					
					organList.addItem(organ);					
				}				
			}
		});
	}

	private String[] doseLevels = new String[0];
	void getDoseLevels(String compound, String organ) {
		doseLevelList.clear();
		barcodeList.clear();
		owlimService.doseLevels(null, null, new AsyncCallback<String[]>() {
			public void onFailure(Throwable caught) {
				Window.alert("Unable to get dose levels.");				
			}
			public void onSuccess(String[] result) {
				doseLevels = result;				
				for (String doseLevel: result) {					
					doseLevelList.addItem(doseLevel);					
				}				
			}
		});
	}

	private String[] barcodes = new String[0];
	void getBarcodes(String compound, String organ, String doseLevel) {
		barcodeList.clear();
		owlimService.barcodes(compound, organ, doseLevel, new AsyncCallback<String[]>() {
			public void onFailure(Throwable caught) {
				Window.alert("Unable to get barcodes.");				
			}
			public void onSuccess(String[] result) {
				barcodes = result;
				for (String barcode: result) {							
					barcodeList.addItem(barcode);					
				}				
			}
		});
	}

	void getExpressions(String barcode) {		
		listDataProvider.setList(new LinkedList<ExpressionRow>());				
		
		AsyncCallback<List<ExpressionRow>> rowCallback = new AsyncCallback<List<ExpressionRow>>() {
			public void onFailure(Throwable caught) {
				Window.alert("Unable to get expression values.");				
			}
			public void onSuccess(List<ExpressionRow> result) {
				listDataProvider.setList(result);				
				
				List<String> probes = new ArrayList();
				List<String> bcs = new ArrayList();
				for (ExpressionRow er: result) {
					probes.add(er.getProbe());
				}
				for (String bc: barcodes) {
					bcs.add(bc);
				}
				kcService.loadDataset(bcs, null, chosenValueType, new AsyncCallback<Void>() {
					public void onFailure(Throwable caught) {}
					public void onSuccess(Void v) {}
				});
			}
		};
		
		if (chosenValueType == ValueType.Absolute) {
			kcService.absoluteValues(barcode, rowCallback);
		} else {
			kcService.foldValues(barcode, rowCallback);
		}
	}
}
