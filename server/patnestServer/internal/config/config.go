package config

import (
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

type Config struct {
	AppPort        int
	AppSlot        string
	DBHost         string
	DBPort         int
	DBName         string
	DBUser         string
	DBPass         string
	JWTSecret      string
	MinIOEndpoint  string
	MinIOAccessKey string
	MinIOSecretKey string
	MinIOBucket    string
	MinIOUseSSL    bool
	RedisAddr      string
	RedisPassword  string
	RedisDB        int
}

func Load() *Config {
	_ = godotenv.Load()

	return &Config{
		AppPort:        getInt("APP_PORT", 9200),
		AppSlot:        getStr("APP_SLOT", "blue"),
		DBHost:         getStr("DB_HOST", "localhost"),
		DBPort:         getInt("DB_PORT", 5432),
		DBName:         getStr("DB_NAME", "patnest_db"),
		DBUser:         getStr("DB_USER", "postgres"),
		DBPass:         getStr("DB_PASSWORD", ""),
		JWTSecret:      getStr("JWT_SECRET", "change-me-in-production"),
		MinIOEndpoint:  getStr("MINIO_ENDPOINT", "localhost:9000"),
		MinIOAccessKey: getStr("MINIO_ACCESS_KEY", ""),
		MinIOSecretKey: getStr("MINIO_SECRET_KEY", ""),
		MinIOBucket:    getStr("MINIO_BUCKET", "patnest-uploads"),
		MinIOUseSSL:    getBool("MINIO_USE_SSL", false),
		RedisAddr:      getStr("REDIS_ADDR", ""),
		RedisPassword:  getStr("REDIS_PASSWORD", ""),
		RedisDB:        getInt("REDIS_DB", 0),
	}
}

func (c *Config) DBDSN() string {
	return "postgres://" + c.DBUser + ":" + c.DBPass + "@" + c.DBHost + ":" + strconv.Itoa(c.DBPort) + "/" + c.DBName +
		"?sslmode=disable&timezone=Asia/Shanghai"
}

func getStr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func getInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func getBool(key string, def bool) bool {
	if v := os.Getenv(key); v != "" {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return def
}
