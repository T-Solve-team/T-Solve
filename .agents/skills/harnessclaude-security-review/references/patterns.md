# Security Grep Patterns

Use with the Grep tool or `grep -rnE`. Exclude `node_modules/`, `dist/`, `vendor/`, `__pycache__/`, `.git/`.

## Secrets — high confidence

```
AKIA[0-9A-Z]{16}                                   # AWS access key ID
gh[pousr]_[A-Za-z0-9_]{36,255}                     # GitHub token
github_pat_[A-Za-z0-9_]{22,}                       # GitHub fine-grained token
sk_live_[0-9a-zA-Z]{24,}|rk_live_[0-9a-zA-Z]{24,}  # Stripe live key
xox[baprs]-[0-9a-zA-Z-]{10,}                       # Slack token
AIza[0-9A-Za-z_-]{35}                              # Google API key
sk-ant-[A-Za-z0-9_-]{40,}                          # Anthropic key
-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----
eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}   # JWT in code
```

## Secrets — needs context

```
(?i)(api[_-]?key|apikey|api[_-]?secret)\s*[:=]\s*['"][A-Za-z0-9/+=_-]{16,}['"]
(?i)(postgres|mysql|mongodb|redis)://[^:]+:[^@]+@
(?i)(password|passwd|pwd)\s*[:=]\s*['"][^'"]{8,}['"]
(?i)(secret|token|credential)\s*[:=]\s*['"][A-Za-z0-9/+=]{16,}['"]
(?i)bearer\s+[A-Za-z0-9._~+/-]{20,}
```

## Dangerous Code

```
# SQL injection
(?i)(query|sql|execute)\s*\(.*\+
(?i)(query|sql|execute)\s*\(`.*\$\{

# XSS
\.innerHTML\s*=
dangerouslySetInnerHTML
document\.write\(
v-html=
\|\s*safe\b                     # Jinja/Django unescaped
<%-                             # EJS unescaped
\{\{\{                          # Handlebars unescaped

# Command injection
(?i)(exec|execSync|spawn|spawnSync)\s*\(.*\+
(?i)(exec|execSync|spawn|spawnSync)\s*\(`.*\$\{
os\.system\(
subprocess\.(call|run|Popen)\(.*\+

# Path traversal
(?i)(readFile|writeFile|createReadStream|open)\s*\(.*req\.(params|query|body)

# Insecure randomness (check the surrounding lines for token/key/secret/session/nonce/salt)
Math\.random\(\)

# Eval
\beval\s*\(
new\s+Function\s*\(
setTimeout\s*\(\s*['"]

# Unsafe deserialization
pickle\.loads
yaml\.load\(
unserialize\(

# TLS verification disabled
rejectUnauthorized\s*:\s*false
NODE_TLS_REJECT_UNAUTHORIZED.*0
(?i)verify\s*=\s*False

# Secrets in logs
(?i)console\.(log|debug)\s*\(.*(password|secret|token|key)
```

## Likely False Positives

Skip after a quick look when the hit is in a test/fixture/example file, reads from the environment (`process.env.`, `os.getenv(`), is a placeholder (`YOUR_`, `REPLACE_`, `xxx`, `<token>`), or sits in a comment.
