package config

import (
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

type Config struct {
	AppPort   int
	AppSlot   string
	DBHost    string
	DBPort    int
	DBName    string
	DBUser    string
	DBPass    string
	JWTSecret string
	// JWTAccessTTLMinutes 访问令牌有效期，默认 30 分钟。
	// 桌面端（KMP ApiClient）和后台（axios 拦截器）都会在用访问令牌撞上 401 时
	// 拿 refresh_token 静默续期并重放一次，所以有效期可以设得比较短。
	JWTAccessTTLMinutes int
	MinIOEndpoint       string
	MinIOAccessKey      string
	MinIOSecretKey      string
	MinIOBucket         string
	MinIOUseSSL         bool
	RedisAddr           string
	RedisPassword       string
	RedisDB             int
	// 高德地图 Web 服务 API（机构地理编码 / POI / 距离计算），仅后端使用，禁止下发客户端
	AMapKey     string
	AMapBaseURL string
	// 高德 Web 端（JS API）Key：桌面端"地图选点"页面用它加载交互地图。
	// JS Key 天生是公开的（靠域名/IP 白名单与 securityJsCode 约束），与上面的 Web服务 Key 不是同一张。
	AMapJSKey      string
	AMapJSSecurity string
}

func Load() *Config {
	_ = godotenv.Load()

	return &Config{
		AppPort:             getInt("APP_PORT", 9200),
		AppSlot:             getStr("APP_SLOT", "blue"),
		DBHost:              getStr("DB_HOST", "localhost"),
		DBPort:              getInt("DB_PORT", 5432),
		DBName:              getStr("DB_NAME", "patnest_db"),
		DBUser:              getStr("DB_USER", "postgres"),
		DBPass:              getStr("DB_PASSWORD", ""),
		JWTSecret:           getStr("JWT_SECRET", "change-me-in-production"),
		JWTAccessTTLMinutes: getInt("JWT_ACCESS_TTL_MINUTES", 30),
		MinIOEndpoint:       getStr("MINIO_ENDPOINT", "localhost:9000"),
		MinIOAccessKey:      getStr("MINIO_ACCESS_KEY", ""),
		MinIOSecretKey:      getStr("MINIO_SECRET_KEY", ""),
		MinIOBucket:         getStr("MINIO_BUCKET", "patnest-uploads"),
		MinIOUseSSL:         getBool("MINIO_USE_SSL", false),
		RedisAddr:           getStr("REDIS_ADDR", ""),
		RedisPassword:       getStr("REDIS_PASSWORD", ""),
		RedisDB:             getInt("REDIS_DB", 0),
		AMapKey:             getStr("AMAP_KEY", ""),
		AMapBaseURL:         getStr("AMAP_BASE_URL", "https://restapi.amap.com"),
		AMapJSKey:           getStr("AMAP_JS_KEY", ""),
		AMapJSSecurity:      getStr("AMAP_JS_SECURITY_CODE", ""),
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
