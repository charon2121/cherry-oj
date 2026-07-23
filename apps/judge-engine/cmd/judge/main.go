package main

import (
	"encoding/json"
	"log"
	"net/http"
)

func main() {
	addr := "127.0.0.1:5051"
	mux := http.NewServeMux()

	mux.HandleFunc("GET /version", versionHandler)
	log.Printf("judge listening on %s", addr)

	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatal(err)
	}
}

func versionHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"name":    "cherry-oj-judge",
		"version": "0.1.0-mvp",
	})
}
