package otgviewer.client;

import java.util.ArrayList;
import java.util.List;

import otgviewer.client.components.Screen;
import otgviewer.client.components.ScreenManager;
import otgviewer.shared.DataColumn;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

public class ColumnScreen extends Screen {
	public static String key = "columns";
	
	private GroupInspector gi;
	private VerticalPanel vp;
	private HorizontalPanel hp;
	
	public ColumnScreen(ScreenManager man) {
		super("Sample group definitions", key, true, false, man,
				resources.groupDefinitionHTML(), resources.groupDefinitionHelp());
	}
	
	@Override
	public boolean enabled() {
		return manager.isConfigured(DatasetScreen.key); 
	}

	public Widget content() {		
		vp = Utils.mkVerticalPanel();
		hp = Utils.mkHorizontalPanel();
		hp.setHeight("100%");
		
		vp.add(hp);
		CompoundSelector cs = new CompoundSelector("Compounds");
		this.addListener(cs);
		hp.add(cs);
		
		TabPanel tp = new TabPanel();
		hp.add(tp);		
		
		gi = new GroupInspector(cs, this);
		this.addListener(gi);
		cs.addListener(gi);
		tp.add(gi, "Sample groups");
		
		final CompoundRanker cr = new CompoundRanker(cs);
		tp.add(cr, "Compound ranking (optional)");
		tp.selectTab(0);
		tp.setHeight("100%");
		
		return vp;
	}

	@Override
	public Widget bottomContent() {
		Button b = new Button("Next: Select probes", new ClickHandler() {			
			public void onClick(ClickEvent event) {
				if (gi.chosenColumns().size() == 0) {
					Window.alert("Please define and activate at least one group.");
				} else {
					configuredProceed(ProbeScreen.key);					
				}
			}
		});
		return b;
	}
	
	@Override
	public void loadState() {
		super.loadState();
		if (visible) {
			//If we became visible, we must have been enabled, so can count on a
			//data filter being present.
			try {
				List<DataColumn> ics = loadColumns("inactiveColumns", 
						new ArrayList<DataColumn>(gi.existingGroupsTable.inverseSelection()));
				if (ics != null) {
					gi.inactiveColumnsChanged(ics);
				}

			} catch (Exception e) {
				Window.alert("Unable to load inactive columns.");
			}
		}
	}

	@Override
	public void tryConfigure() {
		if (chosenColumns.size() > 0) {		
			setConfigured(true);
		}
	}

	
}
