#!/usr/bin/env python3
"""Generate swagger.json from the route table in internal/router/router.go.

The backend has ~200 routes spread across many handler files. Instead of
sprinkling swag annotations over every handler (and keeping them in sync by
hand), this script reads the single source of truth - router.go - and emits an
OpenAPI 2.0 document that gin-swagger serves. Re-run it whenever routes change:

    python internal/swagger/gen_swagger.py

The generated file is embedded into the binary with go:embed, so no generator
or extra files are needed at runtime.
"""

import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROUTER = os.path.normpath(os.path.join(HERE, "..", "router", "router.go"))
OUT = os.path.join(HERE, "swagger.json")

GROUP_RE = re.compile(r'^\s*(\w+)\s*:=\s*(\w+)\.Group\("([^"]*)"\)')
ROUTE_RE = re.compile(
    r'^\s*(\w+)\.(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\("([^"]*)",\s*handler\.(\w+)'
)
PARAM_RE = re.compile(r":(\w+)")

TAG_ORDER = [
    "系统",
    "认证与账号",
    "机构浏览",
    "地图与定位",
    "支付回调",
    "主人端",
    "机构端",
    "管理后台",
    "消息通知",
    "售后工单",
    "文件上传",
    "其他",
]


def tag_for(path):
    if path.startswith("/api/v1/admin"):
        return "管理后台"
    if path.startswith("/api/v1/sitter/"):
        return "机构端"
    if path.startswith("/api/v1/pet-owner/"):
        return "主人端"
    if path.startswith("/api/v1/auth"):
        return "认证与账号"
    if path.startswith("/api/v1/geo"):
        return "地图与定位"
    if path.startswith("/api/v1/support"):
        return "售后工单"
    if path.startswith("/api/v1/upload"):
        return "文件上传"
    if path.startswith("/api/v1/payments"):
        return "支付回调"
    if path.startswith("/api/v1/sitters"):
        return "机构浏览"
    if "/notifications" in path:
        return "消息通知"
    if path in ("/health", "/api/v1/version"):
        return "系统"
    return "其他"


def humanize(name):
    parts = re.sub(r"(?<!^)(?=[A-Z])", " ", name).split()
    return " ".join(w.capitalize() for w in parts)


def collect_routes():
    prefix = {"r": ""}
    secured = {"r": False}
    routes = []
    with open(ROUTER, "r", encoding="utf-8") as fh:
        for lineno, line in enumerate(fh, 1):
            m = GROUP_RE.match(line)
            if m:
                name, parent, path = m.groups()
                base = prefix.get(parent, "")
                prefix[name] = (base.rstrip("/") + path) if path else base
                secured[name] = secured.get(parent, False) or name == "protected"
                continue
            m = ROUTE_RE.match(line)
            if m:
                group, method, path, handler = m.groups()
                if group not in prefix:
                    raise SystemExit("unknown group %s at router.go:%d" % (group, lineno))
                full = prefix[group].rstrip("/") + path
                if not full.startswith("/"):
                    full = "/" + full
                routes.append(
                    {
                        "path": full,
                        "method": method,
                        "handler": handler,
                        "secured": secured.get(group, False),
                        "line": lineno,
                    }
                )
    return routes


def build_operation(route, op_id):
    raw_path = route["path"]
    swagger_path = PARAM_RE.sub(r"{\1}", raw_path)
    method = route["method"].lower()
    handler = route["handler"]
    tag = tag_for(raw_path)

    params = []
    for name in PARAM_RE.findall(raw_path):
        params.append(
            {
                "name": name,
                "in": "path",
                "required": True,
                "type": "string",
                "description": "路径参数 " + name,
            }
        )

    consumes = None
    if "/upload/" in raw_path:
        consumes = ["multipart/form-data"]
        params.append(
            {
                "name": "file",
                "in": "formData",
                "required": True,
                "type": "file",
                "description": "要上传的文件",
            }
        )
    elif method in ("post", "put", "patch"):
        params.append(
            {
                "name": "body",
                "in": "body",
                "required": False,
                "description": "JSON 请求体，字段明细见 docs/03-接口文档.md",
                "schema": {"type": "object"},
            }
        )

    responses = {
        "200": {"description": "成功", "schema": {"$ref": "#/definitions/ApiResponse"}},
        "400": {"description": "参数错误", "schema": {"$ref": "#/definitions/ApiResponse"}},
        "401": {"description": "未登录或令牌失效", "schema": {"$ref": "#/definitions/ApiResponse"}},
        "403": {"description": "无权限", "schema": {"$ref": "#/definitions/ApiResponse"}},
        "404": {"description": "资源不存在", "schema": {"$ref": "#/definitions/ApiResponse"}},
        "500": {"description": "服务端错误", "schema": {"$ref": "#/definitions/ApiResponse"}},
    }

    auth_note = "需要 Bearer Token（登录后取 access_token）。" if route["secured"] else "公开接口，无需登录。"
    op = {
        "tags": [tag],
        "summary": humanize(handler),
        "description": "后端 handler: `%s`（router.go:%d）。%s 响应统一为 `code/message/data`；请求字段明细见 `docs/03-接口文档.md`。"
        % (handler, route["line"], auth_note),
        "operationId": op_id,
        "produces": ["application/json"],
        "parameters": params,
        "responses": responses,
    }
    if consumes:
        op["consumes"] = consumes
    if route["secured"]:
        op["security"] = [{"BearerAuth": []}]
    return swagger_path, method, op


def main():
    routes = collect_routes()
    paths = {}
    used_ids = {}
    for route in routes:
        n = used_ids.get(route["handler"], 0) + 1
        used_ids[route["handler"]] = n
        op_id = "%s_%d" % (route["handler"], n)
        path, method, op = build_operation(route, op_id)
        paths.setdefault(path, {})[method] = op

    doc = {
        "swagger": "2.0",
        "info": {
            "title": "PatNest API",
            "description": (
                "宠栖 PatNest 宠物寄养服务预约系统接口文档。\n\n"
                "- 由 `internal/router/router.go` 自动生成，改动路由后运行 "
                "`python internal/swagger/gen_swagger.py` 重新生成。\n"
                "- 所有接口统一返回 `code` / `message` / `data`，`code=0` 表示成功。\n"
                "- 需要登录的接口点右上角 Authorize，填入 `Bearer <access_token>`。\n"
                "- 请求字段的准确定义见仓库 `docs/03-接口文档.md`。\n"
            ),
            "version": "1.0",
        },
        "basePath": "/",
        "schemes": ["http", "https"],
        "consumes": ["application/json"],
        "produces": ["application/json"],
        "securityDefinitions": {
            "BearerAuth": {
                "type": "apiKey",
                "name": "Authorization",
                "in": "header",
                "description": "填写 Bearer <access_token>，token 由 /api/v1/auth/login 返回",
            }
        },
        "tags": [{"name": t} for t in TAG_ORDER],
        "paths": paths,
        "definitions": {
            "ApiResponse": {
                "type": "object",
                "properties": {
                    "code": {"type": "integer", "example": 0, "description": "业务码，0 表示成功"},
                    "message": {"type": "string", "example": "ok"},
                    "data": {"description": "业务数据，结构随接口而变"},
                },
            }
        },
    }

    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(doc, fh, ensure_ascii=False, indent=2)
        fh.write("\n")

    print("routes parsed :", len(routes))
    print("unique paths  :", len(paths))
    print("operations    :", sum(len(v) for v in paths.values()))
    print("secured ops   :", sum(1 for r in routes if r["secured"]))
    print("written       :", OUT)


if __name__ == "__main__":
    main()
