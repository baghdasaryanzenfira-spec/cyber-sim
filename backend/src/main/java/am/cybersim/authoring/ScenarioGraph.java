package am.cybersim.authoring;

import am.cybersim.scenario.ScenarioEnums.ActionOutcome;
import am.cybersim.scenario.dto.ScenarioDefinition;
import am.cybersim.scenario.dto.ScenarioDefinition.ActionDef;
import am.cybersim.scenario.dto.ScenarioDefinition.EventDef;
import am.cybersim.scenario.dto.ScenarioDefinition.ResourceDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Dependency graph of a scenario: actions, log/alert events and cloud resources as nodes; prerequisites,
 * evidence disclosure and resource references as edges.
 *
 * <pre>
 *   START  --INITIAL------&gt; initially visible event
 *   action --PREREQUISITE-&gt; action that requires it
 *   action --REVEALS------&gt; event it discloses
 *   action --TARGETS------&gt; resource it acts on (EFFECT if it changes the resource status)
 *   event  --ABOUT--------&gt; resource it describes
 * </pre>
 *
 * The same structure is returned to the admin UI (graph view) and used by {@link ScenarioAnalyzer} and the
 * quality scorer, so what the admin sees is exactly what is validated.
 */
public record ScenarioGraph(List<Node> nodes, List<Edge> edges) {

    public enum NodeType { START, ACTION, EVENT, RESOURCE }

    public enum EdgeType { INITIAL, PREREQUISITE, REVEALS, TARGETS, EFFECT, ABOUT }

    /** @param attributes display/analysis hints (phase, outcome, category, evidence, severity ...) */
    public record Node(String id, NodeType type, String key, String label, Map<String, Object> attributes) {
    }

    public record Edge(String from, String to, EdgeType type) {
    }

    public static final String START_ID = "start";

    public static String actionId(String key) {
        return "action:" + key;
    }

    public static String eventId(String key) {
        return "event:" + key;
    }

    public static String resourceId(String key) {
        return "resource:" + key;
    }

    public static ScenarioGraph build(ScenarioDefinition def) {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        nodes.add(new Node(START_ID, NodeType.START, "start", "Incident start", Map.of()));

        for (ResourceDef r : def.resources()) {
            nodes.add(new Node(resourceId(r.key()), NodeType.RESOURCE, r.key(), r.name(),
                    Map.of("resourceType", r.type().name(), "status", r.status())));
        }
        for (EventDef e : def.events()) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("eventType", e.type().name());
            attrs.put("severity", e.severity().name());
            attrs.put("evidence", e.evidence());
            attrs.put("offsetSeconds", e.offsetSeconds());
            nodes.add(new Node(eventId(e.key()), NodeType.EVENT, e.key(), abbreviate(e.message()), attrs));
            if (blank(e.revealedByActionKey())) {
                edges.add(new Edge(START_ID, eventId(e.key()), EdgeType.INITIAL));
            } else {
                edges.add(new Edge(actionId(e.revealedByActionKey()), eventId(e.key()), EdgeType.REVEALS));
            }
            if (!blank(e.resourceKey())) {
                edges.add(new Edge(eventId(e.key()), resourceId(e.resourceKey()), EdgeType.ABOUT));
            }
        }
        for (ActionDef a : def.actions()) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("phase", a.phase().name());
            attrs.put("category", a.category().name());
            attrs.put("outcome", a.outcome().name());
            attrs.put("points", a.points());
            nodes.add(new Node(actionId(a.key()), NodeType.ACTION, a.key(), a.label(), attrs));
            if (!blank(a.prerequisiteActionKey())) {
                edges.add(new Edge(actionId(a.prerequisiteActionKey()), actionId(a.key()), EdgeType.PREREQUISITE));
            }
            if (!blank(a.targetResourceKey())) {
                edges.add(new Edge(actionId(a.key()), resourceId(a.targetResourceKey()),
                        blank(a.effectStatus()) ? EdgeType.TARGETS : EdgeType.EFFECT));
            }
        }
        return new ScenarioGraph(nodes, edges);
    }

    // ---------------------------------------------------------------- analysis helpers (on the definition)

    /** Keys of actions that can be performed at all: every prerequisite chain ends at an action without one. */
    public static Set<String> performableActions(ScenarioDefinition def) {
        Map<String, ActionDef> byKey = actionsByKey(def);
        Set<String> result = new LinkedHashSet<>();
        for (ActionDef a : def.actions()) {
            Set<String> seen = new HashSet<>();
            ActionDef current = a;
            boolean ok = true;
            while (current != null && !blank(current.prerequisiteActionKey())) {
                if (!seen.add(current.key())) {
                    ok = false;
                    break;
                }
                current = byKey.get(current.prerequisiteActionKey());
                if (current == null) {
                    ok = false;
                }
            }
            if (ok) {
                result.add(a.key());
            }
        }
        return result;
    }

    /** Longest prerequisite chain (number of actions); 0 for an empty scenario. */
    public static int maxDepth(ScenarioDefinition def) {
        Map<String, ActionDef> byKey = actionsByKey(def);
        int max = 0;
        for (ActionDef a : def.actions()) {
            int depth = 1;
            Set<String> seen = new HashSet<>();
            ActionDef current = a;
            while (current != null && !blank(current.prerequisiteActionKey()) && seen.add(current.key())) {
                current = byKey.get(current.prerequisiteActionKey());
                depth++;
            }
            max = Math.max(max, depth);
        }
        return max;
    }

    /**
     * Orders the selected actions so that every selected prerequisite comes first, INVESTIGATION actions before
     * RESPONSE actions where the prerequisites allow it. Actions caught in a prerequisite cycle are left out.
     * Used by the test runner to build the "correct path".
     */
    public static List<ActionDef> topologicalOrder(ScenarioDefinition def, Predicate<ActionDef> filter) {
        List<ActionDef> queue = new ArrayList<>(def.actions().stream().filter(filter).toList());
        queue.sort((x, y) -> x.phase().compareTo(y.phase()));
        Set<String> selectedKeys = new HashSet<>();
        queue.forEach(a -> selectedKeys.add(a.key()));
        List<ActionDef> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        boolean progress = true;
        while (!queue.isEmpty() && progress) {
            progress = false;
            for (ActionDef a : queue) {
                String pre = a.prerequisiteActionKey();
                if (blank(pre) || done.contains(pre) || !selectedKeys.contains(pre)) {
                    ordered.add(a);
                    done.add(a.key());
                    queue.remove(a);
                    progress = true;
                    break;
                }
            }
        }
        return ordered;
    }

    public static Map<String, ActionDef> actionsByKey(ScenarioDefinition def) {
        Map<String, ActionDef> map = new HashMap<>();
        def.actions().forEach(a -> map.putIfAbsent(a.key(), a));
        return map;
    }

    public static boolean isExpected(ActionDef a) {
        return a.outcome() == ActionOutcome.EXPECTED;
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String abbreviate(String text) {
        String single = text.replaceAll("\\s+", " ").strip();
        return single.length() <= 80 ? single : single.substring(0, 77) + "...";
    }
}
