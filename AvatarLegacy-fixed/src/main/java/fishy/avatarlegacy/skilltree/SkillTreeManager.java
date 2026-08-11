package fishy.avatarlegacy.skilltree;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central manager for the AvatarLegacy Skill Tree system.
 *
 * ── Trees ─────────────────────────────────────────────────────────────────────
 *   water, earth, fire, air — one per bending element (includes all sub-elements)
 *   chi                     — Chi benders ONLY; Avatar CANNOT use this tree
 *   avatar                  — Avatar ONLY; contains every bending ability (no chi)
 *
 * ── Ability Detection ─────────────────────────────────────────────────────────
 *   PK has a two-level element hierarchy:
 *     Main elements: Water, Earth, Fire, Air, Chi, Avatar
 *     SubElements:   Blood/Healing/Ice/Plant (Water), Lava/Metal/Sand (Earth),
 *                    Lightning/Combustion/BlueFire (Fire), Flight/Spiritual (Avatar)
 *   resolveTreeKey() handles BOTH levels. A SubElement ability is placed in its
 *   PARENT element's tree (e.g. Bloodbending → water tree, Lightning → fire tree).
 *   AvatarAbilities go into the avatar tree only.
 *   MultiAbility is handled the same as any CoreAbility.
 *
 * ── Default moves ─────────────────────────────────────────────────────────────
 *   protected-default-moves in config.yml: 3 abilities per element/chi.
 *   Those nodes are is-default=true — granted on element selection, never lost
 *   on death, never revoked by this system.
 *
 * ── Avatar tree ───────────────────────────────────────────────────────────────
 *   grantAvatarDefaults() grants only is-default nodes when the Avatar is assigned.
 *   All other nodes unlock progressively via the GUI, same as element trees.
 *   Avatar tree nodes are NEVER removed on death.
 *   Chi abilities are NEVER added to the avatar tree.
 */
public class SkillTreeManager {

    public static final List<String> ALL_TREES =
            List.of("water", "earth", "fire", "air", "chi", "avatar");

    private final AvatarLegacy plugin;
    private FileConfiguration config;

    /** treeKey → (nodeId → SkillNode) */
    private final Map<String, Map<String, SkillNode>> trees = new LinkedHashMap<>();

    /** UUID → PlayerSkillData (in-memory cache) */
    private final Map<UUID, PlayerSkillData> cache = new ConcurrentHashMap<>();

    private int baseXp;
    private int basePtMin;
    private int baseKills;

    public SkillTreeManager(AvatarLegacy plugin) {
        this.plugin = plugin;
        initDatabase();
        loadConfig();
    }

    // ════════════════════════════════════════════════════════════
    //  DATABASE
    // ════════════════════════════════════════════════════════════

