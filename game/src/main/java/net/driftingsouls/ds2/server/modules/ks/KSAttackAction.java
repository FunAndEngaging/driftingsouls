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
package net.driftingsouls.ds2.server.modules.ks;

import net.driftingsouls.ds2.server.WellKnownConfigValue;
import net.driftingsouls.ds2.server.battles.Battle;
import net.driftingsouls.ds2.server.battles.BattleShip;
import net.driftingsouls.ds2.server.battles.BattleShipFlag;
import net.driftingsouls.ds2.server.battles.SchlachtLogAktion;
import net.driftingsouls.ds2.server.cargo.Cargo;
import net.driftingsouls.ds2.server.cargo.ItemCargoEntry;
import net.driftingsouls.ds2.server.config.Weapons;
import net.driftingsouls.ds2.server.config.items.Munition;
import net.driftingsouls.ds2.server.config.items.effects.IEAmmo;
import net.driftingsouls.ds2.server.entities.*;
import net.driftingsouls.ds2.server.framework.Common;
import net.driftingsouls.ds2.server.framework.ConfigService;
import net.driftingsouls.ds2.server.framework.Context;
import net.driftingsouls.ds2.server.framework.ContextMap;
import net.driftingsouls.ds2.server.framework.authentication.JavaSession;
import net.driftingsouls.ds2.server.framework.templates.TemplateEngine;
import net.driftingsouls.ds2.server.services.BattleService;
import net.driftingsouls.ds2.server.services.ShipService;
import net.driftingsouls.ds2.server.services.ShipActionService;
import net.driftingsouls.ds2.server.ships.ShipClasses;
import net.driftingsouls.ds2.server.ships.ShipType;
import net.driftingsouls.ds2.server.ships.ShipTypeData;
import net.driftingsouls.ds2.server.ships.ShipTypeFlag;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Berechnet das Waffenfeuer im KS.
 * @author Christopher Jung
 *
 */
@Component
public class KSAttackAction extends BasicKSAction {

	private static final Log log = LogFactory.getLog(KSAttackAction.class);

	private Weapon weapon;
	private BattleShip ownShip;
	private BattleShip enemyShip;
	private Waffenbeschreibung localweapon;
	private String weaponName;
	private String attmode;

	private final ShipService shipService;
	private final ShipActionService shipActionService;

	@Autowired
	public KSAttackAction(BattleService battleService, JavaSession javaSession, ShipService shipService, ShipActionService shipActionService) {
		super(battleService, (User)javaSession.getUser());
		this.shipService = shipService;
		this.shipActionService = shipActionService;
	}

	public void reset(Weapon weapon, String attMode) {
		this.weapon = weapon;

		this.requireOwnShipReady(true);

		this.ownShip = null;
		this.enemyShip = null;
		this.localweapon = null;
		this.weaponName = null;

		this.attmode = attMode;
		if( !this.attmode.equals("single") && !this.attmode.equals("alphastrike") && !this.attmode.equals("strafe") &&
			!this.attmode.equals("alphastrike_max") && !this.attmode.equals("strafe_max") ) {
			this.attmode = "single";
		}
	}

	/**
	 * Basisklasse fuer beim Feuern verwendete Waffenbeschreibungen.
	 */
	private static abstract class Waffenbeschreibung {
		private int count;

		public abstract boolean isArmorRedux();

		public abstract int getAreaDamage();

		public abstract int getSubDamage();

		public int getCount()
		{
			return count;
		}

		public void setCount(int count)
		{
			this.count = count;
		}

		public abstract int getShotsPerShot();

		public abstract int getSubWs();

		@SuppressWarnings("BooleanMethodIsAlwaysInverted")
		public abstract boolean isVeryLongRange();

		public abstract boolean isLongRange();

		public abstract double getDestroyable();

		public abstract int getBaseDamage();

		public abstract int getShieldDamage();

		public abstract int getDefTrefferWs();

		public abstract String getName();

		public abstract ItemCargoEntry<Munition> getAmmoItem();

		public abstract boolean isAreaDamageFull();

		public abstract boolean isDestroyAfter();
	}

	/**
	 * Eine Waffenbeschreibung fuer eine Munition verschiessende Waffe.
	 */
	private static class AmmoBasierteWaffenbeschreibung extends Waffenbeschreibung
	{
		private final Munitionsdefinition ammo;
		private final Weapon weapon;
		private final boolean kleinesZiel;
		private final ItemCargoEntry<Munition> ammoitem;

		private AmmoBasierteWaffenbeschreibung(Weapon weapon, Munitionsdefinition ammo, ItemCargoEntry<Munition> ammoitem, boolean kleinesZiel)
		{
			this.weapon = weapon;
			this.ammo = ammo;
			this.ammoitem = ammoitem;
			this.kleinesZiel = kleinesZiel;
		}

		@Override
		public int getDefTrefferWs()
		{
			if( this.kleinesZiel )
			{
				return ammo.getSmallTrefferWS();
			}
			return ammo.getTrefferWS();
		}

		@Override
		public int getBaseDamage()
		{
			return this.ammo.getDamage();
		}

		@Override
		public int getShieldDamage()
		{
			return this.ammo.getShieldDamage();
		}

		@Override
		public int getShotsPerShot()
		{
			return ammo.getShotsPerShot()*this.weapon.getSingleShots();
		}

		@Override
		public String getName()
		{
			return this.ammo.getName();
		}

		@Override
		public int getSubWs()
		{
			return this.ammo.getSubWS();
		}

		@Override
		public int getSubDamage()
		{
			return this.ammo.getSubDamage();
		}

		@Override
		public boolean isDestroyAfter()
		{
			return false;
		}

		@Override
		public int getAreaDamage()
		{
			return this.ammo.getAreaDamage();
		}

		@Override
		public double getDestroyable()
		{
			return this.ammo.getDestroyable();
		}

		@Override
		public ItemCargoEntry<Munition> getAmmoItem()
		{
			return this.ammoitem;
		}

		@Override
		public boolean isAreaDamageFull()
		{
			return ammo.hasFlag(Munitionsdefinition.Flag.AD_FULL);
		}

		@Override
		public boolean isArmorRedux()
		{
			return ammo.hasFlag(Munitionsdefinition.Flag.ARMOR_REDUX);
		}

		@Override
		public boolean isLongRange()
		{
			return this.weapon.hasFlag(Weapon.Flags.LONG_RANGE);
		}

		@Override
		public boolean isVeryLongRange()
		{
			return this.weapon.hasFlag(Weapon.Flags.VERY_LONG_RANGE);
		}
	}

	/**
	 * Eine Waffenbeschreibung fuer eine regulaere Waffe.
	 */
	private static class WaffenBasierteWaffenbeschreibung extends Waffenbeschreibung
	{
		private final Weapon weapon;
		private final boolean kleinesZiel;

		private WaffenBasierteWaffenbeschreibung(Weapon weapon, boolean kleinesZiel)
		{
			this.kleinesZiel = kleinesZiel;
			this.weapon = weapon;
		}

		@Override
		public ItemCargoEntry<Munition> getAmmoItem()
		{
			return null;
		}

		@Override
		public int getAreaDamage()
		{
			return this.weapon.getAreaDamage();
		}

		@Override
		public int getBaseDamage()
		{
			return this.weapon.getBaseDamage();
		}

		@Override
		public int getDefTrefferWs()
		{
			if( this.kleinesZiel ) {
				return this.weapon.getDefSmallTrefferWS();
			}
			return this.weapon.getDefTrefferWS();
		}

		@Override
		public double getDestroyable()
		{
			return this.weapon.getDestroyable() ? 1.0 : 0.0;
		}

		@Override
		public String getName()
		{
			return this.weapon.getName();
		}

		@Override
		public int getShieldDamage()
		{
			return this.weapon.getShieldDamage();
		}

		@Override
		public int getShotsPerShot()
		{
			return this.weapon.getSingleShots();
		}

		@Override
		public int getSubDamage()
		{
			return this.weapon.getSubDamage();
		}

