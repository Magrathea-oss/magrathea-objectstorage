# C4 model workflow

The canonical C4 workspace for this project is:

```text
docs/c4/workspace.dsl
```

This project uses **Structurizr local** through the official `docker.io/structurizr/structurizr` container image, following the current Structurizr local workflow. The helper scripts are compatible with both **Podman** and **Docker**; Podman is preferred when both are available.

<https://docs.structurizr.com/local/quickstart>

## Start Structurizr local

Preferred command:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-local.sh
```

The script auto-detects the container runtime in this order:

1. `CONTAINER_RUNTIME`, if explicitly set
2. `podman`
3. `docker`

The default image is `docker.io/structurizr/structurizr` so Podman does not depend on short-name registry resolution. Override it with `STRUCTURIZR_IMAGE` if needed.

Examples:

```bash
# auto-detect podman/docker
~/.pi/agent/skills/multi-agent/tools/c4model-local.sh

# force Podman
CONTAINER_RUNTIME=podman ~/.pi/agent/skills/multi-agent/tools/c4model-local.sh

# force Docker
CONTAINER_RUNTIME=docker ~/.pi/agent/skills/multi-agent/tools/c4model-local.sh
```

The scripts run the container as the current host user. With Podman they also add `--userns=keep-id`, so Structurizr can write `workspace.json` and static exports without changing ownership of files under `docs/c4`.

Equivalent Docker Compose command, for Docker-based environments:

```bash
docker compose up structurizr-local
```

If you use Podman Compose externally, the same `structurizr-local` service definition can be used.

Open:

```text
http://localhost:9090
```

Port `9090` is used intentionally because the application uses port `8080`.

## Edit workflow

1. Edit `docs/c4/workspace.dsl`.
2. Keep Structurizr local running.
3. Refresh/open the browser at `http://localhost:9090`.
4. Use the Structurizr diagram editor for manual layout changes.
5. Structurizr local auto-saves layout into `docs/c4/workspace.json`.

When layout changes are intentional, commit both:

```text
docs/c4/workspace.dsl
docs/c4/workspace.json
```

If `workspace.json` does not exist yet, Structurizr local can still render directly from `workspace.dsl`; layout will be automatic until a manual layout is saved.

## Validation

Use the Structurizr vNext command syntax through the `docker.io/structurizr/structurizr` container image. The script works with Podman and Docker using the same runtime auto-detection described above:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-validate.sh
```

Do **not** use the old local `structurizr-cli` syntax (`-w`, DOT export, Graphviz PNG rendering) as the project workflow.

## Export workflow

The project uses the official Structurizr `export` command semantics:

<https://docs.structurizr.com/export>

Generic export:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-export.sh static
~/.pi/agent/skills/multi-agent/tools/c4model-export.sh mermaid
~/.pi/agent/skills/multi-agent/tools/c4model-export.sh plantuml/c4plantuml
~/.pi/agent/skills/multi-agent/tools/c4model-export.sh json
```

Convenience static-site command:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-export-static.sh
```

The export script uses `workspace.json` when present, because it contains curated diagram layout. Otherwise it falls back to `workspace.dsl`.

Default outputs:

```text
static                  -> docs/c4/export/static/
plantuml/c4plantuml    -> docs/c4/export/plantuml-c4plantuml/
mermaid                 -> docs/c4/export/mermaid/
json                    -> docs/c4/export/json/
png/svg                 -> docs/c4/images/
```

Override the output with:

```bash
STRUCTURIZR_EXPORT_OUTPUT=some/path ~/.pi/agent/skills/multi-agent/tools/c4model-export.sh mermaid
```

## Build Structurizr from source for PNG/SVG export

PNG/SVG export requires the Structurizr browser-based renderer. If you don't already have a compatible preview/source WAR, build Structurizr from source:

```bash
~/.pi/agent/skills/multi-agent/tools/c4model-build-structurizr.sh
```

This clones `https://github.com/structurizr/structurizr.git` under `.cache/structurizr-source`, runs the Maven build, and copies the generated WAR to:

```text
.cache/structurizr/structurizr.war
```

`.cache/` is ignored by Git. `~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh` automatically uses this WAR when present. You can override it with `STRUCTURIZR_WAR=/path/to/structurizr.war`.

## PNG/SVG export policy

PNG/SVG must be generated directly by Structurizr's browser-based renderer, not through PlantUML, DOT, Graphviz, or Mermaid:

```bash
# preferred: export from the running Structurizr local diagrams page
~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh png url
~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh svg url

# alternative: export directly from workspace.dsl/workspace.json
~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh png workspace
~/.pi/agent/skills/multi-agent/tools/c4model-export-images.sh svg workspace
```

This follows the official PNG/SVG documentation:

<https://docs.structurizr.com/export/png-and-svg>

The script invokes one of these supported forms:

```text
export -format png|svg -url http://localhost:9090/workspace/1/diagrams -output docs/c4/images
export -format png|svg -workspace docs/c4/workspace.dsl -output docs/c4/images
```

Use `url` mode when Structurizr local is running and you want to export the rendered diagrams from the local instance. Use `workspace` mode for direct file-based export. If `workspace.json` exists, it is preferred over `workspace.dsl` to preserve manual layout.

The official documentation notes that PNG/SVG export currently requires building Structurizr from source or using the preview Java WAR; it is not available in every distribution. The prebuilt `docker.io/structurizr/structurizr` image currently returns `Exporting to PNG/SVG is not supported in this build`.

Therefore:

- the approved PNG/SVG path is still Structurizr `export -format png|svg`;
- agents must not regenerate PNGs via DOT/Graphviz, PlantUML, Mermaid, or other substitute renderers;
- if the installed Structurizr distribution does not support PNG/SVG, stop and report that a supported preview/source build is required.

## Agent rules for C4 model changes

When an agent is asked to work on the C4 model:

1. Treat `docs/c4/workspace.dsl` as the source of truth.
2. Use `~/.pi/agent/skills/multi-agent/tools/c4model-validate.sh` for validation.
3. Use `~/.pi/agent/skills/multi-agent/tools/c4model-local.sh` for visual review; it supports Podman and Docker. Docker users may alternatively use `docker compose up structurizr-local`.
4. Preserve `workspace.json` if present; it contains manual layout.
5. Do not introduce alternate diagram generation workflows unless explicitly requested.
6. Do not overwrite PNG documentation artifacts unless using the approved Structurizr PNG/SVG export command.
