import java.util.*;

public class OutfitGenerator {

    // Minimum compatibility score to draw an edge between two items
    private static final double THRESHOLD = 60.0;

    // Maximum items per outfit
    private static final int MAX_OUTFIT_SIZE = 4;

    // Represents a weighted edge between two clothing items
    static class Edge {
        // The two clothing items at its score
        ClothingItem from;
        ClothingItem to;
        double score;

        // A constructor
        Edge(ClothingItem from, ClothingItem to, double score) {
            this.from = from;
            this.to = to;
            this.score = score;
        }
    }

    // Represents a complete outfit with a final score
    static class Outfit {
        List<ClothingItem> items;
        double score;

        Outfit(List<ClothingItem> items, double score) {
            this.items = items;
            this.score = score;
        }
    }

    // The scorer used to compute compatibility between items
    private final CompatibilityScorer scorer;

    // Constructor
    public OutfitGenerator(CompatibilityScorer scorer) {
        this.scorer = scorer;
    }

    // Takes a wardrobe and returns the top 3 outfits
    public List<Outfit> generateTopOutfits(List<ClothingItem> wardrobe) {
        // Step 1 - Build the graph as an adjacency list
        Map<ClothingItem, List<Edge>> graph = buildGraph(wardrobe);

        // Step 2 - Collect all edges and sort by score descending
        List<Edge> allEdges = getAllEdgesSorted(graph);

        // Step 3 - Greedily generate outfit candidates
        List<Outfit> candidates = new ArrayList<>();
        for (Edge startEdge : allEdges) {
            Outfit outfit = buildOutfitFromEdge(startEdge, graph);
            if (outfit != null) {
                candidates.add(outfit);
            }
        }

        // Step 4 - Deduplicate outfits
        List<Outfit> unique = deduplicateOutfits(candidates);

        // Step 5 - Sort by score descending and return top 3
        unique.sort((a, b) -> Double.compare(b.score, a.score));
        return unique.subList(0, Math.min(3, unique.size()));
    }

    // Helper functiont that builds the graph
    private Map<ClothingItem, List<Edge>> buildGraph(List<ClothingItem> wardrobe) {
        // Initialize adjacency list with empty list for each item
        Map<ClothingItem, List<Edge>> graph = new HashMap<>();
        for (ClothingItem item : wardrobe) {
            graph.put(item, new ArrayList<>());
        }

        // Loop through every unique pair of items
        for (int i = 0; i < wardrobe.size(); i++) {
            for (int j = i + 1; j < wardrobe.size(); j++) {
                ClothingItem a = wardrobe.get(i);
                ClothingItem b = wardrobe.get(j);

                // Compute compatibility score
                double score = scorer.score(a, b);

                // Only add edge if score is above threshold
                if (score >= THRESHOLD) {
                    // Add edge in both directions since graph is undirected
                    graph.get(a).add(new Edge(a, b, score));
                    graph.get(b).add(new Edge(b, a, score));
                }
            }
        }

        return graph;
    }

    // Helper function that collects and sorts the edges.
    private List<Edge> getAllEdgesSorted(Map<ClothingItem, List<Edge>> graph) {
        List<Edge> allEdges = new ArrayList<>();

        // Collect every edge from the adjacency list
        for (List<Edge> edges : graph.values()) {
            allEdges.addAll(edges);
        }

        // Sort edges from highest score to lowest (greedy selection order)
        allEdges.sort((a, b) -> Double.compare(b.score, a.score));

        return allEdges;
    }

    // Helper function to build and outfit from an edge.
    private Outfit buildOutfitFromEdge(Edge startEdge, Map<ClothingItem, List<Edge>> graph) {
        // Start the outfit with the two items in the starting edge
        List<ClothingItem> outfitItems = new ArrayList<>();
        outfitItems.add(startEdge.from);
        outfitItems.add(startEdge.to);

        // Track which types are already in the outfit
        Set<ClothingType> usedTypes = new HashSet<>();
        usedTypes.add(startEdge.from.getType());
        usedTypes.add(startEdge.to.getType());

        // Try to expand the outfit up to MAX_OUTFIT_SIZE
        while (outfitItems.size() < MAX_OUTFIT_SIZE) {
            ClothingItem bestCandidate = null;
            double bestCandidateScore = -1;

            // Look at all neighbors of items already in the outfit
            for (ClothingItem current : outfitItems) {
                List<Edge> neighbors = graph.get(current);
                if (neighbors == null) continue;

                for (Edge edge : neighbors) {
                    ClothingItem candidate = edge.to;

                    // Skip if already in outfit
                    if (outfitItems.contains(candidate)) continue;

                    // Skip if this type is already represented
                    if (candidate.getType() == null) continue;
                    if (usedTypes.contains(candidate.getType())) continue;

                    // Check if candidate is compatible with ALL items in outfit
                    boolean compatibleWithAll = true;
                    double totalScore = 0;
                    for (ClothingItem existing : outfitItems) {
                        double pairScore = scorer.score(candidate, existing);
                        if (pairScore < THRESHOLD) {
                            compatibleWithAll = false;
                            break;
                        }
                        totalScore += pairScore;
                    }

                    if (!compatibleWithAll) continue;

                    // Pick the candidate with the highest average score
                    double avgScore = totalScore / outfitItems.size();
                    if (avgScore > bestCandidateScore) {
                        bestCandidateScore = avgScore;
                        bestCandidate = candidate;
                    }
                }
            }

            // If no valid candidate was found stop expanding
            if (bestCandidate == null) break;

            // Add the best candidate to the outfit
            outfitItems.add(bestCandidate);
            usedTypes.add(bestCandidate.getType());
        }

        // Need at least 2 items to be a valid outfit
        if (outfitItems.size() < 2) return null;

        // Score the outfit by averaging all pairwise scores
        double outfitScore = computeOutfitScore(outfitItems);
        return new Outfit(outfitItems, outfitScore);
    }

    // Helper function to deduplicate outfits
    private List<Outfit> deduplicateOutfits(List<Outfit> candidates) {
        List<Outfit> unique = new ArrayList<>();

        for (Outfit candidate : candidates) {
            boolean isDuplicate = false;

            // Compare against all outfits already in unique list
            for (Outfit existing : unique) {
                if (sameItems(candidate.items, existing.items)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                unique.add(candidate);
            }
        }

        return unique;
    }

    // Checks if two outfits contain exactly the same items regardless of order
    private boolean sameItems(List<ClothingItem> a, List<ClothingItem> b) {
        if (a.size() != b.size()) return false;
        Set<String> idsA = new HashSet<>();
        Set<String> idsB = new HashSet<>();
        for (ClothingItem item : a) idsA.add(item.getId());
        for (ClothingItem item : b) idsB.add(item.getId());
        return idsA.equals(idsB);
    }

    // Averages all pairwise scores between items in an outfit
    private double computeOutfitScore(List<ClothingItem> items) {
        double total = 0;
        int pairs = 0;

        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                total += scorer.score(items.get(i), items.get(j));
                pairs++;
            }
        }

        return pairs == 0 ? 0 : Math.round((total / pairs) * 10.0) / 10.0;
    }
}
