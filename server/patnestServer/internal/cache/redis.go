package cache

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"log"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"
)

// 精简 RESP 客户端：只实现本项目需要的命令，避免额外依赖。

type Client struct {
	addr     string
	password string
	db       int
	timeout  time.Duration
	idle     chan *respConn
	closed   chan struct{}
	once     sync.Once
}

type respConn struct {
	nc net.Conn
	br *bufio.Reader
	bw *bufio.Writer
}

type reply struct {
	kind byte
	str  string
	num  int64
	err  error
	null bool
}

var (
	client *Client
	mu     sync.RWMutex
)

func Init(addr, password string, db int) error {
	if strings.TrimSpace(addr) == "" {
		log.Println("[Redis] SKIPPED: REDIS_ADDR not configured")
		return nil
	}
	c := &Client{
		addr:     addr,
		password: password,
		db:       db,
		timeout:  3 * time.Second,
		idle:     make(chan *respConn, 8),
		closed:   make(chan struct{}),
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := c.Ping(ctx); err != nil {
		return fmt.Errorf("redis ping: %w", err)
	}

	mu.Lock()
	client = c
	mu.Unlock()
	return nil
}

func Enabled() bool {
	mu.RLock()
	defer mu.RUnlock()
	return client != nil
}

func Ping() error {
	c := current()
	if c == nil {
		return errors.New("redis not initialized")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	return c.Ping(ctx)
}

func Close() {
	mu.Lock()
	c := client
	client = nil
	mu.Unlock()
	if c != nil {
		c.Close()
	}
}

func current() *Client {
	mu.RLock()
	defer mu.RUnlock()
	return client
}

func (c *Client) Close() {
	c.once.Do(func() {
		close(c.closed)
		for {
			select {
			case conn := <-c.idle:
				_ = conn.nc.Close()
			default:
				return
			}
		}
	})
}

func (c *Client) Ping(ctx context.Context) error {
	_, err := c.do(ctx, "PING")
	return err
}

func GetInt(key string) (int64, bool, error) {
	c := current()
	if c == nil {
		return 0, false, errors.New("redis not initialized")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	r, err := c.do(ctx, "GET", key)
	if err != nil {
		return 0, false, err
	}
	if r.null {
		return 0, false, nil
	}
	n, convErr := strconv.ParseInt(strings.TrimSpace(r.str), 10, 64)
	if convErr != nil {
		return 0, false, convErr
	}
	return n, true, nil
}

// IncrWindow 计数 +1，首次计数时设置过期时间。
func IncrWindow(key string, ttl time.Duration) (int64, error) {
	c := current()
	if c == nil {
		return 0, errors.New("redis not initialized")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	r, err := c.do(ctx, "INCR", key)
	if err != nil {
		return 0, err
	}
	if r.num == 1 {
		ms := strconv.FormatInt(ttl.Milliseconds(), 10)
		if _, err := c.do(ctx, "PEXPIRE", key, ms); err != nil {
			return r.num, err
		}
	}
	return r.num, nil
}

func Del(key string) error {
	c := current()
	if c == nil {
		return errors.New("redis not initialized")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	_, err := c.do(ctx, "DEL", key)
	return err
}

func currentClientAddr() string {
	c := current()
	if c == nil {
		return ""
	}
	return c.addr
}

// Addr 返回当前 Redis 地址，未启用时为空。
func Addr() string { return currentClientAddr() }

func (c *Client) do(ctx context.Context, args ...string) (reply, error) {
	conn, err := c.borrow(ctx)
	if err != nil {
		return reply{}, err
	}
	if deadline, ok := ctx.Deadline(); ok {
		_ = conn.nc.SetDeadline(deadline)
	} else {
		_ = conn.nc.SetDeadline(time.Now().Add(c.timeout))
	}

	if err := writeCommand(conn.bw, args); err != nil {
		_ = conn.nc.Close()
		return reply{}, err
	}
	if err := conn.bw.Flush(); err != nil {
		_ = conn.nc.Close()
		return reply{}, err
	}
	r, err := readReply(conn.br)
	if err != nil {
		_ = conn.nc.Close()
		return reply{}, err
	}
	c.put(conn)
	if r.err != nil {
		return r, r.err
	}
	return r, nil
}

func (c *Client) borrow(ctx context.Context) (*respConn, error) {
	select {
	case conn := <-c.idle:
		return conn, nil
	default:
	}
	return c.dial(ctx)
}

func (c *Client) put(conn *respConn) {
	select {
	case c.idle <- conn:
	default:
		_ = conn.nc.Close()
	}
}

func (c *Client) dial(ctx context.Context) (*respConn, error) {
	d := net.Dialer{Timeout: c.timeout}
	nc, err := d.DialContext(ctx, "tcp", c.addr)
	if err != nil {
		return nil, err
	}
	conn := &respConn{
		nc: nc,
		br: bufio.NewReaderSize(nc, 4096),
		bw: bufio.NewWriterSize(nc, 4096),
	}
	if c.password != "" {
		if err := writeCommand(conn.bw, []string{"AUTH", c.password}); err != nil {
			_ = nc.Close()
			return nil, err
		}
		if err := conn.bw.Flush(); err != nil {
			_ = nc.Close()
			return nil, err
		}
		if r, err := readReply(conn.br); err != nil || r.err != nil {
			_ = nc.Close()
			if err != nil {
				return nil, err
			}
			return nil, r.err
		}
	}
	if c.db != 0 {
		if err := writeCommand(conn.bw, []string{"SELECT", strconv.Itoa(c.db)}); err != nil {
			_ = nc.Close()
			return nil, err
		}
		if err := conn.bw.Flush(); err != nil {
			_ = nc.Close()
			return nil, err
		}
		if r, err := readReply(conn.br); err != nil || r.err != nil {
			_ = nc.Close()
			if err != nil {
				return nil, err
			}
			return nil, r.err
		}
	}
	return conn, nil
}

func writeCommand(w *bufio.Writer, args []string) error {
	if _, err := fmt.Fprintf(w, "*%d\r\n", len(args)); err != nil {
		return err
	}
	for _, arg := range args {
		if _, err := fmt.Fprintf(w, "$%d\r\n%s\r\n", len(arg), arg); err != nil {
			return err
		}
	}
	return nil
}

func readReply(r *bufio.Reader) (reply, error) {
	line, err := readLine(r)
	if err != nil {
		return reply{}, err
	}
	if len(line) == 0 {
		return reply{}, errors.New("empty redis reply")
	}
	head := line[0]
	body := line[1:]

	switch head {
	case '+':
		return reply{kind: '+', str: body}, nil
	case '-':
		return reply{kind: '-', err: errors.New(body)}, nil
	case ':':
		n, err := strconv.ParseInt(body, 10, 64)
		if err != nil {
			return reply{}, err
		}
		return reply{kind: ':', num: n}, nil
	case '$':
		n, err := strconv.Atoi(body)
		if err != nil {
			return reply{}, err
		}
		if n < 0 {
			return reply{kind: '$', null: true}, nil
		}
		buf := make([]byte, n+2)
		if _, err := ioReadFull(r, buf); err != nil {
			return reply{}, err
		}
		return reply{kind: '$', str: string(buf[:n])}, nil
	default:
		return reply{}, fmt.Errorf("unsupported redis reply type %q", head)
	}
}

func readLine(r *bufio.Reader) (string, error) {
	s, err := r.ReadString('\n')
	if err != nil {
		return "", err
	}
	return strings.TrimSuffix(strings.TrimSuffix(s, "\n"), "\r"), nil
}

func ioReadFull(r *bufio.Reader, buf []byte) (int, error) {
	total := 0
	for total < len(buf) {
		n, err := r.Read(buf[total:])
		total += n
		if err != nil {
			return total, err
		}
	}
	return total, nil
}
