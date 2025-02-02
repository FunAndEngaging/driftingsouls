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
package net.driftingsouls.ds2.server.modules.admin;

import net.driftingsouls.ds2.server.WellKnownAdminPermission;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.bases.Building;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.entities.User;
import net.driftingsouls.ds2.server.entities.ally.Ally;
import net.driftingsouls.ds2.server.framework.Configuration;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.services.AllianzService;
import net.driftingsouls.ds2.server.services.BuildingService;
import net.driftingsouls.ds2.server.services.DismantlingService;
import net.driftingsouls.ds2.server.services.PmService;
import net.driftingsouls.ds2.server.ships.Ship;
import net.driftingsouls.ds2.server.tasks.TaskManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ermoeglicht das Einloggen in einen anderen Account ohne Passwort.
 *
 * @author Christopher Jung
 *
 */
@AdminMenuEntry(category = "Spieler", name = "Spieler löschen", permission = WellKnownAdminPermission.PLAYER_DELETE)
@Component
public class PlayerDelete implements AdminPlugin
{
	private static final Log log = LogFactory.getLog(PlayerDelete.class);

	@PersistenceContext
	private EntityManager em;

	private final BuildingService buildingService;
	private final PmService pmService;
	private final TaskManager taskManager;
	private final DismantlingService dismantlingService;

	public PlayerDelete(BuildingService buildingService, PmService pmService, TaskManager taskManager, DismantlingService dismantlingService) {
		this.buildingService = buildingService;
		this.pmService = pmService;
		this.taskManager = taskManager;
		this.dismantlingService = dismantlingService;
	}

