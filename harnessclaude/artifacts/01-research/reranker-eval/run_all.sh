#!/usr/bin/env bash
# Whole reranker/RAG eval on the GPU server. Usage:
#   CHAT_MODEL=qwen3:4b-instruct-2507-q4_K_M OLLAMA_URL=http://localhost:11434 ./run_all.sh <dataset-tickets-multi-lang3-4k.csv>
# Needs: Ollama with bge-m3 and $CHAT_MODEL pulled; Python 3.10+ with a CUDA build of torch for the reranker.
set -euo pipefail
cd "$(dirname "$0")"
CSV=${1:?path to dataset-tickets-multi-lang3-4k.csv}
export CHAT_MODEL=${CHAT_MODEL:-qwen3:4b-instruct-2507-q4_K_M}
export OLLAMA_URL=${OLLAMA_URL:-http://localhost:11434}

python -c "import torch; print('torch', torch.__version__, 'cuda', torch.cuda.is_available())"
curl -sf "$OLLAMA_URL/api/tags" | grep -q '"bge-m3' || { echo "bge-m3 is not pulled"; exit 1; }
curl -sf "$OLLAMA_URL/api/tags" | grep -q "\"$CHAT_MODEL\"" || { echo "$CHAT_MODEL is not pulled"; exit 1; }

time python gen_queries.py --csv "$CSV"
time python gen_queries.py --same-language --csv "$CSV"
time python gen_variants.py queries.jsonl
time python gen_variants.py queries_same.jsonl

OUT="results_${CHAT_MODEL//[:\/]/_}.txt"
{
  echo "== Vietnamese questions, tickets in en/de/es/fr/pt (chat model $CHAT_MODEL)"
  python evaluate.py --rerank
  echo
  echo "== Questions in the ticket's own language"
  python evaluate.py --rerank --queries queries_same.jsonl
} | tee "$OUT"
echo "Results in $OUT"
