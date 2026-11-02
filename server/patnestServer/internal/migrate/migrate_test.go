package migrate

import (
	"os"
	"path/filepath"
	"testing"
)

func writeFile(t *testing.T, dir, name, body string) {
	t.Helper()
	if err := os.WriteFile(filepath.Join(dir, name), []byte(body), 0o644); err != nil {
		t.Fatalf("write %s: %v", name, err)
	}
}

func TestLoadSortsAndHashes(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "0010_add_index.sql", "CREATE INDEX IF NOT EXISTS idx_a ON t (a);\n")
	writeFile(t, dir, "0002_create_table.sql", "CREATE TABLE IF NOT EXISTS t (a int);\n")
	writeFile(t, dir, "notes.txt", "ignore me")

	list, err := Load(dir)
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if len(list) != 2 {
		t.Fatalf("expected 2 migrations, got %d", len(list))
	}
	if list[0].Version != "0002" || list[0].Name != "0002_create_table.sql" {
		t.Fatalf("unexpected order: %+v", list)
	}
	if len(list[0].Checksum) != 64 {
		t.Fatalf("checksum should be sha256 hex, got %q", list[0].Checksum)
	}
	if list[0].Checksum == list[1].Checksum {
		t.Fatal("different files should have different checksums")
	}
}

func TestLoadRejectsDuplicateVersion(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "0001_a.sql", "SELECT 1;")
	writeFile(t, dir, "0001_b.sql", "SELECT 2;")

	if _, err := Load(dir); err == nil {
		t.Fatal("expected duplicate version error")
	}
}

func TestSplitName(t *testing.T) {
	cases := map[string]string{
		"0002_system_release.sql": "0002",
		"20261030_hotfix.sql":     "20261030",
	}
	for file, want := range cases {
		got, _ := splitName(file)
		if got != want {
			t.Errorf("splitName(%q) = %q, want %q", file, got, want)
		}
	}
	if v, _ := splitName("no-version.sql"); v != "" {
		t.Errorf("expected empty version for invalid name, got %q", v)
	}
}

func TestIsNonTransactional(t *testing.T) {
	cases := []struct {
		name string
		sql  string
		want bool
	}{
		{"plain", "CREATE TABLE t (a int);", false},
		{"marker first", "-- migrate:no-transaction\nCREATE INDEX CONCURRENTLY IF NOT EXISTS i ON t (a);", true},
		{"marker after comments", "-- 说明\n-- 再一行\n-- migrate:no-transaction\nSELECT 1;", true},
		{"marker late", "CREATE TABLE t (a int);\n-- migrate:no-transaction", false},
	}
	for _, c := range cases {
		if got := isNonTransactional(c.sql); got != c.want {
			t.Errorf("%s: isNonTransactional() = %v, want %v", c.name, got, c.want)
		}
	}
}
