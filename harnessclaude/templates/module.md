---
name: {module name}
repos: [{repo keys from the manifest `repos` map}]
dependencies: [{module names}]
complexity: small | medium | large
paths: []  # code AND tests this module owns, e.g. [src/billing/**, tests/billing/**] — the gate opens them only for this module, and trace counts only these tests for it; set it whenever there is more than one module
---

# Module: {name}

## Description
{One paragraph — what this module does}

## Responsibilities
- {what this module owns}

## Interfaces

### Exposed
{What this module provides to others}

| Interface | Type | Description |
|-----------|------|-------------|

### Consumed
{What this module needs from others}

| Dependency | Interface | Description |
|------------|-----------|-------------|

## Target Repos
- {repo}: {what goes here}

## Related Artifacts
- Spec: `artifacts/03-modules/{module}/spec.md`
