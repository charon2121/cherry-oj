package node_test

import (
	"bytes"
	"encoding/json"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestInstallHTTPAuthAndTrailingParts(t *testing.T) {
	root := t.TempDir()
	n, _ := newNode(t, root)
	defer n.Close()
	handler := n.Handler(http.NotFoundHandler())
	for _, tc := range []struct {
		name, token string
		extra       bool
		status      int
	}{{"unauthorized", "wrong", false, 401}, {"extra part", "test-control-token", true, 422}, {"valid", "test-control-token", false, 200}} {
		t.Run(tc.name, func(t *testing.T) {
			b := archive(t, []string{"1.in", "1.out"}, [][]byte{[]byte("1 2\n"), []byte("3\n")}, false)
			m := metadata(n, b)
			var body bytes.Buffer
			writer := multipart.NewWriter(&body)
			part, e := writer.CreateFormField("metadata")
			if e != nil {
				t.Fatal(e)
			}
			if e = json.NewEncoder(part).Encode(m); e != nil {
				t.Fatal(e)
			}
			part, e = writer.CreateFormFile("archive", "asset.zip")
			if e != nil {
				t.Fatal(e)
			}
			if _, e = part.Write(b); e != nil {
				t.Fatal(e)
			}
			if tc.extra {
				if _, e = writer.CreateFormField("extra"); e != nil {
					t.Fatal(e)
				}
			}
			if e = writer.Close(); e != nil {
				t.Fatal(e)
			}
			request := httptest.NewRequest("POST", "/internal/judge-node/v1/install", &body)
			request.Header.Set("Content-Type", writer.FormDataContentType())
			request.Header.Set("Authorization", "Bearer "+tc.token)
			response := httptest.NewRecorder()
			handler.ServeHTTP(response, request)
			if response.Code != tc.status {
				t.Fatalf("%d %s", response.Code, response.Body.String())
			}
			if tc.status != 200 {
				if _, e := os.Stat(filepath.Join(root, m.TestDataVersionID)); !os.IsNotExist(e) {
					t.Fatal("failed HTTP request installed data")
				}
			}
		})
	}
}
