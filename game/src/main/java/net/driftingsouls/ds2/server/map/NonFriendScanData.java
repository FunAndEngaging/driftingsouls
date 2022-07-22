package net.driftingsouls.ds2.server.map;

import net.driftingsouls.ds2.server.Locatable;
import net.driftingsouls.ds2.server.Location;

public class NonFriendScanData implements Locatable {
    private final int system;
    private final int x;
    private final int y;
    private final int nebeltype;
    private final int size;
    private final boolean isEnemy;


    public NonFriendScanData(int system, int x, int y, int nebeltype, int size, boolean isEnemy) {
        this.system = system;
        this.x = x;
        this.y = y;
        this.isEnemy = isEnemy;
        this.size = size;
        this.nebeltype = nebeltype;
    }

    @Override
    public Location getLocation() {
        return new Location(system, x, y);
    }


    public boolean getIsEnemy()
    {
        return isEnemy;
    }

    public int getSize()
    {
        return size;
    }
}
