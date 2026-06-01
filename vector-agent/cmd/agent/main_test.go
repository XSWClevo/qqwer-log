package main

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
)

func TestNewLogWriterWritesToFileAndStdout(t *testing.T) {
	tempDir := t.TempDir()
	stdout := &bytes.Buffer{}

	writer, err := newLogWriter(tempDir, stdout)
	if err != nil {
		t.Fatalf("newLogWriter returned error: %v", err)
	}

	message := []byte("hello vector agent\n")
	if _, err := writer.Write(message); err != nil {
		t.Fatalf("writer.Write returned error: %v", err)
	}

	if got := stdout.String(); got != string(message) {
		t.Fatalf("stdout = %q, want %q", got, string(message))
	}

	logPath := filepath.Join(tempDir, "agent.log")
	data, err := os.ReadFile(logPath)
	if err != nil {
		t.Fatalf("os.ReadFile returned error: %v", err)
	}

	if got := string(data); got != string(message) {
		t.Fatalf("log file = %q, want %q", got, string(message))
	}
}
