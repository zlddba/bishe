package version

import "runtime"

// 以下变量在编译期通过 -ldflags "-X ..." 注入，默认值仅用于本地开发。
var (
	Version   = "dev"
	Commit    = "unknown"
	BuildTime = "unknown"
)

// Slot 表示当前进程所处的蓝绿槽位（blue / green），由 APP_SLOT 环境变量写入。
var Slot = "blue"

type Info struct {
	Version   string `json:"version"`
	Commit    string `json:"commit"`
	BuildTime string `json:"build_time"`
	GoVersion string `json:"go_version"`
	Slot      string `json:"slot"`
}

func Current() Info {
	return Info{
		Version:   Version,
		Commit:    Commit,
		BuildTime: BuildTime,
		GoVersion: runtime.Version(),
		Slot:      Slot,
	}
}
