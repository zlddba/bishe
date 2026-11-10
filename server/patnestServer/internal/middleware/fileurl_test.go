package middleware

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
)

// 文件链接转换的边界行为：库里相对、出口绝对、入口再还原。
const testFileBase = "https://cdn.example.com/files"

func newFileURLEngine(t *testing.T) *gin.Engine {
	t.Helper()
	// FileBase() 只有没被 InitMinIO 初始化时才读环境变量，单测正好走这条路
	t.Setenv("PUBLIC_FILE_BASE", testFileBase)
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.Use(FileURL())
	return r
}

func TestFileURLResponseAbsolutizesRelativePaths(t *testing.T) {
	r := newFileURLEngine(t)
	r.GET("/probe", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"avatar": "/uploads/2026/11/a.png",
			"photos": []string{"/uploads/2026/11/b.png", "/uploads/2026/11/c.png"},
			// 正文中间出现的链接不是字段值，不能被改写
			"note":     "参考 https://cdn.example.com/files/uploads/d.png",
			"relative": "uploads/e.png", // 没有前导斜杠，属于别的东西，不动
		})
	})

	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/probe", nil))

	if w.Code != http.StatusOK {
		t.Fatalf("状态码 = %d, 期望 200", w.Code)
	}
	var got map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &got); err != nil {
		t.Fatalf("响应不是合法 JSON: %v", err)
	}

	wantAvatar := testFileBase + "/uploads/2026/11/a.png"
	if got["avatar"] != wantAvatar {
		t.Errorf("avatar = %v, 期望 %v", got["avatar"], wantAvatar)
	}
	photos := got["photos"].([]any)
	if photos[0] != testFileBase+"/uploads/2026/11/b.png" {
		t.Errorf("photos[0] = %v", photos[0])
	}
	if photos[1] != testFileBase+"/uploads/2026/11/c.png" {
		t.Errorf("photos[1] = %v", photos[1])
	}
	if got["note"] != "参考 https://cdn.example.com/files/uploads/d.png" {
		t.Errorf("正文被误改: %v", got["note"])
	}
	if got["relative"] != "uploads/e.png" {
		t.Errorf("无前导斜杠的值被误改: %v", got["relative"])
	}
}

func TestFileURLResponseLeavesNonJSONAlone(t *testing.T) {
	r := newFileURLEngine(t)
	r.GET("/csv", func(c *gin.Context) {
		c.Header("Content-Type", "text/csv; charset=utf-8")
		_, _ = c.Writer.Write([]byte("cover\n/uploads/2026/11/a.png\n"))
	})

	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/csv", nil))

	if body := w.Body.String(); !strings.Contains(body, "\n/uploads/2026/11/a.png\n") {
		t.Errorf("CSV 被改写: %q", body)
	}
}

func TestFileURLRequestBodyRestoresRelativePaths(t *testing.T) {
	r := newFileURLEngine(t)

	// handler 把解析结果暂存到变量，响应里不带这些值：
	// 否则出站转换会把相对路径又补成绝对地址，测不出入站到底改成了什么。
	var seen struct {
		Avatar string   `json:"avatar"`
		Photos []string `json:"photos"`
		Note   string   `json:"note"`
	}
	r.POST("/probe", func(c *gin.Context) {
		if err := c.ShouldBindJSON(&seen); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"ok": true})
	})

	// 故意混用 https 与 http：客户端可能拿着换证书前的旧地址回传
	payload := `{"avatar":"` + testFileBase + `/uploads/2026/11/a.png",` +
		`"photos":["http://cdn.example.com/files/uploads/2026/11/b.png"],` +
		`"note":"见 https://cdn.example.com/files/uploads/2026/11/c.png"}`
	req := httptest.NewRequest(http.MethodPost, "/probe", bytes.NewBufferString(payload))
	req.Header.Set("Content-Type", "application/json")

	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("状态码 = %d，body=%s", w.Code, w.Body.String())
	}
	if seen.Avatar != "/uploads/2026/11/a.png" {
		t.Errorf("avatar 未还原: %v", seen.Avatar)
	}
	if len(seen.Photos) != 1 || seen.Photos[0] != "/uploads/2026/11/b.png" {
		t.Errorf("数组元素未还原: %v", seen.Photos)
	}
	// 正文里的链接不是字段值，保持原样（避免篡改用户输入）
	if !strings.Contains(seen.Note, "https://cdn.example.com/files/uploads/2026/11/c.png") {
		t.Errorf("正文被误改: %v", seen.Note)
	}
}

func TestFileURLKeepsEmptyStatusCodes(t *testing.T) {
	r := newFileURLEngine(t)
	r.DELETE("/probe", func(c *gin.Context) { c.Status(http.StatusNoContent) })

	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodDelete, "/probe", nil))

	// 缓冲后没有 body，必须把 204 原样刷出去，不能退化成 200
	if w.Code != http.StatusNoContent {
		t.Errorf("状态码 = %d, 期望 204", w.Code)
	}
}

func TestFileURLSkipsWhenBaseMissing(t *testing.T) {
	t.Setenv("PUBLIC_FILE_BASE", "")
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.Use(FileURL())
	r.GET("/probe", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"avatar": "/uploads/2026/11/a.png"})
	})

	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/probe", nil))

	var got map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &got); err != nil {
		t.Fatalf("响应不是合法 JSON: %v", err)
	}
	if got["avatar"] != "/uploads/2026/11/a.png" {
		t.Errorf("未配置前缀时应原样返回，实际 %v", got["avatar"])
	}
}
