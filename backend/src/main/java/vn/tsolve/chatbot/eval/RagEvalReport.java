package vn.tsolve.chatbot.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import vn.tsolve.chatbot.answer.service.impl.ChatbotSource;
import vn.tsolve.chatbot.eval.GoldFile.GoldLine;
import vn.tsolve.chatbot.eval.GoldFile.Kind;
import vn.tsolve.chatbot.eval.GoldFile.Source;

record RagEvalReport(
        double minSimilarity, int sourceLimit, int k, Scores overall, Map<String, Scores> groups, List<Line> lines) {

    static RagEvalReport of(
            List<GoldLine> gold, List<List<ChatbotSource>> results, double minSimilarity, int sourceLimit, int k) {
        var overall = new Totals();
        Map<String, Totals> groupTotals = new TreeMap<>();
        List<Line> lines = new ArrayList<>(gold.size());
        for (int index = 0; index < gold.size(); index++) {
            GoldLine line = gold.get(index);
            List<ChatbotSource> ranked = results.get(index);
            Double top = ranked.isEmpty() ? null : ranked.getFirst().similarity();
            Integer rank = null;
            Double similarity = null;
            for (int result = 0; result < Math.min(ranked.size(), k); result++) {
                ChatbotSource candidate = ranked.get(result);
                if (line.expected().contains(new Source(Kind.valueOf(candidate.kind()), candidate.id()))) {
                    rank = result + 1;
                    similarity = candidate.similarity();
                    break;
                }
            }
            lines.add(new Line(line.line(), rank, rounded(similarity), rounded(top)));
            boolean recalled = rank != null && rank <= sourceLimit && similarity >= minSimilarity;
            double reciprocalRank = rank == null ? 0 : 1.0 / rank;
            boolean noSource = top == null || top < minSimilarity;
            overall.add(recalled, reciprocalRank, noSource);
            line.groups().entrySet().stream()
                    .map(group -> group.getKey() + "=" + group.getValue())
                    .distinct()
                    .forEach(group -> groupTotals
                            .computeIfAbsent(group, key -> new Totals())
                            .add(recalled, reciprocalRank, noSource));
        }
        Map<String, Scores> groups = new TreeMap<>();
        groupTotals.forEach((group, totals) -> groups.put(group, totals.scores()));
        return new RagEvalReport(minSimilarity, sourceLimit, k, overall.scores(), groups, List.copyOf(lines));
    }

    private static Double rounded(Double similarity) {
        return similarity == null ? null : round(similarity);
    }

    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }

    record Line(int line, Integer rank, Double similarity, Double topSimilarity) {}

    record Scores(int questions, double recall, double mrr, double noSourceRate) {}

    private static final class Totals {
        private int questions;
        private int recalled;
        private double reciprocalRanks;
        private int missingSources;

        void add(boolean recalled, double reciprocalRank, boolean noSource) {
            questions++;
            if (recalled) {
                this.recalled++;
            }
            reciprocalRanks += reciprocalRank;
            if (noSource) {
                missingSources++;
            }
        }

        Scores scores() {
            return new Scores(
                    questions,
                    round((double) recalled / questions),
                    round(reciprocalRanks / questions),
                    round((double) missingSources / questions));
        }
    }
}
