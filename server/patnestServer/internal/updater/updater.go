package updater

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime/multipart"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
)

const (
	StateQueued  = "queued"
	StateRunning = "running"
	StateSuccess = "success"
	StateFailed  = "failed"

	maxStageBytes  = 1 << 30
	maxZipEntries  = 20000
	maxManifestSze = 1 << 20
)

var versionRe = regexp.MustCompile(`^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$`)

// jobIDRe 限定任务编号形如 20261104-093012-1f3a9c02。它同时是 JobDetail 的越界防护：
// 编号会被拼进 status/log 的文件路径，放开格式就等于允许路径穿越。
// 提到包级变量，避免每次查询任务状态都重新编译一遍正则。
var jobIDRe = regexp.MustCompile(`^[0-9]{8}-[0-9]{6}-[0-9a-f]{8}$`)

type ManifestFile struct {
	Size   int64  `json:"size"`
	SHA256 string `json:"sha256"`
}

type Manifest struct {
	Schema    int                     `json:"schema"`
	Version   string                  `json:"version"`
	Commit    string                  `json:"commit"`
	BuildTime string                  `json:"build_time"`
	Arch      string                  `json:"arch"`
	Targets   []string                `json:"targets"`
	Files     map[string]ManifestFile `json:"files"`
}

type Config struct {
	Root           string
	ManualDir      string
	TmpDir         string
	QueueDir       string
	ProcessingDir  string
	StatusDir      string
	StagingDir     string
	LogsDir        string
	BackupsDir     string
	ManualHint     string
	MaxUploadBytes int64
	AgentStale     time.Duration
}

type Package struct {
	Name      string   `json:"name"`
	Source    string   `json:"source"`
	Version   string   `json:"version"`
	Commit    string   `json:"commit"`
	BuildTime string   `json:"build_time"`
	Arch      string   `json:"arch"`
	Targets   []string `json:"targets"`
	Size      int64    `json:"size"`
	Modified  string   `json:"modified"`
	SHA256    string   `json:"sha256"`
	Valid     bool     `json:"valid"`
	Error     string   `json:"error,omitempty"`

	prefix   string
	manifest *Manifest
}

type Job struct {
	ID         string   `json:"id"`
	State      string   `json:"state"`
	Step       string   `json:"step"`
	Message    string   `json:"message"`
	Version    string   `json:"version"`
	Targets    []string `json:"targets"`
	Source     string   `json:"source"`
	CreatedAt  string   `json:"created_at"`
	StartedAt  string   `json:"started_at"`
	FinishedAt string   `json:"finished_at"`
	Log        string   `json:"log,omitempty"`
}

type Info struct {
	Root           string `json:"root"`
	ManualDir      string `json:"manual_dir"`
	ManualHint     string `json:"manual_hint"`
	TmpDir         string `json:"tmp_dir"`
	TmpHint        string `json:"tmp_hint"`
	AgentOnline    bool   `json:"agent_online"`
	AgentPID       string `json:"agent_pid"`
	AgentHeartbeat string `json:"agent_heartbeat"`
}

type stagedPackage struct {
	Dir      string
	Version  string
	Targets  []string
	Manifest *Manifest
}

func LoadConfig() Config {
	root := env("UPDATE_DIR", "data/updates")
	manual := env("UPDATE_MANUAL_DIR", filepath.Join(root, "manual"))
	tmp := env("UPDATE_TMP_DIR", filepath.Join(root, "tmp"))
	maxMB := envInt64("UPDATE_MAX_MB", 512)
	stale := envInt64("UPDATE_AGENT_STALE_SECONDS", 30)
	return Config{
		Root:           root,
		ManualDir:      manual,
		TmpDir:         tmp,
		QueueDir:       filepath.Join(root, "queue"),
		ProcessingDir:  filepath.Join(root, "processing"),
		StatusDir:      filepath.Join(root, "status"),
		StagingDir:     filepath.Join(root, "staging"),
		LogsDir:        filepath.Join(root, "logs"),
		BackupsDir:     filepath.Join(root, "backups"),
		ManualHint:     env("UPDATE_MANUAL_HINT", "data/updates/manual"),
		MaxUploadBytes: maxMB << 20,
		AgentStale:     time.Duration(stale) * time.Second,
	}
}