		@Override
		public int getSubWs()
		{
			return this.weapon.getDefSubWS();
		}

		@Override
		public boolean isAreaDamageFull()
		{
			return this.weapon.hasFlag(Weapon.Flags.AD_FULL);
		}

		@Override
		public boolean isArmorRedux()
		{
			return false;
		}

		@Override
		public boolean isDestroyAfter()
		{
			return this.weapon.hasFlag(Weapon.Flags.DESTROY_AFTER);
		}

		@Override
		public boolean isLongRange()
		{
			return this.weapon.hasFlag(Weapon.Flags.LONG_RANGE);
		}

		@Override
		public boolean isVeryLongRange()
		{
			return this.weapon.hasFlag(Weapon.Flags.VERY_LONG_RANGE);
		}
	}

    public BattleShip getAttackedShip()
    {
        return this.enemyShip;
    }

	private int destroyShipOnly(int id, Battle battle, BattleShip eShip) {

		//
		// Schiff als zerstoert makieren
		//

    int remove = 1; // Anzahl der zerstoerten Schiffe

		eShip.addFlag(BattleShipFlag.DESTROYED);

		if( eShip.getDestroyer() == 0 )
		{
			eShip.setDestroyer(id);
		}

		//
		// Ueberpruefen, ob weitere (angedockte) Schiffe zerstoert wurden
		//

		List<BattleShip> enemyShips = battle.getEnemyShips();
		for (BattleShip s : enemyShips)
		{
			if (shipService.getBaseShip(s.getShip()) != null && shipService.getBaseShip(s.getShip()).getId() == eShip.getId())
			{
        //Rettungskapseln sollen nicht zerstört werden, wenn sie gedockt/gelandet wurden
        if ( s.getShip().getTypeData().getShipClass() ==  ShipClasses.RETTUNGSKAPSEL)
        {
          //Den Offizier des Traegerschiffs identifizieren
          Offizier offizier;
          offizier = shipService.getBaseShip(s.getShip()).getOffizier();
          
          //testen, ob es ueberhaupt einen Offizier zu retten gibt
          if (offizier != null)
          {
            // er betritt nun die Rettungskapsel
            offizier.stationierenAuf(s.getShip());
            offizier.setOwner(s.getShip().getOwner());
          }
          //jetzt Rettungskapsel starten/abdocken
          if(s.getShip().isLanded())
          {
            shipService.start(shipService.getBaseShip(s.getShip()), s.getShip());
          }
          else
          {
            shipService.undock(shipService.getBaseShip(s.getShip()), s.getShip());
          }
          //die Kapsel hat das Traegerschiff verlassen
          //nun darf die Rettungskapsel fliehen und in die 2. Reihe
          s.addFlag(BattleShipFlag.FLUCHT);
          s.addFlag(BattleShipFlag.SECONDROW);

               
          //und jetzt raeumen wir noch auf
          shipActionService.recalculateShipStatus(s.getShip());
			shipActionService.recalculateShipStatus(shipService.getBaseShip(s.getShip()));
		
          }
        else
        {
          remove++;
          s.addFlag(BattleShipFlag.DESTROYED);
          if (s.getDestroyer() == 0)
          {
            s.setDestroyer(id);
          }
        }
			}
		}

		return remove;
	}

	private void destroyShip(StringBuilder logMsg, int id, Battle battle, BattleShip eShip) {

		int remove = this.destroyShipOnly(id, battle, eShip);

		// Wurde mehr als ein Schiff zerstoert?
		if( remove > 1 ) {
			logMsg.append(remove - 1).append(" gedockte/gelandete Schiffe wurden bei der Explosion zerstört\n");
			getBattleService().logme(battle,  (remove-1)+" gedockte/gelandete Schiffe wurden bei der Explosion zerst&ouml;rt\n" );
    }
	}

	private int getTrefferWS(int defTrefferWS, BattleShip eShip, ShipTypeData eShipType, int defensivskill, int navskill) {
		ShipTypeData ownShipType = this.ownShip.getTypeData();

		if( (eShip.getCrew() == 0) && (eShipType.getMinCrew() > 0) ) {
			return 100;
		}
		if( (defTrefferWS <= 0) && (eShipType.getCost() > 0) && (eShip.getShip().getEngine() > 0) ) {
			return 0;
		}

		// Das Objekt kann sich nicht bewegen - also 100% trefferws
		long trefferWS = 100;

		// Das Objekt hat einen Antrieb - also TrefferWS anpassen
		if( ( eShipType.getCost() > 0 ) && ( eShip.getShip().getEngine() > 0 ) ) {
			trefferWS = calcTWSthroughDifference(defensivskill, navskill, eShipType, defTrefferWS, ownShipType);
		}


		if( trefferWS < 0 ) {
			trefferWS = 0;
		}
		if( trefferWS > 100 ) {
			trefferWS = 100;
		}

		// Nun die TrefferWS anteilig senken, wenn Crew/Sensoren nicht auf 100 sind
		trefferWS *= (this.ownShip.getShip().getSensors()/100d);
		if( (ownShipType.getMinCrew() > 0) && (this.ownShip.getCrew() < ownShipType.getMinCrew()) ) {
			trefferWS *= this.ownShip.getCrew()/(double)ownShipType.getMinCrew();
		}

		// Und nun die TrefferWS anteilig steigern, wenn die Gegnerische Crew/Antrie nicht auf 100 sind
		long restws = 100-trefferWS;
		trefferWS += restws*((100-eShip.getShip().getEngine())/100d);
		if( eShip.getCrew() < eShipType.getMinCrew() ) {
			trefferWS += restws*((eShipType.getMinCrew()-eShip.getCrew())/(double)eShipType.getMinCrew());
		}

		if( trefferWS < 0 ) {
			trefferWS = 0;
		}
		if( trefferWS > 100 ) {
			trefferWS = 100;
		}

		return (int)trefferWS;
	}

	private int calcTWSthroughDifference(double defensivskill, double navskill, ShipTypeData eShipType, int defTrefferWS, ShipTypeData ownShipType) {
		double differenceSkillz;
		double differenceSize;
		double eSize = eShipType.getSize();
		double ownSize = ownShipType.getSize();
		if ( defensivskill > navskill ){
			differenceSkillz = ((defensivskill/navskill) - 1) * - 1;
			if ( differenceSkillz < -0.4) {
				differenceSkillz = -0.4;
			}
		}
		else if ( defensivskill < navskill ){
			differenceSkillz = (( navskill/defensivskill) - 1);
			if ( differenceSkillz > 0.4){
				differenceSkillz = 0.4;
			}
		}
		else {
			differenceSkillz=0.0;
		}

		// Berechne Aenderung der TWS durch unterschiedliche Schiffsgroesse
		// Original  + round(($eShipType['size'] - $ownShipType['size'])*2)
		if ( eSize < ownSize ){
			differenceSize = ( (eSize/ownSize) - 1);
			if ( differenceSize < -0.4){
				differenceSize = -0.4;
			}
		}
		else if ( ownSize < eShipType.getSize() ){
			differenceSize = ( (ownSize/eSize) -1) * -1;
			if ( differenceSize > 0.4 ){
				differenceSize = 0.4;
			}
		}
		else {
			differenceSize=0.0;
		}

		return (int) Math.round(defTrefferWS + (defTrefferWS * differenceSize) + (defTrefferWS * differenceSkillz));
	}

