package jaipilot

import (
	"encoding/csv"
	"fmt"
	"os/exec"
	"strconv"
	"strings"
)

func processAlive(pid int) bool {
	data, err := exec.Command("tasklist.exe", "/FI", fmt.Sprintf("PID eq %d", pid), "/FO", "CSV", "/NH").Output()
	if err != nil {
		return true
	}
	rows, _ := csv.NewReader(strings.NewReader(string(data))).ReadAll()
	for _, row := range rows {
		if len(row) > 1 && row[1] == strconv.Itoa(pid) {
			return true
		}
	}
	return false
}