func (c Config) Ensure() error {
	dirs := []string{
		c.Root, c.ManualDir, c.TmpDir, c.QueueDir, c.ProcessingDir,
		c.StatusDir, c.StagingDir, c.LogsDir, c.BackupsDir,
	}
	for _, dir := range dirs {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return err
		}
	}
	return nil
}

func (c Config) Info() Info {
	info := Info{
		Root:       c.Root,
		ManualDir:  c.ManualDir,
		ManualHint: c.ManualHint,
		TmpDir:     c.TmpDir,
		TmpHint:    env("UPDATE_TMP_HINT", "data/updates/tmp"),
	}
	if b, err := os.ReadFile(filepath.Join(c.Root, "agent.pid")); err == nil {
		info.AgentPID = strings.TrimSpace(string(b))
	}
	if b, err := os.ReadFile(filepath.Join(c.Root, "agent.heartbeat")); err == nil {
		info.AgentHeartbeat = strings.TrimSpace(string(b))
		if ts, err := time.Parse(time.RFC3339, info.AgentHeartbeat); err == nil {
			info.AgentOnline = time.Since(ts) <= c.AgentStale
		}
	}
	return info
}

func (c Config) ListPackages() ([]Package, error) {
	if err := c.Ensure(); err != nil {
		return nil, err
	}
	var out []Package
	for _, item := range []struct {
		dir    string
		source string
	}{
		{c.TmpDir, "upload"},
		{filepath.Join(c.Root, "inbox"), "upload"},
		{c.ManualDir, "manual"},
	} {
		entries, err := os.ReadDir(item.dir)
		if err != nil {
			if os.IsNotExist(err) {
				continue
			}
			return nil, err
		}
		for _, entry := range entries {
			if entry.IsDir() || !strings.EqualFold(filepath.Ext(entry.Name()), ".zip") {
				continue
			}
			out = append(out, c.Inspect(filepath.Join(item.dir, entry.Name()), item.source))
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Modified > out[j].Modified })
	return out, nil
}

func (c Config) Inspect(file, source string) Package {
	p := Package{Name: relSlash(c.Root, file), Source: source}
	st, err := os.Stat(file)
	if err != nil {
		p.Error = err.Error()
		return p
	}
	p.Size = st.Size()
	p.Modified = st.ModTime().Format(time.RFC3339)
	if sum, err := fileSHA256(file); err == nil {
		p.SHA256 = sum
	}
	zr, err := zip.OpenReader(file)
	if err != nil {
		p.Error = "不是有效的 zip 文件"
		return p
	}
	defer zr.Close()

	prefix, manifest, err := findManifest(&zr.Reader)
	if err != nil {
		p.Error = err.Error()
		return p
	}
	p.prefix = prefix
	p.manifest = manifest

	if manifest != nil {
		p.Version = manifest.Version
		p.Commit = manifest.Commit
		p.BuildTime = manifest.BuildTime
		p.Arch = manifest.Arch
		p.Targets = unique(manifest.Targets)
	}
	if p.Version == "" {
		p.Version = versionFromPrefix(prefix)
	}
	derived := deriveTargets(zr.File, prefix)
	p.Targets = mergeTargets(p.Targets, derived)

	if !versionRe.MatchString(p.Version) {
		p.Error = "更新包版本号无效"
		return p
	}
	if len(p.Targets) == 0 {
		p.Error = "更新包里没有可识别的后端、页面或迁移文件"
		return p
	}
	if contains(p.Targets, "api") {
		if !zipHas(zr.File, prefix+"artifacts/patnest-api") || !zipHas(zr.File, prefix+"artifacts/patnest-migrate") {
			p.Error = "后端更新缺少 patnest-api 或 patnest-migrate"
			return p
		}
	}
	if contains(p.Targets, "web") && !zipHas(zr.File, prefix+"web/dist/index.html") {
		p.Error = "页面更新缺少 web/dist/index.html"
		return p
	}
	p.Valid = true
	return p
}