    private void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS player_skilltree (" +
                "uuid    TEXT NOT NULL, " +
                "tree    TEXT NOT NULL, " +
                "node_id TEXT NOT NULL, " +
                "PRIMARY KEY (uuid, tree, node_id))";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[SkillTree] DB init failed: " + e.getMessage());
        }
    }

    private Connection conn() {
        return plugin.getDatabaseManager().getConnection();
    }

    // ════════════════════════════════════════════════════════════
    //  CONFIG LOADING
    // ════════════════════════════════════════════════════════════

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "skilltree-config.yml");
        if (!file.exists()) plugin.saveResource("skilltree-config.yml", false);
        config = YamlConfiguration.loadConfiguration(file);

        baseXp    = config.getInt("leveling.base-xp",               100);
        basePtMin = config.getInt("leveling.base-playtime-minutes",   60);
        baseKills = config.getInt("leveling.base-kills",               5);

        trees.clear();
        for (String key : ALL_TREES) trees.put(key, new LinkedHashMap<>());

        // Load explicitly configured nodes
        ConfigurationSection treeSec = config.getConfigurationSection("trees");
        if (treeSec != null) {
            for (String treeKey : treeSec.getKeys(false)) {
                String norm = treeKey.toLowerCase();
                if (!ALL_TREES.contains(norm)) {
                    plugin.getLogger().warning("[SkillTree] Unknown tree key: " + treeKey);
                    continue;
                }
                ConfigurationSection nodes = treeSec.getConfigurationSection(treeKey + ".nodes");
                if (nodes != null) {
                    for (String nodeId : nodes.getKeys(false)) {
                        ConfigurationSection ns = nodes.getConfigurationSection(nodeId);
                        if (ns != null) trees.get(norm).put(nodeId, parseNode(nodeId, ns, norm));
                    }
                }
            }
        }

        markDefaultMoves();
        autoDiscoverMoves();
        syncConfigFile(file);

        int total = trees.values().stream().mapToInt(Map::size).sum();
        plugin.getLogger().info("[SkillTree] Loaded " + total + " nodes across " + trees.size() + " trees.");
        for (Map.Entry<String, Map<String, SkillNode>> e : trees.entrySet())
            plugin.getLogger().info("[SkillTree]   " + e.getKey() + " → " + e.getValue().size() + " nodes");
    }

    private void markDefaultMoves() {
        // NOTE: "avatar" is included here so it also gets a visual START node
        // (see below), even though it has no protected-default-moves list of
        // its own. Without this, SkillTreeGUI's
        // manager.getNode("avatar", "start") lookup returned null and the
        // avatar tree's viewport never centered on open.
        for (String treeKey : List.of("water", "earth", "fire", "air", "chi", "avatar")) {
            List<String> defaults = plugin.getConfig()
                    .getStringList("protected-default-moves." + treeKey);
            Map<String, SkillNode> tree = trees.get(treeKey);
            for (String abilityName : defaults) {
                if (abilityName == null || abilityName.isBlank()) continue;

                // Skip passives — grant the perm directly, don't create a visible node
                CoreAbility ability = CoreAbility.getAbility(abilityName);
                if (ability instanceof PassiveAbility) {
                    // Still grant the perm on element selection via grantDefaultMovesForElement,
                    // but we need a passive-flagged node so the perm gets granted.
                    // Create it as passive=true so the GUI skips it.
                    String nodeId = "passive_" + abilityName.toLowerCase().replaceAll("[^a-z0-9]", "_");
                    if (!tree.containsKey(nodeId)) {
                        SkillNode n = buildAutoNode(nodeId, abilityName, treeKey, 4, 4, true);
                        n.setPassive(true);
                        tree.put(nodeId, n);
                    }
                    continue;
                }

                SkillNode found = tree.values().stream()
                        .filter(n -> n.getAbilityName().equalsIgnoreCase(abilityName))
                        .findFirst().orElse(null);
                if (found != null) {
                    found.setDefault(true);
                } else {
                    String nodeId = "default_" + abilityName.toLowerCase().replaceAll("[^a-z0-9]", "_");
                    SkillNode n = buildAutoNode(nodeId, abilityName, treeKey, 4, 0, true);
                    tree.put(nodeId, n);
                }
            }

            // Migration: non-passive default abilities are granted directly and folded into
            // a single visual START node. Passive default nodes must NOT be removed here -
            // they still need to exist (flagged is-passive) so grantDefaultMovesForElement()
            // can find and grant them; the GUI already hides is-passive nodes from view.
            tree.entrySet().removeIf(entry -> !entry.getValue().isPassive()
                    && (entry.getValue().isDefault()
                    || entry.getKey().startsWith("default_") || entry.getKey().startsWith("passive_")));
            SkillNode start = buildAutoNode("start", "START", treeKey, 4, 0, true);
            start.setDisplayName("§6§lSTART");
            start.setLore(List.of("§7Your configured default moves are granted on element choice."));
            tree.put("start", start);
        }
    }

    /**
     * Auto-discovers every enabled PK CoreAbility (including passives) and fills gaps.
     *
     * Tree routing:
     *   - Chi abilities         → chi tree only (NEVER avatar tree)
     *   - Avatar abilities      → avatar tree only (Element.AVATAR subelements: Flight, Spiritual)
     *   - All other abilities   → their element/sub-element parent tree AND avatar tree
     *
     * SubElement parent resolution:
     *   Blood/Healing/Ice/Plant → water
     *   Lava/Metal/Sand         → earth
     *   Lightning/Combustion/BlueFire → fire
     *   (Air has no canon sub-elements in vanilla PK)
     *   Any unrecognised sub-element → falls back to its parent element name
     */
    private void autoDiscoverMoves() {
        if (!plugin.getProjectKorraIntegration().isEnabled()) return;
        Collection<CoreAbility> all = CoreAbility.getAbilities();
        if (all == null || all.isEmpty()) {
            plugin.getLogger().warning("[SkillTree] No PK abilities found during auto-discovery!");
            return;
        }

        int discovered = 0;
        for (CoreAbility ability : all) {
            if (!ability.isEnabled()) continue;
            if (ability.isHiddenAbility()) continue;
            if (ability.getName() == null || ability.getName().isBlank()) continue;

            String treeKey;
            try {
                treeKey = resolveTreeKey(ability);
            } catch (Throwable t) {
                // A third-party addon ability (e.g. ProjectAddons' SoundAbility) can throw
                // if its owning plugin failed/hasn't finished initializing. Skip that single
                // ability rather than aborting discovery (and taking down onEnable) entirely.
                plugin.getLogger().warning("[SkillTree] Skipped ability '" + ability.getName()
                        + "' during auto-discovery: " + t.getClass().getSimpleName()
                        + " (" + t.getMessage() + ")");
                continue;
            }
            if (treeKey == null) continue;

            boolean added = addAutoNodeIfMissing(treeKey, ability);
            if (added) discovered++;

            // Avatar tree is exclusive to abilities PK registers under the Avatar element.
        }
        if (discovered > 0)
            plugin.getLogger().info("[SkillTree] Auto-discovered " + discovered + " new ability nodes.");
    }

    /**
     * Writes any in-memory node that is not already present in skilltree-config.yml
     * into the file, without modifying existing entries.
     *
     * Layout strategy for new nodes:
     *   - Defaults (is-default=true) are placed in row y=0, spread across x=1,4,7.
     *   - Other nodes fill rows left-to-right (x: 0–8), moving down a row every 9.
     *     Row 0 is reserved for defaults; auto nodes start at y=2.
     *
     * This method is safe to call on every startup: it is a no-op for nodes whose
     * path already exists in the file.
     */
    private void syncConfigFile(File file) {
        // Re-read the raw YAML so we know exactly which paths are already persisted.
        // (The in-memory `config` object may have been modified by saveResource.)
        YamlConfiguration persisted = YamlConfiguration.loadConfiguration(file);

        boolean dirty = false;

        // Remove legacy visible default-move nodes from old generated configurations.
        for (String treeKey : List.of("water", "earth", "fire", "air", "chi", "avatar")) {
            ConfigurationSection oldNodes = persisted.getConfigurationSection("trees." + treeKey + ".nodes");
            if (oldNodes == null) continue;
            for (String nodeId : new ArrayList<>(oldNodes.getKeys(false))) {
                if (nodeId.startsWith("default_")) {
                    persisted.set("trees." + treeKey + ".nodes." + nodeId, null);
                    dirty = true;
                }
            }
        }

        for (String treeKey : ALL_TREES) {
            Map<String, SkillNode> tree = trees.get(treeKey);
            if (tree == null || tree.isEmpty()) continue;

            // Track occupied positions per tree so auto-layout doesn't collide.
            Set<String> occupied = collectOccupiedPositions(persisted, treeKey);

            // Cursor for auto-placed nodes (start below default row).
            int[] cursor = nextFreePosition(occupied, 0, 2);

            for (Map.Entry<String, SkillNode> entry : tree.entrySet()) {
                String nodeId = entry.getKey();
                SkillNode node = entry.getValue();

                String basePath = "trees." + treeKey + ".nodes." + nodeId;
                if (persisted.contains(basePath)) continue; // already in file — skip

                // Determine position: use stored value if set by buildAutoNode,
                // otherwise use auto-layout cursor.
                int posX = node.getPosX();
                int posY = node.getPosY();
                boolean needsLayout = (posX == 4 && posY == 4); // sentinel from buildAutoNode

                if (needsLayout) {
                    // Place at cursor, then advance
                    if (node.isDefault()) {
                        // Defaults go in row 0 at next free x among 1,4,7
                        int[] defPos = nextFreeDefaultPosition(occupied);
                        posX = defPos[0];
                        posY = 0;
                    } else {
                        posX = cursor[0];
                        posY = cursor[1];
                        cursor = nextFreePosition(occupied, cursor[0], cursor[1]);
                    }
                }

                String posKey = posX + "," + posY;
                occupied.add(posKey);

                // Write node into persisted config
                persisted.set(basePath + ".ability",      node.getAbilityName());
                persisted.set(basePath + ".display-name", node.getDisplayName()
                        .replace("§", "&")); // store & codes
                List<String> lore = node.getLore().stream()
                        .map(l -> l.replace("§", "&"))
                        .collect(Collectors.toList());
                persisted.set(basePath + ".lore",             lore);
                persisted.set(basePath + ".position.x",       posX);
                persisted.set(basePath + ".position.y",       posY);
                persisted.set(basePath + ".color",            node.getColor());
                persisted.set(basePath + ".is-default",       node.isDefault());
                persisted.set(basePath + ".is-passive",       node.isPassive());
                persisted.set(basePath + ".depends-on",       node.getDependsOn());
                if (node.getSubelement() != null) {
                    persisted.set(basePath + ".subelement", node.getSubelement());
                }

                SkillRequirements req = node.getRequirements();
                if (req != null) {
                    persisted.set(basePath + ".requirements.level",            req.getLevel());
                    persisted.set(basePath + ".requirements.xp",               req.getXpOverride());
                    persisted.set(basePath + ".requirements.playtime-minutes", req.getPlaytimeMinOverride());
                    persisted.set(basePath + ".requirements.kills",            req.getKillsOverride());
                    persisted.set(basePath + ".requirements.prerequisite-nodes",
                            req.getPrerequisiteNodes());
                }

                dirty = true;
            }
        }

        if (dirty) {
            try {
                persisted.save(file);
                plugin.getLogger().info("[SkillTree] skilltree-config.yml updated with new auto-discovered nodes.");
            } catch (IOException e) {
                plugin.getLogger().severe("[SkillTree] Failed to save skilltree-config.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Collects all "x,y" position strings already used by persisted nodes in a tree.
     */
    private Set<String> collectOccupiedPositions(YamlConfiguration persisted, String treeKey) {
        Set<String> occupied = new HashSet<>();
        ConfigurationSection nodes = persisted.getConfigurationSection("trees." + treeKey + ".nodes");
        if (nodes == null) return occupied;
        for (String nodeId : nodes.getKeys(false)) {
            int x = nodes.getInt(nodeId + ".position.x", -1);
            int y = nodes.getInt(nodeId + ".position.y", -1);
            if (x >= 0 && y >= 0) occupied.add(x + "," + y);
        }
        return occupied;
    }

    /**
     * Returns the next unoccupied [x, y] position in a left-to-right, top-to-bottom
     * scan starting from (startX, startY). Rows advance by 2 to leave visual
     * breathing room between tiers. x wraps at 9 (slots 0–8).
     */
    private int[] nextFreePosition(Set<String> occupied, int startX, int startY) {
        int x = startX;
        int y = startY;
        while (occupied.contains(x + "," + y)) {
            x++;
            if (x > 8) {
                x = 0;
                y += 2;
            }
        }
        // Advance cursor past the slot we just claimed
        int nextX = x + 1;
        int nextY = y;
        if (nextX > 8) { nextX = 0; nextY += 2; }
        occupied.add(x + "," + y);
        return new int[]{nextX, nextY};
    }

    /**
     * Returns the next default-row slot among x=1,4,7 (y=0). Falls back to
     * x=0..8 at y=0 if all three preferred slots are taken.
     */
    private int[] nextFreeDefaultPosition(Set<String> occupied) {
        for (int x : new int[]{1, 4, 7}) {
            if (!occupied.contains(x + ",0")) return new int[]{x, 0};
        }
        for (int x = 0; x <= 8; x++) {
            if (!occupied.contains(x + ",0")) return new int[]{x, 0};
        }
        return new int[]{4, 0}; // absolute fallback
    }

    /**
     * Resolves which tree a CoreAbility belongs to.
     *
     * PK element hierarchy handled:
     *   - If getElement() == null → skip
     *   - If element is a SubElement → use getParentElement().getName().toLowerCase()
     *   - If element is a main Element → use element.getName().toLowerCase()
     *   - "avatar" element (Flight, Spiritual, AvatarState) → "avatar" tree
     *   - "chi" element → "chi" tree
     *   - unrecognised → null (ability is skipped)
     */
    private String resolveTreeKey(CoreAbility ability) {
        Element el = ability.getElement();
        if (el == null) return null;

        String elName;
        if (el instanceof Element.SubElement sub) {
            // Sub-element: use parent element name
            Element parent = sub.getParentElement();
            elName = parent != null ? parent.getName().toLowerCase() : el.getName().toLowerCase();
        } else {
            elName = el.getName().toLowerCase();
        }

        return switch (elName) {
            case "water"  -> "water";
            case "earth"  -> "earth";
            case "fire"   -> "fire";
            case "air"    -> "air";
            case "chi"    -> "chi";
            case "avatar" -> "avatar";
            default       -> null;
        };
    }

    /**
     * Returns true if this ability is a Chi-element ability (used by the bending hook
     * to block Avatar players from using chi moves).
     */
    public boolean isChiAbility(CoreAbility ability) {
        if (ability == null || ability.getElement() == null) return false;
        Element el = ability.getElement();
        String name = (el instanceof Element.SubElement sub)
                ? sub.getParentElement().getName()
                : el.getName();
        return "chi".equalsIgnoreCase(name);
    }

    /** Returns true if this ability (by name) is a Chi ability. */
    public boolean isChiAbilityByName(String abilityName) {
        if (abilityName == null) return false;
        CoreAbility ability = CoreAbility.getAbility(abilityName);
        return ability != null && isChiAbility(ability);
    }

    /**
     * Adds an auto node for the given ability to the given tree IF no node already
     * covers that ability name. Returns true if a new node was added.
     */
    private boolean addAutoNodeIfMissing(String treeKey, CoreAbility ability) {
        Map<String, SkillNode> tree = trees.computeIfAbsent(treeKey, k -> new LinkedHashMap<>());
        boolean exists = tree.values().stream()
                .anyMatch(n -> n.getAbilityName().equalsIgnoreCase(ability.getName()));
        if (exists) return false;

        String autoId = "auto_" + ability.getName().toLowerCase().replaceAll("[^a-z0-9]", "_");
        // Avoid node ID collisions across trees by appending tree prefix if needed
        if (tree.containsKey(autoId)) autoId = treeKey + "_" + autoId;

        // posX/posY sentinel (4,4) signals to syncConfigFile() that layout is needed
        SkillNode n = buildAutoNode(autoId, ability.getName(), treeKey, 4, 4, false);
        tree.put(autoId, n);
        return true;
    }

    private SkillNode buildAutoNode(String nodeId, String abilityName, String treeKey,
                                    int posX, int posY, boolean isDefault) {
        SkillNode n = new SkillNode();
        n.setNodeId(nodeId);
        n.setAbilityName(abilityName);
        n.setDisplayName(elementColor(treeKey) + abilityName);
        n.setLore(List.of("§7Configure position in §eskilltree-config.yml§7."));
        n.setPosX(posX);
        n.setPosY(posY);
        CoreAbility ability = CoreAbility.getAbility(abilityName);
        if (ability instanceof PassiveAbility) n.setColor("LIGHT_BLUE");
        else if (ability != null && ability.getElement() instanceof Element.SubElement) n.setColor("PURPLE");
        else if (abilityName.toLowerCase().contains("combo")) n.setColor("GOLD");
        else n.setColor("GRAY");
        // Tag which sub-element (if any) this move belongs to, so the GUI can
        // display it and canUnlock() can gate players who don't have it.
        if (ability != null && ability.getElement() instanceof Element.SubElement sub) {
            n.setSubelement(sub.getName());
        }
        n.setDefault(isDefault);
        n.setPassive(ability instanceof PassiveAbility);
        n.setDependsOn(Collections.emptyList());
        SkillRequirements req = new SkillRequirements();
        req.setLevel(0);
        n.setRequirements(req);
        return n;
    }

    private SkillNode parseNode(String nodeId, ConfigurationSection ns, String treeKey) {
        SkillNode n = new SkillNode();
        n.setNodeId(nodeId);
        n.setAbilityName(ns.getString("ability", ""));
        n.setDisplayName(color(ns.getString("display-name", nodeId)));
        n.setLore(ns.getStringList("lore").stream().map(this::color).collect(Collectors.toList()));
        n.setPosX(ns.getInt("position.x", 4));
        n.setPosY(ns.getInt("position.y", 4));
        n.setColor(ns.getString("color", "GRAY"));
        n.setDefault(ns.getBoolean("is-default", false));
        n.setPassive(ns.getBoolean("is-passive", false));
        n.setDependsOn(ns.getStringList("depends-on"));
        n.setSubelement(ns.getString("subelement", null));

        SkillRequirements req = new SkillRequirements();
        ConfigurationSection rs = ns.getConfigurationSection("requirements");
        if (rs != null) {
            req.setLevel(rs.getInt("level", 0));
            req.setXpOverride(rs.getInt("xp", 0));
            req.setPlaytimeMinOverride(rs.getInt("playtime-minutes", 0));
            req.setKillsOverride(rs.getInt("kills", 0));
            req.setPrerequisiteNodes(rs.getStringList("prerequisite-nodes"));
        }
        n.setRequirements(req);
        return n;
    }

    // ════════════════════════════════════════════════════════════
    //  PLAYER DATA
    // ════════════════════════════════════════════════════════════

    public PlayerSkillData loadPlayerData(UUID uuid) {
        PlayerSkillData cached = cache.get(uuid);
        if (cached != null) return cached;

        PlayerSkillData data = new PlayerSkillData(uuid);
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT tree, node_id FROM player_skilltree WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) data.unlockNode(rs.getString("tree"), rs.getString("node_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("[SkillTree] Load failed for " + uuid + ": " + e.getMessage());
        }
        cache.put(uuid, data);
        return data;
    }

    public void savePlayerData(PlayerSkillData data) {
        String uuid = data.getPlayerUUID().toString();
        try {
            try (PreparedStatement del = conn().prepareStatement(
                    "DELETE FROM player_skilltree WHERE uuid = ?")) {
                del.setString(1, uuid);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn().prepareStatement(
                    "INSERT INTO player_skilltree (uuid, tree, node_id) VALUES (?, ?, ?)")) {
                for (String treeKey : ALL_TREES) {
                    for (String nodeId : data.getUnlockedNodes(treeKey)) {
                        ins.setString(1, uuid);
                        ins.setString(2, treeKey);
                        ins.setString(3, nodeId);
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[SkillTree] Save failed for " + uuid + ": " + e.getMessage());
        }
    }

    public void unloadPlayerData(UUID uuid) {
        PlayerSkillData data = cache.remove(uuid);
        if (data != null) savePlayerData(data);
    }

    public void saveAllPlayerData() {
        for (PlayerSkillData data : cache.values()) savePlayerData(data);
    }

    // ════════════════════════════════════════════════════════════
    //  DEFAULT / AVATAR GRANTS
    // ════════════════════════════════════════════════════════════

    /**
     * Called by ElementChooseListener when a player picks their element.
     * Grants all is-default nodes in that tree via LuckPerms + DB.
     */
    public void grantDefaultMovesForElement(Player player, String treeKey) {
        treeKey = treeKey.toLowerCase();
        PlayerSkillData data = loadPlayerData(player.getUniqueId());
        for (SkillNode node : getTree(treeKey).values()) {
            if (!node.isDefault()) continue;
            data.unlockNode(treeKey, node.getNodeId());
            grantPerm(player.getUniqueId(), node.getAbilityName());
        }
        savePlayerData(data);
    }

    /**
     * Called when a player becomes the Avatar.
     * Grants only is-default nodes in the avatar tree.
     * All other avatar tree nodes unlock progressively via /skilltree avatar.
     */
    public void grantAvatarDefaults(Player player) {
        PlayerSkillData data = loadPlayerData(player.getUniqueId());
        for (SkillNode node : getTree("avatar").values()) {
            if (!node.isDefault()) continue;
            data.unlockNode("avatar", node.getNodeId());
            grantPerm(player.getUniqueId(), node.getAbilityName());
        }
        savePlayerData(data);
    }

    /** @deprecated Redirects to grantAvatarDefaults(). Kept for binary compatibility. */
    @Deprecated
    public void grantAvatarTree(Player player) { grantAvatarDefaults(player); }

    /**
     * Revoke ALL unlocked avatar tree permissions. Called when Avatar title is lost.
     */
    public void revokeAvatarTree(Player player) {
        PlayerSkillData data = loadPlayerData(player.getUniqueId());
        for (SkillNode node : getTree("avatar").values()) {
            if (data.isNodeUnlocked("avatar", node.getNodeId()))
                revokePerm(player.getUniqueId(), node.getAbilityName());
        }
        data.resetTree("avatar");
        savePlayerData(data);
    }

    // ════════════════════════════════════════════════════════════
    //  LEVEL / REQUIREMENTS
    // ════════════════════════════════════════════════════════════

    /** floor(log(xp / base-xp) / log(1.25)), minimum 0. */
    public int computeLevel(int customXp) {
        if (customXp <= 0 || baseXp <= 0) return 0;
        if (customXp < baseXp) return 0;
        return Math.max(0, (int) (Math.log((double) customXp / baseXp) / Math.log(1.25)));
    }

    public boolean canUnlock(Player player, String treeKey, String nodeId) {
        SkillNode node = getNode(treeKey, nodeId);
        if (node == null || node.isDefault()) return false;

        // Avatar cannot unlock or use chi abilities in any way
        if (treeKey.equals("chi") && plugin.getAvatarManager().isAvatar(player.getUniqueId()))
            return false;

        SkillNode n = getNode(treeKey, nodeId);
        if (n != null && isChiAbilityByName(n.getAbilityName())
                && plugin.getAvatarManager().isAvatar(player.getUniqueId()))
            return false;


        PlayerSkillData data = loadPlayerData(player.getUniqueId());
        if (data.isNodeUnlocked(treeKey, nodeId)) return false;

        // Sub-element-gated moves: the player must actually have the
        // sub-element (via rolled/granted trait, tracked live on their
        // ProjectKorra BendingPlayer) or the matching permission override.
        // Without it, the move can never be unlocked no matter how many
        // requirements are otherwise met.
        if (node.getSubelement() != null && !hasSubelementAccess(player, node.getSubelement()))
            return false;

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (pd == null) return false;

        SkillRequirements req = node.getRequirements();
        if (computeLevel(pd.getCustomXP()) < req.getLevel()) return false;

        int reqXp = req.getEffectiveXp(baseXp);
        if (reqXp > 0 && pd.getCustomXP() < reqXp) return false;

        int reqPt = req.getEffectivePlaytimeMin(basePtMin);
        if (reqPt > 0 && pd.getPlaytimeSeconds() / 60 < reqPt) return false;

        int reqKl = req.getEffectiveKills(baseKills);
        if (reqKl > 0 && plugin.getStatsManager().getKillCount(player.getUniqueId()) < reqKl) return false;

        for (String dep : node.getDependsOn()) {
            SkillNode depNode = getNode(treeKey, dep);
            if (!(depNode != null && depNode.isDefault()) && !data.isNodeUnlocked(treeKey, dep))
                return false;
        }
        for (String dep : req.getPrerequisiteNodes()) {
            SkillNode depNode = getNode(treeKey, dep);
            if (!(depNode != null && depNode.isDefault()) && !data.isNodeUnlocked(treeKey, dep))
                return false;
        }
        return true;
    }

    public List<String> getUnmetRequirements(Player player, String treeKey, String nodeId) {
        List<String> unmet = new ArrayList<>();
        SkillNode node = getNode(treeKey, nodeId);
        if (node == null) return unmet;

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (pd == null) return unmet;

        SkillRequirements req  = node.getRequirements();
        PlayerSkillData   data = loadPlayerData(player.getUniqueId());

        if (node.getSubelement() != null && !hasSubelementAccess(player, node.getSubelement())) {
            unmet.add("§c✗ §fRequires the §d" + node.getSubelement() + " §fsub-element (you don't have it)");
        }

        int lv = computeLevel(pd.getCustomXP());
        if (lv < req.getLevel())
            unmet.add("§c✗ §fLevel §e" + lv + " §7/ §e" + req.getLevel());

        int reqXp = req.getEffectiveXp(baseXp);
        if (reqXp > 0 && pd.getCustomXP() < reqXp)
            unmet.add("§c✗ §fXP §e" + pd.getCustomXP() + " §7/ §e" + reqXp);

        int reqPt = req.getEffectivePlaytimeMin(basePtMin);
        if (reqPt > 0 && pd.getPlaytimeSeconds() / 60 < reqPt)
            unmet.add("§c✗ §fPlaytime §e" + (pd.getPlaytimeSeconds() / 60) + "m §7/ §e" + reqPt + "m");

        int reqKl = req.getEffectiveKills(baseKills);
        if (reqKl > 0) {
            int pk = plugin.getStatsManager().getKillCount(player.getUniqueId());
            if (pk < reqKl) unmet.add("§c✗ §fKills §e" + pk + " §7/ §e" + reqKl);
        }

        for (String dep : node.getDependsOn()) {
            SkillNode depNode = getNode(treeKey, dep);
            boolean ok = (depNode != null && depNode.isDefault()) || data.isNodeUnlocked(treeKey, dep);
            if (!ok) unmet.add("§c✗ §fRequires: " + (depNode != null ? depNode.getDisplayName() : dep));
        }
        return unmet;
    }

    // ════════════════════════════════════════════════════════════
    //  UNLOCK / LOCK
    // ════════════════════════════════════════════════════════════

    public void unlockNode(Player player, String treeKey, String nodeId) {
        SkillNode node = getNode(treeKey, nodeId);
        if (node == null) return;

        PlayerSkillData data = loadPlayerData(player.getUniqueId());
        data.unlockNode(treeKey, nodeId);
        savePlayerData(data);
        grantPerm(player.getUniqueId(), node.getAbilityName());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                player.spawnParticle(Particle.FIREWORK,
                        player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            } catch (Exception ignored) {}
            String msg = color(config.getString("messages.unlock-message",
                    "&a✓ &fYou have unlocked &a{move}&f!"))
                    .replace("{move}", node.getDisplayName());
            player.sendMessage(msg);
        });
    }

    public void lockNode(Player player, String treeKey, String nodeId) {
        SkillNode node = getNode(treeKey, nodeId);
        if (node == null) return;
        PlayerSkillData data = loadPlayerData(player.getUniqueId());
        data.lockNode(treeKey, nodeId);
        savePlayerData(data);
        revokePerm(player.getUniqueId(), node.getAbilityName());
        plugin.getProjectKorraIntegration().removeAbilityFromSlot(player, node.getAbilityName());
        if (plugin.getBendingHook() != null)
            plugin.getBendingHook().invalidateCache(player.getUniqueId()); // ← ADD THIS
    }

    public void lockNodeByUUID(UUID uuid, String treeKey, String nodeId) {
        SkillNode node = getNode(treeKey, nodeId);
        if (node == null) return;
        PlayerSkillData data = loadPlayerData(uuid);
        data.lockNode(treeKey, nodeId);
        savePlayerData(data);
        revokePerm(uuid, node.getAbilityName());
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            plugin.getProjectKorraIntegration().removeAbilityFromSlot(p, node.getAbilityName());
            if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  DEATH
    // ════════════════════════════════════════════════════════════

    public SkillNode removeRandomMoveOnDeath(Player player) {
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (pd == null || pd.getElement() == null) return null;

        String treeKey = pd.getElement().toLowerCase();
        if (treeKey.equals("avatar")) return null; // avatar tree never touched on death

        PlayerSkillData data = loadPlayerData(player.getUniqueId());
        Map<String, SkillNode> tree = getTree(treeKey);

        List<String> candidates = new ArrayList<>();
        for (String nodeId : data.getUnlockedNodes(treeKey)) {
            SkillNode node = tree.get(nodeId);
            if (node == null || node.isDefault()) continue;
            if (plugin.getProtectedMovesManager()
                    .isProtectedMove(player.getUniqueId(), node.getAbilityName())) continue;
            candidates.add(nodeId);
        }

        if (candidates.isEmpty()) return null;
        String chosen = candidates.get(new Random().nextInt(candidates.size()));
        SkillNode removed = tree.get(chosen);
        lockNode(player, treeKey, chosen);
        return removed;
    }

    // ════════════════════════════════════════════════════════════
    //  PERMISSIONS
    // ════════════════════════════════════════════════════════════

    private void grantPerm(UUID uuid, String abilityName) {
        if (abilityName == null || abilityName.isBlank()) return;
        plugin.getLuckPermsIntegration()
                .grantPermission(uuid, "bending.ability." + abilityName.toLowerCase());
    }

    /**
     * True if the player is allowed to unlock/use moves belonging to the
     * given sub-element — either because they actually hold that
     * sub-element as a trait (rolled on element choice, or granted via a
     * subelement item — both tracked live on their ProjectKorra
     * BendingPlayer), OR because they hold the explicit
     * {@code avatarlegacy.subelement.<name>} permission override (e.g.
     * granted manually by staff).
     */
    public boolean hasSubelementAccess(Player player, String subelementName) {
        if (subelementName == null) return true; // not a sub-element-gated move
        if (player == null) return false;
        if (plugin.getProjectKorraIntegration().hasSubElement(player, subelementName)) return true;
        return player.hasPermission("avatarlegacy.subelement." + subelementName.toLowerCase());
    }

    private void revokePerm(UUID uuid, String abilityName) {
        if (abilityName == null || abilityName.isBlank()) return;
        plugin.getLuckPermsIntegration()
                .revokePermission(uuid, "bending.ability." + abilityName.toLowerCase());
    }

    // ════════════════════════════════════════════════════════════
    //  QUERIES
    // ════════════════════════════════════════════════════════════

    public Map<String, SkillNode> getTree(String treeKey) {
        return trees.getOrDefault(treeKey.toLowerCase(), Collections.emptyMap());
    }

    public SkillNode getNode(String treeKey, String nodeId) {
        return getTree(treeKey).get(nodeId);
    }

    public SkillNode getNodeByAbility(String treeKey, String abilityName) {
        if (abilityName == null) return null;
        for (SkillNode n : getTree(treeKey).values()) {
            if (n.getAbilityName().equalsIgnoreCase(abilityName)) return n;
        }
        return null;
    }

    public FileConfiguration getConfig() { return config; }
    public int getBaseXp()               { return baseXp; }
    public int getBasePtMin()            { return basePtMin; }
    public int getBaseKills()            { return baseKills; }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════

    private String color(String s) { return s == null ? "" : s.replace("&", "§"); }

    private String elementColor(String treeKey) {
        return switch (treeKey) {
            case "water"  -> "§b";
            case "earth"  -> "§a";
            case "fire"   -> "§c";
            case "air"    -> "§f";
            case "chi"    -> "§6";
            case "avatar" -> "§e";
            default       -> "§7";
        };
    }
}