package handler

import (
	"context"
	"fmt"
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

// UploadImage handles POST /api/v1/upload/image
func UploadImage(c *gin.Context) {
	if storage.Client == nil {
		InternalError(c, "对象存储未初始化，请检查 MinIO 配置")
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
	contentType, ok := allowedImageExt[ext]
	if !ok {
		BadRequest(c, "仅支持 jpg / jpeg / png / gif / webp 格式")
		return
	}

	src, err := file.Open()
	if err != nil {
		InternalError(c, "读取图片失败")
		return
	}
	defer src.Close()

	objectKey := fmt.Sprintf("uploads/%s/%s%s", time.Now().Format("2006/01"), uuid.NewString(), ext)
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	_, err = storage.Client.PutObject(ctx, storage.Bucket(), objectKey, src, file.Size, minio.PutObjectOptions{
		ContentType: contentType,
	})
	if err != nil {
		InternalError(c, "上传到对象存储失败: "+err.Error())
		return
	}

	url := storage.PublicURL(objectKey)
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