func (c Config) ResolvePackage(rel string) (string, error) {
	rel = strings.TrimSpace(strings.ReplaceAll(rel, "\\", "/"))
	clean := path.Clean(rel)
	if clean == "." || strings.HasPrefix(clean, "../") || path.IsAbs(clean) {
		return "", errors.New("更新包路径无效")
	}
	if !strings.HasPrefix(clean, "tmp/") && !strings.HasPrefix(clean, "inbox/") && !strings.HasPrefix(clean, "manual/") {
		return "", errors.New("更新包只能来自 tmp 或 manual 目录")
	}
	if !strings.EqualFold(path.Ext(clean), ".zip") {
		return "", errors.New("只支持 zip 更新包")
	}
	full := filepath.Join(c.Root, filepath.FromSlash(clean))
	root, err := filepath.Abs(c.Root)
	if err != nil {
		return "", err
	}
	abs, err := filepath.Abs(full)
	if err != nil {
		return "", err
	}
	if !strings.HasPrefix(abs, root+string(os.PathSeparator)) {
		return "", errors.New("更新包路径越界")
	}
	if _, err := os.Stat(abs); err != nil {
		return "", err
	}
	return abs, nil
}

func (c Config) SaveUpload(file multipart.File, header *multipart.FileHeader) (Package, error) {
	if err := c.Ensure(); err != nil {
		return Package{}, err
	}
	if header.Size > c.MaxUploadBytes {
		return Package{}, fmt.Errorf("更新包超过 %d MB", c.MaxUploadBytes>>20)
	}
	name := safeFilename(header.Filename)
	if !strings.EqualFold(filepath.Ext(name), ".zip") {
		return Package{}, errors.New("只支持 zip 更新包")
	}
	name = time.Now().Format("20060102-150405") + "-" + name
	full := filepath.Join(c.TmpDir, name)
	tmp := full + ".part"
	out, err := os.OpenFile(tmp, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		return Package{}, err
	}
	n, copyErr := io.Copy(out, io.LimitReader(file, c.MaxUploadBytes+1))
	closeErr := out.Close()
	if copyErr != nil || closeErr != nil || n > c.MaxUploadBytes {
		_ = os.Remove(tmp)
		if n > c.MaxUploadBytes {
			return Package{}, fmt.Errorf("更新包超过 %d MB", c.MaxUploadBytes>>20)
		}
		if copyErr != nil {
			return Package{}, copyErr
		}
		return Package{}, closeErr
	}
	if err := os.Rename(tmp, full); err != nil {
		_ = os.Remove(tmp)
		return Package{}, err
	}
	pkg := c.Inspect(full, "upload")
	if !pkg.Valid {
		_ = os.Remove(full)
		return Package{}, errors.New(pkg.Error)
	}
	return pkg, nil
}

