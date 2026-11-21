"""Two follow-ups to evaluate.py.

1. Threshold: with questions that HAVE an answer (queries*.jsonl) and questions that have NONE (queries_neg.jsonl),
   what does each cut-off give? Today: cosine >= CHATBOT_MIN_SIMILARITY (0.7), top 5. With --rerank also a cut-off on
   the reranker score (top 20 by cosine, re-scored, top 5).
     right    = the gold ticket is among the sources kept
     wrong    = sources kept, but not the gold ticket (an answer from the wrong source)
     none     = no source kept (NO_SOURCE)
     answered = for no-answer questions, any source kept (should be 0)
2. Misses: when the top 1 is not the gold ticket, is it a near-duplicate of it (ticket-to-ticket cosine)?
   A near-duplicate miss is probably a right answer the single-gold metric counts as wrong.

Run after run_all.sh: python gen_queries.py --negatives --csv <csv> && python analyze.py [--rerank]
"""

import argparse

import numpy as np

from evaluate import answer_only, embed, load

COSINE_CUTS = [0.50, 0.55, 0.60, 0.65, 0.70]
RERANK_CUTS = [0.01, 0.05, 0.10, 0.20, 0.30, 0.50]


def sweep(label, cuts, positives, negatives):
    """positives/negatives: per question, a list of (doc, score) best first, already cut to the top N."""
    print(f"\n{label}")
    print(f"{'cut':>6}{'right':>8}{'wrong':>8}{'none':>8}{'| no-answer qs answered':>26}")
    for cut in cuts:
        right = wrong = none = 0
        for gold, ranked in positives:
            kept = [doc for doc, score in ranked[:5] if score >= cut]
            if gold in kept:
                right += 1
            elif kept:
                wrong += 1
            else:
                none += 1
        answered = sum(any(score >= cut for _, score in ranked[:5]) for ranked in negatives)
        n, m = len(positives), max(len(negatives), 1)
        print(f"{cut:>6.2f}{right / n:>8.2f}{wrong / n:>8.2f}{none / n:>8.2f}{answered / m:>26.2f}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rerank", action="store_true")
    args = parser.parse_args()

    corpus = load("corpus.jsonl")
    ids = [doc["id"] for doc in corpus]
    index = {doc_id: i for i, doc_id in enumerate(ids)}
    docs = embed([doc["text"] for doc in corpus], cache="corpus_vectors.npy")
    title = {doc["id"]: doc["text"].split("\n")[0][:90] for doc in corpus}

    def questions(name):
        return [{"q": answer_only(row["q"]), "gold": row["gold"]} for row in load(name)]

    sets = {name: questions(name) for name in ("queries.jsonl", "queries_same.jsonl")}
    negatives = questions("queries_neg.jsonl")

    reranker = None
    if args.rerank:
        from sentence_transformers import CrossEncoder
        reranker = CrossEncoder("BAAI/bge-reranker-v2-m3", max_length=512)
        text_of = dict(zip(ids, (doc["text"] for doc in corpus)))

    def ranked(qs):
        vectors = embed([q["q"] for q in qs])
        by_cosine, by_rerank = [], []
        for q, vector in zip(qs, vectors):
            sims = docs @ vector
            order = np.argsort(-sims)
            by_cosine.append([(ids[i], float(sims[i])) for i in order[:20]])
            if reranker:
                pool = [ids[i] for i in order[:20]]
                scores = reranker.predict([(q["q"], text_of[doc]) for doc in pool])
                by_rerank.append(sorted(zip(pool, map(float, scores)), key=lambda pair: -pair[1]))
        return by_cosine, by_rerank

    neg_cosine, neg_rerank = ranked(negatives)
    for name, qs in sets.items():
        pos_cosine, pos_rerank = ranked(qs)
        golds = [q["gold"] for q in qs]
        print(f"\n===== {name}: {len(qs)} questions with an answer, {len(negatives)} without")
        sweep("cosine cut-off (today 0.70)", COSINE_CUTS, list(zip(golds, pos_cosine)), neg_cosine)
        if reranker:
            sweep("reranker score cut-off", RERANK_CUTS, list(zip(golds, pos_rerank)), neg_rerank)

        for label, results in (("cosine", pos_cosine), ("rerank", pos_rerank)):
            if not results:
                continue
            misses = [(q, r[0][0]) for q, r in zip(qs, results) if r[0][0] != q["gold"]]
            dup = [float(docs[index[top]] @ docs[index[q["gold"]]]) for q, top in misses]
            near = sum(d >= 0.9 for d in dup)
            print(f"\n{label} top-1 misses: {len(misses)}; top 1 is a near-duplicate of the gold (cos >= 0.9): {near}")
            for q, top in misses[:3]:
                print(f"  Q:    {q['q'][:90]}\n  gold: {title[q['gold']]}\n  top1: {title[top]}")


if __name__ == "__main__":
    main()