	private boolean calcDamage(StringBuilder logMsg, Battle battle, BattleShip eShip, ShipTypeData eShipType, int hit, int absSchaden, int schaden, int[] subdmgs) {
		boolean ship_intact = true;

		if(!this.weapon.getMunitionstypen().isEmpty()){
			if ( this.localweapon.isArmorRedux()){
				int tmppanzerung = eShip.getArmor();
				if (tmppanzerung <= 0){
					tmppanzerung = 1;
				}
				if (tmppanzerung >= 10){
					tmppanzerung = 10;
				}
				schaden = schaden*(10-tmppanzerung)/10;

			}
		}


		if( (hit != 0) && (eShip.getShields() > 0) ) {
			if( eShip.getShields() >= absSchaden*hit ) {
				eShip.setShields(eShip.getShields() - absSchaden*hit);
				if( eShip.getShields() == 0 ) {
					getBattleService().logme(battle,  "+ Schilde ausgefallen\n" );
					logMsg.append("+ Schilde ausgefallen\n");
				}
				else {
					getBattleService().logme(battle,  "+ Schaden (Schilde): "+Common.ln(hit*absSchaden)+"\n" );
					logMsg.append("+ Schilde: ").append(Common.ln(hit * absSchaden)).append(" Schaden\n");
				}
				hit = 0;
			}
			else {
				hit -= Math.ceil(eShip.getShields()/absSchaden);
				eShip.setShields(0);
				getBattleService().logme(battle,  "+ Schilde ausgefallen\n" );
				logMsg.append("+ Schilde ausgefallen\n");
			}
		}
		if( hit != 0 ) {
			int hulldamage = hit*schaden;

			if( eShipType.hasFlag(ShipTypeFlag.ZERSTOERERPANZERUNG) ) {
				int dmgThisTurn = eShip.getShip().getHull()-eShip.getHull()+hulldamage;
				if( dmgThisTurn / (double)eShipType.getHull() > 0.25 ) {
					int newhulldamage = (int)(Math.ceil(eShipType.getHull()*0.25) - (eShip.getShip().getHull()-eShip.getHull()));
					getBattleService().logme(battle, "+ Schiffsflag Schadensabsorption absorbiert Schaden ("+Common.ln(hulldamage-newhulldamage)+" dmg)\n");
					logMsg.append("+ Schiffsflag Schadensabsorption absorbiert Schaden  (").append(Common.ln(hulldamage - newhulldamage)).append(" dmg)\n");

					hulldamage = newhulldamage;
				}
			}

			//Ablative Panzerung pruefen
			int ablativeArmor = eShip.getAblativeArmor();
			if(ablativeArmor > 0)
			{
				ablativeArmor -= hulldamage;

				//Angerichteter Schaden
				int damage = eShip.getAblativeArmor() - ablativeArmor;
				if(damage > eShip.getAblativeArmor())
				{
					damage = eShip.getAblativeArmor();
				}

				getBattleService().logme(battle,  "+ Schaden (ablative Panzerung): "+Common.ln(damage)+"\n" );
				logMsg.append("+ Hülle: ").append(Common.ln(damage)).append(" Schaden\n");

				//Ablative Panzerung von VOR dem Treffer abziehen
				hulldamage -= eShip.getAblativeArmor();
				if(hulldamage < 0)
				{
					hulldamage = 0;
				}
				if(ablativeArmor < 0)
				{
					ablativeArmor = 0;
				}
			}
			eShip.setAblativeArmor(ablativeArmor);

			if( eShipType.hasFlag(ShipTypeFlag.GOD_MODE ) ) {
				if( eShip.getHull() - hulldamage < 1 ) {
					hulldamage = eShip.getHull() - 1;
					getBattleService().logme(battle, "+ Schiff nicht zerstörbar\n");
					logMsg.append("+ Schiff nicht zerstörbar\n");
				}
			}

			ship_intact = eShip.getHull() - hulldamage > 0;

			eShip.setHull(eShip.getHull() - hulldamage);
			if( eShip.getHull() < 0 ) {
				eShip.setHull(0);
			}

			if( eShip.getHull() > 0 ) {
				//Hat die ablative Panzerung alles abgefangen?
				if(hulldamage > 0)
				{
					getBattleService().logme(battle,  "+ Schaden (Hülle): "+Common.ln(hulldamage)+"\n" );
					logMsg.append("+ Hülle: ").append(Common.ln(hulldamage)).append(" Schaden\n");
				}

				//Subsysteme - nur treffbar, wenn die ablative Panzerung auf 0 ist
				if( subdmgs != null && (subdmgs.length > 0) && ablativeArmor == 0) {
					final int ENGINE = 0;
					final int WEAPONS = 1;
					final int COMM = 2;
					final int SENSORS = 3;

					List<Integer> subsysteme = new ArrayList<>();
					subsysteme.add(SENSORS);
					subsysteme.add(COMM);

					List<String> subsysteme_name = new ArrayList<>();
					subsysteme_name.add("Sensoren");
					subsysteme_name.add("Kommunikation");

					if( eShipType.getCost() > 0 ) {
						subsysteme.add(ENGINE);
						subsysteme_name.add("Antrieb");
					}

					if( eShipType.isMilitary() ) {
						subsysteme.add(WEAPONS);
						subsysteme_name.add("Waffen");
					}

					for (int subdmg : subdmgs)
					{
						if (subdmg < 1)
						{
							continue;
						}

						int rnd = ThreadLocalRandom.current().nextInt(subsysteme.size());
						int subsys = subsysteme.get(rnd);

						int value = 0;
						switch (subsys)
						{
							case ENGINE:
								eShip.setEngine(Math.max(eShip.getEngine() - subdmg, 0));
								value = eShip.getEngine();
								break;
							case WEAPONS:
								eShip.setWeapons(Math.max(eShip.getWeapons() - subdmg, 0));
								value = eShip.getWeapons();
								break;
							case COMM:
								eShip.setComm(Math.max(eShip.getComm() - subdmg, 0));
								value = eShip.getComm();
								break;
							case SENSORS:
								eShip.setSensors(Math.max(eShip.getSensors() - subdmg, 0));
								value = eShip.getSensors();
								break;
						}

						if (value > 0)
						{
							getBattleService().logme(battle, "+ " + subsysteme_name.get(rnd) + ": " + Common.ln(subdmg) + " Schaden\n");
							logMsg.append("+ ").append(subsysteme_name.get(rnd)).append(": ").append(Common.ln(subdmg)).append(" Schaden\n");
						}
						else
						{
							getBattleService().logme(battle, "+ " + subsysteme_name.get(rnd) + ": ausgefallen\n");
							logMsg.append("+ ").append(subsysteme_name.get(rnd)).append(": ausgefallen\n");
						}
					}
				}
			}
			else {
				getBattleService().logme(battle,  "[color=red]+ Schiff zerstört[/color]\n" );
				logMsg.append("[color=red]+ Schiff zerstört[/color]\n");
				if( !new ConfigService().getValue(WellKnownConfigValue.DESTROYABLE_SHIPS) ) {
					if( eShip.getHull() < 1 ) {
						eShip.setHull(1);
					}
					ship_intact = true;
				}
			}
		}

		if( !ship_intact ) {
			eShip.removeFlag(BattleShipFlag.HIT);
			eShip.addFlag(BattleShipFlag.DESTROYED);
		}
		else {
			eShip.addFlag(BattleShipFlag.HIT);
			if(eShip.hasFlag(BattleShipFlag.FLUCHTNEXT) && (eShip.getEngine() == 0) && (eShipType.getCost() > 0) ) {
				eShip.removeFlag(BattleShipFlag.FLUCHTNEXT);
			}
			if(eShip.hasFlag(BattleShipFlag.FLUCHT) && (eShip.getEngine() == 0) && (eShipType.getCost() > 0) ) {
				eShip.removeFlag(BattleShipFlag.FLUCHT);
				getBattleService().logme(battle,  "+ Flucht gestoppt\n" );
				logMsg.append("[color=red]+ Flucht gestoppt[/color]\n");
			}
		}

		return ship_intact;
	}

	private Waffenbeschreibung getWeaponData() {
		ShipTypeData enemyShipType = this.enemyShip.getTypeData();

		return new WaffenBasierteWaffenbeschreibung(this.weapon, enemyShipType.getSize() <= ShipType.SMALL_SHIP_MAXSIZE);
	}