func (c Config) CreateJob(pkgPath string, targets []string) (Job, error) {
	if err := c.Ensure(); err != nil {
		return Job{}, err
	}
	lock := filepath.Join(c.Root, ".job.lock")
	lf, err := os.OpenFile(lock, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o644)
	if err != nil {
		if os.IsExist(err) {
			return Job{}, errors.New("已有更新任务正在创建或执行")
		}
		return Job{}, err
	}
	_ = lf.Close()
	defer os.Remove(lock)

	if id, ok := c.activeJob(); ok {
		return Job{}, fmt.Errorf("更新任务 %s 正在执行，请等待完成", id)
	}

	pkg := c.Inspect(pkgPath, sourceOf(c.Root, pkgPath))
	if !pkg.Valid {
		return Job{}, errors.New(pkg.Error)
	}
	targets = mergeTargets(targets, nil)
	if len(targets) == 0 {
		targets = pkg.Targets
	}
	for _, target := range targets {
		if target != "api" && target != "web" && target != "db" {
			return Job{}, fmt.Errorf("未知更新目标: %s", target)
		}
		if !contains(pkg.Targets, target) {
			return Job{}, fmt.Errorf("更新包不包含目标: %s", target)
		}
	}
	// 迁移由 release-api 里的 migrate 容器执行，因此迁移必须和后端程序一起发布。
	if contains(targets, "db") && !contains(pkg.Targets, "api") {
		return Job{}, errors.New("更新包只包含数据库迁移，缺少后端程序")
	}
	if contains(targets, "api") && !contains(pkg.Targets, "api") {
		return Job{}, errors.New("更新包不包含后端程序")
	}

	id := time.Now().Format("20060102-150405") + "-" + strings.Split(uuid.NewString(), "-")[0]
	staged, err := c.stage(pkgPath, id)
	if err != nil {
		return Job{}, err
	}
	now := time.Now().Format(time.RFC3339)
	job := Job{
		ID: id, State: StateQueued, Step: "queued",
		Message: "等待服务器更新代理", Version: pkg.Version,
		Targets: targets, Source: pkg.Name, CreatedAt: now,
	}
	content := kv(map[string]string{
		"id": job.ID, "version": job.Version,
		"targets": strings.Join(job.Targets, ","),
		"staging": relSlash(c.Root, staged.Dir), "source": job.Source, "created_at": job.CreatedAt,
	})
	if err := atomicWrite(filepath.Join(c.QueueDir, id+".job"), content); err != nil {
		_ = os.RemoveAll(staged.Dir)
		return Job{}, err
	}
	c.writeStatus(job)
	return job, nil
}

func (c Config) ListJobs(limit int) ([]Job, error) {
	if err := c.Ensure(); err != nil {
		return nil, err
	}
	entries, err := os.ReadDir(c.StatusDir)
	if err != nil {
		return nil, err
	}
	var jobs []Job
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".status") {
			continue
		}
		if job, err := c.readJob(strings.TrimSuffix(entry.Name(), ".status")); err == nil {
			jobs = append(jobs, job)
		}
	}
	sort.Slice(jobs, func(i, j int) bool { return jobs[i].CreatedAt > jobs[j].CreatedAt })
	if limit > 0 && len(jobs) > limit {
		jobs = jobs[:limit]
	}
	return jobs, nil
}

func (c Config) JobDetail(id string) (Job, error) {
	job, err := c.readJob(id)
	if err != nil {
		return Job{}, err
	}
	if b, err := os.ReadFile(filepath.Join(c.LogsDir, id+".log")); err == nil {
		lines := strings.Split(strings.TrimRight(string(b), "\n"), "\n")
		if len(lines) > 120 {
			lines = lines[len(lines)-120:]
		}
		job.Log = strings.Join(lines, "\n")
	}
	return job, nil
}

func (c Config) activeJob() (string, bool) {
	for _, dir := range []string{c.QueueDir, c.ProcessingDir} {
		entries, _ := os.ReadDir(dir)
		for _, entry := range entries {
			if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".job") {
				return strings.TrimSuffix(entry.Name(), ".job"), true
			}
		}
	}
	jobs, _ := c.ListJobs(20)
	for _, job := range jobs {
		if job.State == StateQueued || job.State == StateRunning {
			return job.ID, true
		}
	}
	return "", false
}

