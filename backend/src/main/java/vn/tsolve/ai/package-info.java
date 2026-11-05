/**
 * The AI gateway: the Ollama chat and embedding clients every AI feature goes through, and the AI draft and rewrite
 * of a Solution. Content goes only to T-Solve's own Ollama (OLLAMA_BASE_URL) and is never logged; the module stores
 * nothing.
 */
package vn.tsolve.ai;
