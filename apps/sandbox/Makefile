# sandbox 执行器。只支持 Linux x86_64；libseccomp 静态链接，安装后不依赖系统的 libseccomp。
#
#   make                 构建 build/sandbox
#   make CONFIG=/path    改变编译进二进制的受信配置路径（仅测试构建使用）
#   make BUILD=dir OUT=file  中间文件放 dir，二进制直接写到 file

CC ?= cc
CONFIG ?= /etc/cherry-sandbox/executor.conf
BUILD := build
OUT = $(BUILD)/sandbox

CFLAGS := -std=c11 -O2 -g -Wall -Wextra -Werror -Wshadow -Wformat=2 -Wconversion -Wno-sign-conversion \
	-fstack-protector-strong -D_FORTIFY_SOURCE=3 -fPIE -fno-strict-overflow \
	-DSANDBOX_CONFIG='"$(CONFIG)"'
LDFLAGS := -pie -Wl,-z,relro,-z,now,-z,noexecstack
LIBS := -Wl,-Bstatic -lseccomp -Wl,-Bdynamic

SOURCES := $(wildcard src/*.c)
OBJECTS := $(SOURCES:src/%.c=$(BUILD)/%.o)

$(OUT): $(OBJECTS)
	$(CC) $(LDFLAGS) -o $@ $^ $(LIBS)

$(BUILD)/%.o: src/%.c src/sandbox.h | $(BUILD)
	$(CC) $(CFLAGS) -c -o $@ $<

$(BUILD):
	mkdir -p $@

clean:
	rm -rf $(BUILD)

.PHONY: clean