	private Waffenbeschreibung getAmmoBasedWeaponData( Battle battle, final String weaponName, final Munition munition) {
		BattleShip ownShip = this.ownShip;

		ShipTypeData enemyShipType = this.enemyShip.getTypeData();
		ShipTypeData ownShipType = ownShip.getTypeData();

		Map<String,Integer> weapons = ownShipType.getWeapons();
        if(!weapons.containsKey(weaponName))
        {
            throw new IllegalArgumentException("Weapon: " + weaponName + " not found on ship: " + ownShip.getId());
        }
		int weaponCount = weapons.get(weaponName);

		Munitionsdefinition munitionsdefinition = null;
		ItemCargoEntry<Munition> ammoitem;

		// Munition
		Cargo mycargo = ownShip.getCargo();
		List<ItemCargoEntry<Munition>> itemlist = mycargo.getItemsOfType(Munition.class);

		if( this.weapon.hasFlag(Weapon.Flags.AMMO_SELECT) ) {
			boolean item = itemlist.stream().anyMatch(i -> i.getItem() == munition);

			if( !item ) {
				getBattleService().logme(battle,  "Sie verf&uuml;gen nicht &uuml;ber den angegebenen Munitionstyp\n" );
				return null;
			}

			munitionsdefinition = munition.getEffect().getAmmo();

            if(!this.weapon.getMunitionstypen().contains(munitionsdefinition.getType()))
            {
                log.info("Ein nicht existierender Munitionstyp wurde geladen: " + munition);
                for(String ammoType: this.weapon.getMunitionstypen())
                {
                    log.info("Allowed ammoType: " + ammoType);
                }
                return null;
            }
		}
		else {
			for (ItemCargoEntry<Munition> anItemlist : itemlist)
			{
				IEAmmo effect = anItemlist.getItem().getEffect();

				if( this.weapon.getMunitionstypen().contains(effect.getAmmo().getType()) )
				{
					munitionsdefinition = effect.getAmmo();
					break;
				}
			}

			if( munitionsdefinition == null ) {
				getBattleService().logme(battle,  "Sie verf&uuml;gen &uuml;ber keine Munition\n" );
				return null;
			}
		}

		ammoitem = null;
		for (ItemCargoEntry<Munition> anItemlist : itemlist)
		{
			IEAmmo effect = anItemlist.getItem().getEffect();
			if (effect.getAmmo() == munitionsdefinition)
			{
				ammoitem = anItemlist;
			}
		}

		if( ammoitem == null || ammoitem.getCount() <  weaponCount*this.weapon.getSingleShots() ) {
			getBattleService().logme(battle,  this.weapon.getName()+" k&ouml;nnen nicht abgefeuert werden, da nicht genug Munition f&uuml;r alle Gesch&uuml;tze vorhanden ist.\n" );
			return null;
		}

		getBattleService().logme(battle,  "Feuere "+munitionsdefinition.getName()+" ab...\n" );

		return new AmmoBasierteWaffenbeschreibung(this.weapon, munitionsdefinition, ammoitem, enemyShipType.getSize() <= ShipType.SMALL_SHIP_MAXSIZE);
	}

	public int getAntiTorpTrefferWS(ShipTypeData enemyShipType, BattleShip enemyShip) {
        if(this.localweapon.getDestroyable() == 0.0d)
        {
            return 0;
        }

		Cargo enemyCargo = enemyShip.getCargo();
		Map<String,Integer> eweapons = enemyShipType.getWeapons();
		double antitorptrefferws = 0;

		for( Map.Entry<String,Integer> wpn : eweapons.entrySet() ) {
			final int count = wpn.getValue();
			final Weapon weapon = Weapons.get().weapon(wpn.getKey());

			if( weapon.getTorpTrefferWS() != 0 ) {
				antitorptrefferws += weapon.getTorpTrefferWS()*count;
			}
			else if( !weapon.getMunitionstypen().isEmpty() )
			{
				for (ItemCargoEntry<Munition> entry : enemyCargo.getItemsOfType(Munition.class))
				{
					Munitionsdefinition munitionsdefinition = entry.getItem().getEffect().getAmmo();
					if( weapon.getMunitionstypen().contains(munitionsdefinition.getType()) )
					{
						int ammocount = (int) entry.getCount();
						int shots = weapon.getSingleShots()*count;
						// check if there's enough ammo to fire
						if(	ammocount >= shots && munitionsdefinition.getTorpTrefferWS() != 0)
						{
							// increase antitorptws
							antitorptrefferws += munitionsdefinition.getTorpTrefferWS()*count;
							// reduce amount of ammo in cargo
							enemyCargo.setResource(entry.getResourceID(), ammocount - shots);
							enemyShip.getShip().setCargo(enemyCargo);
							// stop iteration of ammo here
							// TODO maybe we should check if there's a better ammo in cargo
							break;
						}
					}
				}
			}

		}
		antitorptrefferws *= (this.enemyShip.getShip().getWeapons()/100d);

		return (int)antitorptrefferws;
	}

	public int getFighterDefense( Battle battle )
	{
        if((this.localweapon.getDestroyable() == 0.0d) || battle.getEnemyShip().getTypeData().getSize() <= ShipType.SMALL_SHIP_MAXSIZE)
        {
            return 0;
        }
		//FighterDefense des Gegners
		int defcount = 0;		// Anzahl zu verteidigender Schiffe
		int fighterdefcount = 0;// Gesamtpunktzahl an Bombenabwehr durch Jaeger
		int gksdefcount = 0;	// Gesamtpunktzahl an Bombenabwehr durch GKS
		int fighter = 0;		// Gesamtanzahl Jaeger
		int docks = 0;			// Gesamtanzahl Docks
		int docksuse = 0;		// Gesamtanzahl an Schiffen, welche Docks brauchen

		List<BattleShip> enemyShips = battle.getEnemyShips();
		for (BattleShip selectedShip : enemyShips)
		{
			double crewFactor = determineCrewFactor(selectedShip);

			ShipTypeData type = selectedShip.getTypeData();
			// check if ship has to be defended
			if (shipHasToBeDefended(selectedShip))
		    {
				defcount = defcount + 1;
			}

			//check if ship has torpdef
			if (shipHasTorpDef(type))
			{
				// check if ship is a GKS
				if (shipIsGKS(type))
				{
					// increase the gks-torpedo-defense
					gksdefcount = gksdefcount + (int) Math.floor(type.getTorpedoDef() * crewFactor);
				}
				else
				{
					// check if ship is landed
					if (shipIsNotLanded(selectedShip))
					{
						// increase the fighter-torpedo-defense
						fighterdefcount += (int) Math.floor(type.getTorpedoDef() * crewFactor);
						// increase number of fighters
						fighter = fighter + 1;
					}
				}
			}

			// check if ship needs dock
			if (shipNeedsDock(type))
			{
				// increase number of docks needed
				docksuse = docksuse + 1;
			}

			// check if ship has docks
			if (shipHasDocks(type))
			{
				// add docks
				docks = docks + (int) Math.floor(type.getJDocks() * crewFactor);
			}
		}

		if( defcount == 0 )
		{
			defcount = 1;
		}

		// Rechnen wir mal die endgueltige Verteidigung aus
		if (docksuse > docks)
		{
            if(fighter != 0)
            {
                fighterdefcount = (int) Math.floor(((fighter - (docksuse - docks)) / (double) fighter) * fighterdefcount);
            }
		}

		//FighterDefense Verringerung durch eigene Jäger
		int ownfighterdefcount = 0;// Gesamtpunktzahl an Bombenabwehr durch Jaeger
		int ownfighter = 0;		// Gesamtanzahl Jaeger
		int owndocks = 0;			// Gesamtanzahl Docks
		int owndocksuse = 0;		// Gesamtanzahl an Schiffen, welche Docks brauchen

		List<BattleShip> ownShips = battle.getOwnShips();
		for (BattleShip selectedShip : ownShips)
		{
			double crewFactor = determineCrewFactor(selectedShip);

			ShipTypeData type = selectedShip.getTypeData();
			//check if ship has torpdef
			if (shipHasTorpDef(type))
			{
				// check if ship is a GKS
				if (!shipIsGKS(type))
				{
					// check if ship is landed
					if (shipIsNotLanded(selectedShip))
					{
						// increase the fighter-torpedo-defense
						ownfighterdefcount += (int) Math.floor(type.getTorpedoDef() * crewFactor);
						// increase number of fighters
						ownfighter = ownfighter + 1;
					}
				}
			}

			// check if ship needs dock
			if (shipNeedsDock(type))
			{
				// increase number of docks needed
				owndocksuse = owndocksuse + 1;
			}

			// check if ship has docks
			if (shipHasDocks(type))
			{
				// add docks
				owndocks = owndocks + (int) Math.floor(type.getJDocks() * crewFactor);
			}
		}

		// Rechnen wir mal die endgueltige Verteidigung aus
		if (owndocksuse > owndocks)
		{
            if(ownfighter != 0)
            {
                ownfighterdefcount = (int) Math.floor(((ownfighter - (owndocksuse - owndocks)) / (double) ownfighter) * ownfighterdefcount);
            }
		}

		fighterdefcount = fighterdefcount - ownfighterdefcount;
		if( fighterdefcount < 0 )
		{
			fighterdefcount = 0;
		}

		int fighterdef = (int)Math.round( (double)(fighterdefcount + gksdefcount ) / (double)defcount );
		if( fighterdef > 100 )
		{
			fighterdef = 100;
		}

		return fighterdef;
	}

