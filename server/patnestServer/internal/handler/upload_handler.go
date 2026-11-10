package handler

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"net/http"
	"path/filepath"
	"strings"
	"time"

	"patnestServer/internal/storage"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/minio/minio-go/v7"
)

var allowedImageExt = map[string]string{
	".jpg":  "image/jpeg",
	".jpeg": "image/jpeg",
	".png":  "image/png",
	".gif":  "image/gif",
	".webp": "image/webp",
}

// sniffImageType 用文件头部的 magic bytes 判断真实图片格式。
// 只看扩展名等于把上传口当成任意文件的写入口：改名成 .jpg 的脚本会被
// 原样存进对象存储并以公网链接发出去，站内的 <img> 加载不了它，
// 但任何人拿到链接都能把它当成本站托管的 downloadable 资源。
// 返回值是规范化的扩展名，用于替换调用方传来的文件名后缀。
func sniffImageType(head []byte) (contentType string, ext string, ok bool) {
	switch {
	case len(head) >= 8 &&
		head[0] == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G' &&
		head[4] == 0x0d && head[5] == 0x0a && head[6] == 0x1a && head[7] == 0x0a:
		return "image/png", ".png", true
	case len(head) >= 3 && head[0] == 0xff && head[1] == 0xd8 && head[2] == 0xff:
		return "image/jpeg", ".jpg", true
	case len(head) >= 6 && string(head[0:6]) == "GIF89a", len(head) >= 6 && string(head[0:6]) == "GIF87a":
		return "image/gif", ".gif", true
	case len(head) >= 12 && string(head[0:4]) == "RIFF" && string(head[8:12]) == "WEBP":
		return "image/webp", ".webp", true
	}
	return "", "", false
}

// UploadImage handles POST /api/v1/upload/image
func UploadImage(c *gin.Context) {
	if storage.Client == nil {
		InternalError(c, "对象存储未初始化，请检查 MinIO 配置")
		return
	}

	// 上传是站内唯一能把字节写进对象存储的入口，按用户限流；
	// 阈值可通过 UPLOAD_RATE_LIMIT 调整，默认每小时 60 张，够用且不吓人。
	userID := c.GetInt64("user_id")
	if !subjectRateLimit(c, "upload", fmt.Sprintf("u%d", userID), envPositiveInt("UPLOAD_RATE_LIMIT", 60), time.Hour) {
		Fail(c, http.StatusTooManyRequests, 42901, "上传过于频繁，请稍后再试")
		return
	}

	file, err := c.FormFile("file")
	if err != nil {
		BadRequest(c, "请选择要上传的图片")
		return
	}
	if file.Size <= 0 {
		BadRequest(c, "图片内容为空")
		return
	}
	if file.Size > 5*1024*1024 {
		BadRequest(c, "图片不能超过 5MB")
		return
	}

	ext := strings.ToLower(filepath.Ext(file.Filename))
	if _, ok := allowedImageExt[ext]; !ok {
		BadRequest(c, "仅支持 jpg / jpeg / png / gif / webp 格式")
		return
	}

	src, err := file.Open()
	if err != nil {
		InternalError(c, "读取图片失败")
		return
	}
	defer src.Close()

	// 先嗅探真实类型，再决定用什么 ContentType 落盘；扩展名只是第一道筛子。
	reader := bufio.NewReader(src)
	head, err := reader.Peek(12)
	if err != nil && err != io.EOF {
		BadRequest(c, "读取图片失败")
		return
	}
	sniffedType, sniffedExt, ok := sniffImageType(head)
	if !ok {
		BadRequest(c, "文件内容不是有效的图片")
		return
	}
	if allowedImageExt[ext] != sniffedType {
		BadRequest(c, "文件扩展名与实际内容不一致")
		return
	}

	objectKey := fmt.Sprintf("uploads/%s/%s%s", time.Now().Format("2006/01"), uuid.NewString(), sniffedExt)
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	_, err = storage.Client.PutObject(ctx, storage.Bucket(), objectKey, reader, file.Size, minio.PutObjectOptions{
		ContentType: sniffedType,
	})
	if err != nil {
		InternalError(c, "上传到对象存储失败: "+err.Error())
		return
	}

	// 入库用相对路径（/uploads/...）：域名不进数据库。
	// 出站时中间件会补成完整地址，客户端拿到的仍是可直接显示的链接。
	url := storage.RelPath(objectKey)
	c.JSON(http.StatusOK, Response{
		Code:    0,
		Message: "上传成功",
		Data: gin.H{
			"url":  url,
			"key":  objectKey,
			"size": file.Size,
			"name": file.Filename,
		},
	})
}
