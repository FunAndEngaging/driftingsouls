package net.driftingsouls.ds2.server.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import net.driftingsouls.ds2.server.Location;
import net.driftingsouls.ds2.server.bases.Base;
import net.driftingsouls.ds2.server.entities.JumpNode;
import net.driftingsouls.ds2.server.entities.Nebel;
import net.driftingsouls.ds2.server.ships.Ship;

/**
 * Eine Systemkarte.
 * Die Karte wird intern von den diversen Sichten verwendet, um
 * auf die im System enthaltenen Objekte zuzugreifen.
 * 
 * @author Sebastian Gift
 */
@Table(name="starmap")
@Entity
class Starmap
{
	Starmap()
	{}
	
	boolean isNebula(Location location)
	{
		return getNebulaMap().containsKey(location);
	}
	
	/**
	 * @return Die Nummer des Sternensystems
	 */
	int getSystem()
	{
		return this.system;
	}
	
	/**
	 * @return Die JumpNodes im System.
	 */
	Collection<JumpNode> getJumpNodes()
	{
		if(nodes != null)
		{
			return Collections.unmodifiableCollection(this.nodes);
		}
		
		return null;
	}
	
	/**
	 * @return Die Liste der Schiffe im System, sortiert nach Sektoren.
	 */
	Map<Location, List<Ship>> getShipMap()
	{
		if( this.shipMap == null ) {
			this.shipMap = buildShipMap(this.ships);
		}
		return Collections.unmodifiableMap(this.shipMap);
	}
	
	/**
	 * @return Die Liste der Basen im System, sortiert nach Sektoren.
	 */
	Map<Location, List<Base>> getBaseMap()
	{
		if( this.baseMap == null ) {
			this.baseMap = buildBaseMap(this.bases);
		}
		return Collections.unmodifiableMap(this.baseMap);
	}
	
	/**
	 * @return Die Liste der Jumpnodes im System, sortiert nach Sektoren.
	 */
	Map<Location, List<JumpNode>> getNodeMap()
	{
		if( this.nodeMap == null ) {
			this.nodeMap = buildNodeMap(this.nodes);
		}
		return Collections.unmodifiableMap(this.nodeMap);
	}
	
	/**
	 * @return Die Liste der Schiffe im System, sortiert nach Sektoren.
	 */
	Map<Location, Nebel> getNebulaMap()
	{
		if( this.nebulaMap == null ) {
			this.nebulaMap = buildNebulaMap(this.nebulas);
		}
		return Collections.unmodifiableMap(this.nebulaMap);
	}
	
	protected Map<Location, List<Ship>> buildShipMap(List<Ship> ships)
	{
		Map<Location, List<Ship>> shipMap = new HashMap<Location, List<Ship>>();

		for(Ship ship: ships)
		{
			Location position = ship.getLocation();
			if(!shipMap.containsKey(position))
			{
				shipMap.put(position, new ArrayList<Ship>());
			}

			shipMap.get(position).add(ship);
		}

		return shipMap;
	}
	
	protected Map<Location, Nebel> buildNebulaMap(List<Nebel> nebulas)
	{
		Map<Location, Nebel> nebulaMap = new HashMap<Location, Nebel>();

		for(Nebel nebula: nebulas)
		{
			nebulaMap.put(nebula.getLocation(), nebula);
		}

		return nebulaMap;
	}
	
	protected Map<Location, List<JumpNode>> buildNodeMap(List<JumpNode> nodes)
	{
		Map<Location, List<JumpNode>> nodeMap = new HashMap<Location, List<JumpNode>>();

		for(JumpNode node: nodes)
		{
			Location position = new Location(node.getSystem(), node.getX(), node.getY());

			if(!nodeMap.containsKey(position))
			{
				nodeMap.put(position, new ArrayList<JumpNode>());
			}

			nodeMap.get(position).add(node);
		}

		return nodeMap;		
	}
	
	protected Map<Location, List<Base>> buildBaseMap(List<Base> bases)
	{
		Map<Location, List<Base>> baseMap = new HashMap<Location, List<Base>>();

		for(Base base: bases)
		{
			Location position = base.getLocation();
			if(!baseMap.containsKey(position))
			{
				baseMap.put(position, new ArrayList<Base>());
			}

			int size = base.getSize();
			if(size > 0)
			{
				for(int y = base.getY() - size; y <= base.getY() + size; y++)
				{
					for(int x = base.getX() - size; x <= base.getX() + size; x++)
					{
						Location loc = new Location(position.getSystem(), x, y);

						if( !position.sameSector( 0, loc, base.getSize() ) ) {
							continue;	
						}

						if(!baseMap.containsKey(loc))
						{
							baseMap.put(loc, new ArrayList<Base>());
						}

						baseMap.get(loc).add(0, base); //Big objects are always printed first
					}
				}
			}
			else
			{
				baseMap.get(position).add(base);
			}
		}

		return baseMap;		
	}
	
	@Id
	private int system;
	@OneToMany
	@JoinColumn(name="system", nullable=true)
	private List<Ship> ships;
	@OneToMany
	@JoinColumn(name="system", nullable=true)
	private List<Nebel> nebulas;
	@OneToMany
	@JoinColumn(name="system", nullable=true)
	private List<JumpNode> nodes;
	@OneToMany
	@JoinColumn(name="system", nullable=true)
	private List<Base> bases = new ArrayList<Base>();
	
	@Transient
	private Map<Location, List<Ship>> shipMap;
	@Transient
	private Map<Location, Nebel> nebulaMap;
	@Transient
	private Map<Location, List<JumpNode>> nodeMap;
	@Transient
	private Map<Location, List<Base>> baseMap;
}
