package net.driftingsouls.ds2.server.map;

import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.Nebel;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipType;

import java.util.List;
import java.util.Map;

/**
 * Eine benutzerspezifische Sicht auf ein Feld der Sternenkarte.
 */
public interface FieldView
{
	/**
	 * Gibt alle sichtbaren Basen zurueck.
	 * @return Die Liste der Basen
	 */
	List<Base> getBases();

	/**
	 * Gibt alle sichtbaren Schiffe gruppiert nach Besitzer und Typ zurueck.
	 * @return Die Schiffe
	 */
	Map<User, Map<ShipType, List<Ship>>> getShips();

	/**
	 * Gibt alle sichtbaren Spungpunkte zurueck.
	 * @return Die Sprungpunkte
	 */
	List<JumpNode> getJumpNodes();

	/**
	 * Gibt, sofern an der Stelle vorhanden, den entsprechenden Nebel zurueck.
	 * @return Der Nebel oder <code>null</code>
	 */
	Nebel getNebel();

	/**
	 * Gibt alle sichtbaren Schlachten zurueck.
	 * @return Die Schlachten
	 */
	List<Battle> getBattles();
}
