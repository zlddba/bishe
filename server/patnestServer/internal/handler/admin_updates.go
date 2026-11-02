package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"os"
	"strings"

	"patnestServer/internal/db"
	"patnestServer/internal/updater"

	"github.com/gin-gonic/gin"
)

type applyUpdateRequest struct {
	File    string   `json:"file"`
	Targets []string `json:"targets"`
}

func AdminUpdates(c *gin.Context) {
	cfg := updater.LoadConfig()
	packages, err := cfg.ListPackages()
	if err != nil {
		InternalError(c, "读取更新目录失败: "+err.Error())
		return
	}
	jobs, _ := cfg.ListJobs(20)
	OK(c, gin.H{
		"info":     cfg.Info(),
		"packages": packages,
		"jobs":     jobs,
	})
}

func AdminScanUpdates(c *gin.Context) {
	cfg := updater.LoadConfig()
	packages, err := cfg.ListPackages()
	if err != nil {
		InternalError(c, "扫描更新目录失败: "+err.Error())
		return
	}
	OK(c, gin.H{
		"info":     cfg.Info(),
		"packages": packages,
		"scanned":  len(packages),
	})
}

func AdminUploadUpdate(c *gin.Context) {
	cfg := updater.LoadConfig()
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, cfg.MaxUploadBytes+(2<<20))
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		BadRequest(c, "请选择 zip 更新包: "+err.Error())
		return
	}
	defer file.Close()

	pkg, err := cfg.SaveUpload(file, header)
	if err != nil {
		BadRequest(c, err.Error())
		return
	}
	auditUpdate(c, "UPLOAD_UPDATE", map[string]interface{}{
		"file": pkg.Name, "version": pkg.Version, "sha256": pkg.SHA256,
	})
	OK(c, pkg)
}

func AdminApplyUpdate(c *gin.Context) {
	var req applyUpdateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效")
		return
	}
	if strings.TrimSpace(req.File) == "" {
		BadRequest(c, "缺少更新包文件")
		return
	}

	cfg := updater.LoadConfig()
	pkgPath, err := cfg.ResolvePackage(req.File)
	if err != nil {
		BadRequest(c, err.Error())
		return
	}
	job, err := cfg.CreateJob(pkgPath, req.Targets)
	if err != nil {
		Conflict(c, err.Error())
		return
	}
	auditUpdate(c, "APPLY_UPDATE", map[string]interface{}{
		"job_id": job.ID, "file": job.Source, "version": job.Version,
		"targets": strings.Join(job.Targets, ","),
	})
	Created(c, "更新任务已排队", job)
}

func AdminUpdateJob(c *gin.Context) {
	cfg := updater.LoadConfig()
	job, err := cfg.JobDetail(c.Param("id"))
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			NotFound(c, "更新任务不存在")
			return
		}
		BadRequest(c, err.Error())
		return
	}
	OK(c, job)
}

func auditUpdate(c *gin.Context, action string, detail map[string]interface{}) {
	b, _ := json.Marshal(detail)
	adminID, _ := c.Get("user_id")
	id, _ := adminID.(int64)
	_, _ = db.Pool.Exec(c.Request.Context(),
		"SELECT fn_audit($1, $2, 'update_job', NULL, $3::jsonb)",
		id, action, string(b))
}