	private double determineCrewFactor(BattleShip battleShip) {
		ShipTypeData type = battleShip.getTypeData();

		int typeCrew = type.getMinCrew();
		if (typeCrew <= 0)
		{
			typeCrew = 1;
		}
		double crewFactor = ((double) battleShip.getCrew()) / ((double) typeCrew);

		//No bonus for more crew than needed
		if (crewFactor > 1.0)
		{
			crewFactor = 1.0;
		}

		return crewFactor;
	}

	private boolean shipIsNotLanded(BattleShip selectedShip) {
		return !(selectedShip.getShip().isLanded() || selectedShip.getShip().isDocked());
	}

	private boolean shipHasDocks(ShipTypeData type) {
		return type.getJDocks() > 0;
	}

	private boolean shipHasToBeDefended(BattleShip selectedShip) {
		if(selectedShip.hasFlag(BattleShipFlag.JOIN) )
		{
			return false;
		}
		else if(selectedShip.hasFlag(BattleShipFlag.SECONDROW))
		{
			return false;
		}

		return !selectedShip.getTypeData().hasFlag(ShipTypeFlag.JAEGER);
	}

	private boolean shipHasTorpDef(ShipTypeData type) {
		return type.getTorpedoDef() > 0;
	}

	private boolean shipNeedsDock(ShipTypeData type) {
		return !(type.getShipClass() != ShipClasses.JAEGER && type.getShipClass() != ShipClasses.BOMBER);
	}

	private boolean shipIsGKS(ShipTypeData type) {
		return type.getSize() > ShipType.SMALL_SHIP_MAXSIZE;
	}

	private List<BattleShip> getADShipList( Battle battle ) {
		int type = this.enemyShip.getShip().getType();

		// schiffe zusammensuchen
		List<BattleShip> shiplist = new ArrayList<>();
		List<BattleShip> backup = new ArrayList<>();
		boolean gottarget = false;

		List<BattleShip> enemyShips = battle.getEnemyShips();
		for (BattleShip eship : enemyShips)
		{
			if (eship.getShip().getType() == type)
			{
				if (eship == this.enemyShip)
				{
					gottarget = true;
					continue;
				}
				else if (eship.hasFlag(BattleShipFlag.DESTROYED))
				{
					continue;
				}
				else if (eship.hasFlag(BattleShipFlag.FLUCHT) != enemyShip.hasFlag(BattleShipFlag.FLUCHT))
				{
					continue;
				}
				else if ( eship.hasFlag(BattleShipFlag.JOIN) )
				{
					continue;
				}
				else if (eship.getShip().isLanded())
				{
					continue;
				}
				else if (getBattleService().isSecondRow(eship))
				{
					continue;
				}

				shiplist.add(eship);
				if (!gottarget && (shiplist.size() > this.localweapon.getAreaDamage()))
				{
					backup.add(shiplist.remove(0));
				}
				if (gottarget && (shiplist.size() >= this.localweapon.getAreaDamage() * 2))
				{
					break;
				}
			}
		}

		if( shiplist.size() < this.localweapon.getAreaDamage()*2 ) {
			for( int j=shiplist.size(); (j < this.localweapon.getAreaDamage()*2) && !backup.isEmpty(); j++ )	{
				shiplist.add(backup.remove(backup.size()-1));
			}
		}

		final BattleShip emptyRow = new BattleShip();

		// Ein leeres Element hinzufuegen, falls wir nicht genug Elemente haben
		if( (shiplist.size() < this.localweapon.getAreaDamage()*2) && (shiplist.size() % 2 != 0) ) {
			shiplist.add(emptyRow);
		}

		int listmiddle = shiplist.size()/2;

		List<BattleShip> areashiplist = new ArrayList<>(shiplist.size());
		for( int i=0; i < shiplist.size()+1; i++ ) {
			areashiplist.add(emptyRow);
		}

		areashiplist.set(listmiddle, this.enemyShip);
		for( int i=1; i <= shiplist.size(); i++ ) {
			if( i % 2 == 0 ) {
				areashiplist.set(listmiddle-(i/2), shiplist.get(i-1));
			}
			else {
				areashiplist.set(listmiddle+(int)Math.ceil(i/2d), shiplist.get(i-1));
			}
		}

		return areashiplist;
	}

	private int[] getSubDamages(int subPanzerung, int subWS, double damageMod) {
		int subDamage = (int)Math.round(this.localweapon.getSubDamage()*((10-subPanzerung)/10d)*damageMod);

		int[] tmpSubDmgs = new int[this.localweapon.getCount()*this.localweapon.getShotsPerShot()];
		int totalSize = 0;

		for( int i=1; i <= this.localweapon.getCount()*this.localweapon.getShotsPerShot(); i++) {
			int rnd = ThreadLocalRandom.current().nextInt(101);

			if( rnd <= subWS ) {
				tmpSubDmgs[totalSize++] = (int)Math.round((rnd/(double)subWS)*subDamage);
			}
		}

		// Falls nicht alle Felder benoetigt wurden, dann das Array entsprechend gekuerzt zurueckgeben
		if( totalSize < tmpSubDmgs.length ) {
			int[] subDmgs = new int[totalSize];
			System.arraycopy(tmpSubDmgs, 0, subDmgs, 0, totalSize);

			return subDmgs;
		}

		return tmpSubDmgs;
	}

	private void calcADStep(StringBuilder logMsg, Battle battle, int navskill, BattleShip aeShip, int hit, int schaden, int shieldSchaden, double damagemod) {
		getBattleService().logme(battle, "\n"+aeShip.getName()+" ("+aeShip.getId()+"):\n");
		logMsg.append("\n").append(aeShip.getName()).append(" (").append(aeShip.getId()).append("):\n");

		ShipTypeData aeShipType = aeShip.getTypeData();

		int[] tmpsubdmgs = null;

		if( this.localweapon.getSubDamage() > 0 ) {
			int tmppanzerung = aeShip.getArmor();

			int defensivskill = aeShip.getDefensiveValue();

			int subWS = this.getTrefferWS(this.localweapon.getSubWs(), aeShip, aeShipType, defensivskill, navskill );
			getBattleService().logme(battle,  "SubsystemTWS: "+subWS+"%\n" );

			int subPanzerung = tmppanzerung;
			if( subPanzerung > 10 ) {
				subPanzerung = 10;
				getBattleService().logme(battle, "Panzerung absorbiert Subsystemschaden\n");
			}
			else if( subPanzerung > 0 ) {
				getBattleService().logme(battle, "Panzerung reduziert Subsystemschaden ("+(subPanzerung*10)+"%)\n");
			}

			tmpsubdmgs = getSubDamages(subPanzerung, subWS, damagemod);
		}

		boolean mydamage = this.calcDamage(logMsg, battle, aeShip, aeShipType, hit, (int)(shieldSchaden*damagemod), (int)(schaden*damagemod), tmpsubdmgs);
		if( !mydamage && new ConfigService().getValue(WellKnownConfigValue.DESTROYABLE_SHIPS) ) {
			this.destroyShip(logMsg, this.ownShip.getOwner().getId(), battle, aeShip);
		}
	}

