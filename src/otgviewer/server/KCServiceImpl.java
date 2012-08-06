package otgviewer.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import kyotocabinet.DB;
import otg.B2RAffy;
import otg.ExprValue;
import otg.OTGQueries;
import otg.CSVHelper;
import otg.Species;
import otgviewer.client.KCService;
import otgviewer.shared.DataFilter;
import otgviewer.shared.ExpressionRow;
import otgviewer.shared.ExpressionValue;
import otgviewer.shared.ValueType;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class KCServiceImpl extends RemoteServiceServlet implements KCService {

	//Future: keep connection open, close on shutdown.
	
	private DB foldsDB, absDB;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		String homePath = System.getProperty("otg.home");
		
		foldsDB = OTGQueries.open(homePath + "/otgf.kct");
		absDB = OTGQueries.open(homePath + "/otg.kct");
		System.out.println("KC databases are open");
	}
	
	public void destroy() {		
		System.out.println("Closing KC databases");
		foldsDB.close();
		absDB.close();
		super.destroy();
	}
	
	public List<ExpressionRow> absoluteValues(DataFilter filter, String barcode) {
		return valuesFromDb(filter, barcode, absDB);		
	}
	
	public List<ExpressionRow> foldValues(DataFilter filter, String barcode) {
		return valuesFromDb(filter, barcode, foldsDB);
	}
	
	private List<ExpressionRow> valuesFromDb(DataFilter filter, String barcode, DB db) {
		Species s = Utils.speciesFromFilter(filter);
		B2RAffy.connect();		
		try {			
			Map<String, ExprValue> r = OTGQueries.presentValuesByBarcode4J(db, barcode, s);
			System.out.println("Read " + r.size() + " records");
			List<ExpressionRow> rr = new ArrayList<ExpressionRow>();
			String[] geneKeys = r.keySet().toArray(new String[0]);
			List<String> probeTitles = B2RAffy.titlesForJava(geneKeys);
			List<String[]> geneIds = B2RAffy.geneIdsForJava(geneKeys);
			List<String[]> geneSyms = B2RAffy.geneSymsForJava(geneKeys);
			Iterator<String> ts = probeTitles.iterator();
			Iterator<String[]> gs = geneIds.iterator();
			Iterator<String[]> ss = geneSyms.iterator();
			//TODO assuming ts and gs have same size
			for (String probe: r.keySet()) {
				ExprValue ev = r.get(probe);
				ExpressionValue jev = new ExpressionValue(ev.value(), ev.call());
				if (ts.hasNext()) {
					rr.add(new ExpressionRow(probe, ts.next(), gs.next(), ss.next(), jev));
				} else {
					rr.add(new ExpressionRow(probe, "(none)", new String[0], new String[0], jev));
				}
			}
			System.out.println("Returning " + r.size() + " data rows");
			return rr;
		} finally {
			B2RAffy.close();
		}
	}
	

	private String[] filterProbes(DataFilter filter, String[] probes) {	
		String[] realProbes = probes;
		Species s = Utils.speciesFromFilter(filter);
		if (probes == null) {
			// get all probes
			realProbes = OTGQueries.probeIds(s);
		} else {
			realProbes = OTGQueries.filterProbes(probes, s);					
		}
		System.out.println(realProbes.length
				+ " probes requested after filtering");
		return realProbes;
	}
	
	private ExprValue[][] getExprValues(DataFilter filter, List<String> barcodes, String[] probes,
			ValueType type, boolean sparseRead) {
		DB db = null;
		if (barcodes == null) {
			return new ExprValue[0][0];
		}

		switch (type) {
		case Folds:
			db = foldsDB;
			break;
		case Absolute:
			db = absDB;
			break;
		}
		return OTGQueries.presentValuesByBarcodesAndProbes4J(db, barcodes,
				probes, sparseRead, Utils.speciesFromFilter(filter));

	}
	
	public int loadDataset(DataFilter filter, List<String> barcodes, String[] probes, ValueType type) {
		HttpServletRequest request = getThreadLocalRequest();
		HttpSession session = request.getSession();
		
		String[] realProbes = filterProbes(filter, probes);
		ExprValue[][] r = getExprValues(filter, barcodes, realProbes, type, false);

		session.setAttribute("dataset", r);
		session.setAttribute("datasetProbes", realProbes);
		session.setAttribute("datasetBarcodes", barcodes.toArray(new String[0]));
		if (r.length > 0) {
			System.out.println("Stored " + r.length + " x " + r[0].length
					+ " items in session, " + realProbes.length + " probes");
		} else {
			System.out.println("Stored empty data in session");
		}
		return r.length;
	}
	
	private ExpressionRow arrayToRow(String probe, String title, String[] geneIds, String[] geneSyms, ExprValue[] vals) {
		ExpressionValue[] vout = new ExpressionValue[vals.length];

		for (int j = 0; j < vals.length; ++j) {
			vout[j] = new ExpressionValue(vals[j].value(), vals[j].call());						
		}
		return new ExpressionRow(probe, title, geneIds, geneSyms, vout);
	}
	
	private List<ExpressionRow> arrayToRows(String[] probes, ExprValue[][] data, int offset, int size) {
		List<ExpressionRow> r = new ArrayList<ExpressionRow>();

		if (probes != null && data != null) {
			try {
				B2RAffy.connect();				
				List<String> probeTitles = B2RAffy.titlesForJava((String[]) Arrays.copyOfRange(probes, offset, offset+size));
				List<String[]> geneIds = B2RAffy.geneIdsForJava((String[]) Arrays.copyOfRange(probes, offset, offset+size));
				List<String[]> geneSyms = B2RAffy.geneSymsForJava((String[]) Arrays.copyOfRange(probes, offset, offset+size));
				for (int i = offset; i < offset + size && i < probes.length && i < data.length; ++i) {					
					r.add(arrayToRow(probes[i], probeTitles.get(i - offset), geneIds.get(i - offset), 
							geneSyms.get(i - offset), data[i]));					
				}
			} finally {
				B2RAffy.close();
			}
		}

		return r;
	}
	
	public List<ExpressionRow> datasetItems(int offset, int size) {
		HttpServletRequest request = getThreadLocalRequest();
		HttpSession session = request.getSession();
		ExprValue[][] data = (ExprValue[][]) session.getAttribute("dataset");
		if (data != null) {
			System.out.println("I had " + (data).length + " rows stored");
		}
		String[] probes = (String[]) session.getAttribute("datasetProbes");
		return arrayToRows(probes, data, offset, size);
	}
	
	
	public List<ExpressionRow> getFullData(DataFilter filter, List<String> barcodes, 
			String[] probes, ValueType type, boolean sparseRead) {
		String[] realProbes = filterProbes(filter, probes);
		ExprValue[][] r = getExprValues(filter, barcodes, realProbes, type, sparseRead);
		return arrayToRows(realProbes, r, 0, r.length);
		
	}
	
	public String prepareCSVDownload() {
		HttpServletRequest request = getThreadLocalRequest();
		HttpSession session = request.getSession();
		ExprValue[][] data = (ExprValue[][]) session.getAttribute("dataset");		
		if (data != null) {
			System.out.println("I had " + (data).length + " rows stored");
		}
		String[] probes = (String[]) session.getAttribute("datasetProbes");
		String[] barcodes = (String[]) session.getAttribute("datasetBarcodes"); //todo: more helpful names would be good
		return CSVHelper.writeCSV(probes, barcodes, data);	
	}
}
