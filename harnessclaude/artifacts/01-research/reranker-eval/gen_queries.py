"""Build the reranker eval set: a ticket corpus and Vietnamese questions with one known answer each.

corpus.jsonl  - CORPUS_SIZE tickets from the multi-language dataset, text built like EmbeddingText.of
queries.jsonl - QUERIES tickets of the corpus, each rewritten by qwen3:4b as an employee's question (gold = that ticket)

Run: python gen_queries.py --csv <dataset-tickets-multi-lang3-4k.csv>
"""

import argparse
import csv
import json
import os
import random
from pathlib import Path

import requests

OLLAMA = os.environ.get("OLLAMA_URL", "http://localhost:11434")
CHAT_MODEL = os.environ.get("CHAT_MODEL", "qwen3:4b")
CORPUS_SIZE = 600
QUERIES = 100
HERE = Path(__file__).parent

PROMPT = """Below is an IT support ticket. Write the question an employee of a Vietnamese company would type into the
company chatbot when they have this problem. Rules: {language}, 1 or 2 sentences, describe the symptom in your own
words, do not copy the ticket's title, do not mention the solution, keep product names and error codes.
Reply with the question only.

<ticket>{ticket}</ticket>"""


def ticket_text(row):
    # Same sections as backend EmbeddingText.of: title, symptoms, ..., resolution (here: subject, body, answer).
    return "\n".join(s.strip() for s in (row["subject"], row["body"], row["answer"]) if s and s.strip())


LANGUAGE = "Vietnamese"


def ask(ticket):
    reply = requests.post(
        f"{OLLAMA}/api/chat",
        json={
            "model": CHAT_MODEL,
            "stream": False,
            "think": False,
            "options": {"temperature": 0.3},
            "messages": [{"role": "user", "content": PROMPT.format(ticket=ticket[:3000], language=LANGUAGE)}],
        },
        timeout=300,
    )
    reply.raise_for_status()
    return reply.json()["message"]["content"].strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True)
    parser.add_argument("--same-language", action="store_true",
                        help="questions in the ticket's language, written to queries_same.jsonl")
    parser.add_argument("--negatives", action="store_true",
                        help="Vietnamese questions from tickets NOT in the corpus, written to queries_neg.jsonl")
    args = parser.parse_args()
    global LANGUAGE
    if args.same_language:
        LANGUAGE = "the same language as the ticket"

    rows = [r for r in csv.DictReader(open(args.csv, encoding="utf-8")) if r["subject"] and r["answer"]]
    random.seed(42)
    corpus = random.sample(rows, CORPUS_SIZE)
    with open(HERE / "corpus.jsonl", "w", encoding="utf-8") as out:
        for i, row in enumerate(corpus):
            out.write(json.dumps({"id": f"T{i}", "lang": row["language"], "queue": row["queue"],
                                  "text": ticket_text(row)}, ensure_ascii=False) + "\n")

    picked = random.sample(range(CORPUS_SIZE), QUERIES)
    name = "queries_same.jsonl" if args.same_language else "queries.jsonl"
    if args.negatives:
        # Tickets outside the corpus whose subject is not also a corpus subject: no answer exists.
        subjects = {row["subject"].strip().lower() for row in corpus}
        outside = [r for r in rows if r not in corpus and r["subject"].strip().lower() not in subjects]
        corpus = corpus + random.sample(outside, QUERIES)  # only to look the ticket up below; corpus.jsonl is written
        picked = list(range(CORPUS_SIZE, CORPUS_SIZE + QUERIES))
        name = "queries_neg.jsonl"
    done = set()
    path = HERE / name
    if path.exists():  # resume after a crash
        done = {json.loads(line)["gold"] for line in open(path, encoding="utf-8")}
    done_n = set(range(1, sum(1 for _ in open(path, encoding="utf-8")) + 1)) if path.exists() else set()
    with open(path, "a", encoding="utf-8") as out:
        for n, i in enumerate(picked, 1):
            if f"T{i}" in done or (args.negatives and n in done_n):
                continue
            question = ask(ticket_text(corpus[i]))
            gold = "NONE" if args.negatives else f"T{i}"
            out.write(json.dumps({"q": question, "gold": gold}, ensure_ascii=False) + "\n")
            out.flush()
            print(f"{n}/{QUERIES}", flush=True)


if __name__ == "__main__":
    main()
