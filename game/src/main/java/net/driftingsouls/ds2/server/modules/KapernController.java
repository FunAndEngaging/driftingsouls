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

import net.driftingsouls.ds2.server.ContextCommon;
import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ItemCargoEntry;
import net.driftingsouls.ds2.server.config.items.Item;
import net.driftingsouls.ds2.server.entities.*;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.pipeline.Module;
import net.driftingsouls.ds2.server.framework.pipeline.controllers.*;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import net.driftingsouls.ds2.server.framework.templates.TemplateViewResultFactory;
import net.driftingsouls.ds2.server.services.BattleService;
import net.driftingsouls.ds2.server.services.FleetMgmtService;
import net.driftingsouls.ds2.server.services.LocationService;
import net.driftingsouls.ds2.server.services.PmService;
import net.driftingsouls.ds2.server.services.ShipService;
import net.driftingsouls.ds2.server.services.ShipActionService;
import net.driftingsouls.ds2.server.services.ShipyardService;
import net.driftingsouls.ds2.server.services.UserService;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.ships.ShipTypeFlag;
import net.driftingsouls.ds2.server.units.TransientUnitCargo;
import net.driftingsouls.ds2.server.units.UnitCargo;
import net.driftingsouls.ds2.server.units.UnitCargo.Crew;
import net.driftingsouls.ds2.server.units.UnitType;
import net.driftingsouls.ds2.server.werften.ShipWerft;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ermoeglicht das Kapern eines Schiffes sowie verlinkt auf das Pluendern des Schiffes.
 *
 * @author Christopher Jung
 */
@Module(name = "kapern")
public class KapernController extends Controller
{
	private final TemplateViewResultFactory templateViewResultFactory;
	private final BattleService battleService;
	private final ConfigService configService;
	private final ShipService shipService;
	private final PmService pmService;
	private final UserService userService;
	private final LocationService locationService;
	private final FleetMgmtService fleetMgmtService;
	private final ShipyardService shipyardService;
	private final ShipActionService shipActionService;

	@PersistenceContext
	private EntityManager em;

	@Autowired
	public KapernController(TemplateViewResultFactory templateViewResultFactory,
		BattleService battleService,
		ConfigService configService, ShipService shipService, PmService pmService, UserService userService, LocationService locationService, FleetMgmtService fleetMgmtService, ShipyardService shipyardService, ShipActionService shipActionService)
	{
		this.templateViewResultFactory = templateViewResultFactory;
		this.battleService = battleService;
		this.configService = configService;
		this.shipService = shipService;
		this.pmService = pmService;
		this.userService = userService;
		this.locationService = locationService;
		this.fleetMgmtService = fleetMgmtService;
		this.shipyardService = shipyardService;
		this.shipActionService = shipActionService;
	}

