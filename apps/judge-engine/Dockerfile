ARG GO_VERSION=1.26.3

FROM golang:${GO_VERSION}-bookworm AS engine-build
WORKDIR /src

# 依赖层单独缓存；只改业务代码时无需重新下载模块。
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY . ./

FROM engine-build AS judge-build
RUN --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/judge ./cmd/judge

FROM eclipse-temurin:21.0.13_9-jdk-noble AS java-toolchain

# judge 在本进程内执行用户代码（Compose 用零隔离的 devhost 后端，只用于可信代码开发），
# 因此运行时镜像携带 C/C++、Python 3 与 Java 21 工具链。
# 不可信代码的隔离执行见 deploy/sandbox-linux。
FROM debian:bookworm-slim AS judge
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        g++ \
        python3 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 cherry \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin cherry \
    && install -d -o cherry -g cherry -m 0750 /var/log/cherry-oj \
    && install -d -o cherry -g cherry -m 0700 /var/lib/cherry-oj/store \
    && install -d -o cherry -g cherry -m 0700 /var/lib/cherry-oj/testdata

COPY --from=judge-build /out/judge /usr/local/bin/judge
COPY --from=java-toolchain /opt/java/openjdk /opt/java/openjdk

ENV HOME=/tmp
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:${PATH}
USER 10001:10001
EXPOSE 5051
ENTRYPOINT ["/usr/local/bin/judge"]
