/*
 *	Drifting Souls 2
 *	Copyright (c) 2006 Christopher Jung
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.driftingsouls.ds2.server.modules;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.pipeline.Module;
import net.driftingsouls.ds2.server.framework.pipeline.generators.Action;
import net.driftingsouls.ds2.server.framework.pipeline.generators.ActionType;
import net.driftingsouls.ds2.server.framework.pipeline.generators.DSGenerator;
import net.driftingsouls.ds2.server.modules.stats.StatBiggestAsteroid;
import net.driftingsouls.ds2.server.modules.stats.StatBiggestFleet;
import net.driftingsouls.ds2.server.modules.stats.StatBiggestPopulation;
import net.driftingsouls.ds2.server.modules.stats.StatBiggestTrader;
import net.driftingsouls.ds2.server.modules.stats.StatData;
import net.driftingsouls.ds2.server.modules.stats.StatEinheiten;
import net.driftingsouls.ds2.server.modules.stats.StatGtuPrice;
import net.driftingsouls.ds2.server.modules.stats.StatMemberCount;
import net.driftingsouls.ds2.server.modules.stats.StatOwnCiv;
import net.driftingsouls.ds2.server.modules.stats.StatOwnKampf;
import net.driftingsouls.ds2.server.modules.stats.StatOwnOffiziere;
import net.driftingsouls.ds2.server.modules.stats.StatPlayerList;
import net.driftingsouls.ds2.server.modules.stats.StatPopulationDensity;
import net.driftingsouls.ds2.server.modules.stats.StatRichestUser;
import net.driftingsouls.ds2.server.modules.stats.StatShipCount;
import net.driftingsouls.ds2.server.modules.stats.StatShips;
import net.driftingsouls.ds2.server.modules.stats.StatWaren;
import net.driftingsouls.ds2.server.modules.stats.Statistic;

/**
 * Die Statistikseite.
 * @author Christopher Jung
 *
 * @urlparam Integer stat Die ID der Statistik in der ausgewaehlten Kategorie
 * @urlparam Integer show die ID der ausgeaehlten Kategorie
 */
@Module(name="stats")
public class StatsController extends DSGenerator {
	/**
	 * Die minimale User/Ally-ID um in den Statistiken beruecksichtigt zu werden.
	 */
	public static final int MIN_USER_ID = 0;
	/**
	 * Die groesste moegliche Forschungs-ID + 1.
	 */
	public static final int MAX_RESID = 100;

	private static class StatEntry {
		Statistic stat;
		String name;
		int width;

		StatEntry( Statistic stat, String name, int width ) {
			this.stat = stat;
			this.name = name;
			this.width = width;
		}
	}
	private Map<Integer,List<StatEntry>> statslist = new HashMap<Integer,List<StatEntry>>();
	private Map<String,Integer> catlist = new LinkedHashMap<String,Integer>();
	private int show = 0;

	/**
	 * Konstruktor.
	 * @param context Der zu verwendende Kontext
	 */
	public StatsController(Context context) {
		super(context);

		parameterNumber("stat");
		parameterNumber("show");

		setPageTitle("Statistik");
	}

