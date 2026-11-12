// Package backendtls 管理「后端自己终结 TLS」这件事的运行时状态。
//
// 背景：证书在后台管理页面上传（TLS 目录），但后端到底用不用它，
// 过去只能靠改 .env 里的 SERVER_SCHEME，改完还得重启。这里把这件事变成
// 一个运行时开关：页面上切换即生效，不用重启、不用改环境变量。
//
// 两个关键设计：
//
//  1. 开关状态落盘（backend_tls 文件），这样蓝绿双槽位的两个实例
//     能看到同一份配置（各自轮询同步）。
//  2. 证书不是启动时读进内存的，而是握手时现读现用（带 mtime 缓存）。
//     所以在页面上重新上传证书后，新的连接立刻用新证书，不用重启。
package backendtls

import (
	"crypto/tls"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const (
	// CertFile / KeyFile 是证书与私钥的文件名，与后台上传证书的落盘位置一致。
	CertFile = "fullchain.pem"
	KeyFile  = "privkey.pem"
	// SwitchFile 记录后端是否启用 HTTPS（内容 on / off）。
	SwitchFile = "backend_tls"
)

// Dir 证书与开关文件所在目录：nginx 容器只读挂载同一目录。
func Dir() string {
	if d := strings.TrimSpace(os.Getenv("TLS_DIR")); d != "" {
		return d
	}
	return "/var/lib/patnest/tls"
}

// Manager 持有后端 TLS 的开关状态与证书。
type Manager struct {
	// dir 为空时每次动态读 TLS_DIR 环境变量。
	// 动态读的好处是测试里可以随时改 TLS_DIR 指向临时目录；
	// 生产里 TLS_DIR 是启动时定好的，两者行为一致。
	dir string

	mu       sync.Mutex
	cached   *tls.Certificate
	certMod  time.Time
	keyMod   time.Time
	enabled  bool
	initOnce sync.Once
}

// New 创建 Manager。dir 为空时用默认目录（动态读 TLS_DIR）。
func New(dir string) *Manager {
	return &Manager{dir: strings.TrimSpace(dir)}
}

var (
	defaultOnce sync.Once
	defaultMgr  *Manager
)

// Default 返回进程内共用的 Manager。
//
// 后台接口改开关、监听器读开关，必须是同一个实例，否则页面点了没反应。
// 延迟创建是因为证书目录依赖 TLS_DIR 环境变量，而这个变量是 .env 加载后
// 才有的（config.Load 里调 godotenv.Load）。
func Default() *Manager {
	defaultOnce.Do(func() {
		defaultMgr = New("")
	})
	return defaultMgr
}

// Dir 返回证书目录。
func (m *Manager) Dir() string {
	if m.dir != "" {
		return m.dir
	}
	return Dir()
}

// Enabled 返回后端当前是否接受 HTTPS 连接。
func (m *Manager) Enabled() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.enabled
}

// Init 决定初始状态。
//
// 优先级：落盘的开关文件 > defaultOn。
// 落盘文件存在时它就是权威（页面上点过就是以它为准）；
// 没有落盘文件时用 defaultOn（也就是 .env 的 SERVER_SCHEME）。
//
// defaultOn 为 true 但证书不可用时返回错误——启动即要求 HTTPS 时，
// 不能悄悄退回明文。
func (m *Manager) Init(defaultOn bool) error {
	var initErr error
	m.initOnce.Do(func() {
		on := defaultOn
		raw, err := os.ReadFile(m.switchPath())
		switch {
		case err == nil:
			on = strings.EqualFold(strings.TrimSpace(string(raw)), "on")
		case defaultOn:
			if _, cerr := m.loadCert(); cerr != nil {
				initErr = fmt.Errorf("SERVER_SCHEME=https 但证书不可用: %w", cerr)
				on = false
			} else {
				// 落盘，让另一个槽位的实例也知道
				_ = m.writeSwitch(true)
			}
		}
		m.mu.Lock()
		m.enabled = on
		m.mu.Unlock()
	})
	return initErr
}

