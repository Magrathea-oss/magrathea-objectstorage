#!/usr/bin/env python3
"""Generate the ARC42 Gherkin requirements appendix from shared feature files.

The parser intentionally supports only the Gherkin constructs used by this
repository's executable requirement sources: feature-like headers, rules,
scenario/scenario-outline declarations, steps (Given/When/Then/And/But),
Examples blocks with tags and data tables, and doc strings.

Output format: AsciiDoc with `[appendix]` style for inclusion in
arc42-template.adoc as a proper appendix (not a chapter subsection).
"""

from __future__ import annotations

import argparse
import difflib
import re
import sys
from collections import Counter, OrderedDict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

FEATURE_HEADER_RE = re.compile(r"^\s*(Business Need|Ability|Feature):\s*(.*?)\s*$")
RULE_RE = re.compile(r"^\s*Rule:\s*(.*?)\s*$")
SCENARIO_RE = re.compile(r"^\s*(Scenario(?: Outline)?):\s*(.*?)\s*$")
EXAMPLES_RE = re.compile(r"^\s*Examples(?::\s*(.*?))?\s*$")
STEP_RE = re.compile(r"^\s*(Given|When|Then|And|But)\s+(.*?)\s*$")
TABLE_ROW_RE = re.compile(r"^\s*\|.*\|\s*$")
DOC_STRING_DELIM_RE = re.compile(r'^\s*"""\s*$')

TAG_RE = re.compile(r"@[A-Za-z0-9_.:-]+")
REQUIREMENT_ID_RE = re.compile(r"^@REQ-[A-Z0-9]+(?:-[A-Z0-9]+)*$")
REQUIREMENT_GROUP_RE = re.compile(r"^(REQ-[A-Z0-9]+-\d{3})-[A-Z]$")

STATUS_TAGS = {
    "@implemented-and-validated",
    "@implemented-not-e2e-validated",
    "@partial",
    "@config-only",
    "@placeholder",
    "@absent",
    "@not-implemented",
}
CLASSIFICATION_TAGS = {"@functional-requirement", "@non-functional-requirement"}
GENERIC_NON_CAPABILITY_TAGS = {"@requirement", "@protocol-smoke"}
UNKNOWN_STATUS = "unknown/not-tagged"
UNKNOWN_VALIDATION_MODE = "unknown/not-tagged"
UNKNOWN_CLASSIFICATION = "unknown/not-tagged"
UNKNOWN_REQUIREMENT = "unknown/not-tagged"


@dataclass(frozen=True)
class FeatureInfo:
    path: Path
    keyword: str
    title: str
    tags: tuple[str, ...]
    description: tuple[str, ...]  # doc-string / free text right after the header


@dataclass(frozen=True)
class StepInfo:
    keyword: str  # Given / When / Then / And / But
    text: str


@dataclass(frozen=True)
class ExamplesBlock:
    tags: tuple[str, ...]
    name: str  # "WebTestClient validation" etc.
    header: tuple[str, ...]  # column names
    rows: tuple[tuple[str, ...], ...]  # data rows


@dataclass
class ScenarioInfo:
    feature: FeatureInfo
    line: int
    kind: str
    name: str
    rule: str | None
    rule_description: tuple[str, ...]
    scenario_tags: tuple[str, ...]
    steps: list[StepInfo] = field(default_factory=list)
    examples: list[ExamplesBlock] = field(default_factory=list)

    @property
    def all_tags(self) -> tuple[str, ...]:
        return tuple(dedupe([*self.feature.tags, *self.scenario_tags]))

    @property
    def requirement_ids(self) -> tuple[str, ...]:
        return tuple(tag[1:] for tag in self.scenario_tags if REQUIREMENT_ID_RE.match(tag))

    @property
    def requirement_group(self) -> str:
        ids = self.requirement_ids
        if not ids:
            return UNKNOWN_REQUIREMENT
        primary = ids[0]
        match = REQUIREMENT_GROUP_RE.match(primary)
        return match.group(1) if match else primary

    @property
    def classification_tags(self) -> tuple[str, ...]:
        return tuple(tag for tag in self.all_tags if tag in CLASSIFICATION_TAGS)

    @property
    def validation_mode_tags(self) -> tuple[str, ...]:
        return tuple(tag for tag in self.scenario_tags if tag.endswith("-required"))

    @property
    def status_tags(self) -> tuple[str, ...]:
        return tuple(tag for tag in self.scenario_tags if tag in STATUS_TAGS)

    @property
    def protocol_smoke(self) -> bool:
        return "@protocol-smoke" in self.all_tags

    @property
    def capability_area_tags(self) -> tuple[str, ...]:
        capability_tags: list[str] = []
        for tag in self.all_tags:
            if tag in STATUS_TAGS or tag in CLASSIFICATION_TAGS or tag in GENERIC_NON_CAPABILITY_TAGS:
                continue
            if tag.endswith("-required") or REQUIREMENT_ID_RE.match(tag):
                continue
            if re.match(r"^@phase-\d+$", tag):
                continue
            capability_tags.append(tag)
        return tuple(capability_tags)


