"""Print a random sample of (question, ticket) pairs whose cosine is in the band the cut-off decision turns on,
with the LLM judge's grade, so a person can grade the same pairs and check the judge where it matters.
Run after judge.py: python borderline.py [low] [high] [n] > borderline.txt
"""
import json
import random
import sys

import numpy as np

from evaluate import answer_only, embed, load

low, high, n = (float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3])) if len(sys.argv) > 3 else (0.60, 0.70, 30)
corpus = load("corpus.jsonl")
ids = [doc["id"] for doc in corpus]
text_of = {doc["id"]: doc["text"] for doc in corpus}
docs = embed([doc["text"] for doc in corpus], cache="corpus_vectors.npy")
grade = {(row["q"], row["doc"]): row["grade"] for row in load("judgments.jsonl")}
questions = [answer_only(row["q"]) for name in ("queries.jsonl", "queries_same.jsonl", "queries_neg.jsonl")
             for row in load(name)]
band = []
for q, vector in zip(questions, embed(questions)):
    sims = docs @ vector
    for i in np.argsort(-sims)[:5]:
        if low <= sims[i] < high and (q, ids[i]) in grade:
            band.append((q, ids[i], float(sims[i]), grade[(q, ids[i])]))
random.seed(11)
for k, (q, doc, sim, g) in enumerate(random.sample(band, min(n, len(band)))):
    print(f"#{k} cos={sim:.3f} judge={g}\n  Q: {q[:220]}\n  T: {text_of[doc][:420]!r}\n")
print(f"{len(band)} top-5 pairs in [{low}, {high})")