// SetEnabled 切换开关并落盘。
//
// 打开前会先确认证书可用：没有证书却"开着 HTTPS"，握手一定失败，
// 不如在这里就报错，让页面上提示用户先上传证书。
func (m *Manager) SetEnabled(on bool) error {
	if on {
		if _, err := m.loadCert(); err != nil {
			return err
		}
	}
	if err := m.writeSwitch(on); err != nil {
		return err
	}
	m.mu.Lock()
	m.enabled = on
	m.mu.Unlock()
	return nil
}

// Watch 轮询落盘文件，把别的实例（比如蓝绿另一个槽位）改的开关同步过来。
//
// 为什么需要轮询：页面上的切换请求只会打到其中一个实例，
// 另一个实例还在跑旧状态。用轮询（几秒一次）比做集群配置简单得多。
func (m *Manager) Watch(stop <-chan struct{}, onChange func(bool)) {
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-stop:
			return
		case <-ticker.C:
			raw, err := os.ReadFile(m.switchPath())
			if err != nil {
				continue
			}
			on := strings.EqualFold(strings.TrimSpace(string(raw)), "on")
			if on == m.Enabled() {
				continue
			}
			if on {
				// 证书还没上传就同步不了：保持原状态，等用户上传证书后再同步
				if _, err := m.loadCert(); err != nil {
					continue
				}
			}
			m.mu.Lock()
			m.enabled = on
			m.mu.Unlock()
			if onChange != nil {
				onChange(on)
			}
		}
	}
}

// TLSConfig 返回给 tls.Server 用的配置。
//
// GetCertificate 在每次握手时调用，证书被重新上传后新连接立刻用新证书。
func (m *Manager) TLSConfig() *tls.Config {
	return &tls.Config{
		MinVersion:     tls.VersionTLS12,
		GetCertificate: m.getCertificate,
	}
}

func (m *Manager) getCertificate(*tls.ClientHelloInfo) (*tls.Certificate, error) {
	return m.loadCert()
}

// loadCert 读取证书；文件没变就用缓存。
func (m *Manager) loadCert() (*tls.Certificate, error) {
	certPath := filepath.Join(m.Dir(), CertFile)
	keyPath := filepath.Join(m.Dir(), KeyFile)
	certInfo, err := os.Stat(certPath)
	if err != nil {
		return nil, fmt.Errorf("证书文件不存在: %s", certPath)
	}
	keyInfo, err := os.Stat(keyPath)
	if err != nil {
		return nil, fmt.Errorf("私钥文件不存在: %s", keyPath)
	}

	m.mu.Lock()
	defer m.mu.Unlock()
	if m.cached != nil && m.certMod.Equal(certInfo.ModTime()) && m.keyMod.Equal(keyInfo.ModTime()) {
		return m.cached, nil
	}
	pair, err := tls.LoadX509KeyPair(certPath, keyPath)
	if err != nil {
		return nil, fmt.Errorf("证书与私钥不匹配或格式有误: %w", err)
	}
	m.cached = &pair
	m.certMod = certInfo.ModTime()
	m.keyMod = keyInfo.ModTime()
	return m.cached, nil
}

// HasCert 判断目录里有没有可用的证书（不校验配对，只看文件在不在）。
func (m *Manager) HasCert() bool {
	_, err1 := os.Stat(filepath.Join(m.Dir(), CertFile))
	_, err2 := os.Stat(filepath.Join(m.Dir(), KeyFile))
	return err1 == nil && err2 == nil
}

// switchPath 开关文件路径。
func (m *Manager) switchPath() string { return filepath.Join(m.Dir(), SwitchFile) }

// writeSwitch 落盘开关状态。
func (m *Manager) writeSwitch(on bool) error {
	if err := os.MkdirAll(m.Dir(), 0o700); err != nil {
		return fmt.Errorf("创建证书目录失败: %w", err)
	}
	content := "off"
	if on {
		content = "on"
	}
	if err := os.WriteFile(m.switchPath(), []byte(content), 0o600); err != nil {
		return fmt.Errorf("写入开关文件失败: %w", err)
	}
	return nil
}