	private void validiereEigenesUndZielschiff(Ship eigenesSchiff, Ship zielSchiff)
	{
		User user = (User) this.getUser();

		if (eigenesSchiff == null || eigenesSchiff.getOwner().getId() != user.getId())
		{
			throw new ValidierungException("Das angegebene Schiff existiert nicht oder geh&ouml;rt nicht Ihnen.", Common.buildUrl("default", "module", "schiffe"));
		}

		String errorurl = Common.buildUrl("default", "module", "schiff", "ship", eigenesSchiff.getId());

		if (userService.isNoob(user))
		{
			throw new ValidierungException("Sie k&ouml;nnen weder kapern noch pl&uuml;ndern, solange Sie unter Neuspieler-Schutz stehen.<br />Hinweis: Der Neuspieler-Schutz kann unter den Account-Optionen vorzeitig beendet werden.", errorurl);
		}

		if ((eigenesSchiff.getEngine() == 0) || (eigenesSchiff.getWeapons() == 0))
		{
			throw new ValidierungException("Diese Schrottm&uuml;hle wird nichts kapern k&ouml;nnen.", errorurl);
		}

		if (eigenesSchiff.getUnitCargo().isEmpty())
		{
			throw new ValidierungException("Sie ben&ouml;tigen Einheiten, um zu kapern", errorurl);
		}

		if (zielSchiff == null)
		{
			throw new ValidierungException("Das angegebene Zielschiff existiert nicht", errorurl);
		}

		User taruser = zielSchiff.getOwner();
		if (userService.isNoob(taruser))
		{
			throw new ValidierungException("Der Kolonist steht unter Neuspieler-Schutz", errorurl);
		}

		if ((taruser.getVacationCount() != 0) && (taruser.getWait4VacationCount() == 0))
		{
			throw new ValidierungException("Sie k&ouml;nnen Schiffe dieses Spielers nicht kapern oder pl&uuml;ndern, solange er sich im Urlaubs-Modus befindet.", errorurl);
		}

		if (zielSchiff.getOwner().getId() == eigenesSchiff.getOwner().getId())
		{
			throw new ValidierungException("Sie k&ouml;nnen Ihre eigenen Schiffe nicht kapern.", errorurl);
		}

		if (!eigenesSchiff.getLocation().sameSector(0, zielSchiff.getLocation(), 0))
		{
			throw new ValidierungException("Das Zielschiff befindet sich nicht im selben Sektor.", errorurl);
		}

		ShipTypeData dshipTypeData = zielSchiff.getTypeData();
		if ((dshipTypeData.getCost() != 0) && (zielSchiff.getEngine() != 0) && (zielSchiff.getCrew() != 0 || !zielSchiff.getUnitCargo().isEmpty()))
		{
			throw new ValidierungException("Das feindliche Schiff ist noch bewegungsf&auml;hig.", errorurl);
		}

		// Wenn das Ziel ein Geschtz (10) ist....
		if (!dshipTypeData.getShipClass().isKaperbar())
		{
			throw new ValidierungException("Sie k&ouml;nnen " + dshipTypeData.getShipClass().getPlural() + " weder kapern noch pl&uuml;ndern.", errorurl);
		}

		if (zielSchiff.isDocked() || zielSchiff.isLanded())
		{
			if (zielSchiff.isLanded())
			{
				throw new ValidierungException("Sie k&ouml;nnen gelandete Schiffe weder kapern noch pl&uuml;ndern.", errorurl);
			}

			Ship mship = shipService.getBaseShip(zielSchiff);
			if ((mship.getEngine() != 0) && (mship.getCrew() != 0 || !mship.getUnitCargo().isEmpty()))
			{
				throw new ValidierungException("Das Schiff, an das das feindliche Schiff angedockt hat, ist noch bewegungsf&auml;hig.", errorurl);
			}
		}

		//In einem Kampf?
		if ((eigenesSchiff.getBattle() != null) || (zielSchiff.getBattle() != null))
		{
			throw new ValidierungException("Eines der Schiffe ist zurzeit in einen Kampf verwickelt.", errorurl);
		}

		if (zielSchiff.getStatus().contains("disable_iff"))
		{
			throw new ValidierungException("Das Schiff besitzt keine IFF-Kennung und kann daher nicht gekapert/gepl&uuml;ndert werden.", errorurl);
		}
	}

