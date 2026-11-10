package middleware

import (
	"bytes"
	"io"
	"strconv"
	"strings"

	"patnestServer/internal/storage"

	"github.com/gin-gonic/gin"
)

// 文件链接在 HTTP 边界完成「相对 ↔ 绝对」的转换。
//
// 数据库只存相对路径（/uploads/2026/11/xxx.png），对外一律是完整地址：
//
//	出站："/uploads/a.png"          →  "https://api.example.com/files/uploads/a.png"
//	入站："https://.../uploads/a.png" →  "/uploads/a.png"
//
// 为什么放在中间件而不是每个 handler 里拼：图片字段散在 20 多个接口的返回里
// （用户头像、宠物照片、机构封面与相册、资质证件、寄养动态、评价图片……），
// 逐个改 SELECT 或逐个拼字符串一定会漏，而且以后新加的接口还得记得写。
// 放在这里，配置一次 PUBLIC_FILE_BASE 就全局生效，也不会漏掉数组字段。
//
// 为什么不放在数据库用函数拼：那需要在每条 SELECT 的每一列上套 fn_file_url()，
// 数组列还要套 fn_file_urls()，同样是几十处；而且写入侧还得反向剥前缀，
// 否则客户端把完整链接回传一次，库里又变回绝对地址了。边界转换一次解决两头。

// relFilePrefix 入库相对路径的固定前缀，与 upload_handler 的 objectKey 对应。
const relFilePrefix = "/uploads/"

// maxJSONBody 入站改写的体积上限：JSON 请求体都很小，超过这个量说明不是
// 普通的表单提交（或有人在打大包），直接放过比冒险缓冲进内存更稳妥。
const maxJSONBody = 4 << 20

// FileURL 出站补全、入站还原文件链接。
func FileURL() gin.HandlerFunc {
	return func(c *gin.Context) {
		normalizeRequestFileURL(c)

		base := storage.FileBase()
		if base == "" {
			// 没配对外前缀（例如本地只跑 API 不跑 Nginx）：库里是相对路径，
			// 这里原样返回，由前端按自己所在域名处理，不做拼接。
			c.Next()
			return
		}

		w := &fileURLWriter{ResponseWriter: c.Writer, base: base}
		c.Writer = w
		c.Next()
		w.finish()
	}
}

// fileURLWriter 只缓冲 JSON 响应，其余（CSV 导出、文件下载、图片流）直接透传。
type fileURLWriter struct {
	gin.ResponseWriter
	base        string
	buf         bytes.Buffer
	passthrough bool
}

func (w *fileURLWriter) Write(b []byte) (int, error) {
	if !w.passthrough && !strings.HasPrefix(w.Header().Get("Content-Type"), "application/json") {
		// 第一次写入时 Content-Type 已经定了（gin 的 c.JSON 会先设 header 再写 body），
		// 从这里开始不再拦截，避免把 CSV / 下载文件整块读进内存。
		w.passthrough = true
	}
	if w.passthrough {
		return w.ResponseWriter.Write(b)
	}
	return w.buf.Write(b)
}

func (w *fileURLWriter) WriteString(s string) (int, error) {
	return w.Write([]byte(s))
}

// finish 把缓冲的 JSON 里的相对路径补成完整地址后一次性写出。
func (w *fileURLWriter) finish() {
	if w.passthrough {
		return
	}
	if w.buf.Len() == 0 {
		// 没有响应体的状态码（204 / 304 等）：这里必须主动把 gin 记录的状态刷出去，
		// 否则底层 http 服务会按默认 200 处理，DELETE 之类会返回错的状态码。
		w.ResponseWriter.WriteHeaderNow()
		return
	}
	// 只匹配「JSON 字符串以 /uploads/ 开头」的情况：
	// 前面那个引号是关键，否则用户评论里出现的同名字符串也会被改写。
	out := bytes.ReplaceAll(w.buf.Bytes(),
		[]byte(`"`+relFilePrefix),
		[]byte(`"`+w.base+relFilePrefix))
	w.Header().Set("Content-Length", strconv.Itoa(len(out)))
	_, _ = w.ResponseWriter.Write(out)
}

// normalizeRequestFileURL 把请求体里的完整链接还原成相对路径。
//
// 客户端上传后拿到的是完整地址，保存时原样回传；如果直接入库，
// 库里就又会变成绝对地址，白改一场。这里统一剥掉前缀。
func normalizeRequestFileURL(c *gin.Context) {
	if !strings.HasPrefix(c.GetHeader("Content-Type"), "application/json") {
		return
	}
	bases := storage.FileBaseCandidates()
	if len(bases) == 0 {
		return
	}
	original := c.Request.Body
	if original == nil {
		return
	}

	body, err := io.ReadAll(io.LimitReader(original, maxJSONBody+1))
	if err != nil || len(body) > maxJSONBody {
		// 读失败或体积超限：把已读部分拼回去，保证 handler 仍能正常解析
		c.Request.Body = io.NopCloser(io.MultiReader(bytes.NewReader(body), original))
		return
	}

	out := body
	for _, b := range bases {
		// 同样要求「前面是引号」，只处理整段就是链接的字段值，
		// 不动正文里引用了链接的普通文本。
		out = bytes.ReplaceAll(out, []byte(`"`+b+`/`), []byte(`"/`))
	}
	if bytes.Equal(out, body) {
		c.Request.Body = io.NopCloser(bytes.NewReader(body))
		return
	}

	c.Request.Body = io.NopCloser(bytes.NewReader(out))
	c.Request.ContentLength = int64(len(out))
	c.Request.Header.Set("Content-Length", strconv.Itoa(len(out)))
}
