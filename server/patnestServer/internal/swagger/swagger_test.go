package swagger

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"regexp"
	"sort"
	"strings"
	"testing"

	"patnestServer/internal/router"

	"github.com/gin-gonic/gin"
)

var pathParam = regexp.MustCompile(`:([A-Za-z_][A-Za-z0-9_]*)`)

type generatedSpec struct {
	Swagger string                                `json:"swagger"`
	Paths   map[string]map[string]json.RawMessage `json:"paths"`
}

// TestGeneratedSpecCoversRoutes fails when a route was added to router.go but
// gen_swagger.py was not re-run. This is what keeps the generated document from
// silently drifting away from the real route table.
func TestGeneratedSpecCoversRoutes(t *testing.T) {
	gin.SetMode(gin.TestMode)

	var doc generatedSpec
	if err := json.Unmarshal(Spec(), &doc); err != nil {
		t.Fatalf("embedded swagger.json is invalid: %v", err)
	}
	if doc.Swagger != "2.0" {
		t.Fatalf("unexpected swagger version %q", doc.Swagger)
	}

	declared := make(map[string]bool)
	for path, ops := range doc.Paths {
		for method := range ops {
			declared[strings.ToLower(method)+" "+path] = true
		}
	}

	var missing []string
	for _, route := range router.Setup().Routes() {
		if strings.HasPrefix(route.Path, "/swagger") {
			continue
		}
		path := pathParam.ReplaceAllString(route.Path, "{$1}")
		key := strings.ToLower(route.Method) + " " + path
		if !declared[key] {
			missing = append(missing, key)
		}
	}
	sort.Strings(missing)
	if len(missing) > 0 {
		t.Errorf("swagger.json is missing %d route(s); run `python internal/swagger/gen_swagger.py`:\n%s",
			len(missing), strings.Join(missing, "\n"))
	}
}

func TestDocAndUIEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	// Use the real router so CORS and FileURL middlewares are covered too.
	r := router.Setup()
	Register(r)

	doc := httptest.NewRecorder()
	r.ServeHTTP(doc, httptest.NewRequest(http.MethodGet, DocPath, nil))
	if doc.Code != http.StatusOK {
		t.Fatalf("%s returned %d", DocPath, doc.Code)
	}
	var parsed map[string]interface{}
	if err := json.Unmarshal(doc.Body.Bytes(), &parsed); err != nil {
		t.Fatalf("%s is not valid JSON: %v", DocPath, err)
	}
	if parsed["swagger"] != "2.0" {
		t.Fatalf("unexpected spec version: %v", parsed["swagger"])
	}

	ui := httptest.NewRecorder()
	r.ServeHTTP(ui, httptest.NewRequest(http.MethodGet, IndexURL, nil))
	if ui.Code != http.StatusOK {
		t.Fatalf("%s returned %d", IndexURL, ui.Code)
	}
	if !strings.Contains(ui.Body.String(), DocPath) {
		t.Fatalf("Swagger UI page does not point at %s", DocPath)
	}
}