	@Override
	public void output(StringBuilder echo) {
		Context context = ContextMap.getContext();

		int userid = context.getRequest().getParameterInt("userid");

		if( userid == 0 )
		{
			echo.append("<div class='gfxbox' style='width:440px;text-align:center'>");
			echo.append("Hinweis: Es gibt KEINE Sicherheitsabfrage!<br />\n");
			echo.append("<form action=\"./ds\" method=\"post\">");
			echo.append("<table class=\"noBorderX\">\n");
			echo.append("<tr><td class=\"noBorderX\" width=\"60\">Userid:</td><td class=\"noBorderX\">");
			echo.append("<input type=\"text\" name=\"userid\" size=\"6\" />");
			echo.append("</td></tr>\n");
			echo.append("<tr><td class=\"noBorderX\" colspan=\"2\" align=\"center\">");
			echo.append("<input type=\"hidden\" name=\"namedplugin\" value=\"").append(getClass().getName()).append("\" />");
			echo.append("<input type=\"hidden\" name=\"module\" value=\"admin\" />\n");
			echo.append("<input type=\"submit\" value=\"l&ouml;schen\" style=\"width:100px\"/></td></tr>");
			echo.append("</table>\n");
			echo.append("</form>");
			echo.append("</div>");

			return;
		}

		log.info("Loesche Spieler "+userid);

		echo.append("<div class='gfxbox' style='width:540px'>");
		User user = em.find(User.class, userid);
		if( user == null ) {
			echo.append("Der Spieler existiert nicht.<br />\n");
			echo.append("</div>");

			return;
		}
		if( user.isNPC() || user.isAdmin() ) {
			echo.append("Der NPC/Admin kann nicht gelöscht werden.<br />\n");
			echo.append("</div>");

			return;
		}
		if( user.getAccessLevel() >= context.getActiveUser().getAccessLevel() ) {
			echo.append("Du hast nicht das notwendige Berechtigungslevel.<br />\n");
			echo.append("</div>");

			return;
		}
		if( (user.isInVacation() || user.getInactivity() <= 7*14) &&
				!context.hasPermission(WellKnownAdminPermission.PLAYER_DELETE_ACTIVE) ) {
			echo.append("Du hast nicht die Berechtigung einen aktiven Spieler zu löschen.<br />\n");
			echo.append("</div>");

			return;
		}

		if( (user.getAlly() != null) && (user.getAlly().getPresident() == user) )
		{
			echo.append("Der Spieler ").append(userid).append(" ist Anführer einer Allianz.<br />\n");
			echo.append("Die Allianz muss zuerst gelöscht werden oder einen anderen Anführer bekommen, bevor der Spieler gelöscht werden kann.<br />\n");
			echo.append("</div>");

			return;
		}

		long count;

		if( user.getAlly() != null )
		{
			echo.append("Stelle fest ob die Allianz jetzt zu wenig Mitglieder hat\n");

			Ally ally = user.getAlly();

			// Allianzen mit einem NPC als Praesidenten koennen auch mit 1 oder 2 Membern existieren
			if( ally.getPresident().getId() > 0 )
			{
				count = ally.getMemberCount() - 1;
				if( count < 2 )
				{
					taskManager.addTask(TaskManager.Types.ALLY_LOW_MEMBER, 21,
							Integer.toString(ally.getId()), "", "");

					final User sourceUser = em.find(User.class, 0);

					AllianzService allianzService = context.getBean(AllianzService.class, null);
					List<User> supermembers = allianzService.getAllianzfuehrung(ally);
					for( User supermember : supermembers )
					{
						if( supermember.getId() == userid )
						{
							continue;
						}

						pmService.send(
							sourceUser,
							supermember.getId(),
							"Drohende Allianzauflösung",
							"[Automatische Nachricht]\nAchtung!\n"
									+ "Durch das Löschen eines Allianzmitglieds hat Deine Allianz zu wenig Mitglieder, "
									+ "um weiterhin zu bestehen. Du hast nun 21 Ticks Zeit, diesen Zustand zu ändern. "
									+ "Anderenfalls wird die Allianz aufgelöst.");
					}

					echo.append("....sie hat jetzt zu wenig");
				}
			}
			echo.append("<br />\n");
		}

		echo.append("Entferne GTU-Zwischenlager...<br />\n");
		em.createQuery("delete from GtuZwischenlager where user1=:user")
			.setParameter("user", userid)
			.executeUpdate();
		em.createQuery("delete from GtuZwischenlager where user2=:user")
			.setParameter("user", userid)
			.executeUpdate();

		echo.append("Entferne User-Values...");
		count = em.createQuery("delete from UserValue where user=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Entferne Com-Net-visits...");
		count = em.createQuery("delete from ComNetVisit where user=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Ordne Com-Net-Posts ID 0 zu...");
		count = em.createQuery("update ComNetEntry set user=0 where user=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Entferne User-Ränge...");
		count = em.createQuery("delete from UserRank where userRankKey.owner=:user")
				.setParameter("user", userid)
				.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Entferne Fraktionsaktionsmeldungen...");
		count = em.createQuery("delete from FraktionAktionsMeldung where gemeldetVon=:user")
				.setParameter("user", userid)
				.executeUpdate();
		echo.append(count).append("<br />\n");

		// Schiffe
		echo.append("Entferne Schiffe...\n");

		List<Ship> ships = em.createQuery("from Ship where owner=:user", Ship.class)
			.setParameter("user", user)
			.getResultList();
		for (Ship ship: ships)
		{
			dismantlingService.destroy(ship);
			count++;
		}

		echo.append(count).append(" Schiffe entfernt<br />\n");

		// Basen

		List<Base> baseList = em.createQuery("from Base where owner=:user", Base.class)
			.setParameter("user", userid)
			.getResultList();

		echo.append("Übereigne Basen an Spieler 0 (+ reset)...\n");

		User nullUser = (User)em.find(User.class, 0);

		for( Base base : baseList )
		{
			Integer[] bebauung = base.getBebauung();
			for( int i = 0; i < bebauung.length; i++ )
			{
				if( bebauung[i] == 0 )
				{
					continue;
				}

				Building building = Building.getBuilding(bebauung[i]);
				buildingService.cleanup(building, base, bebauung[i]);
				bebauung[i] = 0;
			}
			base.setBebauung(bebauung);

			base.setOwner(nullUser);
			base.setName("Verlassener Asteroid");
			base.setActive(new Integer[] { 0 });
			base.setCore(null);
			base.setCoreActive(false);
			base.setEnergy(0);
			base.setBewohner(0);
			base.setArbeiter(0);
			base.setCargo(new Cargo());
			base.setAutoGTUActs(new ArrayList<>());
		}

		echo.append(baseList.size()).append(" Basen bearbeitet<br />\n");

		echo.append("Entferne Handelseinträge...");
		count = em.createQuery("delete from Handel where who=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Überstelle GTU-Gebote an die GTU (-2)...<br />");
		em.createQuery("update Versteigerung set bieter=-2 where bieter=:user")
			.setParameter("user", userid)
			.executeUpdate();

		em.createQuery("update Versteigerung set owner=-2 where owner=:user")
			.setParameter("user", userid)
			.executeUpdate();

		echo.append("Lösche PM's...");
		count = em.createQuery("delete from PM where empfaenger = :user")
			.setParameter("user", user)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		em.createQuery("update PM set sender=0 where sender = :user")
			.setParameter("user", user)
			.executeUpdate();

		echo.append("Lösche PM-Ordner...");
		count = em.createQuery("delete from Ordner where owner=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />");

		echo.append("Lösche Diplomatieeinträge...");
		count = em.createQuery("delete from UserRelation where user=:user1 or target=:user2")
			.setParameter("user1", userid)
			.setParameter("user2", userid)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Lösche Kontobewegungen...<br />\n");
		em.createQuery("delete from UserMoneyTransfer umt where umt.from= :user or umt.to = :user")
			.setParameter("user", user)
			.executeUpdate();

		echo.append("Lösche Userlogo...<br />\n");
		new File(Configuration.getAbsolutePath() + "data/logos/user/" + userid + ".gif")
				.delete();

		echo.append("Lösche Offiziere...");
		count = em.createQuery("delete from Offizier where owner=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />");

		echo.append("Lösche Shop-Aufträge...");
		count = em.createQuery("delete from FactionShopOrder where user=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Lösche Statistik 'Item-Locations'...");
		count = em.createQuery("delete from StatItemLocations where user=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Lösche Statistik 'User-Cargo'...");
		count = em.createQuery("delete from StatUserCargo where user=:user")
			.setParameter("user", userid)
			.executeUpdate();
		echo.append(count).append("<br />\n");

		echo.append("Lösche Usereintrag...<br />\n");
		em.flush();
		em.remove(user);

		echo.append("<br />Spieler ").append(userid).append(" gelöscht!<br />\n");

		echo.append("</div>");
	}

}