func (c Config) stage(pkgPath, id string) (*stagedPackage, error) {
	pkg := c.Inspect(pkgPath, sourceOf(c.Root, pkgPath))
	if !pkg.Valid {
		return nil, errors.New(pkg.Error)
	}
	zr, err := zip.OpenReader(pkgPath)
	if err != nil {
		return nil, err
	}
	defer zr.Close()

	dest := filepath.Join(c.StagingDir, id)
	_ = os.RemoveAll(dest)
	if err := os.MkdirAll(dest, 0o755); err != nil {
		return nil, err
	}
	cleanup := func(err error) (*stagedPackage, error) {
		_ = os.RemoveAll(dest)
		return nil, err
	}

	var total int64
	entries := 0
	for _, f := range zr.File {
		if f.FileInfo().IsDir() {
			continue
		}
		if f.Mode()&os.ModeSymlink != 0 {
			return cleanup(errors.New("更新包不能包含符号链接"))
		}
		entries++
		if entries > maxZipEntries {
			return cleanup(errors.New("更新包文件数量过多"))
		}
		name := normalizeZipName(f.Name)
		if !strings.HasPrefix(name, pkg.prefix) {
			continue
		}
		rel := strings.TrimPrefix(name, pkg.prefix)
		if !allowedEntry(rel) {
			continue
		}
		clean, err := safeRel(rel)
		if err != nil {
			return cleanup(err)
		}
		total += int64(f.UncompressedSize64)
		if total > maxStageBytes {
			return cleanup(errors.New("更新包解压后超过 1GB"))
		}
		target := filepath.Join(dest, filepath.FromSlash(clean))
		if !strings.HasPrefix(target, dest+string(os.PathSeparator)) {
			return cleanup(errors.New("更新包路径越界"))
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return cleanup(err)
		}
		mode := os.FileMode(0o644)
		if strings.HasPrefix(clean, "artifacts/") {
			mode = 0o755
		}
		out, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
		if err != nil {
			return cleanup(err)
		}
		rc, err := f.Open()
		if err != nil {
			_ = out.Close()
			return cleanup(err)
		}
		_, copyErr := io.Copy(out, io.LimitReader(rc, maxStageBytes+1))
		_ = rc.Close()
		_ = out.Close()
		if copyErr != nil {
			return cleanup(copyErr)
		}
	}

	if contains(pkg.Targets, "api") {
		if err := requireFile(dest, "artifacts/patnest-api"); err != nil {
			return cleanup(err)
		}
		if err := requireFile(dest, "artifacts/patnest-migrate"); err != nil {
			return cleanup(err)
		}
	}
	if contains(pkg.Targets, "web") {
		if err := requireFile(dest, "web/dist/index.html"); err != nil {
			return cleanup(err)
		}
	}
	if pkg.manifest != nil {
		for rel, meta := range pkg.manifest.Files {
			if meta.SHA256 == "" {
				continue
			}
			if _, err := safeRel(rel); err != nil || !allowedEntry(rel) {
				return cleanup(fmt.Errorf("manifest 包含非法文件路径: %s", rel))
			}
			file := filepath.Join(dest, filepath.FromSlash(rel))
			sum, err := fileSHA256(file)
			if err != nil {
				return cleanup(fmt.Errorf("校验 %s 失败: %w", rel, err))
			}
			if !strings.EqualFold(sum, meta.SHA256) {
				return cleanup(fmt.Errorf("%s 的 SHA256 校验失败", rel))
			}
		}
	}
	return &stagedPackage{
		Dir: dest, Version: pkg.Version, Targets: pkg.Targets, Manifest: pkg.manifest,
	}, nil
}

func (c Config) readJob(id string) (Job, error) {
	if !jobIDRe.MatchString(id) {
		return Job{}, errors.New("任务编号无效")
	}
	b, err := os.ReadFile(filepath.Join(c.StatusDir, id+".status"))
	if err != nil {
		return Job{}, err
	}
	values := parseKV(string(b))
	job := Job{
		ID: id, State: values["state"], Step: values["step"], Message: values["message"],
		Version: values["version"], Targets: splitList(values["targets"]),
		Source: values["source"], CreatedAt: values["created_at"],
		StartedAt: values["started_at"], FinishedAt: values["finished_at"],
	}
	if job.State == "" {
		job.State = StateQueued
	}
	return job, nil
}