	private int getDamage(int damage, int offensivskill, ShipTypeData enemyShipType) {
		int schaden = (int)Math.round( (damage + damage*offensivskill/1500d) *
				(this.ownShip.getShip().getWeapons()/100d) *
				this.weapon.getBaseDamageModifier(enemyShipType));

		if( schaden < 0 ) {
			schaden = 0;
		}

		return schaden;
	}

	@Override
	public Result execute(TemplateEngine t, Battle battle) throws IOException
	{
        Context context = ContextMap.getContext();
        final String weaponName = context.getRequest().getParameterString("weapon");
        int ammoId = context.getRequest().getParameterInt("ammoid");

        init(battle, weaponName, ammoId != 0 ? (Munition) context.getDB().get(Munition.class, ammoId) : null);

        return attack(t, battle);
	}

    public boolean init(Battle battle, String weaponName, Munition munition)
    {
        this.ownShip = battle.getOwnShip();
        this.enemyShip = battle.getEnemyShip();

        ShipTypeData shipType = this.ownShip.getTypeData();

        /*
        * 	Die konkreten Waffendaten ermitteln
        */
        Waffenbeschreibung localweapon;

        if( !this.weapon.getMunitionstypen().isEmpty() )
        {
            localweapon = this.getAmmoBasedWeaponData(battle, weaponName, munition);
        }
        else
        {
            localweapon = this.getWeaponData();
        }

        if(localweapon == null)
        {
            return false;
        }

        Map<String,Integer> weaponList = shipType.getWeapons();
        if(!weaponList.containsKey(weaponName))
        {
            //Ship doesn't have this weapon
            return false;
        }

        int weapons = weaponList.get(weaponName);
        localweapon.setCount(weapons);

        this.localweapon = localweapon;
        this.weaponName = weaponName;

        return true;
    }

