/*
 * Copyright (c) 2012-2015 Toxygates authors, National Institutes of Biomedical Innovation, Health and Nutrition 
 * (NIBIOHN), Japan.
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

package otgviewer.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import t.common.shared.DataSchema;
import t.common.shared.SampleClass;
import t.common.shared.SharedUtils;
import t.common.shared.sample.SampleGroup;
import t.viewer.shared.Unit;

/**
 * A group of barcodes. Values will be computed as an average.
 * @author johan
 *
 */
public class Group extends SampleGroup<OTGSample> implements OTGColumn {
	
	protected Unit[] _units;
	
	public Group() {}
	
	public Group(DataSchema schema, String name, OTGSample[] barcodes, String color) {
		super(schema, name, barcodes, color);
		//TODO unit formation will not work if the barcodes have different sample classes 
		// - fix
		if (barcodes.length > 0) {
			_units = Unit.formUnits(schema, barcodes);
		} else {
			_units = new Unit[] {};
		}
	}
	
	public Group(DataSchema schema, String name, OTGSample[] barcodes) { 
		super(schema, name, barcodes);
		//TODO unit formation will not work if the barcodes have different sample classes 
		// - fix
		if (barcodes.length > 0) {
			_units = Unit.formUnits(schema, barcodes);
		} else {
			_units = new Unit[] {};
		}
	}
	
	public Group(DataSchema schema, String name, Unit[] units) { 
		super(schema, name, Unit.collectBarcodes(units)); 
		_units = units;
	}
	
	public Group(DataSchema schema, String name, Unit[] units, String color) {
		this(schema, name, Unit.collectBarcodes(units), color);
	}

	public String getShortTitle() {
		return name;
	}

	public OTGSample[] getSamples() { return _samples; }
	
	public OTGSample[] getTreatedSamples() {
		List<OTGSample> r = new ArrayList<OTGSample>();		
		for (Unit u : _units) {
			if (!schema.isSelectionControl(u)) {
				r.addAll(Arrays.asList(u.getSamples()));
			}
		}
		return r.toArray(new OTGSample[0]);
	}
	
	public OTGSample[] getControlSamples() {
		List<OTGSample> r = new ArrayList<OTGSample>();
		for (Unit u : _units) {
			if (schema.isSelectionControl(u)) {
				r.addAll(Arrays.asList(u.getSamples()));
			}
		}
		return r.toArray(new OTGSample[0]);
	}
	
	public Unit[] getUnits() { return _units; }
	
	public String getTriples(DataSchema schema, int limit, String separator) {
		Set<String> triples = new HashSet<String>();
		boolean stopped = false; 
		for (Unit u : _units) {
			if (schema.isControlValue(u.get(schema.mediumParameter()))) {
				continue;
			}
			if (triples.size() < limit || limit == -1) {
				triples.add(u.tripleString(schema));
			} else {
				stopped = true;
				break;
			}
		}
		String r = SharedUtils.mkString(triples, separator);
		if (stopped) {
			return r + "...";
		} else {
			return r;
		}
	}
	
	public Set<String> getMajors(DataSchema schema) {
		return getMajors((SampleClass) null);
	}
	
	//TODO is this method necessary?
	public Set<String> getMajors(@Nullable SampleClass sc) {
		List<OTGSample> sList = Arrays.asList(_samples);
		List<OTGSample> filtered = (sc != null) ?
				sc.filter(sList) : sList; 
		return SampleClass.collectInner(filtered, schema.majorParameter());				
	}
	
	public Set<String> collect(String parameter) {
		return SampleClass.collectInner(Arrays.asList(_samples), parameter);
	}
	
	public static Set<String> collectAll(Iterable<Group> from, String parameter) {
		Set<String> r = new HashSet<String>();
		for (Group g: from) {
			r.addAll(g.collect(parameter));
		}
		return r;
	}
	
	
	// See SampleGroup for the packing method
	// TODO lift up the unpacking code to have 
	// the mirror images in the same class, if possible
	public static Group unpack(DataSchema schema, String s) {
//		Window.alert(s + " as group");
		String[] s1 = s.split(":::"); // !!
		String name = s1[1];
		String color = "";
		String barcodes = "";
		if (s1.length == 4) {
			color = s1[2];
			barcodes = s1[3];
			if (SharedUtils.indexOf(groupColors, color) == -1) {
				//replace the color if it is invalid.
				//this lets us safely upgrade colors in the future.
				color = groupColors[0]; 
			}
		} else if (s1.length == 3) {
			color = groupColors[0];
			barcodes = s1[2];
		} else if (s1.length == 2) {
			color = groupColors[0];
		}
		if (s1.length >= 3) {
			String[] s2 = barcodes.split("\\^\\^\\^");
			OTGSample[] bcs = new OTGSample[s2.length];			
			for (int i = 0; i < s2.length; ++i) {
				OTGSample b = OTGSample.unpack(s2[i]);
				bcs[i] = b;
			}			
			//DataFilter useFilter = (bcs[0].getUnit().getOrgan() == null) ? filter : null;
			return new Group(schema, name, bcs, color);
			
		} else {
			return new Group(schema, name, new OTGSample[0], color);
		}
	}

}
