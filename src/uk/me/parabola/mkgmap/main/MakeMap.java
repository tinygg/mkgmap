/*
 * Copyright (C) 2006 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 16-Dec-2006
 */
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.mkgmap.osm.ReadOsm;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapSource;
import uk.me.parabola.mkgmap.FormatException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.LBLFile;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.imgfmt.app.Overview;
import uk.me.parabola.imgfmt.app.Polyline;
import uk.me.parabola.imgfmt.app.RGNFile;
import uk.me.parabola.imgfmt.app.Subdivision;
import uk.me.parabola.imgfmt.app.TREFile;
import uk.me.parabola.imgfmt.app.Zoom;

import java.io.FileNotFoundException;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * Main routine to make a map as a command line utility.
 *
 * @author Steve Ratcliffe
 */
public class MakeMap {
	private static final Logger log = Logger.getLogger(MakeMap.class);

	public static void main(String[] args) {
		String name = args[0];
		String mapname = "63240001";

		Args a = new Args();
		a.setName(name);
		a.setMapname(mapname);

		try {
			MakeMap mm = new MakeMap();
			mm.makeMap(a);
		} catch (ExitException e) {
			System.err.println(e.getMessage());
		}
	}

	private void makeMap(Args args) {
		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription("OSM street map");

		Map map = null;
		try {
			map = Map.createMap(args.getMapname(), params);

			MapSource src = loadFromFile(args.getName());

			processInfo(map, src);
			Subdivision div = makeDivisions(map, src);

			List<MapLine> lines = src.getLines();
			processLines(map, div, lines);
			
		} finally {
			if (map != null)
				map.close();
		}
	}

	/**
	 * Set all the information that appears in the header.
	 *
	 * @param map The map to write to.
	 * @param src The source of map information.
	 */
	private void processInfo(Map map, MapSource src) {
		TREFile tre = map.getTRE();

		// The bounds of the map.
		Area bounds = src.getBounds();
		tre.setBounds(bounds);

		// Make a few settings
		tre.setPoiDisplayFlags((byte) 0);

		// You can any old junk here.
		tre.addInfo("Created by mkgmap");
		tre.addInfo("Program released under the GPL");
		tre.addInfo("Map data licenced under Creative Commons Attribution ShareAlike 2.0");

		LBLFile lbl = map.getLBL();

		// This one will not show up.
		Label cpy = lbl.newLabel("mkgmap program licenced under GPL v2");
		tre.addCopyright(cpy);

		// This one gets shown when you switch on, so put the actual
		// map copyright here.
		cpy = lbl.newLabel(src.copyrightMessage());
		tre.addCopyright(cpy);
	}

	private void processLines(Map map, Subdivision div,
							  List<MapLine> lines)
	{
		LBLFile lbl = map.getLBL();
		RGNFile rgn = map.getRGN();

		for (MapLine line : lines) {
			Polyline pl = new Polyline(div, 6);
			String name = line.getName();
			if (name == null)
				continue;

			log.debug("Road " + name + ", t=" + line.getType());
			Label label = lbl.newLabel(name);
			List<Coord> points = line.getPoints();
			for (Coord co : points) {
				log.debug("  point at " + co);
				pl.addCoord(co);
			}

			pl.setLabel(label);
			rgn.addMapObject(pl);
		}
	}

	/**
	 * Make the subdivisions in the map.
	 * As we only use 1 (plus the empty top one) this will change a
	 * lot.
	 * TODO: needs to step though all zoom levels.
	 * TODO: for each zoom level, create subdivisions.
	 * TODO: return something more than a single division.
	 *
	 * @param map The map to operate on.
	 * @param src The source of map information.
	 * @return A single division.  Will be chnaged.
	 */
	private Subdivision makeDivisions(Map map, MapSource src) {
		TREFile tre = map.getTRE();
		RGNFile rgn = map.getRGN();

		Area bounds = src.getBounds();

		// There must be an empty zoom level at the least detailed level.
		Zoom z1 = tre.createZoom(1, 24);

		z1.setInherited(true);
		Subdivision div = Subdivision.topLevelSubdivision(bounds, z1);
		rgn.addDivision(div);

		// Create the most detailed view.
		Zoom z = tre.createZoom(0, 24);
		div = div.createSubdivision(bounds, z);
		rgn.addDivision(div);

		// Set the list of features supported on the map.
		Overview ov = new Overview(6, 1);
		tre.addPolylineOverview(ov);

		// Set the fact that there are lines in the map.
		div.setHasPolylines(true);
		div.setHasPoints(false);
		div.setHasIndPoints(false);
		div.setHasPolygons(false);
		return div;
	}

	private MapSource loadFromFile(String name) {
		try {
			MapSource src = new ReadOsm();

			src.load(name);

			return src;
		} catch (FileNotFoundException e) {
			log.error("open fail", e);
			throw new ExitException("Could not open file: ", e);
		} catch (FormatException e) {
			throw new ExitException("Bad input file format", e);
		}
	}

	private static class Args {
		private String name;
		private String mapname;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getMapname() {
			return mapname;
		}

		public void setMapname(String mapname) {
			this.mapname = mapname;
		}
	}
}