def dedupe(values: Iterable[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        if value not in seen:
            seen.add(value)
            result.append(value)
    return result


def split_table_row(row: str) -> tuple[str, ...]:
    """Split a Gherkin table row '| a | b | c |' into ('a', 'b', 'c')."""
    cells = [cell.strip() for cell in row.strip().split("|")]
    # Strip leading and trailing empty cells (from outer pipes)
    if cells and cells[0] == "":
        cells = cells[1:]
    if cells and cells[-1] == "":
        cells = cells[:-1]
    return tuple(cells)


def parse_feature_file(path: Path, repo_root: Path) -> tuple[FeatureInfo, list[ScenarioInfo]]:
    pending_tags: list[str] = []
    feature: FeatureInfo | None = None
    feature_description: list[str] = []
    current_rule: str | None = None
    current_rule_description: list[str] = []
    scenarios: list[ScenarioInfo] = []
    current_scenario: ScenarioInfo | None = None
    current_examples: ExamplesBlock | None = None
    current_examples_rows: list[tuple[str, ...]] = []
    in_doc_string: bool = False
    doc_string_buffer: list[str] = []
    doc_string_owner: str | None = None  # 'feature' | 'rule' | None
    capture_feature_description: bool = False
    capture_rule_description: bool = False

    def finalize_examples() -> None:
        nonlocal current_examples, current_examples_rows
        if current_examples is not None and current_scenario is not None:
            finalized = ExamplesBlock(
                tags=current_examples.tags,
                name=current_examples.name,
                header=current_examples.header,
                rows=tuple(current_examples_rows),
            )
            current_scenario.examples.append(finalized)
        current_examples = None
        current_examples_rows = []

    lines = path.read_text(encoding="utf-8").splitlines()
    for line_number, raw_line in enumerate(lines, start=1):
        stripped = raw_line.strip()

        # Handle doc strings (multi-line """...""" blocks)
        if DOC_STRING_DELIM_RE.match(raw_line):
            if not in_doc_string:
                in_doc_string = True
                doc_string_buffer = []
            else:
                in_doc_string = False
                if doc_string_owner == "feature":
                    feature_description.extend(doc_string_buffer)
                elif doc_string_owner == "rule":
                    current_rule_description.extend(doc_string_buffer)
                doc_string_buffer = []
                doc_string_owner = None
            continue
        if in_doc_string:
            doc_string_buffer.append(raw_line)
            continue

        if stripped.startswith("@"):
            pending_tags.extend(TAG_RE.findall(stripped))
            continue

        feature_match = FEATURE_HEADER_RE.match(raw_line)
        if feature_match:
            finalize_examples()
            feature = FeatureInfo(
                path=path.relative_to(repo_root),
                keyword=feature_match.group(1),
                title=feature_match.group(2),
                tags=tuple(dedupe(pending_tags)),
                description=tuple(),
            )
            pending_tags = []
            current_rule = None
            current_rule_description = []
            current_scenario = None
            capture_feature_description = True
            capture_rule_description = False
            doc_string_owner = "feature"
            continue

        rule_match = RULE_RE.match(raw_line)
        if rule_match:
            finalize_examples()
            # finalise feature description (any text captured so far)
            if feature is not None and capture_feature_description:
                feature = FeatureInfo(
                    path=feature.path,
                    keyword=feature.keyword,
                    title=feature.title,
                    tags=feature.tags,
                    description=tuple(feature_description),
                )
                capture_feature_description = False
            current_rule = rule_match.group(1)
            current_rule_description = []
            capture_rule_description = True
            doc_string_owner = "rule"
            pending_tags = []
            current_scenario = None
            continue

        scenario_match = SCENARIO_RE.match(raw_line)
        if scenario_match:
            finalize_examples()
            if feature is None:
                raise ValueError(f"{path}:{line_number}: scenario appears before a feature header")
            capture_rule_description = False
            capture_feature_description = False
            current_scenario = ScenarioInfo(
                feature=feature,
                line=line_number,
                kind=scenario_match.group(1),
                name=scenario_match.group(2),
                rule=current_rule,
                rule_description=tuple(current_rule_description),
                scenario_tags=tuple(dedupe(pending_tags)),
            )
            scenarios.append(current_scenario)
            pending_tags = []
            continue

        examples_match = EXAMPLES_RE.match(raw_line)
        if examples_match and current_scenario is not None:
            finalize_examples()
            current_examples = ExamplesBlock(
                tags=tuple(dedupe(pending_tags)),
                name=(examples_match.group(1) or "").strip(),
                header=tuple(),
                rows=tuple(),
            )
            pending_tags = []
            current_examples_rows = []
            continue

        if TABLE_ROW_RE.match(raw_line) and current_examples is not None:
            cells = split_table_row(raw_line)
            if not current_examples.header:
                current_examples = ExamplesBlock(
                    tags=current_examples.tags,
                    name=current_examples.name,
                    header=cells,
                    rows=tuple(),
                )
            else:
                current_examples_rows.append(cells)
            continue

        step_match = STEP_RE.match(raw_line)
        if step_match and current_scenario is not None and current_examples is None:
            current_scenario.steps.append(
                StepInfo(keyword=step_match.group(1), text=step_match.group(2))
            )
            pending_tags = []
            continue

        # Free-text under feature/rule (description) — only when not capturing a doc string
        if stripped and not stripped.startswith("#") and not stripped.startswith("@"):
            if capture_feature_description and current_scenario is None:
                feature_description.append(stripped)
            elif capture_rule_description and current_scenario is None:
                current_rule_description.append(stripped)
            # Otherwise: pending tags reset (was the original behaviour to prevent leakage)
            pending_tags = []

    finalize_examples()
    if feature is None:
        raise ValueError(f"{path}: no Feature, Ability, or Business Need header found")

    # Ensure feature.description carries any text captured before the first Rule/Scenario
    if not feature.description and feature_description:
        feature = FeatureInfo(
            path=feature.path,
            keyword=feature.keyword,
            title=feature.title,
            tags=feature.tags,
            description=tuple(feature_description),
        )

    # Re-bind feature.description on scenarios that captured the original feature instance
    for scenario in scenarios:
        if scenario.feature.path == feature.path and not scenario.feature.description:
            scenario.feature = feature

    return feature, scenarios


def parse_features(features_dir: Path, repo_root: Path) -> tuple[list[FeatureInfo], list[ScenarioInfo]]:
    feature_files = sorted(features_dir.glob("*.feature"), key=lambda item: item.as_posix())
    if not feature_files:
        raise ValueError(f"no .feature files found under {features_dir}")

    features: list[FeatureInfo] = []
    scenarios: list[ScenarioInfo] = []
    for feature_file in feature_files:
        feature, feature_scenarios = parse_feature_file(feature_file, repo_root)
        features.append(feature)
        scenarios.extend(feature_scenarios)
    return features, scenarios


def tags_or_unknown(tags: Iterable[str], unknown: str = "unknown/not-tagged") -> str:
    values = list(tags)
    if not values:
        return unknown
    return ", ".join(f"`{escape_adoc(tag)}`" for tag in values)


def text_or_unknown(value: str | None) -> str:
    if not value:
        return "unknown/not-tagged"
    return escape_adoc(value)


def escape_adoc(value: str) -> str:
    return value.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}")


def escape_table_cell(value: str) -> str:
    """Escape a value for inclusion in an AsciiDoc table cell."""
    # Preserve | as literal (escape only the most disruptive chars)
    return value.replace("|", "\\|").replace("{", "\\{").replace("}", "\\}")


def counter_table(title: str, counter: Counter[str], heading_level: int = 4) -> list[str]:
    prefix = "=" * heading_level
    lines = [f"{prefix} {title}", "", "[cols=\"1,1\",options=\"header\"]", "|===", "|Tag |Scenario count"]
    for key in sorted(counter):
        lines.append(f"|`{escape_adoc(key)}` |{counter[key]}")
    lines.extend(["|===", ""])
    return lines


def render_examples_table(examples: ExamplesBlock) -> list[str]:
    """Render an Examples block as an AsciiDoc table."""
    if not examples.header:
        return []
    lines: list[str] = []
    tag_part = f" {tags_or_unknown(examples.tags, '')}".rstrip() if examples.tags else ""
    name_part = f": {escape_adoc(examples.name)}" if examples.name else ""
    lines.append(f".Examples{name_part}{tag_part}")
    col_count = len(examples.header)
    col_spec = ",".join(["1"] * col_count) if col_count > 0 else "1"
    lines.append(f"[cols=\"{col_spec}\",options=\"header\"]")
    lines.append("|===")
    lines.append("| " + " | ".join(escape_table_cell(h) for h in examples.header))
    for row in examples.rows:
        # Pad row to header length if needed
        padded = list(row) + [""] * (col_count - len(row))
        lines.append("| " + " | ".join(escape_table_cell(cell) for cell in padded[:col_count]))
    lines.append("|===")
    lines.append("")
    return lines


def render_appendix(features: list[FeatureInfo], scenarios: list[ScenarioInfo], features_dir: Path, output_path: Path) -> str:
    status_counts: Counter[str] = Counter()
    validation_counts: Counter[str] = Counter()
    classification_counts: Counter[str] = Counter()
    protocol_smoke_count = 0

    for scenario in scenarios:
        status_tags = scenario.status_tags
        if status_tags:
            status_counts.update(status_tags)
        else:
            status_counts.update([UNKNOWN_STATUS])

        validation_tags = scenario.validation_mode_tags
        if validation_tags:
            validation_counts.update(validation_tags)
        else:
            validation_counts.update([UNKNOWN_VALIDATION_MODE])

        classification_tags = scenario.classification_tags
        if classification_tags:
            classification_counts.update(classification_tags)
        else:
            classification_counts.update([UNKNOWN_CLASSIFICATION])

        if scenario.protocol_smoke:
            protocol_smoke_count += 1

    scenarios_by_feature: OrderedDict[Path, list[ScenarioInfo]] = OrderedDict()
    feature_by_path = {feature.path: feature for feature in features}
    for feature in features:
        scenarios_by_feature[feature.path] = []
    for scenario in scenarios:
        scenarios_by_feature[scenario.feature.path].append(scenario)

    # NOTE: heading levels assume this file is included as a top-level appendix
    # via `[appendix]` in arc42-template.adoc, so `==` is the appendix title and
    # nested sections use `===` and deeper.
    lines: list[str] = [
        "// Generated by scripts/generate-gherkin-requirements-appendix.py; do not edit manually.",
        f"// Source directory: {features_dir.as_posix()}",
        f"// Output path: {output_path.as_posix()}",
        "",
        "[[appendix-gherkin-requirements]]",
        "[appendix]",
        "== Gherkin Requirements",
        "",
        "The `.feature` files under `s3-reactive-api-adapter/src/test/features/requirements` are the source of truth for executable requirements. This generated appendix reproduces, for every declared scenario:",
        "",
        "* the requirement metadata (tags, validation modes, declared status);",
        "* the full Gherkin step text (`Given`/`When`/`Then`/`And`/`But`);",
        "* every `Examples` data table with its own validation-mode tag;",
        "",
        "so that the appendix can be reviewed independently from the source repository while remaining synchronized with the executable feature files.",
        "",
        f"Generated from `{escape_adoc(features_dir.as_posix())}` using `python3 scripts/generate-gherkin-requirements-appendix.py`. Implementation/validation status is taken verbatim from explicit scenario tags; missing tags are reported as `unknown/not-tagged` and must not be upgraded by this report.",
        "",
        "=== Summary",
        "",
        f"* Feature files scanned: {len(features)}",
        f"* Scenarios discovered: {len(scenarios)}",
        f"* Protocol-smoke scenarios: {protocol_smoke_count}",
        "",
    ]
    lines.extend(counter_table("Status tag counts", status_counts, heading_level=4))
    lines.extend(counter_table("Validation mode tag counts", validation_counts, heading_level=4))
    lines.extend(counter_table("Classification tag counts", classification_counts, heading_level=4))

    lines.extend([
        "=== Scenarios by Feature and Requirement ID",
        "",
        "Scenario entries are grouped first by feature file/title, then by requirement group. When a scenario ID uses a final letter suffix such as `REQ-S3-001-A`, the requirement group is shown as `REQ-S3-001` while the full scenario ID remains listed under requirement IDs.",
        "",
    ])

    for feature_path, feature_scenarios in scenarios_by_feature.items():
        feature = feature_by_path[feature_path]
        lines.extend([
            f"==== {escape_adoc(feature.path.as_posix())}",
            "",
            f"* Feature title: {escape_adoc(feature.keyword)}: {escape_adoc(feature.title)}",
            f"* Feature tags: {tags_or_unknown(feature.tags)}",
        ])
        if feature.description:
            lines.append("* Feature description:")
            lines.append("+")
            lines.append("----")
            for desc_line in feature.description:
                lines.append(desc_line)
            lines.append("----")
        lines.append("")

        groups: OrderedDict[str, list[ScenarioInfo]] = OrderedDict()
        for scenario in feature_scenarios:
            groups.setdefault(scenario.requirement_group, []).append(scenario)

        for requirement_group, group_scenarios in groups.items():
            lines.extend([
                f"===== {escape_adoc(requirement_group)}",
                "",
            ])
            for scenario in group_scenarios:
                protocol_smoke = "yes" if scenario.protocol_smoke else "no"
                lines.extend([
                    f"====== {escape_adoc(scenario.kind)}: {escape_adoc(scenario.name)}",
                    "",
                    "[cols=\"1h,3\",options=\"header\"]",
                    "|===",
                    "|Metadata |Value",
                    f"|Source line |`{scenario.feature.path.as_posix()}:{scenario.line}`",
                    f"|Rule |{text_or_unknown(scenario.rule)}",
                    f"|Requirement ID(s) |{tags_or_unknown(scenario.requirement_ids, UNKNOWN_REQUIREMENT)}",
                    f"|Classification |{tags_or_unknown(scenario.classification_tags, UNKNOWN_CLASSIFICATION)}",
                    f"|Capability area tags |{tags_or_unknown(scenario.capability_area_tags)}",
                    f"|Validation mode tags |{tags_or_unknown(scenario.validation_mode_tags, UNKNOWN_VALIDATION_MODE)}",
                    f"|Declared/validated status |{tags_or_unknown(scenario.status_tags, UNKNOWN_STATUS)}",
                    f"|Protocol smoke |`{protocol_smoke}`",
                    f"|All tags |{tags_or_unknown(scenario.all_tags)}",
                    "|===",
                    "",
                ])
                if scenario.rule_description:
                    lines.append(".Rule description")
                    lines.append("----")
                    for desc_line in scenario.rule_description:
                        lines.append(desc_line)
                    lines.append("----")
                    lines.append("")

                # Gherkin steps as a literal block (preserves Given/When/Then formatting)
                if scenario.steps:
                    lines.append(".Gherkin steps")
                    lines.append("[source,gherkin]")
                    lines.append("----")
                    for step in scenario.steps:
                        lines.append(f"  {step.keyword} {step.text}")
                    lines.append("----")
                    lines.append("")

                # Each Examples block as its own table
                for examples in scenario.examples:
                    lines.extend(render_examples_table(examples))

    return "\n".join(lines).rstrip() + "\n"


def build_arg_parser() -> argparse.ArgumentParser:
    repo_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description="Generate or check the ARC42 Gherkin requirements appendix.")
    parser.add_argument(
        "--features-dir",
        default="s3-reactive-api-adapter/src/test/features/requirements",
        help="Directory containing requirement .feature files (default: %(default)s)",
    )
    parser.add_argument(
        "--output",
        default="docs/arc42/generated/gherkin-requirements.adoc",
        help="AsciiDoc output path (default: %(default)s)",
    )
    parser.add_argument("--check", action="store_true", help="Fail if the generated output differs from the file on disk")
    parser.set_defaults(repo_root=repo_root)
    return parser