    public Result attack(TemplateEngine t, Battle battle) throws IOException
    {
        Result result = super.execute(t, battle);
        if( result != Result.OK )
        {
            return result;
        }

        if( this.weapon == null )
        {
            return Result.ERROR;
        }

        boolean breakFlag = false;
        if(localweapon == null)
        {
            breakFlag = true;
        }

        ShipTypeData ownShipType = this.ownShip.getTypeData();

        Map<String,Integer> weaponList = ownShipType.getWeapons();
        Map<String,Integer> maxheatList = ownShipType.getMaxHeat();
        Map<String,Integer> heatList = this.ownShip.getWeaponHeat();

        if( !weaponList.containsKey(weaponName) )
        {
            getBattleService().logme(battle, "Ihr Schiff besitzt keine Waffen des Typs "+this.weapon.getName());
            return Result.ERROR;
        }

        int weapons = this.localweapon.getCount();
        int maxheat = maxheatList.get(weaponName);
        int heat = 0;
        if( heatList.containsKey(weaponName) )
        {
            heat = heatList.get(weaponName);
        }

        // Feststellen wie oft wird welchen Feuerloop durchlaufen sollen

        boolean firstentry = true; // Battlehistory-Log

        int sameShipLoop = 1; // Alphastrike (same ship)
        int nextShipLoop = 1; // Breitseite (cycle through ships)

		switch (this.attmode)
		{
			case "alphastrike":
				sameShipLoop = 5;
				break;
			case "strafe":
				nextShipLoop = 5;
				break;
			case "alphastrike_max":
				if (weapons > 0)
				{
					sameShipLoop = (int) ((maxheat - heat) / (double) weapons);
				}
				else
				{
					sameShipLoop = 1;
				}
				if (sameShipLoop < 1)
				{
					sameShipLoop = 1;
				}
				break;
			case "strafe_max":
				if (weapons > 0)
				{
					nextShipLoop = (int) ((maxheat - heat) / (double) weapons);
				}
				else
				{
					nextShipLoop = 1;
				}
				if (nextShipLoop < 1)
				{
					nextShipLoop = 1;
				}
				break;
		}

        // Und nun checken wir mal ein wenig....

        if( (ownShipType.getMinCrew() > 0) && (this.ownShip.getCrew() < ownShipType.getMinCrew()/2d) )
        {
            getBattleService().logme(battle,  "Nicht genug Besatzung, um mit der Waffe "+this.weapon.getName()+" zu feuern\n" );
            return Result.ERROR;
        }

        if( this.ownShip.hasFlag(BattleShipFlag.DISABLE_WEAPONS) )
        {
            getBattleService().logme(battle,  "Das Schiff kann seine Waffen in diesem Kampf nicht mehr abfeuern\n" );
            return Result.ERROR;
        }

        if( this.ownShip.hasFlag(BattleShipFlag.BLOCK_WEAPONS) )
        {
            getBattleService().logme(battle,  "Sie k&ouml;nnen in dieser Runde keine Waffen mehr abfeuern\n" );
            return Result.ERROR;
        }

        boolean gotone = false;
        if( ownShipType.hasFlag(ShipTypeFlag.DROHNE) )
        {
            List<BattleShip> ownShips = battle.getOwnShips();
            for (BattleShip aship : ownShips)
            {
                ShipTypeData ashiptype = aship.getTypeData();
                if (ashiptype.hasFlag(ShipTypeFlag.DROHNEN_CONTROLLER))
                {
                    gotone = true;
                    break;
                }
            }
        }
        else
        {
            gotone = true;
        }

        if( !gotone )
        {
            getBattleService().logme(battle,  "Sie ben&ouml;tigen ein Drohnen-Kontrollschiff, um feuern zu k&ouml;nnen\n" );
            return Result.ERROR;
        }

        if( weapons <= 0 )
        {
            getBattleService().logme(battle,  "Das Schiff verf&uuml;gt nicht &uuml;ber die von ihnen gew&auml;hlte Waffe ("+weaponName+")\n" );
            return Result.ERROR;
        }

        if( this.ownShip.getShip().getEnergy() < this.weapon.getECost()*weapons )
        {
            getBattleService().logme(battle,  "Nicht genug Energie vorhanden, um mit der Waffe "+this.weapon.getName()+" zu feuern\n" );
            return Result.ERROR;
        }

        if(this.ownShip.getShip().isLanded())
        {
            getBattleService().logme(battle,  "Sie k&ouml;nnen nicht mit gelandeten Schiffen feuern\n" );
            return Result.ERROR;
        }

        int oldenemyship = battle.getEnemyShipIndex();

        // Die auessere Schleife laeuft ueber die generischen Schiffe
        // Die innere Scheife feuernt n Mal auf das gerade ausgewaehlte gegnerische Schiff

        Offizier attoffizier = ownShip.getShip().getOffizier();
        int fighterdef = getFighterDefense(battle);

		StringBuilder logMsg = new StringBuilder();

        result = Result.OK;
        outer: for( int outerloop=0; outerloop < nextShipLoop; outerloop++ ) {
			// Nun das gegnerische Schiff laden und checken
			this.enemyShip = battle.getEnemyShip();

			for (int innerloop = 0; innerloop < sameShipLoop; innerloop++) {
				if ((outerloop > 0) || (innerloop > 0)) {
					getBattleService().logme(battle, "\n[HR]");
					logMsg.append("\n");
				}

				ShipTypeData enemyShipType = this.enemyShip.getTypeData();

				if (this.ownShip.hasFlag(BattleShipFlag.SECONDROW) &&
						!this.localweapon.isLongRange() &&
						!this.localweapon.isVeryLongRange()) {
					getBattleService().logme(battle, this.weapon.getName() + " haben nicht die notwendige Reichweite, um aus der zweiten Reihe heraus abgefeuert zu werden\n");
					breakFlag = true;
					result = Result.ERROR;
					break;
				}

				getBattleService().logme(battle, "Ziel: " + Battle.log_shiplink(this.enemyShip.getShip()) + "\n");

				if (heat + weapons > maxheat) {
					getBattleService().logme(battle, this.weapon.getName() + " k&ouml;nnen nicht abgefeuert werden, da diese sonst &uuml;berhitzen w&uuml;rden\n");
					breakFlag = true;
					result = Result.ERROR;
					break;
				}

				if (!this.weapon.getMunitionstypen().isEmpty()) {
					Cargo mycargo = this.ownShip.getCargo();
					if (mycargo.getResourceCount(this.localweapon.getAmmoItem().getResourceID()) - this.localweapon.getCount() * this.weapon.getSingleShots() < 0) {
						getBattleService().logme(battle, this.weapon.getName() + " k&ouml;nnen nicht abgefeuert werden, da nicht genug Munition vorhanden ist\n");
						breakFlag = true;
						result = Result.ERROR;
						break;
					}
				}

				if (this.ownShip.getShip().getEnergy() < this.weapon.getECost() * weapons) {
					getBattleService().logme(battle, "Nicht genug Energie vorhanden, um mit der Waffe " + this.weapon.getName() + " zu feuern\n");
					breakFlag = true;
					result = Result.ERROR;
					break;
				}

				if (this.enemyShip.hasFlag(BattleShipFlag.DESTROYED)) {
					getBattleService().logme(battle, "Das angegebene Ziel ist bereits zerst&ouml;rt\n");
					breakFlag = true;
					result = Result.ERROR;
					break;
				}

				if (this.enemyShip.hasFlag(BattleShipFlag.FLUCHT) && !ownShipType.hasFlag(ShipTypeFlag.ABFANGEN)) {
					getBattleService().logme(battle, "Ihr Schiff kann keine fl&uuml;chtenden Schiffe abfangen\n");
					breakFlag = true;
					result = Result.ERROR;
					break;
				}

				if (this.enemyShip.hasFlag(BattleShipFlag.SECONDROW) && !this.localweapon.isVeryLongRange()) {
					getBattleService().logme(battle, "Ihre Waffen k&ouml;nnen das angegebene Ziel nicht erreichen\n");
					breakFlag = true;
					result = Result.ERROR;
					break;
				}

				if (this.enemyShip.hasFlag(BattleShipFlag.JOIN)) {
					getBattleService().logme(battle, "Sie k&ouml;nnen nicht auf einem Schiff feuern, das gerade erst der Schlacht beitritt\n");
					breakFlag = true;
					result = Result.ERROR;
					break;
				}

				for (BattleShip battleship : battle.getEnemyShips()) {
					if (battleship.getShip().getTypeData().hasFlag(ShipTypeFlag.SCHUTZSCHILD) && !this.enemyShip.getTypeData().hasFlag(ShipTypeFlag.SCHUTZSCHILD)) {
						getBattleService().logme(battle, "Sie m&uuml;ssen zuerst den Schutzschild zerstören\n");
						result = Result.ERROR;
						break outer;
					}
				}

				/*
				 * 	Anti-Torp-Verteidigungswerte ermitteln
				 */
				int antitorptrefferws = this.getAntiTorpTrefferWS(enemyShipType, this.enemyShip);

				if (antitorptrefferws > 0) {
					getBattleService().logme(battle, "Anti-Torp.-Treffer-WS: " + this.getTWSText(antitorptrefferws) + "%\n");
				}
				if (fighterdef > 0) {
					getBattleService().logme(battle, "Verteidigung durch Schiffe: " + this.getTWSText(fighterdef) + "%\n");
				}

				ownShipType = this.ownShip.getTypeData();
				ShipTypeData ownST = this.weapon.calcOwnShipType(ownShipType, enemyShipType);
				ShipTypeData enemyST = this.weapon.calcEnemyShipType(ownShipType, enemyShipType);

				ownShipType = ownST;
				enemyShipType = enemyST;

				int offensivskill = ownShip.getOffensiveValue();
				int navskill = ownShip.getNavigationalValue();
				int defensivskill = enemyShip.getDefensiveValue();

				if (battle.getCommander(ownShip.getSide()).hasFlag(UserFlag.KS_DEBUG)) {
					getBattleService().logme(battle, "Offensivskill: " + offensivskill + "\n");
					getBattleService().logme(battle, "Navigationsskill: " + navskill + "\n");
					getBattleService().logme(battle, "Defensivskill: " + defensivskill + "\n");
				}

				/*
				 * 	Schadenswerte, Panzerung & TrefferWS ermitteln
				 */
				int absSchaden = this.getDamage(this.localweapon.getBaseDamage(), offensivskill, enemyShipType);
				int shieldSchaden = this.getDamage(this.localweapon.getShieldDamage(), offensivskill, enemyShipType);

				int panzerung = enemyShip.getArmor();
				int schaden = absSchaden;

				int trefferWS = calculateTrefferWS(battle, enemyShipType, fighterdef,
						antitorptrefferws, navskill, defensivskill, true);

				int[] subdmgs = calculateSubsystemTrefferWS(battle, enemyShipType, navskill,
						defensivskill, panzerung);

				if (schaden < 0) {
					schaden = 0;
				}

				if (firstentry) {
					firstentry = false;
				}

				/*
				 * 	Treffer berechnen
				 */
				int hit = calculateHits(logMsg, battle, fighterdef, trefferWS, attoffizier);

				boolean savedamage = this.calcDamage(logMsg, battle, this.enemyShip, enemyShipType, hit, shieldSchaden, schaden, subdmgs);

				/*
				 *  Areadamage - falls notwendig - berechnen
				 */
				if ((this.localweapon.getAreaDamage() != 0) && (hit != 0)) {
					doAreaDamage(logMsg, battle, navskill, shieldSchaden, schaden, hit);
				}

				/*
				 * 	E, Muni usw in die DB schreiben
				 */
				heat += this.localweapon.getCount();
				this.ownShip.getShip().setEnergy(this.ownShip.getShip().getEnergy() - this.weapon.getECost() * this.localweapon.getCount());

				if (!this.weapon.getMunitionstypen().isEmpty()) {
					Cargo mycargo = this.ownShip.getCargo();
					mycargo.substractResource(this.localweapon.getAmmoItem().getResourceID(), this.localweapon.getCount() * this.weapon.getSingleShots());
					this.ownShip.getShip().setCargo(mycargo);
				}

				heatList.put(weaponName, heat);
				this.ownShip.getShip().setWeaponHeat(heatList);


				/*
				 *  BETAK - Check
				 */
				if (battle.getBetakStatus(battle.getOwnSide()) && !enemyShipType.isMilitary()) {
					battle.setBetakStatus(battle.getOwnSide(), false);
					getBattleService().logme(battle, "[color=red][b]Sie haben die BETAK-Konvention verletzt[/b][/color]\n\n");
					logMsg.append("[color=red][b]Die BETAK-Konvention wurde verletzt[/b][/color]\n\n");
				}

				/*
				 *	Schiff falls notwendig zerstoeren
				 */
				if (!savedamage && new ConfigService().getValue(WellKnownConfigValue.DESTROYABLE_SHIPS)) {
					this.destroyShip(logMsg, this.ownShip.getOwner().getId(), battle, this.enemyShip);
					int newindex = battle.getNewTargetIndex();
					if (newindex != -1) {
						battle.setEnemyShipIndex(newindex);
					} else {
						breakFlag = true;
					}
					this.enemyShip = battle.getEnemyShip();
				}

				/*
				 * 	Wenn das angreifende Schiff auch zerstoert werden muss tun wir das jetzt mal
				 */
				if (this.localweapon.isDestroyAfter()) {
					getBattleService().logme(battle, "[color=red]+ Angreifer zerst&ouml;rt[/color]\n");
					logMsg.append("[color=red]+ Angreifer zerstört[/color]\n");

					if (new ConfigService().getValue(WellKnownConfigValue.DESTROYABLE_SHIPS)) {
						this.destroyShipOnly(this.ownShip.getOwner().getId(), battle, this.ownShip);

						breakFlag = true;
						break;
					}
				}
			}

			if (outerloop < nextShipLoop - 1) {
				int newindex = battle.getNewTargetIndex();
				if (newindex == -1) {
					newindex = 0;
				}
				battle.setEnemyShipIndex(newindex);
			}

			if (breakFlag) {
				break;
			}
		}

        this.ownShip.getShip().setBattleAction(true);
        this.ownShip.addFlag(BattleShipFlag.SHOT);

        if( !firstentry )
        {
			getBattleService().log(battle, new SchlachtLogAktion(battle.getOwnSide(), logMsg.toString()));
        }

        if( !battle.getEnemyShip(oldenemyship).hasFlag(BattleShipFlag.DESTROYED) )
        {
            battle.setEnemyShipIndex(oldenemyship);
        }

		shipActionService.recalculateShipStatus(ownShip.getShip());

        return result;
    }

