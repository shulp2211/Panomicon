package otgviewer.client;
import otgviewer.shared.Barcode;
import otgviewer.shared.DataFilter;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

@RemoteServiceRelativePath("owlim")
public interface OwlimService extends RemoteService {
	
	//Methods relating to metadata about our microarrays.
	public String[] compounds(DataFilter filter);
	public String[] organs(DataFilter filter, String compound);
	public String[] doseLevels(DataFilter filter, String compound, String organ);	
	public Barcode[] barcodes(DataFilter filter, String compound, String organ, String doseLevel, String time);	
	public String[] times(DataFilter filter, String compound, String organ);		
	
	//Other methods.
	public String[] probes(DataFilter filter);
	
	/**
	 * Obtain pathway names matching the pattern.
	 * @param pattern
	 * @return
	 */
	public String[] pathways(DataFilter filter, String pattern);
	
	/**
	 * Obtain probes that belong to the named pathway.
	 * @param pathway
	 * @return
	 */
	public String[] probesForPathway(DataFilter filter, String pathway);
	
	/**
	 * Obtain probes that correspond to proteins targeted by
	 * the named compound.
	 * @param compound
	 * @return
	 */
	public String[] probesTargetedByCompound(DataFilter filter, String compound, String service);
	
	public String[] goTerms(String pattern);
	
	public String[] probesForGoTerm(DataFilter filter, String goTerm);
	
	public String[][] geneSymsForProbes(String[] probes);
}
