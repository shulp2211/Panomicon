package otgviewer.client;

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.resources.client.TextResource;

public interface Resources extends ClientBundle {

	@Source("images/16_statistics.png")
	ImageResource chart();
	
	@Source("images/16_close.png")
	ImageResource close();
	
	@Source("images/16_info.png")
	ImageResource info();
	
	@Source("images/16_search.png")
	ImageResource magnify();
	
	@Source("images/16_faq.png")
	ImageResource help();
	
	@Source("images/12_filter.png")
	ImageResource filter();
	
	@Source("help/default.html")
	TextResource defaultHelpHTML();
	
	@Source("help/groupDefinition.png")
	ImageResource groupDefinitionHelp();
	
	@Source("help/groupDefinition.html")
	TextResource groupDefinitionHTML();	
	
	@Source("help/probeSelection.png")
	ImageResource probeSelectionHelp();
	
	@Source("help/probeSelection.html")
	TextResource probeSelectionHTML();
	
	@Source("help/dataDisplay.html")
	TextResource dataDisplayHTML();
	
	@Source("help/dataDisplay.png")
	ImageResource dataDisplayHelp();
	
	@Source("help/compoundRanking.html")
	TextResource compoundRankingHTML();
	
	@Source("help/compoundRanking.png")
	ImageResource compoundRankingHelp();
	
	@Source("help/about.html")
	TextResource aboutHTML();
	
	@Source("help/about.png")
	ImageResource about();
	
	@Source("help/versions.html")
	TextResource versionHTML();
	
	@Source("help/banner.html")
	TextResource bannerHTML();
	
	@Source("help/adjuvantBanner.html")
	TextResource adjuvantBannerHTML();
}