	private void doAreaDamage(StringBuilder logMsg, Battle battle, int navskill, int shieldSchaden, int schaden,
							  int hit)
	{
		List<BattleShip> areashiplist = this.getADShipList(battle);

		// In der $areashiplist ist das aktuell ausgewaehlte Schiff immer in der Mitte (abgerundet)
		int targetindex = areashiplist.size()/2;

		// schaden anwenden
		int damagemod = 0;

		if( !this.localweapon.isAreaDamageFull() )
		{
			damagemod = 1 / (this.localweapon.getAreaDamage()+1);
		}

		for( int i=1; i <= this.localweapon.getAreaDamage(); i++ )
		{
			// Es kann sein, dass die Liste nicht vollstaendig gefuellt ist (Schiffe ohne Schlacht).
			// Diese muessen wir jetzt rausfiltern
			if( (targetindex-i >= 0) && areashiplist.get(targetindex-i).getBattle() != null )
			{
				BattleShip aeShip = areashiplist.get(targetindex-i);

				this.calcADStep(logMsg, battle, navskill, aeShip, hit, schaden, shieldSchaden, 1-i*damagemod);
			}
			if( (targetindex+i < areashiplist.size()) && areashiplist.get(targetindex+i).getBattle() != null )
			{
				BattleShip aeShip = areashiplist.get(targetindex+i);

				this.calcADStep(logMsg, battle, navskill, aeShip, hit, schaden, shieldSchaden, 1-i*damagemod);
			}
		}
	}

	private int calculateHits(StringBuilder logMsg, Battle battle, int fighterdef, int trefferWS, Offizier attoffizier)
	{
		int hit = 0;
		int def = 0;
		int gesamtSchuesse = this.localweapon.getCount() * this.localweapon.getShotsPerShot();
		for( int i=1; i <= gesamtSchuesse; i++)
		{
			int rnd = ThreadLocalRandom.current().nextInt(1,101);
			if( battle.getCommander(ownShip.getSide()).hasFlag( UserFlag.KS_DEBUG )) {
				getBattleService().logme(battle,  i + ". Schuss: " + rnd + "%\n");
			}
			if( rnd <= trefferWS )
			{
				hit++;
				if( attoffizier != null)
				{
					int rnd2 = ThreadLocalRandom.current().nextInt(1,101);
					if( rnd2 <= 38)
					{
						attoffizier.gainExperience(Offizier.Ability.WAF, 1);
					}
					else if( rnd2 <= 76)
					{
						attoffizier.gainExperience(Offizier.Ability.COM, 1);
					}
					else
					{
						attoffizier.gainExperience(Offizier.Ability.NAV, 1);
					}
				}
			}
			else if((rnd <= trefferWS+fighterdef) && (this.localweapon.getDestroyable() > 0) )
			{
				def++;
			}
		}
		getBattleService().logme(battle,  this.weapon.getName()+": "+hit+" von "+ gesamtSchuesse +" Sch&uuml;ssen haben getroffen\n" );
		logMsg.append(Battle.log_shiplink(this.ownShip.getShip())).append(" feuert auf ").append(Battle.log_shiplink(this.enemyShip.getShip())).append("\n+ Waffe: ").append(this.localweapon.getName()).append("\n");
		if( this.localweapon.getDestroyable() > 0 && (def != 0) )
		{
			getBattleService().logme(battle,  this.weapon.getName()+": "+def+" von "+ gesamtSchuesse +" Sch&uuml;ssen wurden abgefangen\n" );
			logMsg.append("+ ").append(this.weapon.getName()).append(": ").append(def).append(" von ").append(gesamtSchuesse).append(" Schüssen wurden abgefangen\n");
		}
		return hit;
	}

	private int[] calculateSubsystemTrefferWS(Battle battle, ShipTypeData enemyShipType,
											  int navskill, int defensivskill, int panzerung)
	{
		int[] subdmgs = null;

		/*
		 * 	Subsystem-Schaden, falls notwendig, berechnen
		 */
		if( this.localweapon.getSubDamage() > 0 )
		{
			int subWS = this.getTrefferWS(this.localweapon.getSubWs(), this.enemyShip, enemyShipType, defensivskill, navskill );
			if( battle.getCommander(ownShip.getSide()).hasFlag( UserFlag.KS_DEBUG )) {
				getBattleService().logme(battle,  "Subsystem-TWS: "+ subWS +"%\n" );
			}
			else
			{
				getBattleService().logme(battle,  "Subsystem-TWS: "+ this.getTWSText(subWS) +"\n");
			}

			int subPanzerung = panzerung;
			if( subPanzerung > 10 )
			{
				subPanzerung = 10;
				getBattleService().logme(battle, "Panzerung absorbiert Subsystemschaden\n");
			}
			else if( subPanzerung > 0 )
			{
				getBattleService().logme(battle, "Panzerung reduziert Subsystemschaden ("+(subPanzerung*10)+"%)\n");
			}

			subdmgs = this.getSubDamages( subPanzerung, subWS, 1);
		}
		return subdmgs;
	}

	public int calculateTrefferWS(Battle battle, ShipTypeData enemyShipType, int fighterdef,
			int antitorptrefferws, int navskill, int defensivskill, boolean useBattleLog)
	{
		int trefferWS = this.getTrefferWS(this.localweapon.getDefTrefferWs(), this.enemyShip, enemyShipType, defensivskill, navskill);

        if(useBattleLog)
        {
            if( battle.getCommander(ownShip.getSide()).hasFlag( UserFlag.KS_DEBUG )) {
                getBattleService().logme(battle,  "Basis-Treffer-WS: "+ trefferWS +"%\n");
                getBattleService().logme(battle,  "Jäger-Torp.-Verteidigung: "+ fighterdef +"%\n");
                getBattleService().logme(battle,  "Anti-Torp.-Treffer-WS: "+ antitorptrefferws +"%\n");
            }
            else
            {
                getBattleService().logme(battle,  "Basis-Treffer-WS: "+ this.getTWSText(trefferWS) +"\n");
            }
        }


		// Minimum bei 5% bei zerstoerbaren Waffen
		if( this.localweapon.getDestroyable() > 0 && trefferWS > 5) {
            trefferWS -= antitorptrefferws;
            trefferWS -= fighterdef;
            if(trefferWS < 5)
            {
                trefferWS = 5;
            }
		}
		if( battle.getCommander(ownShip.getSide()).hasFlag( UserFlag.KS_DEBUG )) {
			getBattleService().logme(battle,  "Treffer-WS: "+ trefferWS +"%\n" );
		}
		else
		{
			getBattleService().logme(battle,  "Treffer-WS: "+ this.getTWSText(trefferWS) +"\n");
		}
		return trefferWS;
	}

	private String getTWSText(int chance)
	{
		String answer;
		if (chance == 0)
		{
			answer = "nicht vorhanden";
		}
		else if (chance <=  25)
		{
			answer = "gering";
		}
		else if ( chance <= 50)
		{
			answer = "ausreichend";
		}
		else if ( chance <= 75)
		{
			answer = "gut";
		}
		else if ( chance <= 99)
		{
			answer = "hervorragend";
		}
		else
		{
			answer = "unfehlbar";
		}
		return answer;
	}
}
