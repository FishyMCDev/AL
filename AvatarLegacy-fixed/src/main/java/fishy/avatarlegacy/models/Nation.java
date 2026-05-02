package fishy.avatarlegacy.models;

import org.bukkit.Location;

import java.util.UUID;

public class Nation {
    private int id;
    private String name;
    private UUID leaderUuid;
    private long creationTimestamp;
    private boolean corePlaced;
    private Location coreLocation;
    private double treasury;
    private double taxRate;
    private boolean allowMultiElement;
    private boolean allowRefugees;
    private long gracePeriodEnd;

    public Nation(int id, String name, UUID leaderUuid) {
        this.id = id;
        this.name = name;
        this.leaderUuid = leaderUuid;
        this.creationTimestamp = System.currentTimeMillis();
        this.corePlaced = false;
        this.coreLocation = null;
        this.treasury = 0.0;
        this.taxRate = 0.0;
        this.allowMultiElement = false;
        this.allowRefugees = true;
        this.gracePeriodEnd = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public UUID getLeaderUuid() { return leaderUuid; }
    public void setLeaderUuid(UUID leaderUuid) { this.leaderUuid = leaderUuid; }
    public long getCreationTimestamp() { return creationTimestamp; }
    public boolean isCorePlaced() { return corePlaced; }
    public void setCorePlaced(boolean corePlaced) { this.corePlaced = corePlaced; }
    public Location getCoreLocation() { return coreLocation; }
    public void setCoreLocation(Location coreLocation) { this.coreLocation = coreLocation; }
    public double getTreasury() { return treasury; }
    public void setTreasury(double treasury) { this.treasury = treasury; }
    public void addToTreasury(double amount) { this.treasury += amount; }

    public boolean withdrawFromTreasury(double amount) {
        if (treasury >= amount) { treasury -= amount; return true; }
        return false;
    }

    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { this.taxRate = Math.min(taxRate, 50.0); }
    public boolean isAllowMultiElement() { return allowMultiElement; }
    public void setAllowMultiElement(boolean allowMultiElement) { this.allowMultiElement = allowMultiElement; }
    public boolean isAllowRefugees() { return allowRefugees; }
    public void setAllowRefugees(boolean allowRefugees) { this.allowRefugees = allowRefugees; }
    public long getGracePeriodEnd() { return gracePeriodEnd; }
    public boolean isInGracePeriod() { return System.currentTimeMillis() < gracePeriodEnd; }
}
