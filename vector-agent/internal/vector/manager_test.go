package vector

import (
	"testing"
	"time"
)

func TestComponentMetricsFromInternalMetricLinesUsesCounterDeltas(t *testing.T) {
	manager := &Manager{}
	now := time.Date(2026, 6, 1, 10, 15, 0, 0, time.UTC)

	first := manager.componentMetricsFromInternalMetricLines([]string{
		`{"name":"component_sent_events_total","tags":{"component_id":"file_source","component_kind":"source","component_type":"file"},"counter":{"value":100.0}}`,
		`{"name":"component_sent_event_bytes_total","tags":{"component_id":"file_source","component_kind":"source","component_type":"file"},"counter":{"value":4096.0}}`,
	}, now)

	if first["file_source"].EventsProcessed != 100 {
		t.Fatalf("first events = %d, want 100", first["file_source"].EventsProcessed)
	}
	if first["file_source"].BytesProcessed != 4096 {
		t.Fatalf("first bytes = %d, want 4096", first["file_source"].BytesProcessed)
	}

	second := manager.componentMetricsFromInternalMetricLines([]string{
		`{"name":"component_sent_events_total","tags":{"component_id":"file_source","component_kind":"source","component_type":"file"},"counter":{"value":135.0}}`,
		`{"name":"component_sent_event_bytes_total","tags":{"component_id":"file_source","component_kind":"source","component_type":"file"},"counter":{"value":6144.0}}`,
	}, now.Add(time.Second))

	if second["file_source"].EventsProcessed != 35 {
		t.Fatalf("second events = %d, want 35", second["file_source"].EventsProcessed)
	}
	if second["file_source"].BytesProcessed != 2048 {
		t.Fatalf("second bytes = %d, want 2048", second["file_source"].BytesProcessed)
	}
}
