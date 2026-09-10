from __future__ import annotations

import argparse
import json
import os

from .client import MobileClient, MobileClientError


def main() -> int:
    parser = argparse.ArgumentParser(description="SHADOW mobile gateway client")
    parser.add_argument("goal", nargs="?", help="goal to send to SHADOW")
    parser.add_argument("--url", default=os.environ.get("SHADOW_GATEWAY_URL", "http://127.0.0.1:8787"))
    parser.add_argument("--token", default=os.environ.get("SHADOW_GATEWAY_TOKEN"))
    parser.add_argument("--health", action="store_true", help="check gateway health")
    args = parser.parse_args()

    client = MobileClient(args.url, args.token)
    try:
        result = client.health() if args.health else client.run_goal(args.goal or "Hello SHADOW")
    except (MobileClientError, ValueError) as exc:
        print(json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False))
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
