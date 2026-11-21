"""Compare retrieval methods on the eval set (corpus.jsonl + queries.jsonl from gen_queries.py).

Methods (each returns a ranked list of ticket ids):
  cosine        bge-m3 cosine, as SolutionRetrievalRepository today
  bm25          keyword only
  hybrid        RRF of cosine and bm25 (step 1)
  mq_hyde       RRF of cosine lists for the question, 3 rewrites and a hypothetical answer (step 3)
  mq_hyde+bm25  the same plus the bm25 list
  *+rerank      top 20 of the method re-scored by bge-reranker-v2-m3 (only with --rerank; downloads the model)

Metrics: hit@1, hit@5, MRR@10, and "kept@0.7": gold in the top 5 AND its cosine >= 0.7, i.e. what the
product's CHATBOT_MIN_SIMILARITY filter would let through.

Run: python evaluate.py [--rerank]
"""

import argparse
import json
import os
import re
from pathlib import Path

import numpy as np
import requests
from rank_bm25 import BM25Okapi

OLLAMA = os.environ.get("OLLAMA_URL", "http://localhost:11434")
CHAT_MODEL = os.environ.get("CHAT_MODEL", "qwen3:4b")
HERE = Path(__file__).parent
RRF_K = 60
POOL = 50


def answer_only(text):
    # qwen3 on this Ollama ignores think=false: keep what follows the reasoning block.
    return text.split("</think>")[-1].strip()


def load(name):
    return [json.loads(line) for line in open(HERE / name, encoding="utf-8")]


def embed(texts, cache=None):
    if cache and (HERE / cache).exists():
        return np.load(HERE / cache)
    vectors = []
    for start in range(0, len(texts), 16):
        reply = requests.post(f"{OLLAMA}/api/embed", json={"model": "bge-m3", "input": texts[start:start + 16]},
                              timeout=600)
        reply.raise_for_status()
        vectors.extend(reply.json()["embeddings"])
    matrix = np.array(vectors, dtype=np.float32)
    matrix /= np.linalg.norm(matrix, axis=1, keepdims=True)
    if cache:
        np.save(HERE / cache, matrix)
    return matrix


def tokens(text):
    return re.findall(r"\w+", text.lower())


def rrf(*rankings):
    score = {}
    for ranking in rankings:
        for rank, doc in enumerate(ranking[:POOL]):
            score[doc] = score.get(doc, 0) + 1 / (RRF_K + rank + 1)
    return sorted(score, key=score.get, reverse=True)


def metrics(results, golds, cosine_of):
    hit1 = hit5 = mrr = kept = 0
    for ranking, gold, cos in zip(results, golds, cosine_of):
        top = ranking[:10]
        if gold in top:
            rank = top.index(gold)
            mrr += 1 / (rank + 1)
            hit1 += rank == 0
            hit5 += rank < 5
            kept += rank < 5 and cos[gold] >= 0.7
    n = len(golds)
    return {"hit@1": hit1 / n, "hit@5": hit5 / n, "MRR@10": mrr / n, "kept@0.7": kept / n}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rerank", action="store_true")
    parser.add_argument("--queries", default="queries.jsonl")
    args = parser.parse_args()

    corpus = load("corpus.jsonl")
    ids = [doc["id"] for doc in corpus]
    queries = [{"q": answer_only(row["q"]), "gold": row["gold"]} for row in load(args.queries)]
    vfile = args.queries.replace("queries", "variants")
    variants = {row["gold"]: row for row in load(vfile)} if (HERE / vfile).exists() else {}

    doc_vectors = embed([doc["text"] for doc in corpus], cache="corpus_vectors.npy")
    question_vectors = embed([q["q"] for q in queries])
    bm25 = BM25Okapi([tokens(doc["text"]) for doc in corpus])

    def cosine_ranking(vector):
        sims = doc_vectors @ vector
        return [ids[i] for i in np.argsort(-sims)], dict(zip(ids, sims))

    methods = {"cosine": [], "bm25": [], "hybrid": []}
    if variants:
        methods.update({"mq_hyde": [], "mq_hyde+bm25": []})
    cosines = []
    for query, vector in zip(queries, question_vectors):
        by_cosine, sims = cosine_ranking(vector)
        cosines.append(sims)
        bm25_scores = bm25.get_scores(tokens(query["q"]))
        by_bm25 = [ids[i] for i in np.argsort(-bm25_scores)]
        methods["cosine"].append(by_cosine)
        methods["bm25"].append(by_bm25)
        methods["hybrid"].append(rrf(by_cosine, by_bm25))
        if variants:
            extra = variants[query["gold"]]
            texts = extra["rewrites"] + [extra["hypothetical_answer"]]
            lists = [by_cosine] + [cosine_ranking(v)[0] for v in embed(texts)]
            methods["mq_hyde"].append(rrf(*lists))
            methods["mq_hyde+bm25"].append(rrf(*lists, by_bm25))

    if args.rerank:
        from sentence_transformers import CrossEncoder
        import time
        reranker = CrossEncoder("BAAI/bge-reranker-v2-m3", max_length=512)
        text_of = dict(zip(ids, (doc["text"] for doc in corpus)))
        seconds = []
        cache = {}
        for name in list(methods):
            reranked = []
            for query, ranking in zip(queries, methods[name]):
                pool = ranking[:20]
                # Methods share most of their top 20: score each (question, ticket) pair once.
                todo = [doc for doc in pool if (query["q"], doc) not in cache]
                if todo:
                    start = time.perf_counter()
                    for doc, score in zip(todo, reranker.predict([(query["q"], text_of[doc]) for doc in todo])):
                        cache[(query["q"], doc)] = score
                    if name == "cosine":  # a full pool of 20: the latency the product would see
                        seconds.append(time.perf_counter() - start)
                scores = np.array([cache[(query["q"], doc)] for doc in pool])
                reranked.append([pool[i] for i in np.argsort(-scores)])
            methods[name + "+rerank"] = reranked
        device = reranker.model.device
        print(f"rerank of 20 tickets on {device}: median {np.median(seconds):.2f}s per question")

    golds = [q["gold"] for q in queries]
    print(f"{len(queries)} questions, {len(corpus)} tickets")
    print(f"{'method':<22}{'hit@1':>8}{'hit@5':>8}{'MRR@10':>8}{'kept@0.7':>10}")
    for name, results in methods.items():
        m = metrics(results, golds, cosines)
        print(f"{name:<22}{m['hit@1']:>8.2f}{m['hit@5']:>8.2f}{m['MRR@10']:>8.2f}{m['kept@0.7']:>10.2f}")
    no_source = sum(max(c.values()) < 0.7 for c in cosines)
    print(f"questions with no source >= 0.7 (product answers NO_SOURCE): {no_source}/{len(queries)}")


if __name__ == "__main__":
    main()
