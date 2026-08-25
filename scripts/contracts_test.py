#!/usr/bin/env python3
"""contracts/ 的轻量结构测试，只依赖 Python 标准库。"""

from __future__ import annotations

import json
import unittest
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
CONTRACTS = ROOT / "contracts"


def load(name: str) -> dict[str, Any]:
    with (CONTRACTS / name).open(encoding="utf-8") as file:
        return json.load(file)


def walk(value: Any):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def resolve_pointer(document: Any, fragment: str) -> Any:
    if not fragment:
        return document
    if not fragment.startswith("/"):
        raise AssertionError(f"只支持 JSON Pointer fragment，得到 #{fragment}")
    current = document
    for raw_part in fragment[1:].split("/"):
        part = raw_part.replace("~1", "/").replace("~0", "~")
        current = current[int(part)] if isinstance(current, list) else current[part]
    return current


class ContractsTest(unittest.TestCase):
    def test_expected_contracts_exist(self) -> None:
        expected = {
            "judge.schema.json",
            "submission.json",
            "problem-judge-snapshot.schema.json",
            "execution-profile.schema.json",
            "judge-input.schema.json",
            "judge-events.schema.json",
            "run.schema.json",
            "verdict.json",
            "web-api.openapi.json",
        }
        self.assertEqual({path.name for path in CONTRACTS.glob("*.json")}, expected)

    def test_all_local_refs_resolve(self) -> None:
        for path in CONTRACTS.glob("*.json"):
            document = load(path.name)
            for node in walk(document):
                if not isinstance(node, dict) or "$ref" not in node:
                    continue
                ref = node["$ref"]
                if ref.startswith("http://") or ref.startswith("https://"):
                    continue
                filename, separator, fragment = ref.partition("#")
                target_name = filename or path.name
                target_path = CONTRACTS / target_name
                self.assertTrue(target_path.is_file(), f"{path.name}: $ref 文件不存在: {ref}")
                target = load(target_name)
                try:
                    resolve_pointer(target, fragment if separator else "")
                except (KeyError, IndexError, ValueError, TypeError) as error:
                    self.fail(f"{path.name}: 无法解析 $ref {ref}: {error}")

    def test_examples_cover_required_fields(self) -> None:
        for path in CONTRACTS.glob("*.json"):
            for node in walk(load(path.name)):
                if not isinstance(node, dict) or "examples" not in node:
                    continue
                required = set(node.get("required", []))
                properties = set(node.get("properties", {}))
                for example in node["examples"]:
                    if not isinstance(example, dict):
                        continue
                    self.assertFalse(
                        required - set(example),
                        f"{path.name}: example 缺少 {sorted(required - set(example))}",
                    )
                    if node.get("additionalProperties") is False:
                        self.assertFalse(
                            set(example) - properties,
                            f"{path.name}: example 含未知字段 {sorted(set(example) - properties)}",
                        )

    def test_judge_v2_names_are_the_only_resource_names(self) -> None:
        definitions = load("judge.schema.json")["definitions"]
        request = definitions["JudgeRequest"]
        result = definitions["JudgeResult"]
        case_result = definitions["CaseResult"]

        self.assertTrue(
            {
                "problemVersionId",
                "testDataVersionId",
                "languageId",
            }.issubset(request["required"])
        )
        self.assertNotIn("language", request["properties"])
        self.assertIn("environmentFingerprint", result["required"])
        for schema in (result, case_result):
            self.assertIn("cpuNs", schema["properties"])
            self.assertIn("memoryBytes", schema["properties"])
            self.assertNotIn("time", schema["properties"])
            self.assertNotIn("memory", schema["properties"])
        self.assertIn("caseResults", result["properties"])
        self.assertNotIn("cases", result["properties"])

    def test_event_payloads_are_closed_and_source_free(self) -> None:
        definitions = load("judge-events.schema.json")["definitions"]
        payload_names = ("JudgeRequested", "JudgeStarted", "JudgeCompleted", "JudgeFailed")
        forbidden = {
            "source",
            "completeSource",
            "judgeTemplate",
            "testData",
            "password",
            "jwt",
            "token",
        }
        for name in payload_names:
            payload = definitions[name]
            self.assertIs(payload.get("additionalProperties"), False, name)
            self.assertTrue(forbidden.isdisjoint(payload["properties"]), name)

        requested = definitions["JudgeRequested"]
        self.assertEqual(
            set(requested["properties"]),
            {"submissionId", "judgeInputContractVersion"},
        )

    def test_web_api_uses_envelope_and_problem_details(self) -> None:
        document = load("web-api.openapi.json")
        self.assertEqual(document["openapi"], "3.1.2")

        schemas = document["components"]["schemas"]
        self.assertEqual(set(schemas["SystemStatusSuccess"]["required"]), {"data", "meta"})
        self.assertEqual(set(schemas["ApiMeta"]["required"]), {"requestId"})
        self.assertTrue(
            {"type", "title", "status", "code", "meta"}.issubset(
                schemas["ApiProblem"]["required"]
            )
        )

        status_responses = document["paths"]["/api/status"]["get"]["responses"]
        success = status_responses["200"]
        self.assertIn("X-Request-Id", success["headers"])
        self.assertEqual(
            success["content"]["application/json"]["schema"]["$ref"],
            "#/components/schemas/SystemStatusSuccess",
        )

        problem_response = document["components"]["responses"]["ApiProblemResponse"]
        self.assertIn("X-Request-Id", problem_response["headers"])
        self.assertEqual(
            problem_response["content"]["application/problem+json"]["schema"]["$ref"],
            "#/components/schemas/ApiProblem",
        )

    def test_web_api_request_ids_match_in_examples(self) -> None:
        document = load("web-api.openapi.json")
        success = document["paths"]["/api/status"]["get"]["responses"]["200"]
        success_example = success["content"]["application/json"]["example"]
        problem_response = document["components"]["responses"]["ApiProblemResponse"]
        problem_example = problem_response["content"]["application/problem+json"]["example"]

        self.assertEqual(
            success_example["meta"]["requestId"],
            document["components"]["schemas"]["SystemStatusSuccess"]["examples"][0][
                "meta"
            ]["requestId"],
        )
        self.assertIn(problem_example["meta"]["requestId"], problem_example["instance"])


if __name__ == "__main__":
    unittest.main()
