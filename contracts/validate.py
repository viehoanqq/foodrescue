"""Validate shared event schemas and checked-in examples.

Install: python -m pip install -r contracts/requirements.txt
Run:     python contracts/validate.py
"""

import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parent
SCHEMAS = {}
for path in (ROOT / "schemas").glob("*.json"):
    schema = json.loads(path.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    SCHEMAS[path.name] = schema

registry = Registry()
for schema in SCHEMAS.values():
    registry = registry.with_resource(schema["$id"], Resource.from_contents(schema))

count = 0
for path in sorted((ROOT / "examples").glob("*.json")):
    schema_name = path.name.replace(".json", ".schema.json")
    if schema_name not in SCHEMAS:
        schema_name = path.name.replace(".", "-", 1).replace(".json", ".schema.json")
    schema = SCHEMAS[schema_name]
    data = json.loads(path.read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, registry=registry, format_checker=FormatChecker())
    validator.validate(data)
    wrong_type = dict(data, eventType="other.invalid.v1")
    try:
        validator.validate(wrong_type)
    except ValidationError:
        pass
    else:
        raise AssertionError(f"Schema failed to reject incorrect eventType: {path.name}")
    assert data["eventType"] == path.stem
    count += 1

print(f"Validated {len(SCHEMAS)} schemas and {count} event examples")

for filename in sys.argv[1:]:
    path = Path(filename)
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    schema_name = data["eventType"] + ".schema.json"
    if schema_name not in SCHEMAS:
        schema_name = data["eventType"].replace(".", "-", 1) + ".schema.json"
    schema = SCHEMAS[schema_name]
    Draft202012Validator(schema, registry=registry, format_checker=FormatChecker()).validate(data)
    print(f"Validated {path} against {schema_name}")