	@Override
	protected boolean validateAndPrepare(String action) {
		registerStat( "Spieler", new StatOwnCiv(), "Meine Zivilisation", 0 );
		registerStat( "Spieler", new StatBiggestFleet(false), "Die gr&ouml;ssten Flotten", 60 );
		registerStat( "Spieler", new StatBiggestTrader(false), "Die gr&ouml;ssten Handelsflotten", 60);
		registerStat( "Spieler", new StatRichestUser(false), "Die reichsten Siedler", 60);
		registerStat( "Spieler", new StatBiggestPopulation(false), "Die gr&ouml;&szlig;ten V&ouml;lker", 30 );
		registerStat( "Spieler", new StatBiggestAsteroid(), "Die gr&ouml;&szlig;ten Asteroiden", 100 );
		registerStat( "Spieler", new StatGtuPrice(), "Die h&ouml;chsten Gebote", 60 );

		registerStat( "Allianzen", new StatBiggestFleet(true), "Die gr&ouml;ssten Flotten", 60 );
		registerStat( "Allianzen", new StatBiggestTrader(true), "Die gr&ouml;ssten Handelsflotten", 60);
		registerStat( "Allianzen", new StatRichestUser(true), "Die reichsten Allianzen", 60);
		registerStat( "Allianzen", new StatBiggestPopulation(true), "Die gr&ouml;&szlig;ten V&ouml;lker", 30 );
		registerStat( "Allianzen", new StatMemberCount(), "Die gr&ouml;&szlig;ten Allianzen", 30 );

		registerStat( "Sonstiges", new StatPopulationDensity(), "Siedlungsdichte", 0 );
		registerStat( "Sonstiges", new StatShips(), "Schiffe", 0 );
		registerStat( "Sonstiges", new StatShipCount(), "Schiffsentwicklung", 0 );
		registerStat( "Sonstiges", new StatWaren(), "Waren", 0 );
		registerStat( "Sonstiges", new StatEinheiten(), "Einheiten", 0);
		registerStat( "Sonstiges", new StatData(), "Diverse Daten", 0 );

		registerStat( "Eigene K&auml;mpfe", new StatOwnKampf(), "Eigene K&auml;mpfe", 0 );
		registerStat( "Offiziere", new StatOwnOffiziere(), "Offiziere", 0 );
		registerStat( "Spielerliste", new StatPlayerList(), "Spielerliste", 0 );

		int show = getInteger("show");
		if( (show == 0) || !this.statslist.containsKey(show) ) {
			show = 1;
		}
		this.show = show;

		return true;
	}

	private void registerStat( String cat, Statistic stat, String name, int size ) {
		if( !this.catlist.containsKey(cat) ) {
			this.catlist.put(cat, this.catlist.size()+1);
		}

		if( !this.statslist.containsKey(this.catlist.get(cat)) ) {
			this.statslist.put(this.catlist.get(cat), new ArrayList<StatEntry>());
		}
		this.statslist.get(this.catlist.get(cat)).add(new StatEntry(stat, name, size));
	}

	private void printMenu() throws IOException {
		Writer echo = getContext().getResponse().getWriter();

		Map<Integer,String> lists = new HashMap<Integer,String>();

		for( int listkey : this.statslist.keySet() ) {
			StringBuilder builder = new StringBuilder();

			List<StatEntry> alist = this.statslist.get(listkey);
			for( int i=0; i < alist.size(); i++ ) {
				builder.append("<dd><a style='font-size:12px;font-weight:normal' class='back' href='"+Common.buildUrl("default", "show", listkey, "stat", i)+"'>"+alist.get(i).name+"</a></dd>");
			}

			lists.put(listkey, builder.toString());
		}

		int catsize = this.catlist.size();
		int catpos = 0;

		echo.append("<div class='gfxbox' style='width:850px;text-align:center'>");
		for( String catkey : this.catlist.keySet() ) {
			int cat = this.catlist.get(catkey);

			if( this.statslist.containsKey(cat) && (this.statslist.get(cat).size() > 1) ) {
				echo.append("<div class='dropdown' style='width:120px'><dl><dt ");
				if( this.show == cat ) {
					echo.append("style=\"text-decoration:underline\"");
				}
				echo.append(">"+catkey+"<img style='vertical-align:middle; border:0px' src='./data/interface/uebersicht/icon_dropdown.gif' alt='' /></dt>\n");
				echo.append(lists.get(cat));
				echo.append("</dl></div>");
			}
			else {
				echo.append("<a ");
				if( this.show == cat ) {
					echo.append("style=\"text-decoration:underline\"");
				}
				echo.append(" class=\"forschinfo\" href=\""+Common.buildUrl("default", "show", cat)+"\">"+catkey+"</a>\n");
			}

			if( catpos < catsize - 1 ) {
				echo.append(" | \n");
			}
			catpos++;
		}
		echo.append("</div>");
		echo.append("<div><br /><br /></div>\n");
	}

	/**
	 * Anzeige der Statistiken.
	 * @throws IOException
	 */
	@Override
	@Action(ActionType.DEFAULT)
	public void defaultAction() throws IOException {
		int stat = getInteger("stat");

		if( this.statslist.get(show).size() <= stat ) {
			stat = 1;
		}

		StatEntry mystat = this.statslist.get(this.show).get(stat);

		printMenu();

		Writer echo = getContext().getResponse().getWriter();

		echo.append("<div class='gfxbox' style='width:850px'>");

		mystat.stat.show(this, mystat.width);

		echo.append("</div>");
	}
}
