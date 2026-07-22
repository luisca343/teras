<!-- horus:policy BEGIN (managed by `horus wire`; do not edit inside) -->
## horus — code graph (query it before searching text)

`horus` keeps a symbol/edge index of this repo in `.horus/graph.db`. **Locate code by querying the
graph; use text search only for what the graph cannot answer** — comments, config, strings, docs,
and other non-code text.

| Need | Command |
| --- | --- |
| (re)build the index | `horus build` |
| find a symbol by name / FQN / source | `horus search <name>` |
| symbol + callers + callees + blast radius | `horus explore <symbol>` |
| who calls this | `horus callers <symbol>` |
| what this calls | `horus callees <symbol>` |
| everything a change would touch | `horus impact <symbol>` |
| index health + unresolved-edge causes | `horus status` |

**Order of operations:** `horus search`/`explore` to locate → targeted `grep` on the file it named
to read the exact lines → *never* a broad `grep`/`rg`/`find` over the repo as the first move.

Before you edit a shared symbol, run `horus impact <symbol>` and account for every caller it lists.
If a command reports the graph is missing or stale, run `horus build` (incremental, seconds) and
retry — do not fall back to grepping the whole repo.
<!-- horus:policy END -->

<!-- mimir:policy BEGIN (managed by `mimir wire`; do not edit inside) -->
## mimir — persistent memory (recall before deciding, record after)

`mimir` is a durable, searchable log of decisions, bugfixes, patterns and preferences for this
project, plus a global store shared across every project. It survives context windows and sessions.

| Need | Command |
| --- | --- |
| what is already known here | `mimir context` |
| recall prior work on a topic | `mimir search "<terms>"` |
| record something worth keeping | `mimir save "<title>" -t <type> --what "…" --why "…"` |
| read one entry in full | `mimir show <id>` |
| what happened recently | `mimir timeline` |

`<type>` is one of `decision`, `bugfix`, `pattern`, `preference`, `session`. Add `--topic <key>` to
group related entries, and `--global` for something true of every project (tooling, house style).

**Recall first.** Before designing anything non-trivial, debugging something that feels familiar, or
re-deriving a constraint, run `mimir search` on the topic. A recorded decision stands unless you
have a reason to overturn it — and if you overturn it, record the replacement.

**Record as you go, not at the end.** After a non-obvious choice, a fixed bug, or a discovered
gotcha, save it with the *why*, not just the *what*. One fact per entry, titled so a future
`mimir search` finds it.
<!-- mimir:policy END -->
