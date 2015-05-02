package t.viewer.client.rpc;

import java.util.List;

import otgviewer.shared.MatchResult;
import otgviewer.shared.RankRule;
import otgviewer.shared.Series;
import t.common.shared.Dataset;
import t.common.shared.SampleClass;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface SeriesServiceAsync {
	public void rankedCompounds(Dataset[] ds, SampleClass sc, 
			RankRule[] rules, AsyncCallback<MatchResult[]> callback);

	public void getSingleSeries(SampleClass sc, String probe,
			String timeDose, String compound, AsyncCallback<Series> callback);

	public void getSeries(SampleClass sc, String[] probes, String timeDose,
			String[] compounds, AsyncCallback<List<Series>> callback);
	
	public void expectedTimes(Series s, AsyncCallback<String[]> callback);
}