func (c Config) writeStatus(job Job) {
	content := kv(map[string]string{
		"id": job.ID, "state": job.State, "step": job.Step, "message": job.Message,
		"version": job.Version, "targets": strings.Join(job.Targets, ","),
		"source": job.Source, "created_at": job.CreatedAt,
		"started_at": job.StartedAt, "finished_at": job.FinishedAt,
	})
	_ = atomicWrite(filepath.Join(c.StatusDir, job.ID+".status"), content)
}

func findManifest(zr *zip.Reader) (string, *Manifest, error) {
	var best *zip.File
	for _, f := range zr.File {
		name := normalizeZipName(f.Name)
		if path.Base(name) != "update-manifest.json" {
			continue
		}
		if best == nil || strings.Count(name, "/") < strings.Count(normalizeZipName(best.Name), "/") {
			best = f
		}
	}
	if best == nil {
		prefix := detectPrefix(zr.File)
		if prefix == "" {
			return "", nil, errors.New("找不到 update-manifest.json，也不是完整的 patnest-docker 发布包")
		}
		return prefix, nil, nil
	}
	if best.UncompressedSize64 > maxManifestSze {
		return "", nil, errors.New("update-manifest.json 过大")
	}
	rc, err := best.Open()
	if err != nil {
		return "", nil, err
	}
	defer rc.Close()
	b, err := io.ReadAll(io.LimitReader(rc, maxManifestSze))
	if err != nil {
		return "", nil, err
	}
	var m Manifest
	if err := json.Unmarshal(b, &m); err != nil {
		return "", nil, errors.New("update-manifest.json 不是有效 JSON")
	}
	if !versionRe.MatchString(m.Version) {
		return "", nil, errors.New("update-manifest.json 里的版本号无效")
	}
	name := normalizeZipName(best.Name)
	return strings.TrimSuffix(name, "update-manifest.json"), &m, nil
}

func detectPrefix(files []*zip.File) string {
	for _, f := range files {
		name := normalizeZipName(f.Name)
		if strings.HasSuffix(name, "/artifacts/patnest-api") {
			return strings.TrimSuffix(name, "artifacts/patnest-api")
		}
		if strings.HasSuffix(name, "/web/dist/index.html") {
			return strings.TrimSuffix(name, "web/dist/index.html")
		}
	}
	return ""
}

func deriveTargets(files []*zip.File, prefix string) []string {
	var out []string
	hasAPI, hasWeb, hasDB := false, false, false
	for _, f := range files {
		name := normalizeZipName(f.Name)
		if !strings.HasPrefix(name, prefix) {
			continue
		}
		rel := strings.TrimPrefix(name, prefix)
		if rel == "artifacts/patnest-api" {
			hasAPI = true
		}
		if strings.HasPrefix(rel, "web/dist/") {
			hasWeb = true
		}
		if strings.HasPrefix(rel, "db/migrations/") && strings.HasSuffix(rel, ".sql") {
			hasDB = true
		}
	}
	if hasAPI {
		out = append(out, "api")
	}
	if hasWeb {
		out = append(out, "web")
	}
	if hasDB {
		out = append(out, "db")
	}
	return out
}

func versionFromPrefix(prefix string) string {
	base := strings.Trim(strings.SplitN(prefix, "/", 2)[0], "/")
	for _, p := range []string{"patnest-docker-", "patnest-update-"} {
		if strings.HasPrefix(base, p) {
			return strings.TrimPrefix(base, p)
		}
	}
	return ""
}

