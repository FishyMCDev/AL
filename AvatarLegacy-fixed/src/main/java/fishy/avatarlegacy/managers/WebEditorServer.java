package fishy.avatarlegacy.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;

/** Deliberately local-only editor for skilltree-config.yml. Never exposes the server on a network interface. */
public final class WebEditorServer {
    private final AvatarLegacy plugin;
    private final Gson gson = new Gson();
    private HttpServer server;
    public WebEditorServer(AvatarLegacy plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("web-editor.enabled", true)) return;
        try {
            int port = plugin.getConfig().getInt("web-editor.port", 67);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/", this::page);
            server.createContext("/api/nodes", this::nodes);
            server.createContext("/api/reload", this::reload);
            server.start();
            plugin.getLogger().info("Skill-tree editor available at http://localhost:" + port);
        } catch (Exception e) { plugin.getLogger().warning("Could not start local web editor: " + e.getMessage()); }
    }
    public void stop() { if (server != null) server.stop(0); }
    private File configFile() { return new File(plugin.getDataFolder(), "skilltree-config.yml"); }
    private void page(HttpExchange e) throws java.io.IOException {
        if (!"GET".equals(e.getRequestMethod())) { send(e, 405, "text/plain", "Method not allowed"); return; }
        try (InputStream in = plugin.getResource("webeditor/index.html")) {
            send(e, 200, "text/html; charset=utf-8", in == null ? "Editor resource missing." : new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    private void nodes(HttpExchange e) throws java.io.IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile());
        if ("GET".equals(e.getRequestMethod())) {
            String tree = query(e.getRequestURI().getQuery(), "tree");
            ArrayList<Map<String, Object>> out = new ArrayList<>();
            org.bukkit.configuration.ConfigurationSection trees = yaml.getConfigurationSection("trees");
            if (trees != null) for (String treeName : trees.getKeys(false)) {
                if (tree != null && !tree.equalsIgnoreCase(treeName)) continue;
                org.bukkit.configuration.ConfigurationSection nodes = yaml.getConfigurationSection("trees." + treeName + ".nodes");
                if (nodes == null) continue;
                for (String nodeId : nodes.getKeys(false)) {
                    org.bukkit.configuration.ConfigurationSection node = nodes.getConfigurationSection(nodeId);
                    if (node == null) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tree", treeName); item.put("nodeId", nodeId); item.put("ability", node.getString("ability", nodeId));
                    item.put("lore", node.getStringList("lore"));
                    item.put("xp", node.getInt("requirements.xp", -1)); item.put("playtime", node.getInt("requirements.playtime-minutes", -1)); item.put("kills", node.getInt("requirements.kills", -1));
                    item.put("dependencies", node.getStringList("requirements.prerequisite-nodes"));
                    item.put("x", node.getInt("position.x", 4)); item.put("y", node.getInt("position.y", 4));
                    item.put("isDefault", node.getBoolean("is-default", false));
                    String subelement = node.getString("subelement", null);
                    if (subelement != null) item.put("subelement", subelement);
                    out.add(item);
                }
            }
            send(e, 200, "application/json", gson.toJson(out)); return;
        }
        if ("POST".equals(e.getRequestMethod())) {
            JsonObject body = gson.fromJson(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
            String tree = body.get("tree").getAsString().toLowerCase();
            String nodeId = body.get("nodeId").getAsString().replaceAll("[^A-Za-z0-9_-]", "_");
            String base = "trees." + tree + ".nodes." + nodeId;
            if (yaml.contains(base)) { send(e, 409, "text/plain", "Node already exists"); return; }
            yaml.set(base + ".ability", body.get("ability").getAsString()); yaml.set(base + ".display-name", body.get("ability").getAsString());
            yaml.set(base + ".lore", java.util.List.of("&7Custom skill node.")); yaml.set(base + ".position.x", 4); yaml.set(base + ".position.y", 4);
            yaml.set(base + ".requirements.xp", 0); yaml.set(base + ".requirements.playtime-minutes", -1); yaml.set(base + ".requirements.kills", -1); yaml.save(configFile()); plugin.getSkillTreeManager().loadConfig(); send(e, 201, "application/json", "{\"ok\":true}"); return;
        }
        if (!"PUT".equals(e.getRequestMethod())) { send(e, 405, "text/plain", "Method not allowed"); return; }
        String[] path = e.getRequestURI().getPath().split("/");
        if (path.length < 5) { send(e, 400, "text/plain", "Use /api/nodes/{tree}/{nodeId}"); return; }
        JsonObject body = gson.fromJson(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
        String base = "trees." + path[3] + ".nodes." + path[4];
        if (body.has("lore")) yaml.set(base + ".lore", gson.fromJson(body.get("lore"), Object.class));
        if (body.has("xp")) yaml.set(base + ".requirements.xp", gson.fromJson(body.get("xp"), Object.class));
        if (body.has("playtime")) yaml.set(base + ".requirements.playtime-minutes", gson.fromJson(body.get("playtime"), Object.class));
        if (body.has("kills")) yaml.set(base + ".requirements.kills", gson.fromJson(body.get("kills"), Object.class));
        if (body.has("x")) yaml.set(base + ".position.x", body.get("x").getAsInt());
        if (body.has("y")) yaml.set(base + ".position.y", body.get("y").getAsInt());
        if (body.has("isDefault")) yaml.set(base + ".is-default", body.get("isDefault").getAsBoolean());
        if (body.has("dependencies")) yaml.set(base + ".requirements.prerequisite-nodes", gson.fromJson(body.get("dependencies"), Object.class));
        yaml.save(configFile()); plugin.getSkillTreeManager().loadConfig(); send(e, 200, "application/json", "{\"ok\":true}");
    }
    private void reload(HttpExchange e) throws java.io.IOException { if (!"POST".equals(e.getRequestMethod())) { send(e,405,"text/plain","Method not allowed"); return; } plugin.getSkillTreeManager().loadConfig(); send(e,200,"application/json","{\"ok\":true}"); }
    private String query(String raw, String key) { if (raw == null) return null; for (String pair : raw.split("&")) { String[] p = pair.split("=",2); if (p.length == 2 && key.equals(p[0])) return p[1]; } return null; }
    private void send(HttpExchange e, int code, String type, String body) throws java.io.IOException { byte[] data = body.getBytes(StandardCharsets.UTF_8); e.getResponseHeaders().set("Content-Type", type); e.sendResponseHeaders(code, data.length); e.getResponseBody().write(data); e.close(); }
}