// Package config 提供配置装配机制：默认值 → YAML 文件 → 环境变量，最后校验。
//
// 本包不认识任何具体配置项。每个服务在自己的目录里定义配置类型、默认值与校验规则，
// 于是一个服务的配置写错不会让另一个服务拒绝启动。
//
// 什么该进配置：**运行策略**——比对方式、要不要回传标准答案、各类大小上限、监听地址。
// 它们的共同点是「换个部署环境就可能要改，但和某一次判题请求无关」。
// 什么不该进：随请求变的东西（题目的时空限制、源码、测例），那些走 contract 里的请求类型。
// 判断标准：**进程生命周期内不变的进配置，每次请求都可能不同的进请求。**
package config

import (
	"bytes"
	"fmt"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// EnvPrefix 是所有配置环境变量的前缀。
const EnvPrefix = "CHERRY_OJ"

// Validatable 由各服务的配置类型实现。校验在装配的最后一步执行，
// 把「配错了」挡在启动时，而不是让它伪装成一个合理的判题结论。
type Validatable interface {
	Validate() error
}

// Duration 让配置文件里能写 "60s" 而不是 60000000000。
//
// 契约（judge.schema.json）里坚持用 ns，是因为那是给机器和跨语言调用方看的；
// 配置文件是给人编辑的，可读性优先。两边受众不同，规则可以不同。
type Duration time.Duration

func (d Duration) Std() time.Duration { return time.Duration(d) }
func (d Duration) String() string     { return time.Duration(d).String() }

func (d *Duration) UnmarshalYAML(value *yaml.Node) error {
	var s string
	if err := value.Decode(&s); err != nil {
		return fmt.Errorf("a duration should be written as a string such as \"60s\": %w", err)
	}
	parsed, err := time.ParseDuration(s)
	if err != nil {
		return fmt.Errorf("parse duration %q: %w", s, err)
	}
	*d = Duration(parsed)
	return nil
}

// Logging 是各服务共用的日志落盘规则。
// 服务名由各进程固定提供，避免部署配置把 judge 的日志伪装成另一个服务。
type Logging struct {
	Path  string `yaml:"path"`
	Level string `yaml:"level"`
}

// Validate 供各服务的 Validate 调用；本类型自身不参与装配顺序。
func (l Logging) Validate() error {
	if l.Path == "" {
		return fmt.Errorf("logging.path must not be empty")
	}
	switch l.Level {
	case "DEBUG", "INFO", "WARN", "ERROR":
	default:
		return fmt.Errorf("logging.level must be DEBUG, INFO, WARN or ERROR, got %q", l.Level)
	}
	return nil
}

// Load 按「默认值 → YAML 文件 → 环境变量」的顺序装配配置，最后校验。
//
// path 为空，或文件不存在，都只用默认值 + 环境变量——**这不是错误**。
// 容器化部署里全靠环境变量、根本不挂配置文件，是很常见的做法。
func Load[T Validatable](path string, defaults T) (T, error) {
	cfg := defaults
	var zero T

	if path != "" {
		b, err := os.ReadFile(path)
		switch {
		case err == nil:
			// KnownFields：YAML 里出现结构体没有的字段就报错。
			// 少了这行，一个拼错的 key（reveaExpected）会被静默忽略，
			// 你会盯着「配置明明开了却不生效」查很久。
			dec := yaml.NewDecoder(bytes.NewReader(b))
			dec.KnownFields(true)
			if err := dec.Decode(&cfg); err != nil {
				return zero, fmt.Errorf("parse configuration file %s: %w", path, err)
			}
		case os.IsNotExist(err):
			// 不存在就算了，用默认值 + 环境变量
		default:
			return zero, fmt.Errorf("read configuration file %s: %w", path, err)
		}
	}

	if err := applyEnv(&cfg); err != nil {
		return zero, err
	}
	if err := cfg.Validate(); err != nil {
		return zero, err
	}
	return cfg, nil
}
