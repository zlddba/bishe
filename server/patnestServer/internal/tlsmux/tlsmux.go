// Package tlsmux 让同一个 TCP 端口同时接受明文 HTTP 与 TLS 客户端。
//
// 为什么需要它：后台可以随时打开「后端 HTTPS」开关，而开关切换必须立刻生效，
// 不能要求重启后端。如果只有一种协议，开关就必然要重启（http.Server 的
// 监听方式在启动时就定死了）。两种都收就不用重启。
//
// 怎么分辨：TLS 握手的第一条记录 ContentType 是 0x16，明文 HTTP 请求的第一个
// 字节是 ASCII 字母（GET/POST/HEAD…）。看第一个字节就能确定。
//
// 代价：走 sniffConn 的连接，http.Server 认不出它是 *tls.Conn，
// 所以 Request.TLS 为 nil。仓里没有任何地方依赖 Request.TLS
// （协议判断一律走 X-Forwarded-Proto），所以这个副作用没有实际影响。
package tlsmux

import (
	"bufio"
	"crypto/tls"
	"net"
	"sync"
	"sync/atomic"
)

// TLSProvider 提供当前是否启用 TLS 以及握手用的配置。
type TLSProvider interface {
	Enabled() bool
	TLSConfig() *tls.Config
}

// Listener 在底层监听器上做协议嗅探。
type Listener struct {
	net.Listener
	provider TLSProvider
}

// New 包装一个监听器。
func New(l net.Listener, p TLSProvider) *Listener {
	return &Listener{Listener: l, provider: p}
}

// Accept 接收连接。未启用 TLS 时原样返回。
func (l *Listener) Accept() (net.Conn, error) {
	c, err := l.Listener.Accept()
	if err != nil {
		return nil, err
	}
	if l.provider == nil || !l.provider.Enabled() {
		return c, nil
	}
	return &sniffConn{Conn: c, cfg: l.provider.TLSConfig()}, nil
}

// sniffConn 在第一次读的时候决定这条连接是明文还是 TLS。
type sniffConn struct {
	net.Conn
	cfg *tls.Config

	// decideMu 只保护"首次判定"这一小段：
	// 判定之后就不再需要它了，读取本身可能长时间阻塞（等客户端下一个请求），
	// 绝不能拿着它去挡写路径。
	decideMu sync.Mutex
	decided  bool
	r        *bufio.Reader

	// tlsConn 用原子变量存：写路径要读它，而写路径不能被读路径阻塞。
	tlsConn atomic.Pointer[tls.Conn]
}

// Read 决定协议并转发读取。
func (c *sniffConn) Read(p []byte) (int, error) {
	c.decideMu.Lock()
	if !c.decided {
		if c.r == nil {
			c.r = bufio.NewReader(c.Conn)
		}
		// Peek 不消费数据，所以之后无论走明文还是 TLS 都不会丢字节。
		// 这里阻塞是有上限的：http.Server 在调 Read 之前已经设了
		// ReadHeaderTimeout 的 deadline，客户端不发首字节会被超时断开。
		first, err := c.r.Peek(1)
		if err != nil {
			c.decideMu.Unlock()
			return 0, err
		}
		c.decided = true
		if first[0] == 0x16 { // TLS handshake record
			// 用 r 读而不是原始 conn：Peek 过的那个字节还在缓冲区里，
			// 直接给 tls.Server 会丢掉它，握手就会失败。
			tc := tls.Server(&readerConn{Conn: c.Conn, r: c.r}, c.cfg)
			c.tlsConn.Store(tc)
			c.decideMu.Unlock()
			return tc.Read(p)
		}
		c.decideMu.Unlock()
		return c.r.Read(p)
	}
	c.decideMu.Unlock()

	if tc := c.tlsConn.Load(); tc != nil {
		return tc.Read(p)
	}
	return c.r.Read(p)
}

// Write 响应写回。
//
// 判定为 TLS 的连接必须经 tls.Conn 写：直接写底层会把响应以明文发出去，
// 客户端会看到"HTTP 响应"而不是它要的 TLS 记录，报
// "http: server gave HTTP response to HTTPS client"。
func (c *sniffConn) Write(p []byte) (int, error) {
	if tc := c.tlsConn.Load(); tc != nil {
		return tc.Write(p)
	}
	return c.Conn.Write(p)
}

// IsTLS 返回这条连接最终被判定的协议（判定前为 false）。
func (c *sniffConn) IsTLS() bool {
	return c.tlsConn.Load() != nil
}

// readerConn 把读取来源换成 bufio.Reader，其余还是原连接。
type readerConn struct {
	net.Conn
	r interface {
		Read([]byte) (int, error)
	}
}

func (c *readerConn) Read(p []byte) (int, error) { return c.r.Read(p) }
