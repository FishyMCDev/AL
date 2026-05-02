package fishy.avatarlegacy.models;

public class War {
    private int id;
    private int attackerNationId;
    private int defenderNationId;
    private long startTimestamp;
    private long endTimestamp;
    private String status;

    public War(int id, int attackerNationId, int defenderNationId) {
        this.id = id;
        this.attackerNationId = attackerNationId;
        this.defenderNationId = defenderNationId;
        this.startTimestamp = System.currentTimeMillis();
        this.endTimestamp = 0;
        this.status = "active";
    }

    public int getId() {
        return id;
    }

    public int getAttackerNationId() {
        return attackerNationId;
    }

    public int getDefenderNationId() {
        return defenderNationId;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    public void endWar(String endStatus) {
        this.status = endStatus;
        this.endTimestamp = System.currentTimeMillis();
    }
}