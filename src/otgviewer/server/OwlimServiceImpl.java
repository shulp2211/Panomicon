package otgviewer.server;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import otg.B2RAffy;
import otg.B2RKegg;
import otg.BCode;
import otg.CHEMBL;
import otg.OTGOwlim;
import otg.OTGQueries;
import otgviewer.client.OwlimService;
import otgviewer.shared.Barcode;
import otgviewer.shared.DataFilter;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class OwlimServiceImpl extends RemoteServiceServlet implements
		OwlimService {

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		OTGOwlim.connect();
	}
	
	public void destroy() {
		OTGOwlim.close();
		super.destroy();		
	}
	
	private otg.Filter toScala(DataFilter filter) {
		return new otg.Filter(filter.cellType.toString(), filter.organ.toString(),
				filter.repeatType.toString(), filter.organism.toString());
	}
	
	public String[] compounds(DataFilter filter) {		
		return OTGOwlim.compounds(toScala(filter));		
	}

	public String[] organs(DataFilter filter, String compound) {		
		return OTGOwlim.organs(toScala(filter), compound);		
	}

	public String[] doseLevels(DataFilter filter, String compound, String organ) {		
		return OTGOwlim.doseLevels(toScala(filter), compound);		
	}

	public Barcode[] barcodes(DataFilter filter, String compound, String organ,
			String doseLevel, String time) {

		BCode[] codes = OTGOwlim.barcodes(toScala(filter), compound, doseLevel, time);
		Barcode[] r = new Barcode[codes.length];
		int i = 0;
		for (BCode code : codes) {
			r[i] = new Barcode(code.code(), code.individual(), code.dose(),
					code.time());
			i += 1;
		}
		return r;
	}
	
	public String[] times(DataFilter filter, String compound, String organ) {
		return OTGOwlim.times(toScala(filter), compound);
	}
	
	public String probeTitle(String probe) {		
		return B2RAffy.title(probe);					
	}
	
	public String[] probes(DataFilter filter) {
		String homePath = System.getProperty("otg.home");
		return OTGQueries.probes(homePath + "/rat.probes.txt");
	}
	
	public String[] pathways(String pattern) {
		try {
			B2RKegg.connect();
			return B2RKegg.pathways(pattern, "rno");
		} finally {
			B2RKegg.close();
		}		
	}
	
	public String[] probesForPathway(String pathway) {
		try {
			B2RKegg.connect();			
			String homePath = System.getProperty("otg.home");
			String[] geneIds = B2RKegg.geneIds(pathway, "rno");
			System.out.println("Probes for " + geneIds.length + " genes");
			String [] probes = OTGOwlim.probesForEntrezGenes(geneIds);
			return OTGQueries.filterProbes(probes, homePath + "/rat.probes.txt");			
		} finally {
			B2RKegg.close();			
		}		
	}
	
	public String[] probesTargetedByCompound(String compound) {
		try {
			CHEMBL.connect();			
			String homePath = System.getProperty("otg.home");
			String[] prots = CHEMBL.targetProtsForCompound(compound, "Rattus norvegicus");
			System.out.println("Probes for " + prots.length + " genes");
			String [] probes = OTGOwlim.probesForUniprot(prots);
			return OTGQueries.filterProbes(probes, homePath + "/rat.probes.txt");			
		} finally {
			CHEMBL.close();			
		}		
	}

}
