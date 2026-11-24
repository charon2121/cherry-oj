#!/bin/sh
# 用同一套输入运行区间求和夹具。顺序由 problem-build 决定。
set -eu
fixture=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
exec "$1" -toolchain-manifest "$2" -output "$3" -judge-url "$4" \
  -statement "$fixture/statement.md" \
  -generator "$fixture/generator.cpp" -validator "$fixture/validator.cpp" \
  -reference "$fixture/reference.cpp" -oracle "$fixture/oracle.cpp" \
  -case "sample=$fixture/sample.recipe" -sample "sample=$fixture/sample.in" -expected "sample=$fixture/sample.out" \
  -case "endpoints=$fixture/endpoints.recipe" -wrong "endpoints=$fixture/wrong-endpoint.cpp" \
  -case "single-negative=$fixture/single-negative.recipe" \
  -case "random-small=$fixture/random-small.recipe" \
  -case "maximum=$fixture/maximum.recipe" -expected "maximum=$fixture/maximum.out" \
  -case "overflow=$fixture/overflow.recipe" -wrong "overflow=$fixture/wrong-overflow.cpp" \
  -oracle-case endpoints -oracle-case overflow -oracle-case random-small -oracle-case sample -oracle-case single-negative
