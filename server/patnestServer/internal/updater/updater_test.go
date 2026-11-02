package updater

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestPackageLifecycle(t *testing.T) {
	root := t.TempDir()
	cfg := Config{
		Root: root, ManualDir: filepath.Join(root, "manual"), TmpDir: filepath.Join(root, "tmp"),
		QueueDir: filepath.Join(root, "queue"), ProcessingDir: filepath.Join(root, "processing"),
		StatusDir: filepath.Join(root, "status"), StagingDir: filepath.Join(root, "staging"),
		LogsDir: filepath.Join(root, "logs"), BackupsDir: filepath.Join(root, "backups"),
		MaxUploadBytes: 10 << 20, AgentStale: time.Minute,
	}
	if err := cfg.Ensure(); err != nil {
		t.Fatal(err)
	}

	api := []byte("api-binary")
	migrate := []byte("migrate-binary")
	index := []byte("<html>")
	manifest := fmt.Sprintf(`{
  "schema": 1, "version": "9.9.9", "commit": "abc", "build_time": "2026-10-30T00:00:00Z",
  "arch": "linux/amd64", "targets": ["api", "web", "db"],
  "files": {
    "artifacts/patnest-api": {"size": %d, "sha256": "%s"},
    "artifacts/patnest-migrate": {"size": %d, "sha256": "%s"},
    "web/dist/index.html": {"size": %d, "sha256": "%s"}
  }
}`, len(api), testSum(api), len(migrate), testSum(migrate), len(index), testSum(index))

	pkgPath := filepath.Join(cfg.ManualDir, "patnest-docker-9.9.9.zip")
	writeTestZip(t, pkgPath, map[string][]byte{
		"patnest-docker-9.9.9/update-manifest.json":        []byte(manifest),
		"patnest-docker-9.9.9/artifacts/patnest-api":       api,
		"patnest-docker-9.9.9/artifacts/patnest-migrate":   migrate,
		"patnest-docker-9.9.9/web/dist/index.html":         index,
		"patnest-docker-9.9.9/db/migrations/0003_test.sql": []byte("select 1;"),
		"patnest-docker-9.9.9/scripts/install.sh":          []byte("must not be copied"),
	})

	pkg := cfg.Inspect(pkgPath, "manual")
	if !pkg.Valid {
		t.Fatalf("package should be valid: %s", pkg.Error)
	}
	if pkg.Version != "9.9.9" || len(pkg.Targets) != 3 {
		t.Fatalf("unexpected package: %+v", pkg)
	}

	job, err := cfg.CreateJob(pkgPath, nil)
	if err != nil {
		t.Fatal(err)
	}
	if job.State != StateQueued || job.Version != "9.9.9" {
		t.Fatalf("unexpected job: %+v", job)
	}
	jobFile := filepath.Join(cfg.QueueDir, job.ID+".job")
	raw, err := os.ReadFile(jobFile)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(raw), "staging=staging/"+job.ID) {
		t.Fatalf("job must carry a relative staging path:\n%s", raw)
	}
	for _, rel := range []string{"artifacts/patnest-api", "web/dist/index.html", "db/migrations/0003_test.sql"} {
		if _, err := os.Stat(filepath.Join(cfg.StagingDir, job.ID, filepath.FromSlash(rel))); err != nil {
			t.Fatalf("staged file %s missing: %v", rel, err)
		}
	}
	if _, err := os.Stat(filepath.Join(cfg.StagingDir, job.ID, "scripts", "install.sh")); !os.IsNotExist(err) {
		t.Fatal("scripts must not be copied into staging")
	}

	jobs, err := cfg.ListJobs(10)
	if err != nil || len(jobs) != 1 {
		t.Fatalf("list jobs: %v %+v", err, jobs)
	}
	if _, err := cfg.JobDetail(job.ID); err != nil {
		t.Fatal(err)
	}
}

func TestRejectsPathTraversal(t *testing.T) {
	root := t.TempDir()
	cfg := Config{
		Root: root, ManualDir: filepath.Join(root, "manual"), TmpDir: filepath.Join(root, "tmp"),
		QueueDir: filepath.Join(root, "queue"), ProcessingDir: filepath.Join(root, "processing"),
		StatusDir: filepath.Join(root, "status"), StagingDir: filepath.Join(root, "staging"),
		LogsDir: filepath.Join(root, "logs"), BackupsDir: filepath.Join(root, "backups"),
		MaxUploadBytes: 10 << 20, AgentStale: time.Minute,
	}
	_ = cfg.Ensure()
	pkgPath := filepath.Join(cfg.ManualDir, "bad.zip")
	writeTestZip(t, pkgPath, map[string][]byte{
		"demo/update-manifest.json":      []byte(`{"schema":1,"version":"1.0.0","targets":["web"],"files":{}}`),
		"demo/web/dist/index.html":       []byte("<html>"),
		"demo/web/dist/../../escape.txt": []byte("bad"),
	})
	_, err := cfg.CreateJob(pkgPath, []string{"web"})
	if err != nil {
		t.Fatalf("traversal entry should be ignored, not fatal: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, "escape.txt")); !os.IsNotExist(err) {
		t.Fatal("path traversal escaped staging root")
	}
}

func testSum(b []byte) string {
	h := sha256.Sum256(b)
	return hex.EncodeToString(h[:])
}

func TestResolvePackageOnlyFromUploadDirs(t *testing.T) {
	root := t.TempDir()
	cfg := Config{
		Root: root, ManualDir: filepath.Join(root, "manual"), TmpDir: filepath.Join(root, "tmp"),
		QueueDir: filepath.Join(root, "queue"), ProcessingDir: filepath.Join(root, "processing"),
		StatusDir: filepath.Join(root, "status"), StagingDir: filepath.Join(root, "staging"),
		LogsDir: filepath.Join(root, "logs"), BackupsDir: filepath.Join(root, "backups"),
		MaxUploadBytes: 10 << 20, AgentStale: time.Minute,
	}
	if err := cfg.Ensure(); err != nil {
		t.Fatal(err)
	}
	for _, rel := range []string{"tmp/pkg.zip", "manual/pkg.zip"} {
		full := filepath.Join(root, filepath.FromSlash(rel))
		if err := os.WriteFile(full, []byte("zip"), 0o644); err != nil {
			t.Fatal(err)
		}
		got, err := cfg.ResolvePackage(rel)
		if err != nil {
			t.Fatalf("%s should resolve: %v", rel, err)
		}
		if got != full {
			t.Fatalf("unexpected path %s", got)
		}
	}
	if err := os.WriteFile(filepath.Join(root, "queue", "pkg.zip"), []byte("zip"), 0o644); err != nil {
		t.Fatal(err)
	}
	for _, rel := range []string{"queue/pkg.zip", "../etc/passwd.zip", "tmp/../../x.zip", "tmp/pkg.tar"} {
		if _, err := cfg.ResolvePackage(rel); err == nil {
			t.Fatalf("%s must be rejected", rel)
		}
	}
}

func writeTestZip(t *testing.T, dest string, files map[string][]byte) {
	t.Helper()
	f, err := os.Create(dest)
	if err != nil {
		t.Fatal(err)
	}
	zw := zip.NewWriter(f)
	for name, content := range files {
		w, err := zw.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := w.Write(content); err != nil {
			t.Fatal(err)
		}
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}
}