	/**
	 * Kapert das Schiff.
	 *
	 * @param eigenesSchiff Die ID des Schiffes, mit dem der Spieler kapern moechte
	 * @param zielSchiff Die ID des zu kapernden/pluendernden Schiffes
	 */
	@Action(ActionType.DEFAULT)
	public TemplateEngine erobernAction(@UrlParam(name = "ship") Ship eigenesSchiff, @UrlParam(name = "tar") Ship zielSchiff)
	{
		TemplateEngine t = templateViewResultFactory.createFor(this);
		User user = (User) this.getUser();

		validiereEigenesUndZielschiff(eigenesSchiff, zielSchiff);

		t.setVar(
				"ownship.id", eigenesSchiff.getId(),
				"ownship.name", eigenesSchiff.getName(),
				"targetship.id", zielSchiff.getId(),
				"targetship.name", zielSchiff.getName());

		t.setVar("kapern.showkaperreport", 1);

		String errorurl = Common.buildUrl("default", "module", "schiff", "ship", eigenesSchiff.getId());

		if (zielSchiff.getTypeData().hasFlag(ShipTypeFlag.NICHT_KAPERBAR))
		{
			throw new ValidierungException("Sie k&ouml;nnen dieses Schiff nicht kapern.", errorurl);
		}

		User targetUser = em.find(User.class, zielSchiff.getOwner().getId());

		String kapermessage = "<div align=\"center\">Die Einheiten st&uuml;rmen die " + zielSchiff.getName() + ".</div><br />";
		StringBuilder msg = new StringBuilder();

		//Trying to steal a ship already costs bounty or one could help another player to get a ship without any penalty
		if (!targetUser.hasFlag(UserFlag.NO_AUTO_BOUNTY))
		{
			BigDecimal account = new BigDecimal(targetUser.getKonto());
			account = account.movePointLeft(1).setScale(0, RoundingMode.HALF_EVEN);

			BigInteger shipBounty = zielSchiff.getTypeData().getBounty();
			shipBounty = account.toBigIntegerExact().min(shipBounty);

			if (shipBounty.compareTo(BigInteger.ZERO) != 0)
			{
				user.addBounty(shipBounty);
				//Make it public that there's a new bounty on a player, so others can go to hunt
				int bountyChannel = configService.getValue(WellKnownConfigValue.BOUNTY_CHANNEL);
				ComNetChannel channel = em.find(ComNetChannel.class, bountyChannel);
				if( channel != null )
				{
					ComNetEntry entry = new ComNetEntry(user, channel);
					entry.setHead("Kopfgeld");
					entry.setText("Gesuchter: " + user.getNickname() + "[BR]Wegen: Diebstahl von Schiffen[BR]Betrag: " + shipBounty);
					em.persist(entry);
				}
			}
		}

		boolean ok;

		// Falls Crew auf dem Zielschiff vorhanden ist
		if (zielSchiff.getCrew() != 0 || !zielSchiff.getUnitCargo().isEmpty())
		{
			if (zielSchiff.getTypeData().getCrew() == 0)
			{
				throw new ValidierungException("Dieses Schiff ist nicht kaperbar.", errorurl);
			}

			if (zielSchiff.getTypeData().getShipClass() == ShipClasses.STATION)
			{
				if (!checkAlliedShipsReaction(t, targetUser, eigenesSchiff, zielSchiff))
				{
					return t;
				}
			}

			msg.append("Die Einheiten der ").append(eigenesSchiff.getName()).append(" (").append(eigenesSchiff.getId()).append("), eine ").append(eigenesSchiff.getTypeData().getNickname()).append(", st&uuml;rmt die ").append(zielSchiff.getName()).append(" (").append(zielSchiff.getId()).append("), eine ").append(zielSchiff.getTypeData().getNickname()).append(", bei ").append(locationService.displayCoordinates(zielSchiff.getLocation(), false)).append(".\n\n");

			StringBuilder kapernLog = new StringBuilder();
			ok = doFighting(kapernLog, eigenesSchiff, zielSchiff);

			msg.append(kapernLog);

			t.setVar("kapern.message", kapermessage + kapernLog.toString().replace("\n", "<br />"));

		}
		// Falls keine Crew auf dem Zielschiff vorhanden ist
		else
		{
			ok = true;

			t.setVar("kapern.message", kapermessage + "Das Schiff wird widerstandslos &uuml;bernommen.");

			msg.append("Das Schiff ").append(zielSchiff.getName()).append("(").append(zielSchiff.getId()).append("), eine ").append(zielSchiff.getTypeData().getNickname()).append(", wird bei ").append(locationService.displayCoordinates(zielSchiff.getLocation(), false)).append(" an ").append(eigenesSchiff.getName()).append(" (").append(eigenesSchiff.getId()).append(") &uuml;bergeben.\n");
		}

		// Transmisson
		pmService.send(user, targetUser.getId(), "Kaperversuch", msg.toString());

		// Wurde das Schiff gekapert?
		if (ok)
		{
			// Evt unbekannte Items bekannt machen
			processUnknownItems(user, zielSchiff);

			transferShipToNewOwner(user, zielSchiff);
		}

		shipActionService.recalculateShipStatus(eigenesSchiff);
		shipActionService.recalculateShipStatus(zielSchiff);

		return t;
	}

	private void transferShipToNewOwner(User user, Ship targetShip)
	{
		String currentTime = Common.getIngameTime(ContextMap.getContext().get(ContextCommon.class).getTick());

		targetShip.getHistory().addHistory("Gekapert am " + currentTime + " durch " + user.getName() + " (" + user.getId() + ").");

		fleetMgmtService.removeShip(targetShip.getFleet(), targetShip);
		targetShip.setOwner(user);

		List<Ship> docked = em.createQuery("from Ship where id>0 and docked in (:docked,:landed)", Ship.class)
				.setParameter("docked", Integer.toString(targetShip.getId()))
				.setParameter("landed", "l " + targetShip.getId())
				.getResultList();
		for (Ship dockShip : docked)
		{
			fleetMgmtService.removeShip(dockShip.getFleet(), dockShip);
			dockShip.setOwner(user);

			for (Offizier offi : dockShip.getOffiziere())
			{
				offi.setOwner(user);
			}
			if (dockShip.getTypeData().getWerft() != 0)
			{
				ShipWerft werft = em.createQuery("from ShipWerft where ship=:ship", ShipWerft.class)
						.setParameter("ship", dockShip)
						.getSingleResult();

				if (werft.getKomplex() != null)
				{
					shipyardService.removeFromKomplex(werft);
				}
				werft.setLink(null);
			}

		}

		for (Offizier offi : targetShip.getOffiziere())
		{
			offi.setOwner(user);
		}
		if (targetShip.getTypeData().getWerft() != 0)
		{
			ShipWerft werft = em.createQuery("from ShipWerft where ship=:ship", ShipWerft.class)
					.setParameter("ship", targetShip)
					.getSingleResult();

			if (werft.getKomplex() != null)
			{
				shipyardService.removeFromKomplex(werft);
			}
			werft.setLink(null);
		}
	}