func allowedEntry(rel string) bool {
	rel = path.Clean(strings.TrimPrefix(rel, "/"))
	if rel == "update-manifest.json" {
		return true
	}
	if rel == "artifacts/patnest-api" || rel == "artifacts/patnest-migrate" {
		return true
	}
	if strings.HasPrefix(rel, "web/dist/") {
		return true
	}
	if strings.HasPrefix(rel, "db/migrations/") && strings.HasSuffix(rel, ".sql") {
		rest := strings.TrimPrefix(rel, "db/migrations/")
		return rest != "" && !strings.Contains(rest, "/")
	}
	return false
}

func safeRel(rel string) (string, error) {
	clean := path.Clean(strings.TrimPrefix(rel, "/"))
	if clean == "." || clean == ".." || strings.HasPrefix(clean, "../") || strings.Contains(clean, "\\") {
		return "", errors.New("更新包包含非法路径")
	}
	return clean, nil
}

func normalizeZipName(name string) string {
	name = strings.ReplaceAll(name, "\\", "/")
	for strings.HasPrefix(name, "./") {
		name = strings.TrimPrefix(name, "./")
	}
	return name
}

func requireFile(root, rel string) error {
	st, err := os.Stat(filepath.Join(root, filepath.FromSlash(rel)))
	if err != nil || st.IsDir() || st.Size() == 0 {
		return fmt.Errorf("更新包缺少 %s", rel)
	}
	return nil
}

func fileSHA256(file string) (string, error) {
	f, err := os.Open(file)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

func atomicWrite(file, content string) error {
	tmp := file + ".tmp"
	if err := os.WriteFile(tmp, []byte(content), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, file)
}

func kv(values map[string]string) string {
	keys := make([]string, 0, len(values))
	for k := range values {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var b strings.Builder
	for _, k := range keys {
		v := strings.ReplaceAll(values[k], "\n", " ")
		v = strings.ReplaceAll(v, "\r", " ")
		b.WriteString(k)
		b.WriteString("=")
		b.WriteString(v)
		b.WriteString("\n")
	}
	return b.String()
}

func parseKV(content string) map[string]string {
	out := make(map[string]string)
	for _, line := range strings.Split(content, "\n") {
		if i := strings.IndexByte(line, '='); i > 0 {
			out[strings.TrimSpace(line[:i])] = strings.TrimSpace(line[i+1:])
		}
	}
	return out
}

func safeFilename(name string) string {
	name = filepath.Base(strings.ReplaceAll(name, "\\", "/"))
	name = regexp.MustCompile(`[^0-9A-Za-z._-]+`).ReplaceAllString(name, "_")
	name = strings.Trim(name, "._")
	if name == "" {
		name = "update.zip"
	}
	if len(name) > 120 {
		name = name[len(name)-120:]
	}
	return name
}

func sourceOf(root, file string) string {
	rel := relSlash(root, file)
	if strings.HasPrefix(rel, "manual/") {
		return "manual"
	}
	return "upload"
}

func relSlash(root, file string) string {
	rel, err := filepath.Rel(root, file)
	if err != nil {
		return filepath.Base(file)
	}
	return filepath.ToSlash(rel)
}

func zipHas(files []*zip.File, name string) bool {
	for _, f := range files {
		if normalizeZipName(f.Name) == name {
			return true
		}
	}
	return false
}

func unique(items []string) []string {
	seen := map[string]bool{}
	var out []string
	for _, item := range items {
		item = strings.ToLower(strings.TrimSpace(item))
		if item == "" || seen[item] {
			continue
		}
		seen[item] = true
		out = append(out, item)
	}
	return out
}

func mergeTargets(a, b []string) []string {
	return unique(append(append([]string{}, a...), b...))
}

func contains(items []string, want string) bool {
	for _, item := range items {
		if item == want {
			return true
		}
	}
	return false
}

func splitList(s string) []string {
	if s == "" {
		return nil
	}
	return unique(strings.Split(s, ","))
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envInt64(key string, def int64) int64 {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			return n
		}
	}
	return def
}
