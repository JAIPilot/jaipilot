//go:build !windows

package jaipilot

import (
	"os"
	"os/exec"
	"syscall"
)

func shellCommand(root, command string) *exec.Cmd {
	cmd := exec.Command("/bin/sh", "-lc", command)
	cmd.Dir = root
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	return cmd
}
func terminate(cmd *exec.Cmd, force bool) {
	sig := syscall.SIGTERM
	if force {
		sig = syscall.SIGKILL
	}
	if cmd.Process != nil {
		_ = syscall.Kill(-cmd.Process.Pid, sig)
	}
}
func lockFile(file *os.File) error {
	return syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB)
}
func unlockFile(file *os.File) error { return syscall.Flock(int(file.Fd()), syscall.LOCK_UN) }

func terminationSignals() []os.Signal { return []os.Signal{os.Interrupt, syscall.SIGTERM} }
