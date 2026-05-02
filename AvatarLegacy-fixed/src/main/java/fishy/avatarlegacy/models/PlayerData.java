package fishy.avatarlegacy.models;

import org.bukkit.Location;

import java.util.UUID;

public class PlayerData {
    private UUID uuid;
    private String username;
    private boolean tutorialCompleted;
    private String element;
    private long elementSelectionTimestamp;
    private boolean elementPermanent;
    private long playtimeSeconds;
    private int customXP;
    private long lastLogin;
    private boolean visitedFireTerritory;
    private boolean visitedWaterTerritory;
    private boolean visitedEarthTerritory;
    private boolean visitedAirTerritory;
    private long lastActivityTime;
    private Location bedSpawn;
    private boolean refugee;
    
    private boolean hasEverChosen;

    public PlayerData(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
        this.tutorialCompleted = false;
        this.element = null;
        this.elementSelectionTimestamp = 0;
        this.elementPermanent = false;
        this.playtimeSeconds = 0;
        this.customXP = 0;
        this.lastLogin = System.currentTimeMillis();
        this.visitedFireTerritory = false;
        this.visitedWaterTerritory = false;
        this.visitedEarthTerritory = false;
        this.visitedAirTerritory = false;
        this.lastActivityTime = System.currentTimeMillis();
        this.bedSpawn = null;
        this.refugee = false;
        this.hasEverChosen = false;
    }

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isTutorialCompleted() { return tutorialCompleted; }
    public void setTutorialCompleted(boolean tutorialCompleted) { this.tutorialCompleted = tutorialCompleted; }
    public String getElement() { return element; }
    public void setElement(String element) { this.element = element; }
    public long getElementSelectionTimestamp() { return elementSelectionTimestamp; }
    public void setElementSelectionTimestamp(long elementSelectionTimestamp) { this.elementSelectionTimestamp = elementSelectionTimestamp; }
    public boolean isElementPermanent() { return elementPermanent; }
    public void setElementPermanent(boolean elementPermanent) { this.elementPermanent = elementPermanent; }
    public long getPlaytimeSeconds() { return playtimeSeconds; }
    public void setPlaytimeSeconds(long playtimeSeconds) { this.playtimeSeconds = playtimeSeconds; }
    public void addPlaytimeSeconds(long seconds) { this.playtimeSeconds += seconds; }
    public int getCustomXP() { return customXP; }
    public void setCustomXP(int customXP) { this.customXP = customXP; }
    public void addCustomXP(int xp) { this.customXP += xp; }
    public long getLastLogin() { return lastLogin; }
    public void setLastLogin(long lastLogin) { this.lastLogin = lastLogin; }
    public boolean hasVisitedFireTerritory() { return visitedFireTerritory; }
    public void setVisitedFireTerritory(boolean visited) { this.visitedFireTerritory = visited; }
    public boolean hasVisitedWaterTerritory() { return visitedWaterTerritory; }
    public void setVisitedWaterTerritory(boolean visited) { this.visitedWaterTerritory = visited; }
    public boolean hasVisitedEarthTerritory() { return visitedEarthTerritory; }
    public void setVisitedEarthTerritory(boolean visited) { this.visitedEarthTerritory = visited; }
    public boolean hasVisitedAirTerritory() { return visitedAirTerritory; }
    public void setVisitedAirTerritory(boolean visited) { this.visitedAirTerritory = visited; }
    public long getLastActivityTime() { return lastActivityTime; }
    public void setLastActivityTime(long lastActivityTime) { this.lastActivityTime = lastActivityTime; }
    public void updateActivity() { this.lastActivityTime = System.currentTimeMillis(); }
    public boolean isAFK(long afkThresholdMillis) { return System.currentTimeMillis() - lastActivityTime > afkThresholdMillis; }
    public Location getBedSpawn() { return bedSpawn; }
    public void setBedSpawn(Location bedSpawn) { this.bedSpawn = bedSpawn; }
    public boolean isRefugee() { return refugee; }
    public void setRefugee(boolean refugee) { this.refugee = refugee; }
    public boolean hasEverChosen() { return hasEverChosen; }
    public void setHasEverChosen(boolean hasEverChosen) { this.hasEverChosen = hasEverChosen; }

    public int getTerritoriesVisited() {
        int count = 0;
        if (visitedFireTerritory) count++;
        if (visitedWaterTerritory) count++;
        if (visitedEarthTerritory) count++;
        if (visitedAirTerritory) count++;
        return count;
    }
}
