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
package net.driftingsouls.ds2.server.config.items.effects;

import java.io.IOException;
import java.io.Writer;

import net.driftingsouls.ds2.server.framework.Context;

/**
 * <h1>Item-Effekt "IFF deaktivieren".</h1>
 * <p>Schiffe ohne IFF-Kennung koennen nicht angegriffen, gekapert, gepluendert oder Ziel eines
 * Warentransfers sein. Zudem ist ihr Besitzer nicht erkennbar.</p>
 * <p>Der Effekttyp ist lediglich ein "Marker" und besitzt selbst keine Eigenschaften.</p>
 * @author Christopher Jung
 *
 */
public class IEDisableIFF extends ItemEffect {
	protected IEDisableIFF() {
		super(ItemEffect.Type.DISABLE_IFF);
	}
	
	/**
	 * Laedt einen Effect aus einem String.
	 * @param effectString Der Effect als String
	 * @return Der Effect
	 * @throws IllegalArgumentException falls der Effect nicht richtig geladen werden konnte
	 */
	public static ItemEffect fromString(String effectString) throws IllegalArgumentException {
		return new IEDisableIFF();
	}
	
	/**
	 * Laedt einen Effect aus einem Context.
	 * @param context der Context
	 * @return der Effect
	 */
	public static ItemEffect fromContext(Context context) {
		return new IEDisableIFF();
	}
	
	/**
	 * Gibt das passende Fenster fuer das Adminmenue aus.
	 * @param echo Der Writer des Adminmenues
	 * @throws IOException Exception falls ein fehler auftritt
	 */
	@Override
	public void getAdminTool(Writer echo) throws IOException {
		
		echo.append("<input type=\"hidden\" name=\"type\" value=\"disable-iff\" >");
	}
	
	/**
	 * Gibt den Itemeffect als String aus.
	 * @return der Effect als String
	 */
	@Override
	public String toString() {
		return "disable-iff:0";
	}
}
