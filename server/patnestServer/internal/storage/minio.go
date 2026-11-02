package storage

import (
	"context"
	"fmt"
	"time"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

var Client *minio.Client

var (
	bucketName string
	publicBase string
	endpoint   string
)

type MinIOConfig struct {
	Endpoint  string
	AccessKey string
	SecretKey string
	Bucket    string
	UseSSL    bool
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

// PublicURL 返回对象的公网访问地址
func PublicURL(objectKey string) string {
	return publicBase + "/" + objectKey
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