def resolve_repo_path(repo_root: Path, value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else repo_root / path


def main(argv: list[str] | None = None) -> int:
    parser = build_arg_parser()
    args = parser.parse_args(argv)
    repo_root: Path = args.repo_root
    features_dir = resolve_repo_path(repo_root, args.features_dir)
    output_path = resolve_repo_path(repo_root, args.output)

    try:
        features, scenarios = parse_features(features_dir, repo_root)
        generated = render_appendix(
            features=features,
            scenarios=scenarios,
            features_dir=features_dir.relative_to(repo_root),
            output_path=output_path.relative_to(repo_root),
        )
    except Exception as exc:  # noqa: BLE001 - command-line tool should report cleanly.
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    if args.check:
        if not output_path.exists():
            print(f"ERROR: {output_path.relative_to(repo_root)} does not exist; run generator first", file=sys.stderr)
            return 1
        existing = output_path.read_text(encoding="utf-8")
        if existing != generated:
            diff = difflib.unified_diff(
                existing.splitlines(keepends=True),
                generated.splitlines(keepends=True),
                fromfile=f"current/{output_path.relative_to(repo_root)}",
                tofile=f"generated/{output_path.relative_to(repo_root)}",
            )
            print("ERROR: generated Gherkin requirements appendix is stale", file=sys.stderr)
            for index, line in enumerate(diff):
                if index >= 200:
                    print("... diff truncated after 200 lines", file=sys.stderr)
                    break
                print(line, end="", file=sys.stderr)
            return 1
        print(f"OK: {output_path.relative_to(repo_root)} is fresh ({len(scenarios)} scenarios from {len(features)} feature files)")
        return 0

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(generated, encoding="utf-8")
    print(f"Wrote {output_path.relative_to(repo_root)} ({len(scenarios)} scenarios from {len(features)} feature files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
