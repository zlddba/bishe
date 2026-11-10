package storage

import (
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

var Client *minio.Client

var (
	bucketName string
	publicBase string
	endpoint   string
	// fileBase 对外统一前缀（PUBLIC_FILE_BASE），为空时回退到对象存储直链
	fileBase string
)

type MinIOConfig struct {
	Endpoint  string
	AccessKey string
	SecretKey string
	Bucket    string
	UseSSL    bool
	// PublicBase 对外访问前缀（如 https://api.example.com/files）。
	// 留空则用对象存储直链。
	PublicBase string
}

func InitMinIO(cfg MinIOConfig) error {
	client, err := minio.New(cfg.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.AccessKey, cfg.SecretKey, ""),
		Secure: cfg.UseSSL,
	})
	if err != nil {
		return fmt.Errorf("create minio client: %w", err)
	}

	// Test connection: check if bucket exists, create if not
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	exists, err := client.BucketExists(ctx, cfg.Bucket)
	if err != nil {
		return fmt.Errorf("minio connection failed: %w", err)
	}

	if !exists {
		err = client.MakeBucket(ctx, cfg.Bucket, minio.MakeBucketOptions{})
		if err != nil {
			return fmt.Errorf("create bucket %s: %w", cfg.Bucket, err)
		}
	}

	Client = client
	bucketName = cfg.Bucket
	endpoint = cfg.Endpoint
	scheme := "http"
	if cfg.UseSSL {
		scheme = "https"
	}
	publicBase = fmt.Sprintf("%s://%s/%s", scheme, cfg.Endpoint, cfg.Bucket)
	fileBase = strings.TrimRight(cfg.PublicBase, "/")

	// 图片需要被浏览器直接访问，这里给桶设置匿名只读策略
	policy := fmt.Sprintf(`{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {"AWS": ["*"]},
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::%s/*"]
    }
  ]
}`, cfg.Bucket)
	_ = client.SetBucketPolicy(ctx, cfg.Bucket, policy)

	return nil
}

// Bucket 返回当前使用的桶名
func Bucket() string {
	return bucketName
}

// Endpoint 返回对象存储地址（host:port）
func Endpoint() string {
	return endpoint
}

// 文件链接的「存储相对 / 展示绝对」约定。
//
// 数据库里只存相对路径（/uploads/2026/11/xxx.png），域名、协议、端口一律不入库：
// 换域名、上 HTTPS、切 CDN、加反向代理都只改配置，不用动数据。
//
// 对外输出时再拼成完整地址，配置项是 PUBLIC_FILE_BASE
// （如 https://api.example.com/files，由 Nginx 的 /files/ 反代到对象存储）。
// 拼接发生在 HTTP 边界，见 internal/middleware/fileurl.go。

// FileBase 返回对外文件地址前缀（不含结尾斜杠）。
// 配了 PUBLIC_FILE_BASE 就是统一域名，否则回退对象存储直链，保证老部署能用。
func FileBase() string {
	if b := configuredBase(); b != "" {
		return b
	}
	return publicBase
}

// configuredBase 只认 PUBLIC_FILE_BASE。
//
// 除了 InitMinIO 写入的变量，这里再读一次环境变量，是为了让没走初始化的场景
// （工具、单测）也能拿到前缀——否则中间件在单测里永远是空转。
func configuredBase() string {
	if fileBase != "" {
		return fileBase
	}
	return strings.TrimRight(strings.TrimSpace(os.Getenv("PUBLIC_FILE_BASE")), "/")
}

// FileBaseCandidates 返回存量数据里可能出现的全部绝对前缀。
//
// 客户端可能提交统一域名，也可能提交对象存储直链；协议还可能是 http 或 https
// （部署中途换过证书的机器上两种都有）。入库前都要能识别并还原成相对路径。
func FileBaseCandidates() []string {
	seen := make(map[string]bool, 4)
	out := make([]string, 0, 4)
	for _, b := range []string{configuredBase(), publicBase} {
		b = strings.TrimRight(strings.TrimSpace(b), "/")
		if b == "" {
			continue
		}
		for _, v := range []string{b, swapScheme(b)} {
			if v == "" || seen[v] {
				continue
			}
			seen[v] = true
			out = append(out, v)
		}
	}
	return out
}

func swapScheme(u string) string {
	switch {
	case strings.HasPrefix(u, "http://"):
		return "https://" + strings.TrimPrefix(u, "http://")
	case strings.HasPrefix(u, "https://"):
		return "http://" + strings.TrimPrefix(u, "https://")
	}
	return ""
}

// RelPath 把对象 key 转成入库用的相对路径：
// uploads/2026/11/a.png -> /uploads/2026/11/a.png
func RelPath(objectKey string) string {
	return "/" + strings.TrimPrefix(strings.TrimSpace(objectKey), "/")
}

// AbsoluteURL 把相对路径补成可访问的完整地址；已经是绝对地址的原样返回。
func AbsoluteURL(p string) string {
	p = strings.TrimSpace(p)
	if p == "" {
		return ""
	}
	if strings.HasPrefix(p, "http://") || strings.HasPrefix(p, "https://") || strings.HasPrefix(p, "//") {
		return p
	}
	base := FileBase()
	if base == "" {
		return p
	}
	if !strings.HasPrefix(p, "/") {
		p = "/" + p
	}
	return base + p
}

func Ping() error {
	if Client == nil {
		return fmt.Errorf("minio client not initialized")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_, err := Client.ListBuckets(ctx)
	return err
}