	private void processUnknownItems(User user, Ship targetShip)
	{
		Cargo cargo = targetShip.getCargo();

		List<ItemCargoEntry<Item>> itemlist = cargo.getItemEntries();
		for (ItemCargoEntry<Item> item : itemlist)
		{
			Item itemobject = item.getItem();
			if (itemobject.isUnknownItem())
			{
				user.addKnownItem(item.getItemID());
			}
		}
	}

	private boolean doFighting(StringBuilder msg, Ship ownShip, Ship targetShip)
	{
		boolean ok = false;

		Crew dcrew = new UnitCargo.Crew(targetShip.getCrew());
		UnitCargo ownUnits = ownShip.getUnitCargo();
		UnitCargo enemyUnits = targetShip.getUnitCargo();

		UnitCargo saveunits = ownUnits.trimToMaxSize(targetShip.getTypeData().getMaxUnitSize());


		int attmulti = 1;
		int defmulti = 1;

		Offizier defoffizier = targetShip.getOffizier();
		if (defoffizier != null)
		{
			defmulti = defoffizier.getKaperMulti(true);
		}
		Offizier attoffizier = ownShip.getOffizier();
		if (attoffizier != null)
		{
			attmulti = attoffizier.getKaperMulti(false);
		}

		if (!ownUnits.isEmpty() && !(enemyUnits.isEmpty() && targetShip.getCrew() == 0))
		{

			UnitCargo toteeigeneUnits = new TransientUnitCargo();
			UnitCargo totefeindlicheUnits = new TransientUnitCargo();

			if (ownUnits.kapern(enemyUnits, toteeigeneUnits, totefeindlicheUnits, dcrew, attmulti, defmulti))
			{
				ok = true;
				if (toteeigeneUnits.isEmpty() && totefeindlicheUnits.isEmpty())
				{
					if (attoffizier != null)
					{
						attoffizier.gainExperience(Offizier.Ability.COM, 5);
					}
					msg.append("Das Schiff ist kampflos verloren.\n");
				}
				else
				{
					msg.append("Das Schiff ist verloren.\n");
					Map<UnitType, Long> ownunitlist = toteeigeneUnits.getUnitMap();
					Map<UnitType, Long> enemyunitlist = totefeindlicheUnits.getUnitMap();

					if (!ownunitlist.isEmpty())
					{
						for (Entry<UnitType, Long> unit : ownunitlist.entrySet())
						{
							UnitType unittype = unit.getKey();
							msg.append("Angreifer:\n").append(unit.getValue()).append(" ").append(unittype.getName()).append(" erschossen.\n");
						}
					}

					if (!enemyunitlist.isEmpty())
					{
						for (Entry<UnitType, Long> unit : enemyunitlist.entrySet())
						{
							UnitType unittype = unit.getKey();
							msg.append("Verteidiger:\n").append(unit.getValue()).append(" ").append(unittype.getName()).append(" gefallen.\n");
						}
					}

					if (attoffizier != null)
					{
						attoffizier.gainExperience(Offizier.Ability.COM, 3);
					}
				}
			}
			else
			{
				msg.append("Angreifer flieht.\n");
				Map<UnitType, Long> ownunitlist = toteeigeneUnits.getUnitMap();
				Map<UnitType, Long> enemyunitlist = totefeindlicheUnits.getUnitMap();

				if (!ownunitlist.isEmpty())
				{
					for (Entry<UnitType, Long> unit : ownunitlist.entrySet())
					{
						UnitType unittype = unit.getKey();
						msg.append("Angreifer:\n").append(unit.getValue()).append(" ").append(unittype.getName()).append(" erschossen.\n");
					}
				}

				if (!enemyunitlist.isEmpty())
				{
					for (Entry<UnitType, Long> unit : enemyunitlist.entrySet())
					{
						UnitType unittype = unit.getKey();
						msg.append("Verteidiger:\n").append(unit.getValue()).append(" ").append(unittype.getName()).append(" gefallen.\n");
					}
				}

				if (defoffizier != null)
				{
					defoffizier.gainExperience(Offizier.Ability.SEC, 5);
				}
			}
		}
		else if (!ownUnits.isEmpty())
		{
			ok = true;
			if (attoffizier != null)
			{
				attoffizier.gainExperience(Offizier.Ability.COM, 5);
			}
			msg.append("Schiff wird widerstandslos &uuml;bernommen.\n");
		}

		ownUnits.addCargo(saveunits);

		ownShip.setUnitCargo(ownUnits);

		targetShip.setUnitCargo(enemyUnits);
		targetShip.setCrew(dcrew.getValue());
		return ok;
	}

	private boolean checkAlliedShipsReaction(TemplateEngine t,
		User targetUser, Ship ownShip, Ship targetShip)
	{
		List<User> ownerlist = new ArrayList<>();
		if (targetUser.getAlly() != null)
		{
			ownerlist.addAll(targetUser.getAlly().getMembers());
		}
		else
		{
			ownerlist.add(targetUser);
		}

		int shipcount = 0;
		List<Ship> shiplist = em.createQuery("from Ship where x=:x and y=:y and system=:system and owner in :ownerlist and id>0 and battle is null", Ship.class)
						.setParameter("x", targetShip.getX())
						.setParameter("y", targetShip.getY())
						.setParameter("system", targetShip.getSystem())
						.setParameter("ownerlist", ownerlist)
						.getResultList();
		for (Ship ship : shiplist)
		{
			if (ship.getTypeData().isMilitary() && ship.getCrew() > 0)
			{
				shipcount++;
			}
		}

		if (shipcount > 0)
		{
			double ws = -Math.pow(0.7, shipcount / 3d) + 1;
			ws *= 100;

			boolean found = false;
			for (int i = 1; i <= shipcount; i++)
			{
				if (ThreadLocalRandom.current().nextInt(101) > ws)
				{
					continue;
				}
				found = true;
				break;
			}
			if (found)
			{
				User source = em.find(User.class, -1);
				pmService.send(source, targetShip.getOwner().getId(), "Kaperversuch entdeckt", "Ihre Schiffe haben einen Kaperversuch bei " + locationService.displayCoordinates(targetShip.getLocation(), false) + " vereitelt und den Gegner angegriffen.");

				Battle battle = battleService.erstelle(targetShip.getOwner(), targetShip, ownShip, true);

				t.setVar(
						"kapern.message", "Ihr Kaperversuch wurde entdeckt und einige gegnerischen Schiffe haben das Feuer er&ouml;ffnet.",
						"kapern.battle", battle.getId());

				return false;
			}
		}

		return true;
	}

	/**
	 * Zeigt die Auswahl ab, ob das Schiff gekapert oder gepluendert werden soll.
	 *  @param eigenesSchiff Die ID des Schiffes, mit dem der Spieler kapern moechte
	 * @param zielSchiff Die ID des zu kapernden/pluendernden Schiffes
	 */
	@Action(ActionType.DEFAULT)
	public TemplateEngine defaultAction(@UrlParam(name = "ship") Ship eigenesSchiff, @UrlParam(name = "tar") Ship zielSchiff)
	{
		TemplateEngine t = templateViewResultFactory.createFor(this);

		validiereEigenesUndZielschiff(eigenesSchiff, zielSchiff);

		t.setVar(
				"ownship.id", eigenesSchiff.getId(),
				"ownship.name", eigenesSchiff.getName(),
				"targetship.id", zielSchiff.getId(),
				"targetship.name", zielSchiff.getName());

		t.setVar("kapern.showmenu", 1);

		if ((zielSchiff.getTypeData().getCost() != 0) && (zielSchiff.getEngine() != 0))
		{
			if (zielSchiff.getCrew() == 0 && zielSchiff.getUnitCargo().isEmpty())
			{
				t.setVar("targetship.status", "verlassen",
						"menu.showpluendern", 1,
						"menu.showkapern", !zielSchiff.getTypeData().hasFlag(ShipTypeFlag.NICHT_KAPERBAR));
			}
			else
			{
				t.setVar("targetship.status", "noch bewegungsf&auml;hig");
			}
		}
		else
		{
			t.setVar("targetship.status", "bewegungsunf&auml;hig",
					"menu.showpluendern", (zielSchiff.getCrew() == 0),
					"menu.showkapern", !zielSchiff.getTypeData().hasFlag(ShipTypeFlag.NICHT_KAPERBAR));
		}
		return t;
	}
}
